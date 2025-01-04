package com.AFF;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.AFF.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class AskingForFunApplication {

    public static void main(String[] args) {
        SpringApplication.run(AskingForFunApplication.class, args);
    }

}
