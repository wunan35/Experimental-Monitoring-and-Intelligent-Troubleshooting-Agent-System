package org.example.config;

import org.example.interceptor.TraceInterceptor;
import org.example.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 配置静态资源和拦截器
 * 注意: CORS配置已移至CorsConfig类
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TraceInterceptor traceInterceptor;

    @Autowired(required = false)
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源映射
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册追踪拦截器
        registry.addInterceptor(traceInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/info", "/api/actuator/**");

        // 注册限流拦截器（如果存在）
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns("/api/health", "/api/info", "/api/actuator/**");
        }
    }
}
