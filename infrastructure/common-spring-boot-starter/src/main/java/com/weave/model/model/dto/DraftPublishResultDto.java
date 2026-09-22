package com.weave.model.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 草稿发布结果回执消息
 * 由 post-service 发布成功后发送，draft-service 消费后回写 publishedPostId 并归档草稿
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftPublishResultDto {
    /** 草稿ID */
    private Long draftId;
    /** 发布生成的帖子ID */
    private Long postId;
    /** 是否发布成功 */
    private boolean success;
}
