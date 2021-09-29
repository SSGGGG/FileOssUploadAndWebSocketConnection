# 一、背景
最近因为有个需求：
1. web前端同时上传多个大文件，并且要求支持断点续传和取消上传，还要显示上传进度条
2. 上传完成之后，前端自动将文件传至后台，后台调用算法SDK进行压缩，同时显示压缩进度，压缩成功后返回压缩后的内容，提供url下载

（<font color='red'>前提是压缩算法SDK的入参是文件的url路径，并且算法SDK在压缩完成之后负责上传压缩后的文件到OSS，最终返回结果是压缩后文件的url路径</font>）

以下代码保存在github中：[FileOssUploadAndWebSocketConnection代码](https://github.com/SSGGGG/FileOssUploadAndWebSocketConnection)

# 二、实现方案
**第一个需求**
对于第一个需求，实现方案是采用OSS的断点续传SDK。直接由Web前端将文件上传到OSS，并得到文件的url路径。
这样做的好处是：绕过SpringBoot后台上传，可以避免多一个步骤（前端传给后台，后台再传给OSS），加快上传的速度。

**第二个需求**
对于第二个需求，由于是同时压缩多个文件，因此采用线程池异步压缩的方式。并且需要后台主动推送完成进度，实现前端的压缩进度条，因此采用WebSocket，主要的流程如下：

1. 客户端获取到上传文件在OSS中的url之后，随机生成一个uuid作为当前文件的id，携带文件url以及uuid，调用后台接口，并且以uuid作为长连接的唯一凭证，与服务端建立WebSocket长连接；

2. 后台接口处：采用线程池实现并发异步压缩。

   线程的工作即：创建新的数据alg_task，其中taskId为前端传的uuid，fileUrl为前端传的文件url，并调用压缩算法SDK进行压缩，算法压缩完成之后将文件上传OSS（<font color='red'>这里判断是否为大文件，若是的话，则采用OSS分片上传SDK</font>），将得到的结果返回后台更新到数据库中（其中包含了压缩文件在OSS的url），结束线程。

   在线程工作的过程中，后台通过uuid的websocket长连接传送当前完成的进度。

但如果压缩算法SDK支持入参是**文件流**的话，其实最好的方案是Web前端分片断点续传到后台-----后台分片上传到算法SDK-----算法SDK处理完成之后自行上传压缩后的文件到OSS，得到url路径-----返回url。
这样的话整个流程总共只需要四次IO传输（前端传输文件----后台传输文件----算法处理完成上传OSS----返回url给后台），而我上述提到的方案的整个流程总共需要六次IO传输（前端上传OSS----前端传输参数给后台----后台传输参数给算法----算法根据url从OSS拉取文件----算法处理完成上传文件----返回url给后台）。

# 三、具体实现流程——Web前端断点续传大文件到OSS
本文采用传统的html+js实现前端，相关的阿里云OSS文档：[传统JS的SDK示例](https://help.aliyun.com/document_detail/64041.html)

(<font color='red'>记得一定要按照这里的要求配置跨域问题</font>)

文档首先就提出为了遵循阿里云的安全最佳实践，前端应该采用RAM用户和STS凭证的方式调用OSS的SDK。因此本文也采用RAM用户和STS的方式，具体创建RAM用户和STS凭证的方式见官方文档：[创建RAM用户和STS凭证](https://help.aliyun.com/document_detail/100624.html)。

### 3.1、后台实现
这里后台的实现很简单，只需要提供一个返回STS凭证的接口。

**1. 加载maven依赖**
```xml
<!-- OSS -->
<dependency>
     <groupId>com.aliyun.oss</groupId>
     <artifactId>aliyun-sdk-oss</artifactId>
     <version>3.10.2</version>
 </dependency>
 <!-- OSS_STS -->
 <dependency>
     <groupId>com.aliyun</groupId>
     <artifactId>aliyun-java-sdk-sts</artifactId>
     <version>3.0.0</version>
 </dependency>
 <dependency>
     <groupId>com.aliyun</groupId>
     <artifactId>aliyun-java-sdk-core</artifactId>
     <version>4.4.6</version>
 </dependency>
```

**2. yml配置文件**
```yml
aliyun:
  oss:
    stsEndpoint: sts.cn-shenzhen.aliyuncs.com
    stsAccessKeyId: xxx
    stsAccessKeySecret: xxx
    stsRoleArn: xxx
```
**3. OSSUtils工具类**

```java
@Component
public class OSSUtils {

    private static final Logger logger = LoggerFactory.getLogger(OSSUtils.class);

    @Value("${aliyun.oss.stsEndpoint}")
    private String stsEndpoint;
    @Value("${aliyun.oss.stsAccessKeyId}")
    private String stsAccessKeyId;
    @Value("${aliyun.oss.stsAccessKeySecret}")
    private String stsAccessKeySecret;
    @Value("${aliyun.oss.stsRoleArn}")
    private String stsRoleArn;

    private static final String POLICY = "{\n" +
            "    \"Version\": \"1\",\n" +
            "    \"Statement\": [\n" +
            "        {\n" +
            "            \"Effect\": \"Allow\",\n" +
            "            \"Action\": [\n" +
            "                \"oss:ListObjects\",\n" +
            "                \"oss:GetObject\",\n" +
            "                \"oss:PutObject\"\n" +
            "            ],\n" +
            "            \"Resource\": [\n" +
            "                \"acs:oss:*:*:excelman\",\n" +
            "                \"acs:oss:*:*:excelman/*\"\n" +
            "            ]\n" +
            "        }\n" +
            "    ]\n" +
            "}\n";
    private static final String roleSessionName = "excelmanInfo"; //　自定义的角色会话名称

    /**
     * 获取sts的凭证
     * @return
     */
    public AssumeRoleResponse getStsToken(){
        try {
            // 添加endpoint（直接使用STS endpoint，前两个参数留空，无需添加region ID）
            DefaultProfile.addEndpoint("", "", "Sts", stsEndpoint);
            // 构造default profile（参数留空，无需添加region ID）
            IClientProfile profile = DefaultProfile.getProfile("", stsAccessKeyId, stsAccessKeySecret);
            // 用profile构造client
            DefaultAcsClient client = new DefaultAcsClient(profile);
            final AssumeRoleRequest request = new AssumeRoleRequest();
            request.setMethod(MethodType.POST);
            request.setRoleArn(stsRoleArn);
            request.setRoleSessionName(roleSessionName);
            // 若policy为空，则用户将获得该角色下所有权限
            request.setPolicy(POLICY);
            // 设置凭证有效时间
            request.setDurationSeconds(3600L);
            final AssumeRoleResponse response = client.getAcsResponse(request);
            System.out.println("Expiration: " + response.getCredentials().getExpiration());
            System.out.println("Access Key Id: " + response.getCredentials().getAccessKeyId());
            System.out.println("Access Key Secret: " + response.getCredentials().getAccessKeySecret());
            System.out.println("Security Token: " + response.getCredentials().getSecurityToken());
            System.out.println("RequestId: " + response.getRequestId());
            return response;
        } catch (ClientException e) {
            logger.error("getStsToken发生了异常：{}",e);
        }
        return null;
    }
}
```
**4. Controller接口**
```java
@RestController
@RequestMapping("/oss")
public class OSSController {

    @Resource
    private OSSUtils ossUtils;

    @GetMapping("/getStsToken")
    public Result getStsToken(){
        AssumeRoleResponse stsToken = ossUtils.getStsToken();
        // StsTokenResponse是我自定义的一个实体类，可以不用这个，直接返回结果
        StsTokenResponse stsTokenResponse = new StsTokenResponse();
        stsTokenResponse.setAccessKeyId(stsToken.getCredentials().getAccessKeyId());
        stsTokenResponse.setAccessKeySecret(stsToken.getCredentials().getAccessKeySecret());
        stsTokenResponse.setSecurityToken(stsToken.getCredentials().getSecurityToken());
        return Result.success(stsTokenResponse);
    }
}
```


### 3.2、前端实现
前端的实现主要有以下的关键点：
- 调用aliyun-oss SDK之前访问后台接口获取sts token
- 定义上传分片大小,如果文件小于分片大小则采用普通上传,否则使用分片上传(断点续传)
- 上传过程中展示上传进度
- 上传过程中,如果STS Token快过期了,先暂停上传重新获取token
- 支持手动暂停/续传功能
- 上传完成之后返回文件对应的url

这里的实现借鉴了其他博客：[阿里云OSS上传](https://segmentfault.com/a/1190000020963346)
（若有侵权，请私聊删除哈～）

**1. 引入SDK**
参考链接，内含多种方式：[引入SDK](https://help.aliyun.com/document_detail/32069.html)

**2. HTML**
```html
<div>
  <input type="file" id='fileInput' multiple='true'>
  <button id="uploadBtn" onclick="upload()">Upload</button>
  <button id="stopBtn" onclick="stop()">Stop</button>
  <button id="resumeBtn" onclick="resume()">resume</button>
  <h2 id='status'></h2>
</div>
```

**3. 定义变量**
```js
let credentials = null; // STS凭证
let ossClient = null; // oss客户端实例
const fileInput = document.getElementById('fileInput'); // 文件选择器
const status = document.getElementById('status'); // 状态显示元素
const bucket = 'mudontire-test'; // bucket名称
const region = 'oss-cn-shanghai'; // oss服务区域名称
const partSize = 1024 * 1024; // 每个分片大小(byte)
const parallel = 3; // 同时上传的分片数
const checkpoints = {}; // 所有分片上传文件的检查点
```
**4. 获取STS凭证，创建OSS Client**
```js
// 获取STS Token
function getCredential() {
    return fetch('http://localhost:9505/oss/getStsToken')
        .then(res => {
            return res.json()
        })
        .then(res => {
            console.log("获取Credential成功!")
            credentials = res.data;
        })
        .catch(err => {
            console.error(err);
        });
}

// 获取STS Token
getCredential();

// 创建OSS Client
async function initOSSClient() {
    ossClient = new OSS({
        accessKeyId: credentials.accessKeyId,
        accessKeySecret: credentials.accessKeySecret,
        stsToken: credentials.securityToken,
        bucket,
        region
    });
    console.log("初始化OSSClient成功!")
}
```

**5. 定义三个按钮事件**
```js
// 点击上传按钮事件
async function upload() {
   status.innerText = 'Uploading...';
   const { files } = fileInput;
   const fileList = Array.from(files);
   const uploadTasks = fileList.forEach(file => {
       // 如果文件大学小于分片大小，使用普通上传，否则使用分片上传
       if (file.size < partSize) {
           commonUpload(file);
       } else {
           multipartUpload(file);
       }
   });
}

// 点击暂停上传按钮事件
function stop(){
   status.innerText = 'Stopping...';
   if (ossClient) ossClient.cancel();
}

// 点击续传按钮事件
function resume(){
   status.innerText = 'Resuming...';
   if (ossClient) resumeMultipartUpload();
}
```

**6. 普通上传的方式**
```js
// 普通上传方式
async function commonUpload(file){
    if (!ossClient) {
        await initOSSClient();
    }
    const fileName = file.name;
    return ossClient.put(fileName, file).then(result => {
        console.log(`Common upload ${file.name} succeeded, result === `, result)
        status.innerText = 'Success!';
    }).catch(err => {
        console.log(`Common upload ${file.name} failed === `, err);
    });
}
```
**7. 分片上传的方式**
```js
// 分片上传方式
async function multipartUpload(file){
    if(!ossClient){
        await initOSSClient();
    }
    const fileName = file.name;
    return ossClient.multipartUpload(fileName, file, {
        parallel,
        partSize,
        progress: onMultipartUploadProgress
    }).then(result => {
        // 生成文件下载地址
        const url = `http://${bucket}.${region}.aliyuncs.com/${fileName}`;
        console.log(`Multipart upload ${file.name} succeeded, url === `, url)
        status.innerText = 'Success!';
    }).catch(err => {
        console.log(`Multipart upload ${file.name} failed === `, err);
    });
}
```

**8. 分片上传进度回调函数**
```js
// 分片上传进度改变回调
async function onMultipartUploadProgress(progress, checkpoint) {
    console.log(`${checkpoint.file.name} 上传进度 ${progress}`);
    checkpoints[checkpoint.uploadId] = checkpoint;
    // 判断STS Token是否将要过期，过期则重新获取
    const { Expiration } = credentials;
    const timegap = 1;
    if (Expiration && moment(Expiration).subtract(timegap, 'minute').isBefore(moment())) {
        console.log(`STS token will expire in ${timegap} minutes，uploading will pause and resume after getting new STS token`);
        if (ossClient) {
            ossClient.cancel();
        }
        await getCredential();
        await resumeMultipartUpload();
    }
}
```

**9. 断点续传**
```js
// 断点续传
async function resumeMultipartUpload() {
    console.log("断点续传...")
    // 遍历checkpoint,上传剩下的文件分片
    Object.values(checkpoints).forEach((checkpoint) => {
        const { uploadId, file, name } = checkpoint;
        ossClient.multipartUpload(uploadId, file, {
            parallel,
            partSize,
            progress: onMultipartUploadProgress,
            checkpoint
        }).then(result => {
            console.log('before delete checkpoints === ', checkpoints);
            delete checkpoints[checkpoint.uploadId];
            console.log('after delete checkpoints === ', checkpoints);
            const url = `http://${bucket}.${region}.aliyuncs.com/${name}`;
            console.log(`Resume multipart upload ${file.name} succeeded, url === `, url)
            status.innerText = 'Success!';
        }).catch(err => {
            console.log('Resume multipart upload failed === ', err);
        });
    });
}
```
### 3.3、效果图
**上传页面**
![在这里插入图片描述](https://img-blog.csdnimg.cn/05be09bc1a984e8691bdfc9d78649743.png#pic_center)
**暂停上传页面，log中显示进度（进度条还没实现）**
![在这里插入图片描述](https://img-blog.csdnimg.cn/7b02b431f12a4dcd8504fd29d1428767.png?x-oss-process=image/watermark,type_ZHJvaWRzYW5zZmFsbGJhY2s,shadow_50,text_Q1NETiBARXhjZWxNYW5f,size_16,color_FFFFFF,t_70,g_se,x_16#pic_center)**断点续传页面**
![在这里插入图片描述](https://img-blog.csdnimg.cn/42d203b3187544ceb3d0a8d9fbf04487.png?x-oss-process=image/watermark,type_ZHJvaWRzYW5zZmFsbGJhY2s,shadow_50,text_Q1NETiBARXhjZWxNYW5f,size_20,color_FFFFFF,t_70,g_se,x_16#pic_center)

# 四、具体实现流程——WebSocket+线程池实现多文件异步压缩并推送压缩进度
采用的技术是WebSocket以及SockJs和Stomp协议，详细内容见以往的一篇博客：
[【原创】基于Springboot、WebSocket的一对一聊天室](https://blog.csdn.net/a602389093/article/details/83271886)

### 4.1、后台实现
**1. 引入maven依赖**
```xml
<!-- websocket -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

**2. WebSocket配置**
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {
    /**
     * 注册Stomp协议的节点（endpoint），并指定映射的url
     * @param registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/endpointExcelman").setAllowedOrigins("*").withSockJS();
    }
    /**
     * 配置消息代理
     * @param registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 点对点配置一个消息代理
        registry.enableSimpleBroker("/uuid");
        // 点对点使用的订阅前缀
        registry.setUserDestinationPrefix("/uuid");
    }
}
```

**3. 跨域配置**
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    static final String ORIGINS[] = new String[] { "GET", "POST", "PUT", "DELETE" };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 所有的当前站点的请求地址，都支持跨域访问。
                .allowedOrigins("*") // 所有的外部域都可跨域访问。 如果是localhost则很难配置，因为在跨域请求的时候，外部域的解析可能是localhost、127.0.0.1、主机名
                .allowCredentials(true) // 是否支持跨域用户凭证
                .allowedMethods(ORIGINS) // 当前站点支持的跨域请求类型是什么
                .maxAge(3601); // 超时时长设置为1小时。 时间单位是秒。
    }
}
```

**4. Controller接口**

`SimpMessagingTemplate`是org.springframework.messaging.simp包下的一个类，用于将信息传输到指定的长连接路径上（这里指WebSocket建立的长连接）。

```java
@RestController
public class CompressController {

    private static final Logger logger = LoggerFactory.getLogger(CompressController.class);

    /**
     * 伪造进度条
     */
    private static final Map<Integer,String> PROGRESS = new HashMap<>();
    static{
        PROGRESS.put(1,"10%");  PROGRESS.put(2, "40%");  PROGRESS.put(3, "70%");    PROGRESS.put(4, "90%");    PROGRESS.put(5, "100%");
    }

    @Resource(name = "threadPool")
    private ThreadPoolExecutor executor;

    @Resource
    private AlgTaskService taskService;

    /**
     * WebSocket发送消息的template
     */
    @Resource
    private SimpMessagingTemplate template;

    /**
     * 主线程创建新的AlgTask，其中uuid为taskId
     * 线程池创建Thread异步执行以下流程：
     *      1. 调用压缩算法
     *      2. 压缩算法SDK处理完成，自行上传OSS，返回url
     *      3. 将得到的URL存储到数据库
     *      （期间打点推送进度）
     * @param uuid 作为taskId
     * @param fileUrl 文件的url
     * @return
     */
    @PostMapping("/compressTask")
    public Result compressTask(String uuid, String fileUrl) throws InterruptedException {

        Thread.sleep(3000L);
		
		// 第一个参数：唯一辨识凭证；第二个参数：长连接路径（前端定义）；第三个参数：传输内容
        template.convertAndSendToUser(uuid, "/queue/getResponse", PROGRESS.get(1));

        // main thread
        AlgTask algTask = new AlgTask();
        algTask.setUserName("ADMIN");
        algTask.setApiName("/compressTask");
        algTask.setModuleName("compress");
        algTask.setUrl("/compressTask");
        algTask.setMethod("POST");
        algTask.setTaskId(uuid);
        algTask.setInput(fileUrl); // 暂放input
        taskService.save(algTask);

        Thread.sleep(3000L);

        template.convertAndSendToUser(uuid, "/queue/getResponse", PROGRESS.get(2));

        // thread pool
        executor.execute(()->{
            logger.info("开始压缩...当前线程名:{}",Thread.currentThread().getName());

            template.convertAndSendToUser(uuid, "/queue/getResponse", PROGRESS.get(3));

            // todo 调用压缩算法，得到返回结果

            template.convertAndSendToUser(uuid, "/queue/getResponse", PROGRESS.get(4));

            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String compressResultUrl = "http://compressResult.jpg";
            taskService.updateAlgTaskByUUID(uuid, compressResultUrl);

            logger.info("压缩完成...");

            template.convertAndSendToUser(uuid, "/queue/getResponse", PROGRESS.get(5));
        });
        return Result.success(null);
    }
}
```

### 4.2、前端实现
**1. 引入JS**
```js
<!-- JQuery -->
<script type="text/javascript" src="http://apps.bdimg.com/libs/jquery/2.1.4/jquery.min.js" charset="utf-8"></script>
<!-- SockJs -->
<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.1.4/sockjs.min.js" charset="utf-8"></script>
<!-- Stomp -->
<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js" charset="utf-8"></script>
```
**2. Html**
```html
<div>
    <h1>Compress Test</h1>
    <!-- 生成多个不同uuid的文件，并且与服务端建立websocket连接，等待进度条推送 -->
    <button onclick="addTask()">点击生成新的文件任务</button>
    <div id="compressQueue">
    </div>
</div>
```

**3. 生成新的文件任务**
```js
/*
生成新的文件任务
1. 新增div
2. 创建新的websocket，建立长连接
3. ajax调用后台接口，显示进度条
 */
function addTask(){
    var uuid = getUuid();
    var fileUrl = "http://testUploadUrl.jpg";

    var newDiv = "<div>" +
        "<div>当前任务的uuid：" + uuid + "</div>" +
        "<div style='background-color: wheat; height: 50px; width: 500px;' id='progress" + uuid + "'></div>" +
        "</div>";
    var compressQueue = $("#compressQueue");
    compressQueue.append(newDiv);
	
	// websocket连接
    connect(uuid);

    // ajax
    $.ajax({
        url:'http://localhost:9505/compressTask',
        method:'POST',
        data: {
            "uuid" : uuid,
            "fileUrl" : fileUrl
        },
        success: function (result) {
            console.log("ajax 成功，结果："+result)
        },
        error: function (error) {
            console.log("ajax 发生错误"+error)
        }
    });
}
```

**4. 获取uuid**
```js
// 生成uuid
function getUuid() {
    var s = [];
    var hexDigits = "0123456789abcdef";
    for (var i = 0; i < 36; i++) {
        s[i] = hexDigits.substr(Math.floor(Math.random() * 0x10), 1);
    }
    s[14] = "4"; // bits 12-15 of the time_hi_and_version field to 0010
    s[19] = hexDigits.substr((s[19] & 0x3) | 0x8, 1); // bits 6-7 of the clock_seq_hi_and_reserved to 01
    s[8] = s[13] = s[18] = s[23] = "-";

    var uuid = s.join("");
    return uuid;
}
```

**5. WebSocket连接**
```js
// websocket连接
function connect(uuid){
    console.log(uuid+"开始连接WebSocket");

    var socket = new SockJS('http://127.0.0.1:9505/endpointExcelman'); //连接SockJS的endpoint名称为"endpointOyzc"
    var stompClient = Stomp.over(socket);//使用STMOP子协议的WebSocket客户端
    stompClient.connect({},function(frame){//连接WebSocket服务端
        console.log('Connected:' + frame);
        //通过stompClient.subscribe订阅/queue/getResponse 目标(destination)发送的消息
        stompClient.subscribe('/uuid/' + uuid + '/queue/getResponse',function(response){
            console.log("当前的response:" + response.body);
            showProgress(response.body, uuid);
            // 判断进度条是否完成，若完成的话，释放websocket连接
            if(response.body === "100%"){
                disconnect(stompClient);
            }
        });
    });
}
```

**6. 推进进度条**
```js
// 推进进度条
function showProgress(currentProgress, uuid){
    var progressUuid = $("#progress"+uuid);
    progressUuid.append(currentProgress);
}
```

**7. 关闭wbesocket双通道**
```js
//关闭双通道
function disconnect(stompClient){
     if(stompClient != null) {
         stompClient.disconnect();
     }
     console.log("Disconnected");
 }
```

### 4.3、效果图
**首页**
![在这里插入图片描述](https://img-blog.csdnimg.cn/871e3b41c3a34e17bbc1bfe505ca00c9.png#pic_center)
**压缩任务显示进度条**
![在这里插入图片描述](https://img-blog.csdnimg.cn/ad57795c7a244bd4ada850e2f6f9cdd9.png?x-oss-process=image/watermark,type_ZHJvaWRzYW5zZmFsbGJhY2s,shadow_50,text_Q1NETiBARXhjZWxNYW5f,size_19,color_FFFFFF,t_70,g_se,x_16#pic_center)
