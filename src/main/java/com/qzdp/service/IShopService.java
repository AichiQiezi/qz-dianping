package com.qzdp.service;

import com.qzdp.dto.Result;
import com.qzdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *

 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据商铺的 id查询商铺信息，先查缓存
     * @param id 商铺 id
     * @return 商铺结果
     */
    Result queryById(Long id);

    /**
     * 更新商铺
     * @param shop
     * @return
     */
    Result update(Shop shop);

    /**
     * 分页查询附近的商铺
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
