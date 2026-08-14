package com.weave.post.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weave.post.model.enums.PostStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import com.weave.post.model.entity.Post;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Update("UPDATE post SET view_count = view_count + 1 WHERE post_id = #{id}")
    void increaseViewCount(Long id);

    @Update("UPDATE post SET like_count = GREATEST(0, like_count + #{delta}) WHERE post_id = #{id}")
    void updateLikeCount(Long id, int delta);

    @Update("UPDATE post SET collect_count = GREATEST(0, collect_count + #{delta}) WHERE post_id = #{id}")
    void updateCollectCount(Long id, int delta);

    @Update("UPDATE post SET comment_count = GREATEST(0, comment_count + #{delta}) WHERE post_id = #{id}")
    void updateCommentCount(Long id, int delta);

    default List<Post> selectPublishedPostByIds(List<Long> needQueryIds) {
        if (CollectionUtils.isEmpty(needQueryIds)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Post> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Post::getPostId, needQueryIds);
        queryWrapper.eq(Post::getStatus, PostStatus.PUBLIC);
        return this.selectList(queryWrapper);
    }

    default List<Post> selectHiddenPostByUserId(Long userId) {
        LambdaQueryWrapper<Post> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Post::getUserId, userId);
        queryWrapper.eq(Post::getStatus, PostStatus.HIDDEN);
        return this.selectList(queryWrapper);
    }

    default List<Post> selectPostsByUser(Long userId){
        LambdaQueryWrapper<Post> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Post::getUserId, userId);
        return this.selectList(queryWrapper);
    }
}
