package com.tancheng.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 秒杀结果实时推送（SSE）。
 *
 * 流程：下单接口返回 orderId → 前端用 orderId 订阅本服务 → Redis Stream 消费端落库完成后
 * 调用 complete(...) 把结果推给前端，前端无需轮询。
 *
 * 提醒：emitter 存在单机内存中。如果以后多实例部署，需要用 Redis Pub/Sub 把结果广播到
 * 持有该连接的那台机器，否则会推送不到。
 */
@Slf4j
@Service
public class SeckillResultService {

    private static final long TIMEOUT_MS = 30_000L;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long orderId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.put(orderId, emitter);
        emitter.onCompletion(() -> emitters.remove(orderId));
        emitter.onTimeout(() -> {
            emitters.remove(orderId);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(orderId));
        try {
            emitter.send(SseEmitter.event().name("connected").data(orderId));
        } catch (IOException e) {
            emitters.remove(orderId);
            log.warn("SSE 连接建立失败, orderId={}", orderId, e);
        }
        return emitter;
    }

    public void complete(Long orderId, boolean success, String message) {
        SseEmitter emitter = emitters.remove(orderId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("result").data(Map.of(
                    "orderId", orderId,
                    "success", success,
                    "message", message
            )));
            emitter.complete();
        } catch (Exception e) {
            log.warn("推送秒杀结果失败, orderId={}", orderId, e);
        }
    }

    public SseEmitter completed(boolean success, String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name("result").data(Map.of(
                    "success", success,
                    "message", message
            )));
            emitter.complete();
        } catch (IOException e) {
            log.warn("发送秒杀结果失败", e);
        }
        return emitter;
    }
}