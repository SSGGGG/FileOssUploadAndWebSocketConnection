package com.excelman.fileossuploadandwebsocketconnection.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.excelman.fileossuploadandwebsocketconnection.entity.AlgTask;
import com.excelman.fileossuploadandwebsocketconnection.mapper.AlgTaskMapper;
import com.excelman.fileossuploadandwebsocketconnection.service.AlgTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author SenseMobile
 * @since 2021-01-18
 */
@Service
public class AlgTaskServiceImpl extends ServiceImpl<AlgTaskMapper, AlgTask> implements AlgTaskService {

    private static final Logger logger = LoggerFactory.getLogger(AlgTaskServiceImpl.class);

    @Resource
    private AlgTaskMapper algTaskMapper;

    @Override
    public void updateAlgTaskByUUID(String uuid, String output) {
        try{
            algTaskMapper.updateAlgTaskByUUID(uuid, output);
        } catch (Exception e){
            logger.error("更新发生异常:{}",e);
        }
    }
}
