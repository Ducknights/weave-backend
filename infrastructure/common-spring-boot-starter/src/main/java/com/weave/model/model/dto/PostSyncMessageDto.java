package com.weave.model.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostSyncMessageDto {
    // 操作类型
    private String operation;
    // 数据
    private SearchDocumentDto data;
}
