package com.aiassistant.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.user.UpdateProfileRequest;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.user.UserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private final SysUserService users = mock(SysUserService.class);
    private final JwtTokenUtil jwt = mock(JwtTokenUtil.class);
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserContext.clear();
        when(jwt.parseToken("valid-token")).thenReturn(Jwts.claims().subject("7").build());
        when(users.updateCurrentUserProfile(eq(7L), any(UpdateProfileRequest.class)))
                .thenAnswer(invocation -> {
                    UpdateProfileRequest request = invocation.getArgument(1);
                    return UserProfileVO.builder().id(7L).username("original-user")
                            .nickname(request.getNickname()).email(request.getEmail())
                            .phone("13800138000").avatarUrl("/avatar.png").roleCode("USER").build();
                });
        mvc = MockMvcBuilders.standaloneSetup(new UserController(users, new FileStorageProperties()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwt))
                .build();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void refusesRequestsWithoutAuthentication() throws Exception {
        mvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"学习者\"}"))
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(users);
    }

    @Test
    void refusesInvalidTokens() throws Exception {
        when(jwt.parseToken("expired-token")).thenThrow(new IllegalArgumentException("Expired"));

        mvc.perform(put("/api/user/profile").header("Authorization", "Bearer expired-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"学习者\"}"))
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(users);
    }

    @Test
    void usesAuthenticatedIdentityAndIgnoresProtectedBodyFields() throws Exception {
        mvc.perform(put("/api/user/profile").header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nickname":" 新昵称 ","email":" learner@example.com ",
                                 "id":99,"userId":99,"username":"changed","roleCode":"ADMIN",
                                 "password":"changed","passwordHash":"changed","phone":"changed"}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.email").value("learner@example.com"))
                .andExpect(jsonPath("$.data.username").value("original-user"))
                .andExpect(jsonPath("$.data.roleCode").value("USER"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.avatarUrl").value("/avatar.png"));

        ArgumentCaptor<UpdateProfileRequest> request = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(users).updateCurrentUserProfile(eq(7L), request.capture());
        assertEquals("新昵称", request.getValue().getNickname());
        assertEquals("learner@example.com", request.getValue().getEmail());
        assertNull(UserContext.getCurrentUserId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void normalizesEmptyEmailToNull(String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nickname", "学习者");
        body.put("email", email);

        update(body, 200);

        ArgumentCaptor<UpdateProfileRequest> request = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(users).updateCurrentUserProfile(eq(7L), request.capture());
        assertNull(request.getValue().getEmail());
    }

    @Test
    void omittingEmailAlsoClearsIt() throws Exception {
        update(Map.of("nickname", "学习者"), 200);

        ArgumentCaptor<UpdateProfileRequest> request = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(users).updateCurrentUserProfile(eq(7L), request.capture());
        assertNull(request.getValue().getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "name@", "a b@example.com"})
    void rejectsInvalidEmailBeforeCallingService(String email) throws Exception {
        update(Map.of("nickname", "学习者", "email", email), 400);
        verifyNoInteractions(users);
    }

    @Test
    void acceptsOneHundredCharacterEmailButRejectsLongerEmail() throws Exception {
        update(Map.of("nickname", "学习者", "email", "a".repeat(64) + "@" + "b".repeat(31) + ".com"), 200);
        update(Map.of("nickname", "学习者", "email", "a".repeat(64) + "@" + "b".repeat(32) + ".com"), 400);

        verify(users).updateCurrentUserProfile(eq(7L), any(UpdateProfileRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"学", "12345678901234567890"})
    void acceptsNicknameLengthBoundaries(String nickname) throws Exception {
        update(Map.of("nickname", nickname), 200);
        verify(users).updateCurrentUserProfile(eq(7L), any(UpdateProfileRequest.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "123456789012345678901"})
    void rejectsMissingBlankOrLongNickname(String nickname) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nickname", nickname);
        update(body, 400);
        verifyNoInteractions(users);
    }

    private void update(Map<String, ?> body, int code) throws Exception {
        mvc.perform(put("/api/user/profile").header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(code));
    }
}
