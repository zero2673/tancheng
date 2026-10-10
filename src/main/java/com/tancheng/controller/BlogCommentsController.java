package com.tancheng.controller;

import com.tancheng.dto.Result;
import com.tancheng.entity.BlogComments;
import com.tancheng.service.IBlogCommentsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记评论接口。
 * 查询评论无需登录，发表与删除需要登录（由 MvcConfig 的拦截器控制）。
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 发表评论
     */
    @PostMapping
    public Result saveComment(@RequestBody BlogComments comment) {
        return blogCommentsService.saveComment(comment);
    }

    /**
     * 分页查询某篇笔记的评论
     */
    @GetMapping("/of/blog/{blogId}")
    public Result queryCommentsByBlogId(@PathVariable("blogId") Long blogId,
                                        @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryCommentsByBlogId(blogId, current);
    }

    /**
     * 删除自己的评论
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return blogCommentsService.deleteComment(id);
    }
}