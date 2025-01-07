package com.AFF.controller;


import com.AFF.dto.Result;
import com.AFF.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author UMBRELLAZG
 * @since 2024-11-21
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id")  Long followedUserId, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(followedUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }
}
