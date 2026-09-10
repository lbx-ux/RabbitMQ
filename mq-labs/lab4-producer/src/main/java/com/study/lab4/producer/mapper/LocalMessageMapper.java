package com.study.lab4.producer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.lab4.producer.entity.LocalMessage;
import org.apache.ibatis.annotations.Mapper;

/** 本地消息表 Mapper（MyBatis-Plus 自动扫描 @Mapper 接口，单表 CRUD 免写 SQL） */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {
}
