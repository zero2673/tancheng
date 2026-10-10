package com.tancheng.controller;

import com.tancheng.dto.Result;
import com.tancheng.service.IVoucherOrderService;
import com.tancheng.service.SeckillResultService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 优惠券秒杀下单接口。
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private SeckillResultService seckillResultService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 订阅秒杀结果。前端拿到 orderId 后：
     * const es = new EventSource('/api/voucher-order/seckill/result/' + orderId);
     * es.addEventListener('result', e => { ... es.close(); });
     */
    @GetMapping(value = "seckill/result/{orderId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter seckillResult(@PathVariable("orderId") Long orderId) {
        // 订单已落库（消费比订阅更快），直接返回结果，避免前端等 30 秒
        if (voucherOrderService.getById(orderId) != null) {
            return seckillResultService.completed(true, "下单成功");
        }
        return seckillResultService.subscribe(orderId);
    }
}