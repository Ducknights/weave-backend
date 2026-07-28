package com.weave.post.controller;

import lombok.NonNull;
import com.weave.post.model.enums.PostApiStatus;
import com.weave.model.model.ApiResult;
import com.weave.minio.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/post/file")
public class PostFileController {

    private final FileService fileService;

    public PostFileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 获取单个文件预签名URL
     * @param path 文件路径
     * @param expiry 有效期（秒），默认3600秒
     * @return 预签名URL
     */
    @GetMapping("/url")
    public ResponseEntity<ApiResult<String>> getFileUrl(
            @NonNull @RequestParam String path,
            @RequestParam(defaultValue = "3600") int expiry) {
        String url = fileService.getFileUrl(path, expiry);
        return ResponseEntity.ok(PostApiStatus.SUCCESS.response(url));
    }

    /**
     * 批量获取文件预签名URL
     * @param paths 文件路径列表
     * @param expiry 有效期（秒），默认3600秒
     * @return 文件路径与预签名URL的映射
     */
    @GetMapping("/url/batch")
    public ResponseEntity<ApiResult<Map<String, String>>> getFileUrls(
            @RequestParam List<String> paths,
            @RequestParam(defaultValue = "3600") int expiry) {
        Map<String, String> urlMap = fileService.getFileUrls(paths, expiry);
        return ResponseEntity.ok(PostApiStatus.SUCCESS.response(urlMap));
    }
}
