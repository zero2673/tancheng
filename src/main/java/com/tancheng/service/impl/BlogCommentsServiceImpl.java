package com.tancheng.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tancheng.dto.Result;
import com.tancheng.dto.UserDTO;
import com.tancheng.entity.Blog;
import com.tancheng.entity.BlogComments;
import com.tancheng.entity.User;
import com.tancheng.mapper.BlogCommentsMapper;
import com.tancheng.mapper.BlogMapper;
import com.tancheng.service.IBlogCommentsService;
import com.tancheng.service.IUserService;
import com.tancheng.utils.SystemConstants;
import com.tancheng.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔记评论：发表、分页查询、删除自己的评论。
 * 评论数落在 note.comments 字段上，随评论增删同步维护。
 */
@Slf4j
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private IUserService userService;

    @Override
    public Result saveComment(BlogComments comment) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (comment == null || comment.getBlogId() == null) {
            return Result.fail("笔记不存在");
        }
        if (StrUtil.isBlank(comment.getContent())) {
            return Result.fail("评论内容不能为空");
        }
        if (comment.getContent().length() > 255) {
            return Result.fail("评论内容不能超过 255 个字符");
        }
        if (blogMapper.selectById(comment.getBlogId()) == null) {
            return Result.fail("笔记不存在");
        }
        comment.setId(null);
        comment.setUserId(user.getId());
        comment.setParentId(comment.getParentId() == null ? 0L : comment.getParentId());
        comment.setAnswerId(comment.getAnswerId() == null ? 0L : comment.getAnswerId());
        comment.setLiked(0);
        comment.setStatus(Boolean.FALSE);
        save(comment);
        blogMapper.update(null, Wrappers.<Blog>lambdaUpdate()
                .setSql("comments = IFNULL(comments, 0) + 1")
                .eq(Blog::getId, comment.getBlogId()));
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryCommentsByBlogId(Long blogId, Integer current) {
        if (blogId == null) {
            return Result.fail("笔记不存在");
        }
        int pageNo = current == null || current < 1 ? 1 : current;
        Page<BlogComments> page = lambdaQuery()
                .eq(BlogComments::getBlogId, blogId)
                .and(wrapper -> wrapper.isNull(BlogComments::getStatus)
                        .or().eq(BlogComments::getStatus, Boolean.FALSE))
                .orderByDesc(BlogComments::getCreateTime)
                .page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE));
        fillUserInfo(page.getRecords());
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result deleteComment(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        if (!user.getId().equals(comment.getUserId())) {
            return Result.fail("只能删除自己的评论");
        }
        removeById(id);
        blogMapper.update(null, Wrappers.<Blog>lambdaUpdate()
                .setSql("comments = GREATEST(IFNULL(comments, 0) - 1, 0)")
                .eq(Blog::getId, comment.getBlogId()));
        return Result.ok();
    }

    private void fillUserInfo(List<BlogComments> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> userIds = records.stream()
                .map(BlogComments::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        for (BlogComments item : records) {
            User user = userMap.get(item.getUserId());
            if (user != null) {
                item.setName(user.getNickName());
                item.setIcon(user.getIcon());
            }
        }
    }
}