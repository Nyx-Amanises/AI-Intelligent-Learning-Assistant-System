package com.aiassistant.learning.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.util.JwtTokenUtil;
import io.jsonwebtoken.Jwts;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiKnowledgePointsControllerTest {

    private final AiQuestionService generator = mock(AiQuestionService.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final JwtTokenUtil jwt = mock(JwtTokenUtil.class);
    private MockMvc mvc;

    @BeforeEach
    void configureAuthenticatedApi() {
        UserContext.clear();
        when(jwt.parseToken("valid-token")).thenReturn(Jwts.claims().subject("7").build());
        SysUser account = new SysUser();
        account.setId(7L);
        account.setStatus(1);
        account.setTokenVersion(0);
        when(users.selectById(7L)).thenReturn(account);
        mvc = MockMvcBuilders.standaloneSetup(new AiController(
                        generator, mock(AiSummaryService.class), mock(AiConfigService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwt, users))
                .build();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void candidateEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/ai/material/42/knowledge-points"))
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(generator);
    }

    @Test
    void candidateEndpointUsesAuthenticatedIdentityAndReturnsStringList() throws Exception {
        when(generator.listKnowledgePoints(7L, 42L)).thenReturn(List.of("封装", "继承"));

        mvc.perform(get("/api/ai/material/42/knowledge-points?userId=99")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value("封装"))
                .andExpect(jsonPath("$.data[1]").value("继承"));

        verify(generator).listKnowledgePoints(7L, 42L);
    }
}
