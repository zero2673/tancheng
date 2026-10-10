package com.tancheng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tancheng.dto.Result;
import com.tancheng.dto.ScrollResult;
import com.tancheng.dto.UserDTO;
import com.tancheng.entity.Blog;
import com.tancheng.entity.Follow;
import com.tancheng.entity.User;
import com.tancheng.mapper.BlogMapper;
import com.tancheng.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tancheng.service.IUserService;
import com.tancheng.utils.SystemConstants;
import com.tancheng.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tancheng.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.tancheng.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FollowServiceImpl followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
                    this.queryBlogUser(blog);
                    this.isBlogLiked(blog);
                }

        );
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        //查询blog对应的user
        queryBlogUser(blog);
//查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
///1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            //用户未登录不做判断
            return;
        }
        Long userId = user.getId();
        //2.判断当前用户是否点赞
        String key = "blog:like:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
///1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //3.点赞，修改点赞数量
            //3.1数据库点赞数+1
            boolean update = update().setSql("liked=liked+1").eq("id", id).update();
            //3.2保存用户到redis的zset集合里
            if (update) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }

        } else {
            //4.已点赞取消点赞，修改点赞数量
            //4.1数据库点赞数-1
            boolean update = update().setSql("liked=liked-1").eq("id", id).update();
            //4.2从redis的set集合里移除用户
            if (update) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }

        }


        return Result.ok();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //1.查询top5的点赞用户 zrange
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (range == null || range.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //2.解析出其中的用户id
        List<Long> collect = range.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.根据id查询用户
        String idStr = StrUtil.join(",", collect);
        List<UserDTO> userDTOS = userService.query().in("id", collect).
                last("ORDER BY FIELD(id," + idStr + ")").list().
                stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //2.保存探店笔记
        boolean save = save(blog);
        if (!save) {
            return Result.fail("新增笔记失败");
        }
        //3.查询笔记作者的所有粉丝
        List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();
        //4.推送笔记id给所有粉丝
        for (Follow follow : followList) {
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        //5.返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {

        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        //非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //3.解析数据：blogId,minTime(时间戳),offset(偏移量)
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //3.1获取id
            ids.add(Long.valueOf(tuple.getValue()));
            //3.2获取时间戳
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }

        //4.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> list = query().in("id", ids).
                last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Blog blog : list) {
            //查询blog对应的user
            queryBlogUser(blog);
            //查询blog是否被点赞
            isBlogLiked(blog);
        }
        //5.封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(list);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);

    }
}
