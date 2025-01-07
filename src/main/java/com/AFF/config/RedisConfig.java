package com.AFF.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClinet(){
        Config config=new Config();
        config.useSingleServer().setAddress("redis://101.200.43.186:6379").setPassword("zhangge1121");
        return Redisson.create(config);
    }

}
