package com.qzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.qzdp.dto.Result;
import com.qzdp.dto.ScrollResult;
import com.qzdp.dto.UserDTO;
import com.qzdp.entity.Blog;
import com.qzdp.entity.Follow;
import com.qzdp.entity.User;
import com.qzdp.mapper.BlogMapper;
import com.qzdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzdp.service.IFollowService;
import com.qzdp.service.IUserService;
import com.qzdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.qzdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.qzdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *

 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 2.查询blog有关的用户
        queryBlogUser(blog);
        // 3.查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 1、每次查询完成后，我们要分析出查询出数据的最小时间戳，这个值会作为下一次查询的条件
     * 2、我们需要找到与上一次查询相同的查询个数作为偏移量，下次查询时，跳过这些查询过的数据，拿到我们需要的数据
     * 综上：我们的请求参数中就需要携带 lastId：上一次查询的最小时间戳 和偏移量这两个参数。
     * 这两个参数第一次会由前端来指定，以后的查询就根据后台结果作为条件，再次传递到后台。
     *
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录!");
        }
        //1.拼接key获取当前用户的收件箱
        Long userId = user.getId();
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //2.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //3.解析数据：blogId、minTime（时间戳）、offset
        //存放博客集合
        List<String> blogIds = new ArrayList<>(typedTuples.size());
        //偏移量 1 1 2 2 3
        int deviation = 1;
        long minTime = 0;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            blogIds.add(typedTuple.getValue());
            //比较时间戳
            long time = typedTuple.getScore().longValue();
            if (minTime == time){
                deviation ++;
            }else {
                minTime = time;
                deviation = 1;
            }
        }
        //4.根据博客id查询博客
        // 5.根据id查询blog
        String idStr = StrUtil.join(",", blogIds);
        List<Blog> blogs = query().in("id", blogIds).last("ORDER BY FIELD(id," + idStr + ")").list();
        blogs.stream().forEach(blog -> {
            //查询blog的作者信息
            queryBlogUser(blog);
            //查询当前用户是否未该博客点赞
            isBlogLiked(blog);
        });
        //5.封装分页数据并返回
        ScrollResult scrollResult = ScrollResult.builder()
                .list(blogs)
                .offset(deviation)
                .minTime(minTime).build();
        return Result.ok(scrollResult);
    }

    /**
     * 获取博客点赞的集合
     * 要求：
     * 在探店笔记的详情页面，应该把给该笔记点赞的人显示出来，比如最早点赞的TOP5，形成点赞排行榜
     * 解决：
     * 使用 redis中的 sorted set数据结构，可以实现排序的功能。
     * 修改 likeBlog方法
     *
     * @param id
     * @return List<UserDTO> 给当前笔记点赞的前五名用户集合，
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //1.合理性校验
        String key = BLOG_LIKED_KEY + id;
        //获取top5点赞的用户id的集合
        Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (set == null || set.isEmpty()) {
            //若不存在此id的喜欢列表，返回一个空集合即可
            return Result.ok(Collections.emptyList());
        }
        //2.遍历用户id集合，返回
        /*下面代码很不好，有多少id就要去查询多少次数据库，那这数据库分分钟炸裂
        List<UserDTO> collect = set.stream().map(userId -> {
            User user = userService.getById(userId);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            return userDTO;
        }).collect(Collectors.toList());
        优化：先把id转化为list集合进行一次查询
        */
        List<Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //3.根据用户id查询用户,ORDER BY FIELD来自定义查询结果的顺序
        List<UserDTO> collect = userService.query()
                .in("id", ids).last("ORDER BY FIELD (id," + idStr + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(collect);
    }

    /**
     * done 要防止用户无限点赞，且只能登录用户进行点赞
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.先查看用户是否登录
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        //2.根据博客id到redis中查询，判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        if (score == null) {
            //未点赞，可以点赞,修改数据库
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            /*
                保存用户到redis相应的集合中
                zadd key value score  规则从小到大排序，可以使用当前时间戳作为排序的score！！
             */
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, user.getId().toString(), System.currentTimeMillis());
            }
        } else {
            //已点赞，则点赞数 - 1，修改数据库
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                //操作redis
                stringRedisTemplate.opsForZSet().remove(key, user.getId().toString());
            }
        }
        //3.若未点赞，则把当前在redis中记录
        return Result.ok();
    }


    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录！");
        }
        blog.setUserId(user.getId());
        // 2.保存探店博文
        boolean save = save(blog);
        if (!save){
            return Result.fail("新增探店笔记失败");
        }
        // 3.查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 4.推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 4.1.获取粉丝id
            Long userId = follow.getUserId();
            // 4.2.推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 5.返回id
        return Result.ok(blog.getId());
    }
}
