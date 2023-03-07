package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 崔昂
 * @since 2023-2-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static DefaultRedisScript<Long> ADD_BLOG_LIKES_LUA;
    static {
        ADD_BLOG_LIKES_LUA = new DefaultRedisScript<>();
        ADD_BLOG_LIKES_LUA.setLocation(new ClassPathResource("blogLikes.lua"));
        ADD_BLOG_LIKES_LUA.setResultType(Long.class);
    }
    @Override
    public Result queryHotBlog(Integer current) {
    Page<Blog> page = query()
            .orderByDesc("liked")
            .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    // 获取当前页数据
    List<Blog> records = page.getRecords();
    // 查询用户
    records.forEach(this::setUserDate);
    return Result.ok(records);
    }
    @Override
    public Result queryBlogById(Long id) {
        //获取博客信息
        Blog blog = getById(id);
        if (blog==null){
            return Result.fail("博客不存在!");
        }
        //获取发送博客的用户信息
        setUserDate(blog);
        return Result.ok(blog);
    }

    @Override
    public Result addLikes(Long id) {
        Long userId = UserHolder.getUser().getId();
        Long timeMillis = System.currentTimeMillis();
        //执行lua脚本，若当前用户已经为该blog点过一次赞，就返回1，没有点赞资格，有就返回0
        Double score = stringRedisTemplate.opsForZSet().score("blog:blogId:" + id.toString(), userId.toString());
        Long result = stringRedisTemplate.execute(
                ADD_BLOG_LIKES_LUA,
                Collections.emptyList(),
                id.toString(), userId.toString(), Long.toString(timeMillis)
        );
        assert result != null;
        if (result==1){
            return Result.fail("只能点一次赞哦");
        }
        boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
        if (!isSuccess){
            //如果添加不成功将缓存里的数据也清除掉
            stringRedisTemplate.opsForZSet().remove("blog:blogId:"+id,userId);
        }
        return Result.ok();
    }

    private void setUserDate(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
