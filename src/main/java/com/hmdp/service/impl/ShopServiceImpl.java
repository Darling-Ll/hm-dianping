package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop==null) {
            return Result.fail("店铺不存在");
        }
        //返回结果
        return Result.ok(shop);
    }

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY+id;
        //从redis缓存获取商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //若命中，直接返回
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中的是否是空值(解决缓存穿透）
        if(shopJson!=null){
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        Shop shop1 = null;
        try {
            //判断是否加锁成功
            if (!tryLock(lockKey)) {
                //失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //若未命中，查询数据库（加锁成功）
            shop1 = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //查询数据库，数据库中不存在，直接返回错误信息
            if (shop1==null){
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在，把信息添加到redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop1),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //返回结果
        return shop1;
    }
    /**
     * 解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY+id;
        //从redis缓存获取商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //若命中，直接返回
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中的是否是空值(解决缓存穿透）
        if(shopJson!=null){
            return null;
        }
        //若未命中，查询数据库
        Shop shop1 = getById(id);
        //查询数据库，数据库中不存在，直接返回错误信息
        if (shop1==null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，把信息添加到redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop1),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回结果
        return shop1;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //查询数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
