package com.study.mq.consumer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置（consumer 侧）—— 让「MQ 可视化实验台」页面能访问本服务的错误管理接口
 *
 * 页面部署在 publisher（:8080）的 static/ 下，而 /error/list、/error/replay 在本服务（:8083），
 * 属于跨源请求 —— 必须在这里显式放行，否则浏览器控制台会报
 * 「blocked by CORS policy: No 'Access-Control-Allow-Origin' header」。
 *
 * 与 publisher 的 CorsConfig 保持一致：学习项目放行所有来源；生产只放可信域名。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST")
                .maxAge(3600);
    }
}
