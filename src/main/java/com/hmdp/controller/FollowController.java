package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;
    @PutMapping("/{followUserId}/{isFollow}")
    public Result follow(@PathVariable("followUserId") Long followUserId,@PathVariable("isFollow") boolean isFollow){
        return followService.follow(followUserId,isFollow);
    }
    @GetMapping("/or/not/{followUserId}")
    public Result isFollow(@PathVariable("followUserId") Long followUserId){
        return followService.isFollow(followUserId);
    }

}
