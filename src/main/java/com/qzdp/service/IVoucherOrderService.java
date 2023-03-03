package com.qzdp.service;

import com.qzdp.dto.Result;
import com.qzdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *

 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     *  秒杀优惠卷
     * @param voucherId
     * @return
     */
    Result secKillVoucher(Long voucherId);

}
