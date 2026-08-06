package com.weave.search.controller;

import com.weave.search.service.ConversionService;
import com.weave.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.model.model.ApiResult;
import com.weave.model.model.dto.PostDetailVo;
import com.weave.search.model.dto.SearchPageDto;
import com.weave.search.model.dto.SearchResultDto;
import com.weave.search.model.enums.SearchApiStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 搜索控制器
 * 提供搜索相关的RESTful API接口
 */
@Log4j2
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    private final ConversionService conversionService;

    /**
     * 搜索内容
     * GET /api/search
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页大小
     * @return 搜索结果
     */
    @GetMapping("/post")
    public ResponseEntity<ApiResult<SearchPageDto>> searchPost(@RequestParam String keyword,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        log.info("搜索关键词: {}, 页码: {}, 每页大小: {}", keyword, page, size);

        // 1. 从 ES 获取搜索结果（仅包含 ID 和分数）
        List<SearchResultDto> results = searchService.search(keyword, page, size);

        // 2. 通过 ConversionService 获取完整帖子信息并设置搜索分数
        List<PostDetailVo> postList = conversionService.convertToPostDetailVo(results);

        // 3. 组装分页结果
        SearchPageDto result = SearchPageDto.builder()
                .keyword(keyword)
                .posts(postList)
                .pageNum(page)
                .pageSize(size)
                .total(postList.size())
                .build();

        return ResponseEntity.ok(SearchApiStatus.SEARCH_SUCCESS.response(result));
    }

    /**
     * 健康检查接口
     * GET /api/search/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok().body("服务运行正常");
    }
}
