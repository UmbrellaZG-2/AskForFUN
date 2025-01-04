package com.AFF.config;

import com.AFF.utils.LoginInterceptor;
import com.AFF.utils.RefreshInterptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop-type/**",
                        "/voucher/**",
                        "upload/**"
                ).order(1);
        registry.addInterceptor(new RefreshInterptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
    }
}
