package com.excelman.fileossuploadandwebsocketconnection.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Excelman
 * @date 2021/9/28 下午4:55
 * @description
 */
@Data
@Getter
@Setter
public class StsTokenResponse {

    private String accessKeyId;

    private String AccessKeySecret;

    private String SecurityToken;

}
