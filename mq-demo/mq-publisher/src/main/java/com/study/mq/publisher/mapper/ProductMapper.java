package com.study.mq.publisher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.mq.publisher.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/** 商品表 Mapper：MyBatis-Plus 的 BaseMapper 已提供常规 CRUD */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
