package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:"+userId;
        //查询当前登录用户是否已经关注该用户
        if (isFollow){
            //关注，新增用户
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            //将该用户的关注放入redis的set集合中
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            //取消关注,删除用户数据  delete from tb_follow where follow_user_id = ? and user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("follow_user_id", followUserId).eq("user_id", userId));
            //将该用户的关注从redis的set集合中移除
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //查询当前登录用户是否已经关注该用户
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("follow_user_id", followUserId).eq("user_id", userId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        //查询当前登录用户和目标用户共同关注的用户id
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("follows:" + userId, "follows:" + id);
        //如果没有共同关注，直接返回
        if (intersect==null||intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据用户id查询用户
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

}
