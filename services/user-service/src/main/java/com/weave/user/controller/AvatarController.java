package com.weave.user.controller;

import com.weave.user.model.dto.UpdateUserInfoDto;
import com.weave.user.model.eunms.UserApiStatus;
import com.weave.model.model.ApiResult;
import com.weave.minio.service.FileService;
import com.weave.user.service.UserInfoService;
import com.weave.security.util.SecurityUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/avatar")
public class AvatarController {

    private final FileService fileService;
    private final UserInfoService userInfoService;


    public AvatarController(FileService fileService, UserInfoService userInfoService) {
        this.fileService = fileService;
        this.userInfoService = userInfoService;

    }

    /**
     * 上传用户头像
     * @param file 头像文件
     * @return 头像路径
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String path = fileService.uploadAvatar(file);
        Long userId = SecurityUtils.getCurrentUserId();
        UpdateUserInfoDto updateUserInfoDto = UpdateUserInfoDto.builder()
                .id(userId)
                .avatar(path)
                .build();
        userInfoService.updateUser(updateUserInfoDto);
        return ResponseEntity.ok(UserApiStatus.CREATE_SUCCESS.response(path));
    }

    /**
     * 获取文件预签名URL
     * @param path 文件路径
     * @param expiry 有效期（秒），默认3600秒
     * @return 预签名URL
     */
    @GetMapping("/url")
    public ResponseEntity<ApiResult<String>> getFileUrl(
            @RequestParam String path,
            @RequestParam(defaultValue = "3600") int expiry) {

        String url = fileService.getFileUrl(path, expiry);
        return ResponseEntity.ok(UserApiStatus.GET_SUCCESS.response(url));
    }

    /**
     * 获取文件预签名URL
     * @param paths 文件路径列表
     * @return 预签名URL列表
     */
    @PostMapping("/urls")
    public ResponseEntity<ApiResult<Map<String, String>>> getFileUrls(@RequestBody List<String> paths) {
        Map<String, String> urls = fileService.getFileUrls(paths, 3600);
        return ResponseEntity.ok(UserApiStatus.GET_SUCCESS.response(urls));
    }
}
