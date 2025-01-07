package com.AFF.service;

import com.AFF.dto.Result;
import com.AFF.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author umbrellazg
 * @since 2024-11-22
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followedUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
