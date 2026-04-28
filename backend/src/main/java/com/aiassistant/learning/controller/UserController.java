package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.vo.user.UserProfileVO;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 当前用户相关接口。
 *
 * <p>这里的接口通常需要登录后才能访问。登录校验通过后，
 * {@link com.aiassistant.learning.interceptor.AuthInterceptor} 会把用户 ID 放入 {@link UserContext}。</p>
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final SysUserService sysUserService;
    private final FileStorageProperties fileStorageProperties;

    /**
     * 构造方法注入用户服务。
     *
     * @param sysUserService 用户查询与资料组装服务
     */
    public UserController(SysUserService sysUserService, FileStorageProperties fileStorageProperties) {
        this.sysUserService = sysUserService;
        this.fileStorageProperties = fileStorageProperties;
    }

    /**
     * 获取当前登录用户资料。
     *
     * @return 当前用户的个人资料
     */
    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(sysUserService.getCurrentUserProfile(userId));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserProfileVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("\u5934\u50cf\u5df2\u66f4\u65b0", sysUserService.uploadAvatar(userId, file));
    }

    @GetMapping("/avatar/files/{filename:.+}")
    public ResponseEntity<Resource> avatarFile(@PathVariable String filename) {
        try {
            Path avatarDir = Path.of(fileStorageProperties.getUploadDir(), "avatars")
                    .toAbsolutePath()
                    .normalize();
            Path filePath = avatarDir.resolve(filename).normalize();
            if (!filePath.startsWith(avatarDir) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }

            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            MediaType mediaType = contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                    .body(resource);
        } catch (MalformedURLException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found", exception);
        } catch (Exception exception) {
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found", exception);
        }
    }
}
