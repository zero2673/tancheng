package com.tancheng.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tancheng.dto.Result;
import com.tancheng.entity.BlogComments;

public interface IBlogCommentsService extends IService<BlogComments> {

    Result saveComment(BlogComments comment);

    Result queryCommentsByBlogId(Long blogId, Integer current);

    Result deleteComment(Long id);
}