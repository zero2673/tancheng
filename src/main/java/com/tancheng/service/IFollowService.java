package com.tancheng.service;

import com.tancheng.dto.Result;
import com.tancheng.entity.Follow;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long id, Boolean isFollow);

    Result isFollow(Long id);

    Result followCommon(Long id);


}
