package com.weave.post.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 帖子状态枚举
 * 草稿与审核流程已解耦至 draft-service，帖子仅保留发布后状态。
 * code 保持与历史数据一致。
 */
@Getter
public enum PostStatus {
    PUBLIC(1, "公开"),
    HIDDEN(2, "隐藏"),
    DELETED(3, "删除");

    @EnumValue
    private final int code;
    @JsonValue
    private final String desc;

    PostStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
