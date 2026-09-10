package com.study.lab5.consumer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.lab5.consumer.entity.ErrorMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错误消息 Mapper
 */
@Mapper
public interface ErrorMessageMapper extends BaseMapper<ErrorMessage> {
}
