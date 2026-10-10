package com.tancheng.controller;


import com.tancheng.dto.Result;
import com.tancheng.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
@Resource
    private IFollowService iFollowService;

@PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,@PathVariable("isFollow") Boolean isFollow) {

    return iFollowService.follow(id, isFollow);
}
@GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
return iFollowService.isFollow(id);
}
@GetMapping("/common/{id}")
    public Result followCommon(@PathVariable("id") Long id) {
return iFollowService.followCommon(id);
}
}
