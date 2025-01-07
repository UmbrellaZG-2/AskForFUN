package com.AFF.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.AFF.dto.Result;
import com.AFF.dto.UserDTO;
import com.AFF.entity.Blog;
import com.AFF.entity.User;
import com.AFF.mapper.BlogMapper;
import com.AFF.service.IBlogService;
import com.AFF.service.IUserService;
import com.AFF.utils.SystemConstants;
import com.AFF.utils.UserHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.AFF.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author UMBRELLAZG
 * @since 2024-11-21
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService  userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        //查询blog有关的用户
        queryBlogUser(blog);
        //查询blog是否被点赞
        isBlogliked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog->{this.queryBlogUser(blog);this.isBlogliked(blog);});
        //查询blog是否被点赞
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //判断当前用户点赞了吗
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        //没有点赞
        if (score==null) {;
            //数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            if(isSuccess){
                //保存用户到Redis的set
                //点赞数+1
                stringRedisTemplate.opsForZSet().add(key, String.valueOf(userId),System.currentTimeMillis());
            }
        } else {
            //已点赞
            //点赞数-1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().remove(key, String.valueOf(userId));
            //数据库点赞数-1
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //查询点赞用户 ZRANGE 0~5
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok();
        }
        //解析top5
        List<Long> ids= top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据id查用户
        String idsStr= StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("ORDER BY FIELD(id"+idsStr+")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    private void isBlogliked(Blog blog) {
        //获取用户id
        if (UserHolder.getUser() == null) {
            //用户未登录
            return;
        }
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        //判断用户是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(userId));
        blog.setIsLike(score!=null);
    }
}
