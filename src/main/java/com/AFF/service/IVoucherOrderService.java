package com.AFF.service;

import com.AFF.dto.Result;
import com.AFF.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author umbrellazg
 * @since 2024-11-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);
    Result createVoucherOrder(Long voucherId);
}
