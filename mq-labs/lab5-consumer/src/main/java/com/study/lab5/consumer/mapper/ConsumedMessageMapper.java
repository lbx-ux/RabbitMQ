package com.study.lab5.consumer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.lab5.consumer.entity.ConsumedMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消费记录 Mapper —— MyBatis-Plus 自动扫描 @Mapper，提供 insert 等基础 CRUD
 */
@Mapper
public interface ConsumedMessageMapper extends BaseMapper<ConsumedMessage> {
}
