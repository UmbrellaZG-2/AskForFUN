package com.AFF.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.AFF.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author UMBRELLAZG
 * @since 2024-11-21
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
