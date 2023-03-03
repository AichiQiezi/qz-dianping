package com.qzdp.service;

import com.qzdp.dto.Result;
import com.qzdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *

 */
public interface IBlogService extends IService<Blog> {

    /**
     * 根据 id查询博客
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 获取关注者的博客
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);

    /**
     * 根据 id查询博客的点赞
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 给博客点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 保存博客，并推送给粉丝
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);
}
