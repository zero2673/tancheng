package com.tancheng;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.tancheng.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class TanChengApplication {

    public static void main(String[] args) {
        SpringApplication.run(TanChengApplication.class, args);
    }

}
