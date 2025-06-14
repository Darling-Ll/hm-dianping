package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送个短信验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //如果不符合，return
            return Result.fail("手机号不符合");
        }

        //生成验证码
        String code= RandomUtil.randomNumbers(6);

//        //保存验证码到session
//        session.setAttribute("code",code);

        //保存验证码到session set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功，验证码:{}",code);
        return Result.ok();
    }

    /**
     * 校验验证码登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //如果不符合，return
            return Result.fail("手机号不符合");
        }
        //从redis获取校验码并校验
        if(loginForm.getCode()==null||!loginForm.getCode().equals(stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone))){
            return Result.fail("验证码错误！");
        }
        //根据手机号查询用户
        User user = query().eq("phone", phone).one();
        if (user==null){
            //如果不存在，创建新用户，并保存到数据库
            User user1 = new User();
            user1.setPhone(phone);
            user1.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
            user=user1;
            save(user);
        }
        //保存用户到redis
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //将user对象转化为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().
                                                                                    setIgnoreNullValue(true).
                                                                                    setFieldValueEditor((filedName,filedValve)->filedValve.toString()));
        //存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //给token设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.SECONDS);
        //返回token
        return Result.ok(token);
    }
}
