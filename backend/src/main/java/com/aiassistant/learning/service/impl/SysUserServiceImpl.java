package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.vo.user.UserProfileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统用户业务实现类。
 *
 * <p>继承 {@link ServiceImpl} 后，可以直接使用 getById、save、updateById 等基础方法。
 * 当前类只补充项目里常用的用户查询和资料组装逻辑。</p>
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final long MAX_AVATAR_FILE_SIZE = 5L * 1024 * 1024;

    private static final String AVATAR_PUBLIC_PATH = "/api/user/avatar/files/";

    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp",
            ".gif"
    );

    private final FileStorageProperties fileStorageProperties;

    public SysUserServiceImpl(FileStorageProperties fileStorageProperties) {
        this.fileStorageProperties = fileStorageProperties;
    }

    /**
     * 根据用户名查询单个用户。
     *
     * <p>{@link LambdaQueryWrapper} 使用方法引用指定字段，
     * 比直接写字符串字段名更安全，重构字段名时也更容易发现问题。</p>
     *
     * @param username 用户名
     * @return 查询到的用户；不存在时返回 null
     */
    @Override
    public SysUser getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
    }

    /**
     * 根据用户 ID 查询用户，查不到就抛出异常。
     *
     * @param userId 用户 ID
     * @return 用户实体
     */
    @Override
    public SysUser getRequiredById(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /**
     * 获取当前登录用户资料。
     *
     * <p>这里把数据库实体 SysUser 转换为 UserProfileVO，只返回前端需要展示的字段，
     * 不返回 passwordHash 这类敏感信息。</p>
     *
     * @param userId 当前登录用户 ID
     * @return 前端展示用的用户资料
     */
    @Override
    public UserProfileVO getCurrentUserProfile(Long userId) {
        SysUser user = this.getRequiredById(userId);
        return UserProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .roleCode(user.getRoleCode())
                .lastLoginTime(user.getLastLoginTime())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("\u5934\u50cf\u6587\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (file.getSize() > MAX_AVATAR_FILE_SIZE) {
            throw new BusinessException("\u5934\u50cf\u6587\u4ef6\u4e0d\u80fd\u8d85\u8fc7 5MB");
        }

        String contentType = normalizeContentType(file.getContentType());
        String extension = resolveAvatarExtension(file.getOriginalFilename(), contentType);
        if (StringUtils.hasText(contentType) && !ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("\u4ec5\u652f\u6301 JPG\u3001PNG\u3001WebP \u6216 GIF \u56fe\u7247");
        }

        Path avatarDir = initAvatarDir();
        String storedFilename = UUID.randomUUID() + extension;
        Path targetPath = avatarDir.resolve(storedFilename).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(500, "\u5934\u50cf\u4fdd\u5b58\u5931\u8d25");
        }

        SysUser user = this.getRequiredById(userId);
        user.setAvatarUrl(AVATAR_PUBLIC_PATH + storedFilename);
        this.updateById(user);
        return this.getCurrentUserProfile(userId);
    }

    private Path initAvatarDir() {
        try {
            Path avatarDir = Path.of(fileStorageProperties.getUploadDir(), "avatars")
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(avatarDir);
            return avatarDir;
        } catch (IOException exception) {
            throw new BusinessException(500, "\u5934\u50cf\u76ee\u5f55\u521b\u5efa\u5931\u8d25");
        }
    }

    private String resolveAvatarExtension(String fileName, String contentType) {
        if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) {
            return ".jpg";
        }
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        if ("image/gif".equals(contentType)) {
            return ".gif";
        }

        String extension = getExtension(fileName).toLowerCase(Locale.ROOT);
        if (!ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
            throw new BusinessException("\u4ec5\u652f\u6301 JPG\u3001PNG\u3001WebP \u6216 GIF \u56fe\u7247");
        }
        return ".jpeg".equals(extension) ? ".jpg" : extension;
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.toLowerCase(Locale.ROOT) : "";
    }
}
