package com.tancheng.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisDate {
    private LocalDateTime expireTime;
    private Object date;
}
