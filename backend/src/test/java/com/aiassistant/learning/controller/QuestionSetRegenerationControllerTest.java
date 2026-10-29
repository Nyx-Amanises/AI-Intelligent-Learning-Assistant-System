package com.aiassistant.learning.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.QuestionSetRegenerationService;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QuestionSetRegenerationControllerTest {
    private static final String BODY = """
            {"singleCount":0,"multipleCount":2,"judgeCount":0,"shortAnswerCount":0,
             "knowledgePoints":["事务"],"difficultyLevel":5,"modelName":"model","configurationReviewed":true}
            """;
    private final QuestionSetMapper sets = mock(QuestionSetMapper.class);
    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final AiTaskService tasks = mock(AiTaskService.class);
    private MockMvc mvc;

    @BeforeAll
    static void metadata() {
        var builder = new MapperBuilderAssistant(new MybatisConfiguration(), "regen-controller");
        TableInfoHelper.initTableInfo(builder, QuestionSet.class);
        TableInfoHelper.initTableInfo(builder, StudyMaterial.class);
    }

    @BeforeEach
    void setup() {
        UserContext.clear();
        JwtTokenUtil jwt = mock(JwtTokenUtil.class);
        SysUserMapper users = mock(SysUserMapper.class);
        when(jwt.parseToken("valid-token")).thenReturn(Jwts.claims().subject("7").build());
        SysUser account = new SysUser();
        account.setId(7L); account.setStatus(1); account.setTokenVersion(0);
        when(users.selectById(7L)).thenReturn(account);
        QuestionSet source = new QuestionSet();
        source.setId(91L); source.setUserId(7L); source.setMaterialId(42L); source.setStatus("PUBLISHED"); source.setTitle("原题集");
        source.setGenerationRequestJson(BODY.replace(",\"configurationReviewed\":true", ""));
        when(sets.selectOne(any())).thenReturn(source);
        StudyMaterial material = new StudyMaterial();
        material.setId(42L); material.setParseStatus("SUCCESS");
        when(materials.getOne(any())).thenReturn(material);
        when(tasks.submitQuestionGenerateTask(eq(7L), eq(42L), any())).thenReturn(AiTaskDetailVO.builder().id(201L).status("PENDING").build());
        var service = new QuestionSetRegenerationService(sets, mock(QuestionItemMapper.class), materials, tasks, new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new QuestionSetController(mock(QuestionSetService.class), service))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(new AuthInterceptor(jwt, users)).build();
    }

    @AfterEach
    void cleanup() { UserContext.clear(); }

    @Test
    void anonymousConfigAndRegenerationRequestsAreDenied() throws Exception {
        mvc.perform(get("/api/question-set/91/regeneration-config")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(post("/api/question-set/91/regenerate").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(tasks, sets);
    }

    @Test
    void authenticatedPostUsesOriginalMaterialAndReturnsNewTask() throws Exception {
        mvc.perform(get("/api/question-set/91/regeneration-config").header("Authorization", "Bearer valid-token"))
                .andExpect(jsonPath("$.data.parameterMode").value("SAVED"))
                .andExpect(jsonPath("$.data.request.difficultyLevel").value(5));
        mvc.perform(post("/api/question-set/91/regenerate?userId=99&materialId=100")
                        .header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.id").value(201));
        verify(tasks).submitQuestionGenerateTask(eq(7L), eq(42L), any());
    }

    @Test
    void omittedDifficultyCannotSilentlyUseDefaultThree() throws Exception {
        mvc.perform(post("/api/question-set/91/regenerate").header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"difficultyLevel\":5,", "")))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(tasks);
    }
}
