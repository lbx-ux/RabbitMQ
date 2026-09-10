package com.study.lab5.consumer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.lab5.consumer.entity.SmsRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信记录 Mapper
 */
@Mapper
public interface SmsRecordMapper extends BaseMapper<SmsRecord> {
}
