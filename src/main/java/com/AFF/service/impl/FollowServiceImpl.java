package com.AFF.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.AFF.dto.Result;
import com.AFF.dto.UserDTO;
import com.AFF.entity.Follow;
import com.AFF.mapper.FollowMapper;
import com.AFF.service.IFollowService;
import com.AFF.service.IUserService;
import com.AFF.utils.UserHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author UMBRELLAZG
 * @since 2024-11-21
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followedUserId, Boolean isFollow) {
        //获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        String key="follows:"+userId;
         if(isFollow){
             //关注，新增数据
             Follow follow = new Follow();
             follow.setUserId(userId);
             follow.setFollowUserId(followedUserId);
             boolean isSuccess = save(follow);
             if(isSuccess){
                 stringRedisTemplate.opsForSet().add(key,String.valueOf(followedUserId));
             }
         }else{
             //取关，删除数据
             boolean isSuccess = remove(new QueryWrapper<Follow>()
                     .eq("user_id", userId)
                     .eq("follow_user_id", followedUserId));
             if(isSuccess) {
                 stringRedisTemplate.opsForSet().remove(key, String.valueOf(followedUserId));
             }
         }
         return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //查询是否关注select *from tb_follow where user_id=? and follow_user_id = ?
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key="follows:"+userId;
        //求交集
        String key2="follows:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        //解析出id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
