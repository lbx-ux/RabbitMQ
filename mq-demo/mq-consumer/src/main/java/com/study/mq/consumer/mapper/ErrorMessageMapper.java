package com.study.mq.consumer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.mq.consumer.entity.ErrorMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErrorMessageMapper extends BaseMapper<ErrorMessage> {
}
