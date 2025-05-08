package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
@Component
public class RedisIdWorker {
    /**
     * id生成器
     */

    private StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    private static final long BEGIN_TIMESTAMP=1744729980L;
    private static final int COUNT_BITS=32;

    public long nextId(String prefix){
        //64位，1（0，表示整数）+31（时间戳）+32（序列号）
        //获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = epochSecond - BEGIN_TIMESTAMP;
        //获取当前天数，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //生成序列号
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + date);
        //拼接
        return timestamp<<COUNT_BITS|count;
    }

}
