package com.hmdp.service.impl;


import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
//    @Autowired
//    private VoucherOrderServiceImpl voucherOrderServiceImpl;
    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private final static DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //判断结果是否为零
        if (result.intValue()!=0){
            //不为0，没有购买资格
            return Result.fail(result.intValue()==1?"库存不足":"您已经购买过一次了");
        }
        //获取订单id
        Long orderId = redisIdWorker.nextId("order");
        //为0，将优惠券id，用户id和订单id保存到redis中

        // TODO 保存阻塞队列
        //返回订单id
        return Result.ok(0);


        //性能一般

        /*//根据优惠券id查询优惠券信息
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        //判断秒杀是否开启
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())||seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀有效期无效！");
        }
        //判断库存是否充足
        if (seckillVoucher.getStock()<1) {
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        *//*synchronized (userId.toString().intern()) {
            //获取事务有关代理对象
            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        }*//* //如果同一用户用不同机器发起请求 仍会发生并发安全问题
        //创建锁对象(自定义锁）
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //创建锁对象(redisson提供锁）
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock(1,100,TimeUnit.SECONDS);
        if (!isLock) {
            Result.fail("同一用户不允许重复下单");
        }
        try {
            //获取事务有关代理对象
            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }*/
    }
    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count>0) {
            return Result.fail("用户已经购买过一次");
        }
        //扣减库存
        boolean success = iSeckillVoucherService.update().setSql("stock=stock-1")//set stock =stock-1
                .eq("voucher_id", voucherId)
                .gt("stock",0)// where voucher_id =? and stock>0  （乐观锁实现）
                .update();
        //扣减失败
        if (!success) {
            return Result.fail("库存不足");
        }

        //创建订单  orderId userId voucherId
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }
}
