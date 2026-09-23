package com.weave.search.model.enums;

import lombok.Getter;
import com.weave.model.model.ApiStatus;

/**
 * 搜索服务API状态枚举
 */
@Getter
public enum SearchApiStatus implements ApiStatus {
    // 成功状态
    SUCCESS(200, "成功"),
    SEARCH_SUCCESS(200, "搜索成功"),
    INDEX_SUCCESS(201, "索引成功"),
    UPDATE_INDEX_SUCCESS(200, "更新索引成功"),
    DELETE_INDEX_SUCCESS(204, "删除索引成功"),
    GET_INDEX_SUCCESS(200, "获取索引成功"),
    
    // 参数错误
    INVALID_PARAM(400, "参数无效"),
    MISSING_KEYWORD(400, "搜索关键词不能为空"),
    MISSING_DOCUMENT_ID(400, "文档ID不能为空"),
    INVALID_PAGE(400, "页码必须大于0"),
    INVALID_SIZE(400, "每页大小必须在1-100之间"),
    EMPTY_TITLE(400, "标题不能为空"),
    
    // 资源不存在
    DOCUMENT_NOT_FOUND(404, "索引文档不存在"),
    
    // 业务逻辑错误
    INDEX_FAILED(500, "索引失败"),
    UPDATE_INDEX_FAILED(500, "更新索引失败"),
    DELETE_INDEX_FAILED(500, "删除索引失败"),
    GET_INDEX_FAILED(500, "获取索引失败"),
    SEARCH_FAILED(500, "搜索失败"),
    ES_CONNECTION_ERROR(500, "Elasticsearch连接错误"),
    SYSTEM_ERROR(500, "系统错误");

    private final int code;
    private final String msg;

    SearchApiStatus(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
