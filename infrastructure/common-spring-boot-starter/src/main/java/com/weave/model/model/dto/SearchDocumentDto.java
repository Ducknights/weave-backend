package com.weave.model.model.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class SearchDocumentDto {
    private Long id;
    private String title;
    private String content;
}
