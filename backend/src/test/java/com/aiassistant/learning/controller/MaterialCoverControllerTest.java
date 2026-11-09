package com.aiassistant.learning.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.MaterialCoverService;
import com.aiassistant.learning.util.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MaterialCoverControllerTest {
    private final MaterialCoverService service = mock(MaterialCoverService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new MaterialCoverController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        UserContext.setCurrentUserId(7L);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test
    void uploadUsesAuthenticatedOwnerAndReturnsVersionedMetadata() throws Exception {
        when(service.upload(eq(7L), eq(42L), any())).thenReturn("/api/material/42/cover?v=version");
        mvc.perform(multipart("/api/material/42/cover")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1, 2}))
                        .param("userId", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.coverUrl").value("/api/material/42/cover?v=version"));
        verify(service).upload(eq(7L), eq(42L), any());
    }

    @Test
    void readReturnsPrivateImageBytesAndNeverJsonWrappedBytes() throws Exception {
        byte[] bytes = new byte[]{(byte) 0xff, (byte) 0xd8, 1, 2};
        when(service.read(7L, 42L)).thenReturn(bytes);
        mvc.perform(get("/api/material/42/cover").param("v", "version"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(bytes)).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void restoreDefaultReturnsExplicitNullCover() throws Exception {
        mvc.perform(delete("/api/material/42/cover").param("userId", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.coverUrl").value(nullValue()));
        verify(service).restoreDefault(7L, 42L);
    }

    @Test
    void unavailableCoverAndRejectedImageUseHttpErrorStatusForBlobClients() throws Exception {
        when(service.read(7L, 42L)).thenThrow(new BusinessException(404, "资料封面不存在"));
        mvc.perform(get("/api/material/42/cover")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404));
        when(service.upload(eq(7L), eq(42L), isNull())).thenThrow(new BusinessException(400, "请选择封面图片"));
        mvc.perform(multipart("/api/material/42/cover")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void actualAuthInterceptorRequiresHeaderAndRejectsQueryTokensOnEveryEndpoint() throws Exception {
        JwtTokenUtil jwt = mock(JwtTokenUtil.class);
        SysUserMapper users = mock(SysUserMapper.class);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("7");
        when(jwt.parseToken("unit-token")).thenReturn(claims);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setStatus(1);
        user.setRoleCode("USER");
        when(users.selectById(7L)).thenReturn(user);
        MockMvc guarded = MockMvcBuilders.standaloneSetup(new MaterialCoverController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(new AuthInterceptor(jwt, users)).build();
        guarded.perform(get("/api/material/42/cover").param("token", "unit-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        guarded.perform(multipart("/api/material/42/cover").param("token", "unit-token"))
                .andExpect(status().isUnauthorized());
        guarded.perform(delete("/api/material/42/cover").param("token", "unit-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        when(service.read(7L, 42L)).thenReturn(new byte[]{1});
        guarded.perform(get("/api/material/42/cover").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk());
        verify(service).read(7L, 42L);
    }
}
