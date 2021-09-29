package com.excelman.fileossuploadandwebsocketconnection.controller;

import com.excelman.fileossuploadandwebsocketconnection.entity.AlgTask;
import com.excelman.fileossuploadandwebsocketconnection.result.Result;
import com.excelman.fileossuploadandwebsocketconnection.service.AlgTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Excelman
 * @date 2021/9/27 下午7:08
 * @description
 */
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
