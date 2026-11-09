package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.MaterialCoverService;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 与资料 API 使用相同的 Authorization 鉴权，不提供公开文件或查询令牌通道。 */
@RestController
@RequestMapping("/api/material/{id}/cover")
public class MaterialCoverController {
    private final MaterialCoverService service;

    public MaterialCoverController(MaterialCoverService service) { this.service = service; }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CoverResult(String coverUrl) {}

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoverResult> upload(@PathVariable Long id, @RequestParam(required = false) MultipartFile file) {
        return ApiResponse.success("封面已更新", new CoverResult(service.upload(UserContext.getCurrentUserId(), id, file)));
    }

    @GetMapping
    public ResponseEntity<byte[]> read(@PathVariable Long id) {
        byte[] bytes = service.read(UserContext.getCurrentUserId(), id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).contentLength(bytes.length)
                .cacheControl(CacheControl.noStore().cachePrivate()).header("X-Content-Type-Options", "nosniff").body(bytes);
    }

    @DeleteMapping
    public ApiResponse<CoverResult> restoreDefault(@PathVariable Long id) {
        service.restoreDefault(UserContext.getCurrentUserId(), id);
        return ApiResponse.success("已恢复默认封面", new CoverResult(null));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> failure(BusinessException exception) {
        return ResponseEntity.status(exception.getCode()).cacheControl(CacheControl.noStore())
                .body(ApiResponse.fail(exception.getCode(), exception.getMessage()));
    }
}
