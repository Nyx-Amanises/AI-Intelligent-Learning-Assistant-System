package com.aiassistant.learning.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.assistant.AssistantMessageSendRequest;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.AssistantService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.assistant.AssistantSessionDetailVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AssistantStreamingControllerTest {

    private static final String STREAM_PATH = "/api/assistant/sessions/31/messages/stream";
    private static final String BODY = "{\"contentText\":\"解释光合作用\"}";
    private final AssistantService assistant = mock(AssistantService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserContext.clear();
        JwtTokenUtil jwt = mock(JwtTokenUtil.class);
        SysUserMapper users = mock(SysUserMapper.class);
        when(jwt.parseToken("valid-token")).thenReturn(Jwts.claims().subject("7").build());
        SysUser account = new SysUser();
        account.setId(7L);
        account.setStatus(1);
        account.setTokenVersion(0);
        when(users.selectById(7L)).thenReturn(account);
        mvc = MockMvcBuilders.standaloneSetup(new AssistantController(assistant))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwt, users)).build();
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    void authenticatedPostStreamsAnEventBeforeCompletionWithAntiBufferingHeaders() throws Exception {
        SseEmitter emitter = new SseEmitter(300000L);
        when(assistant.streamMessage(eq(7L), eq(31L), any(AssistantMessageSendRequest.class))).thenReturn(emitter);

        MvcResult result = mvc.perform(post(STREAM_PATH + "?userId=99")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content(BODY))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // 不等待 complete：验证 Spring 已将首事件交给 HTTP 响应输出。
        emitter.send(SseEmitter.event().name("delta").data("first-chunk"));
        header().string("X-Accel-Buffering", "no").match(result);
        header().string("Cache-Control", "no-cache, no-transform").match(result);
        content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM).match(result);
        content().string(containsString("event:delta\ndata:first-chunk\n\n")).match(result);
        emitter.send(SseEmitter.event().name("delta").data("second-chunk"));
        emitter.complete();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data:second-chunk")));
        verify(assistant).streamMessage(eq(7L), eq(31L), any(AssistantMessageSendRequest.class));
    }

    @Test
    void anonymousStreamingPostStillRequiresAuthentication() throws Exception {
        mvc.perform(post(STREAM_PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(request().asyncNotStarted());
        verifyNoInteractions(assistant);
    }

    @Test
    void emptyStreamingMessageIsRejectedBeforeOpeningAStream() throws Exception {
        mvc.perform(post(STREAM_PATH).header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contentText\":\" \"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(request().asyncNotStarted());
        verifyNoInteractions(assistant);
    }

    @Test
    void ordinaryAssistantApiKeepsItsJsonResponse() throws Exception {
        when(assistant.getSessionDetail(7L, 31L, 30)).thenReturn(AssistantSessionDetailVO.builder().id(31L).build());
        mvc.perform(get("/api/assistant/sessions/31").header("Authorization", "Bearer valid-token"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("X-Accel-Buffering"))
                .andExpect(request().asyncNotStarted());
    }
}
