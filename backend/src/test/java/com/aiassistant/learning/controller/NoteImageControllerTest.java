package com.aiassistant.learning.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.NoteImageService;
import com.aiassistant.learning.service.PersonalNoteService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NoteImageControllerTest {
    @TempDir Path temporary;
    private MockMvc mvc;
    private NoteImageService service;
    private byte[] png;

    @BeforeEach
    void setup() throws Exception {
        var storage = new FileStorageProperties(); storage.setUploadDir(temporary.toString());
        service = new NoteImageService(storage);
        mvc = MockMvcBuilders.standaloneSetup(new NoteImageController(service),
                        new PersonalNoteController(mock(PersonalNoteService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 12, BufferedImage.TYPE_INT_ARGB), "png", buffer);
        png = buffer.toByteArray();
        UserContext.setCurrentUserId(7L);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test
    void fullUploadReadFlowUsesAuthenticatedOwnerAndPrivateBinaryHeaders() throws Exception {
        var response = mvc.perform(multipart("/api/notes/images").file(new MockMultipartFile("file", "note.png", "image/png", png))
                        .param("userId", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.width").value(20)).andExpect(jsonPath("$.data.height").value(12))
                .andReturn().getResponse();
        String url = new ObjectMapper().readTree(response.getContentAsString()).path("data").path("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        UserContext.setCurrentUserId(8L);
        mvc.perform(get(url).param("userId", "7")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void invalidUploadsAndNamesUseHttpErrorsForBlobClients() throws Exception {
        mvc.perform(multipart("/api/notes/images")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(multipart("/api/notes/images").file(new MockMultipartFile("file", "fake.png", "image/png", "<svg/>".getBytes())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/notes/images/fake.svg")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void realAuthenticationRejectsMissingHeadersAndQueryTokens() throws Exception {
        var uploaded = service.upload(7L, new MockMultipartFile("file", "note.png", "image/png", png));
        JwtTokenUtil jwt = mock(JwtTokenUtil.class);
        SysUserMapper users = mock(SysUserMapper.class);
        Claims claims = mock(Claims.class); when(claims.getSubject()).thenReturn("7");
        when(jwt.parseToken("unit-image-token")).thenReturn(claims);
        var user = new SysUser(); user.setId(7L); user.setStatus(1); user.setRoleCode("USER");
        when(users.selectById(7L)).thenReturn(user);
        var guarded = MockMvcBuilders.standaloneSetup(new NoteImageController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(new AuthInterceptor(jwt, users)).build();
        guarded.perform(get(uploaded.url()).param("token", "unit-image-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        guarded.perform(multipart("/api/notes/images").file(new MockMultipartFile("file", png)).param("token", "unit-image-token"))
                .andExpect(status().isUnauthorized());
        guarded.perform(get(uploaded.url()).header("Authorization", "Bearer unit-image-token"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
    }
}
