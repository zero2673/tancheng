package com.tancheng.service;

import com.tancheng.dto.Result;
import com.tancheng.entity.VoucherOrder;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 模拟支付：把订单从「未支付」改成「已支付」
     */
    Result payOrder(Long orderId);

    /**
     * 处理到期的未支付订单（取消 + 回滚库存），返回处理条数
     */
    int cancelTimeoutOrders();
}
