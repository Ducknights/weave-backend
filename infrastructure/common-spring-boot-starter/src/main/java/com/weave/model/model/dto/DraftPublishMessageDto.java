package com.weave.model.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 草稿审核通过后发布消息
 * 由 draft-service 在审核通过时发送，post-service 消费后创建已发布帖子
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftPublishMessageDto {
    /** 草稿ID（用于幂等/追踪） */
    private Long draftId;
    /** 作者用户ID */
    private Long userId;
    /** 社团ID */
    private Long clubId;
    /** 标题 */
    private String title;
    /** 内容 */
    private String content;
    /** 资源（封面图等）路径列表 */
    private List<String> resources;
}
