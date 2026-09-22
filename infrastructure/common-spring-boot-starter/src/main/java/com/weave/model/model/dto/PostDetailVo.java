package com.weave.model.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostDetailVo {
    private Long id;            // postID
    private Long userId;        // 用户id
    private String username;    // 用户名
    private Long clubId;        // 社团id
    private String clubName;    // 社团名
    private String avatar;      // 头像
    private String title;       // 标题
    private String content;     // 内容
    private List<String> urls;  // 资源地址
    private Integer viewCount;  // 浏览次数
    private Integer likeCount;  // 点赞次数
    private Boolean likeStatus; // 点赞状态（用户是否点赞）
    private Integer collectCount; // 收藏次数
    private Boolean collectStatus; // 收藏状态（用户是否收藏）
    private Integer commentCount; // 评论次数
    private LocalDateTime createdTime; // 创建时间
    private LocalDateTime updatedTime; // 更新时间
    private Float score;        // 搜索相关性分数

    public static PostDetailVo buildEmpty() {
        return new PostDetailVo(0L, 0L, null, null, null, null, null, null, null, 0, 0, false, 0, false, 0, null, null,    0.0f);
    }
}