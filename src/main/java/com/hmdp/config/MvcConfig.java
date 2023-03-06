package com.hmdp.config;

import com.hmdp.Interceptor.RefreshInterceptor;
import com.hmdp.Interceptor.loginInterceptor;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录验证拦截器
        registry.addInterceptor(new loginInterceptor())
                .excludePathPatterns("/user/login",
                                     "/user/code","/voucher/**",
                                     "/shop/**",
                                     "/shop-type/**",
                                     "/upload/**",
                                     "/blog/hot").order(1);
        //用户ttl刷新拦截器
        registry.addInterceptor(new RefreshInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
