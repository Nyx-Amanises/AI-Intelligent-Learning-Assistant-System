package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.NoteImageService;
import com.aiassistant.learning.vo.note.NoteImageVO;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/notes/images")
public class NoteImageController {
    private final NoteImageService service;
    public NoteImageController(NoteImageService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<NoteImageVO> upload(@RequestParam(required = false) MultipartFile file) {
        return ApiResponse.success(service.upload(UserContext.getCurrentUserId(), file));
    }

    @GetMapping("/{name}")
    public ResponseEntity<byte[]> read(@PathVariable String name) {
        var image = service.read(UserContext.getCurrentUserId(), name);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).contentLength(image.bytes().length)
                .cacheControl(CacheControl.noStore().cachePrivate()).header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(image.bytes());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> failure(BusinessException exception) {
        return ResponseEntity.status(exception.getCode()).cacheControl(CacheControl.noStore())
                .body(ApiResponse.fail(exception.getCode(), exception.getMessage()));
    }
}
