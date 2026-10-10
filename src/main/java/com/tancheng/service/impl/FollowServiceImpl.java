package com.tancheng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.tancheng.dto.Result;
import com.tancheng.dto.UserDTO;
import com.tancheng.entity.Follow;
import com.tancheng.mapper.FollowMapper;
import com.tancheng.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tancheng.service.IUserService;
import com.tancheng.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tancheng.utils.RedisConstants.FOLLOW;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService iUserService;


    @Override
    public Result follow(Long id, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW + "userId";
        //1.判断操作是取关还是关注
        if (isFollow) {
            //2.关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean save = save(follow);
            if (save) {
                //关注成功，把关注的人id放入redis的set集合中
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }

        } else {
            //3.取关
            boolean remove = remove(new QueryWrapper<Follow>().
                    eq("user_id", userId).
                    eq("follow_user_id", id));
            //把关注用户的id从redis的set集合中移除
            if (remove) {
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
//1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注
        Long count = query().eq("user_id", userId).
                eq("follow_user_id", id).count();
        //3.判断，返回结果
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommon(Long id) {
//1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW + "userId";
        //2.查询共同关注
        String key2 = FOLLOW + "userId";
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> userDTOS = iUserService.listByIds(ids).stream().
                map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());


        return Result.ok(userDTOS);
    }


}
