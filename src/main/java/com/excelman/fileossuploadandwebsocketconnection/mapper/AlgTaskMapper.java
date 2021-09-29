package com.excelman.fileossuploadandwebsocketconnection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.excelman.fileossuploadandwebsocketconnection.entity.AlgTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author SenseMobile
 * @since 2021-01-18
 */
@Mapper
public interface AlgTaskMapper extends BaseMapper<AlgTask> {

    @Update("UPDATE t_alg_task SET output = #{output} WHERE task_id = #{uuid}")
    void updateAlgTaskByUUID(String uuid, String output);
}
