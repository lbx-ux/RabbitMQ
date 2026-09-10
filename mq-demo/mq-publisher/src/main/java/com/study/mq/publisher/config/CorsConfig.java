package com.study.mq.publisher.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置 —— 配合「MQ 可视化实验台」前端页面（static/index.html）
 *
 * 【为什么需要它？】
 *  页面正常部署在本服务的 static/ 下（http://localhost:8080）时是同源请求，其实不需要 CORS。
 *  但如果你用 VS Code Live Server（:5500）、直接双击 HTML（file://）等方式打开页面，
 *  浏览器的同源策略就会拦截对 :8080 / :8083 的 fetch 请求。
 *  学习项目从宽处理：放开所有来源 + GET/POST，保证页面用任何方式打开都能用。
 *
 * 【生产环境的正确姿势】（面试也常问）
 *  - 只放行可信来源：allowedOriginPatterns("https://www.your-app.com")
 *  - 需要携带 Cookie 时必须 allowCredentials(true)，且此时【禁止】origins("*")（浏览器直接拒绝）
 *  - 网关层（Nginx/Gateway）统一配置 CORS，业务服务不再各自为政
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")   // 2.4+ 用 OriginPatterns 才能与 allowCredentials 共存；这里不传凭证，等价放行
                .allowedMethods("GET", "POST")
                .maxAge(3600);                // 预检(OPTIONS)结果缓存 1 小时，减少探顶请求
    }
}
