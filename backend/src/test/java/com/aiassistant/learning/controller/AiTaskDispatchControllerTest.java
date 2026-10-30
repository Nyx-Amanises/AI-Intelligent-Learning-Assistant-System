package com.aiassistant.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.config.JwtProperties;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.AiGenerationRecordMapper;
import com.aiassistant.learning.mapper.AiTaskMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.cache.AiTaskRedisCacheService;
import com.aiassistant.learning.service.impl.AiTaskServiceImpl;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 实际经过 JWT 鉴权、控制器和服务归属检查；仅持久层与异步执行器使用替身。 */
class AiTaskDispatchControllerTest {
    private static final long USER_ID = 7L;
    private static final long TASK_ID = 81L;
    private final AiTaskMapper taskMapper = mock(AiTaskMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final AiTaskService executor = mock(AiTaskService.class);
    private final AiTaskRedisCacheService cache = mock(AiTaskRedisCacheService.class);
    private MockMvc mvc;
    private String token;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "dispatch-controller"), AiTask.class);
    }

    @BeforeEach
    void setup() {
        UserContext.clear();
        TransactionSynchronizationManager.initSynchronization();
        JwtProperties properties = new JwtProperties();
        properties.setSecret("dispatch-controller-test-secret-01234567890123456789");
        properties.setExpireHours(1L);
        JwtTokenUtil jwt = new JwtTokenUtil(properties);
        token = jwt.createToken(USER_ID, "learner", 0);
        SysUser ordinaryUser = new SysUser();
        ordinaryUser.setId(USER_ID);
        ordinaryUser.setStatus(1);
        ordinaryUser.setTokenVersion(0);
        ordinaryUser.setRoleCode("USER");
        when(users.selectById(USER_ID)).thenReturn(ordinaryUser);
        AiTaskServiceImpl service = new AiTaskServiceImpl(taskMapper, mock(StudyMaterialMapper.class),
                mock(PracticeSessionMapper.class), mock(QuestionSetMapper.class), mock(AiQuestionService.class),
                mock(AiGenerationRecordMapper.class), mock(AiSummaryService.class), List.of(), new ObjectMapper(), cache, executor);
        mvc = MockMvcBuilders.standaloneSetup(new AiTaskController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwt, users)).build();
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
        UserContext.clear();
    }

    @Test
    void ordinaryUserCanRedispatchOwnPendingTaskAfterCommit() throws Exception {
        when(taskMapper.selectOne(any())).thenReturn(task("PENDING"));

        mvc.perform(post("/api/ai/tasks/81/dispatch?userId=99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(TASK_ID))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        assertOwnedLockQuery();
        assertNull(UserContext.getCurrentUserId(), "请求完成后应清除登录上下文");
        verifyNoInteractions(executor);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(executor).executeTask(TASK_ID);
        verify(taskMapper, never()).insert(any(AiTask.class));
    }

    @Test
    void anonymousRequestNeverReachesTaskService() throws Exception {
        mvc.perform(post("/api/ai/tasks/81/dispatch")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(taskMapper, cache, executor);
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void taskOutsideTheLoggedInUsersScopeIsNotFound() throws Exception {
        // 数据库按 user_id + id 查询时，他人的任务不会返回。
        when(taskMapper.selectOne(any())).thenReturn(null);

        mvc.perform(post("/api/ai/tasks/81/dispatch?userId=99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(404));

        assertOwnedLockQuery();
        verifyNoInteractions(cache, executor);
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "SUCCESS", "FAILED", "CANCELLED"})
    void nonPendingTaskCannotBeRedispatched(String status) throws Exception {
        when(taskMapper.selectOne(any())).thenReturn(task(status));

        mvc.perform(post("/api/ai/tasks/81/dispatch").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只有等待中的任务才能重新派发"));

        verifyNoInteractions(cache, executor);
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void assertOwnedLockQuery() {
        ArgumentCaptor<LambdaQueryWrapper<AiTask>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectOne(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("id =") && sql.contains("user_id =") && sql.contains("for update"));
        assertEquals(Set.of(USER_ID, TASK_ID), Set.copyOf(query.getValue().getParamNameValuePairs().values()));
    }

    private AiTask task(String status) {
        AiTask task = new AiTask();
        task.setId(TASK_ID); task.setUserId(USER_ID); task.setStatus(status);
        task.setTaskType("EMBEDDING"); task.setBizType("MATERIAL"); task.setBizId(42L);
        task.setProgressRate(0); task.setRetryCount(0);
        return task;
    }
}
