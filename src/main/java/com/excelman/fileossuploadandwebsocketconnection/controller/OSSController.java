package com.excelman.fileossuploadandwebsocketconnection.controller;

import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.excelman.fileossuploadandwebsocketconnection.entity.StsTokenResponse;
import com.excelman.fileossuploadandwebsocketconnection.result.Result;
import com.excelman.fileossuploadandwebsocketconnection.utils.OSSUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author Excelman
 * @date 2021/9/28 下午2:48
 * @description
 */
@RestController
@RequestMapping("/oss")
public class OSSController {

    @Resource
    private OSSUtils ossUtils;

    @GetMapping("/getStsToken")
    public Result getStsToken(){
        AssumeRoleResponse stsToken = ossUtils.getStsToken();
        StsTokenResponse stsTokenResponse = new StsTokenResponse();
        stsTokenResponse.setAccessKeyId(stsToken.getCredentials().getAccessKeyId());
        stsTokenResponse.setAccessKeySecret(stsToken.getCredentials().getAccessKeySecret());
        stsTokenResponse.setSecurityToken(stsToken.getCredentials().getSecurityToken());
        return Result.success(stsTokenResponse);
    }
}
