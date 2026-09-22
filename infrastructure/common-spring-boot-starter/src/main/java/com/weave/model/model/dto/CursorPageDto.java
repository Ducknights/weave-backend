package com.weave.model.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CursorPageDto<T> {
    private List<T> pageData;
    private String cursorId;
    private boolean hasNext;
}



