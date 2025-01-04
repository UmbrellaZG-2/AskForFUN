package com.AFF.service;

import com.AFF.dto.Result;
import com.AFF.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author umbrellazg
 * @since 2024.1.2
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryShopType();
}
