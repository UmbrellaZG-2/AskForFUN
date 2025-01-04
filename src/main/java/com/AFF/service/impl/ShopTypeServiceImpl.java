package com.AFF.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.AFF.dto.Result;
import com.AFF.entity.ShopType;
import com.AFF.mapper.ShopTypeMapper;
import com.AFF.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;

import javax.annotation.Resource;
import javax.print.DocFlavor;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.AFF.utils.RedisConstants.CACHE_SHOP_TPYE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author UMBRELLAZG
 * @since 2024-11-21
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryShopType() {
        //查询缓存
        List<String> shopTypeJson = stringRedisTemplate.opsForList().range(CACHE_SHOP_TPYE_KEY,0,-1);
        //存在则返回
        if (CollectionUtil.isNotEmpty(shopTypeJson)){
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson.toString(), ShopType.class);
            shopTypes.sort(((o1, o2) -> o1.getSort() - o2.getSort()));
            return Result.ok(shopTypes);
        }
        //不存在则查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if(CollectionUtil.isEmpty(shopTypes)){
            return Result.fail("店铺类型不存在");
        }
        //写入缓存
        List<String> shopTypesJson = shopTypes.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        // 因为从数据库读出来的时候已经是按照顺序读出来的，这里想要维持顺序必须从右边push，类似队列
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TPYE_KEY, shopTypesJson);
        // 返回
        return Result.ok(shopTypes);
    }
}
