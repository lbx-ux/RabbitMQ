package com.study.mq.consumer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.mq.consumer.entity.ConsumedMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConsumedMessageMapper extends BaseMapper<ConsumedMessage> {
}
