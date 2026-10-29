package com.aiassistant.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.wrongquestion.WrongQuestionPageQuery;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.WrongQuestionService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.wrongquestion.WrongQuestionKnowledgePointVO;
import com.aiassistant.learning.vo.wrongquestion.WrongQuestionVO;
import io.jsonwebtoken.Jwts;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WrongQuestionControllerTest {
    private final WrongQuestionService service = mock(WrongQuestionService.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final JwtTokenUtil jwt = mock(JwtTokenUtil.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserContext.clear();
        when(jwt.parseToken("valid-token")).thenReturn(Jwts.claims().subject("7").build());
        SysUser account = new SysUser();
        account.setId(7L);
        account.setStatus(1);
        account.setTokenVersion(0);
        when(users.selectById(7L)).thenReturn(account);
        mvc = MockMvcBuilders.standaloneSetup(new WrongQuestionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwt, users))
                .build();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void candidatesUseStaticRouteAndAuthenticatedUserWithoutPaging() throws Exception {
        when(service.listKnowledgePoints(7L, 41L, "MULTIPLE"))
                .thenReturn(List.of(new WrongQuestionKnowledgePointVO("算法", 12)));

        mvc.perform(get("/api/wrong-questions/knowledge-points")
                        .header("Authorization", "Bearer valid-token")
                        .param("materialId", "41").param("questionType", "MULTIPLE")
                        .param("current", "2").param("size", "1").param("keyword", "不会限制候选"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].knowledgePoint").value("算法"))
                .andExpect(jsonPath("$.data[0].wrongCount").value(12));
        verify(service).listKnowledgePoints(7L, 41L, "MULTIPLE");
    }

    @Test
    void pageBindsExactKnowledgePointSeparatelyFromKeyword() throws Exception {
        when(service.pageWrongQuestions(eq(7L), any())).thenReturn(PageVO.<WrongQuestionVO>builder().records(List.of()).build());
        mvc.perform(get("/api/wrong-questions/page")
                        .header("Authorization", "Bearer valid-token")
                        .param("knowledgePoint", "算法").param("keyword", "排序")
                        .param("materialId", "41").param("questionType", "SINGLE"))
                .andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<WrongQuestionPageQuery> query = ArgumentCaptor.forClass(WrongQuestionPageQuery.class);
        verify(service).pageWrongQuestions(eq(7L), query.capture());
        assertEquals("算法", query.getValue().getKnowledgePoint());
        assertEquals("排序", query.getValue().getKeyword());
        assertEquals(41L, query.getValue().getMaterialId());
    }

    @Test
    void redoUsesTheCurrentUserAndReturnsTheNewSession() throws Exception {
        when(service.redoWrongQuestion(7L, 11L)).thenReturn(PracticeDetailVO.builder()
                .sessionId(501L).questionIds(List.of(21L)).build());
        mvc.perform(post("/api/wrong-questions/11/redo").header("Authorization", "Bearer valid-token"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(501))
                .andExpect(jsonPath("$.data.questionIds[0]").value(21));
        verify(service).redoWrongQuestion(7L, 11L);
    }

    @Test
    void candidatesAndRedoRequireAuthentication() throws Exception {
        mvc.perform(get("/api/wrong-questions/knowledge-points")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(post("/api/wrong-questions/11/redo")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void overlyLongKnowledgePointIsRejectedBeforeQuerying() throws Exception {
        mvc.perform(get("/api/wrong-questions/page")
                        .header("Authorization", "Bearer valid-token").param("knowledgePoint", "点".repeat(201)))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }
}
