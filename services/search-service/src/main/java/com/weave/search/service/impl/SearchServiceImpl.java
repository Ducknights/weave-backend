package com.weave.search.service.impl;

import com.weave.search.model.entity.SearchDocument;
import com.weave.search.repository.SearchDocumentRepository;
import com.weave.search.service.SearchService;
import lombok.extern.log4j.Log4j2;
import com.weave.search.model.dto.SearchResultDto;
import org.jetbrains.annotations.NotNull;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 搜索服务实现
 * 提供搜索相关的业务逻辑实现
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SearchDocumentRepository searchDocumentRepository;

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public List<SearchResultDto> search(String keyword, int page, int size) {
        log.info("执行搜索: keyword={}, page={}, size={}", keyword, page, size);

        NativeQuery query = buildSearchQuery(keyword, page, size);
        SearchHits<SearchDocument> searchHits = elasticsearchOperations.search(query, SearchDocument.class);

        List<SearchResultDto> results = new ArrayList<>();
        searchHits.forEach(hit -> {
            SearchResultDto result = SearchResultDto.builder()
                    .id(Long.valueOf(Objects.requireNonNull(hit.getId())))
                    .score(hit.getScore())
                    .build();
            results.add(result);
        });

        return results;
    }

    private @NotNull NativeQuery buildSearchQuery(String keyword, int page, int size) {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        boolQueryBuilder.should(MatchQuery.of(m -> m
                .field("content")
                .query(keyword)
                .fuzziness("AUTO"))._toQuery());

        boolQueryBuilder.should(MatchQuery.of(m -> m
                .field("title")
                .query(keyword))._toQuery());

        boolQueryBuilder.minimumShouldMatch("1");
        
        Query query = Query.of(q -> q.bool(boolQueryBuilder.build()));
        
        Pageable pageable = PageRequest.of(page - 1, size);
        
        return new NativeQueryBuilder()
                .withQuery(query)
                .withPageable(pageable)
                .build();
    }

    @Override
    public void indexContent(SearchDocument document) {
        try {
            log.info("索引内容: id={}, title={}", 
                    document.getId(), document.getTitle());
            document.setIsPublic(true);
            searchDocumentRepository.save(document);
        } catch (Exception e) {
            log.error("索引内容失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void updateIndex(SearchDocument document) {
        try {
            log.info("更新索引: id={}", document.getId());
            searchDocumentRepository.save(document);
        } catch (Exception e) {
            log.error("更新索引失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteIndex(Long id) {
        try {
            log.info("删除索引: id={}", id);
            updateIsPublic(id, false);
        } catch (Exception e) {
            log.error("删除索引失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void hideIndex(Long id) {
        try {
            log.info("隐藏索引: id={}", id);
            updateIsPublic(id, false);
        } catch (Exception e) {
            log.error("隐藏索引失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void restoreIndex(Long id) {
        try {
            log.info("恢复索引: id={}", id);
            updateIsPublic(id, true);
        } catch (Exception e) {
            log.error("恢复索引失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 局部更新文档的 isPublic 字段，避免全量覆盖
     */
    private void updateIsPublic(Long id, boolean isPublic) {
        Document document = Document.create();
        document.put("isPublic", isPublic);
        UpdateQuery updateQuery = UpdateQuery.builder(String.valueOf(id))
                .withDocument(document)
                .build();
        elasticsearchOperations.update(updateQuery,
                elasticsearchOperations.getIndexCoordinatesFor(SearchDocument.class));
    }
}
