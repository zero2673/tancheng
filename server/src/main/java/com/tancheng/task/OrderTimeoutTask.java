package com.tancheng.task;

import com.tancheng.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时关单定时任务。
 *
 * 每 10 秒从 Redis 延时队列取一批到期订单，取消并回滚库存。
 * 取单动作由 Lua 原子完成（order_timeout.lua），多实例部署不会重复处理同一订单。
 */
@Slf4j
@Component
public class OrderTimeoutTask {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Scheduled(fixedDelayString = "${app.order-timeout-scan-ms:10000}")
    public void cancelTimeoutOrders() {
        try {
            int cancelled = voucherOrderService.cancelTimeoutOrders();
            if (cancelled > 0) {
                log.info("本轮取消超时订单 {} 笔", cancelled);
            }
        } catch (Exception e) {
            log.error("处理超时订单失败", e);
        }
    }
}