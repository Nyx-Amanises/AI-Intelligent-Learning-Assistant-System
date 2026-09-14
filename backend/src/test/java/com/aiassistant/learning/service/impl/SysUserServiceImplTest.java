package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.dto.user.UpdateProfileRequest;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.vo.user.UserProfileVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class SysUserServiceImplTest {

    private final SysUserMapper mapper = mock(SysUserMapper.class);
    private final FileStorageProperties storage = new FileStorageProperties();
    private final SysUserServiceImpl service = new SysUserServiceImpl(storage);

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SysUser.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"new@example.com"})
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updatesOnlyNicknameAndEmailIncludingExplicitNull(String email) {
        SysUser original = user();
        SysUser updated = user();
        updated.setNickname("新昵称");
        updated.setEmail(email);
        when(mapper.selectById(7L)).thenReturn(original, updated);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("新昵称");
        request.setEmail(email);

        UserProfileVO result = service.updateCurrentUserProfile(7L, request);

        ArgumentCaptor<Wrapper<SysUser>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), update.capture());
        LambdaUpdateWrapper<SysUser> statement = (LambdaUpdateWrapper<SysUser>) update.getValue();
        Set<String> columns = Arrays.stream(statement.getSqlSet().split(","))
                .map(assignment -> assignment.substring(0, assignment.indexOf('=')).trim())
                .collect(Collectors.toSet());
        assertEquals(Set.of("nickname", "email"), columns);
        assertTrue(statement.getSqlSegment().contains("id ="));
        assertEquals(3, statement.getParamNameValuePairs().size());
        assertTrue(statement.getParamNameValuePairs().containsValue(7L));
        assertTrue(statement.getParamNameValuePairs().containsValue("新昵称"));
        assertTrue(statement.getParamNameValuePairs().containsValue(email));

        assertEquals("新昵称", result.getNickname());
        assertEquals(email, result.getEmail());
        assertEquals(original.getId(), result.getId());
        assertEquals(original.getUsername(), result.getUsername());
        assertEquals(original.getPhone(), result.getPhone());
        assertEquals(original.getAvatarUrl(), result.getAvatarUrl());
        assertEquals(original.getRoleCode(), result.getRoleCode());
        assertEquals(original.getLastLoginTime(), result.getLastLoginTime());
        assertEquals("original-hash", original.getPasswordHash());
        assertEquals("原昵称", original.getNickname());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void avatarUploadDoesNotOverwriteConcurrentlyChangedProfile(@TempDir Path uploadDir) throws Exception {
        storage.setUploadDir(uploadDir.toString());
        SysUser original = user();
        SysUser updated = user();
        updated.setNickname("并发更新后的昵称");
        updated.setEmail(null);
        when(mapper.selectById(7L)).thenReturn(original, updated);
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<SysUser> statement = invocation.getArgument(1);
            String avatarUrl = statement.getParamNameValuePairs().values().stream()
                    .filter(String.class::isInstance).map(String.class::cast).findFirst().orElseThrow();
            updated.setAvatarUrl(avatarUrl);
            return 1;
        });
        byte[] image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jGQAAAABJRU5ErkJggg==");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", image);

        UserProfileVO result = service.uploadAvatar(7L, file);

        ArgumentCaptor<Wrapper<SysUser>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), update.capture());
        LambdaUpdateWrapper<SysUser> statement = (LambdaUpdateWrapper<SysUser>) update.getValue();
        assertEquals("avatar_url", statement.getSqlSet().split("=")[0]);
        assertTrue(statement.getSqlSegment().contains("id ="));
        assertEquals(2, statement.getParamNameValuePairs().size());
        assertTrue(statement.getParamNameValuePairs().containsValue(7L));
        assertTrue(statement.getParamNameValuePairs().containsValue(result.getAvatarUrl()));
        assertEquals("并发更新后的昵称", result.getNickname());
        assertNull(result.getEmail());
        assertEquals(original.getUsername(), result.getUsername());
        assertEquals(original.getPhone(), result.getPhone());
        assertEquals(original.getRoleCode(), result.getRoleCode());
        assertEquals(original.getLastLoginTime(), result.getLastLoginTime());
        assertEquals("/avatar.png", original.getAvatarUrl());
        String prefix = "/api/user/avatar/files/";
        assertTrue(result.getAvatarUrl().startsWith(prefix));
        Path storedFile = uploadDir.resolve("avatars").resolve(result.getAvatarUrl().substring(prefix.length()));
        assertArrayEquals(image, Files.readAllBytes(storedFile));
    }

    @Test
    void refusesMissingUserContextBeforeAccessingDatabase() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateCurrentUserProfile(null, new UpdateProfileRequest()));

        assertEquals(401, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void refusesUnknownUserWithoutIssuingAnUpdate() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateCurrentUserProfile(99L, new UpdateProfileRequest()));

        assertEquals(404, exception.getCode());
        verify(mapper).selectById(99L);
        verifyNoMoreInteractions(mapper);
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("original-user");
        user.setNickname("原昵称");
        user.setEmail("old@example.com");
        user.setPasswordHash("original-hash");
        user.setPhone("13800138000");
        user.setAvatarUrl("/avatar.png");
        user.setRoleCode("USER");
        user.setStatus(1);
        user.setLastLoginTime(LocalDateTime.of(2026, 9, 14, 6, 0));
        return user;
    }
}
