package com.weave.draft.controller;

import com.weave.draft.model.enums.DraftApiStatus;
import com.weave.model.model.ApiResult;
import com.weave.minio.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 草稿文件控制器
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/draft/file")
public class DraftFileController {

    private final FileService fileService;

    /**
     * 上传草稿图片
     * @param files 图片文件列表
     * @return 上传的图片路径列表
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'OFFICER')")
    public ResponseEntity<ApiResult<List<String>>> uploadImages(@RequestParam("files") List<MultipartFile> files) {
        List<String> paths = fileService.uploadFiles(files);
        return ResponseEntity.ok(DraftApiStatus.SUCCESS.response(paths));
    }
}
