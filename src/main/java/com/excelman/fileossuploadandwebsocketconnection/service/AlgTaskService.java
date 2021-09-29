package com.excelman.fileossuploadandwebsocketconnection.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.excelman.fileossuploadandwebsocketconnection.entity.AlgTask;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author SenseMobile
 * @since 2021-01-18
 */
public interface AlgTaskService extends IService<AlgTask> {

    void updateAlgTaskByUUID(String uuid, String output);

}
