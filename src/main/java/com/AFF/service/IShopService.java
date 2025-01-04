package com.AFF.service;

import com.AFF.dto.Result;
import com.AFF.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author umbrellazg
 * @since 2024-11-22
 */
public interface IShopService extends IService<Shop> {

    Result queryShopById(Long id);
    Result update(Shop shop);
}
