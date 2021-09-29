package com.excelman.fileossuploadandwebsocketconnection.utils;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author Excelman
 * @date 2021/9/28 下午2:40
 * @description OSS工具类
 */
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
