package com.aiassistant.learning.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.common.security.RequireAdmin;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.resource.ResourceAdminPageQuery;
import com.aiassistant.learning.dto.resource.ResourcePageQuery;
import com.aiassistant.learning.dto.resource.ResourceStatusRequest;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.StudyResourceService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.resource.ResourceVO;
import java.util.List;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudyResourceControllerTest {
    private final StudyResourceService service = mock(StudyResourceService.class);
    private MockMvc mvc;

    @BeforeEach
    void configureApi() {
        UserContext.setCurrentUserId(7L);
        mvc = MockMvcBuilders.standaloneSetup(new StudyResourceController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void publicPageUsesAuthenticatedUserAndExpectedDefaults() throws Exception {
        when(service.page(eq(7L), any())).thenReturn(PageVO.<ResourceVO>builder()
                .current(1L).size(12L).total(0L).pages(0L).records(List.of()).build());
        mvc.perform(get("/api/resources/page").param("userId", "999"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.size").value(12));
        ArgumentCaptor<ResourcePageQuery> query = ArgumentCaptor.forClass(ResourcePageQuery.class);
        verify(service).page(eq(7L), query.capture());
        assertEquals(1L, query.getValue().getCurrent());
        assertEquals(12L, query.getValue().getSize());
        assertFalse(query.getValue().getBookmarkedOnly());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "-1", "not-a-number"})
    void badPageSizeIsRejectedBeforeCallingService(String size) throws Exception {
        mvc.perform(get("/api/resources/page").param("size", size)).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void overlongSearchAndUnknownAdminStatusAreRejected() throws Exception {
        mvc.perform(get("/api/resources/page").param("keyword", "字".repeat(101)))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/resources/admin/page").param("status", "ANY"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void resourceKindBindsForBothPublicAndAdminPage() throws Exception {
        mvc.perform(get("/api/resources/page").param("resourceKind", "TEXTBOOK"))
                .andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<ResourcePageQuery> query = ArgumentCaptor.forClass(ResourcePageQuery.class);
        verify(service).page(eq(7L), query.capture());
        assertEquals("TEXTBOOK", query.getValue().getResourceKind());
        mvc.perform(get("/api/resources/admin/page").param("resourceKind", "REVIEW_PACK"))
                .andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<ResourceAdminPageQuery> adminQuery = ArgumentCaptor.forClass(ResourceAdminPageQuery.class);
        verify(service).adminPage(eq(7L), adminQuery.capture());
        assertEquals("REVIEW_PACK", adminQuery.getValue().getResourceKind());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOOK", "textbook", "ALL", ""})
    void invalidResourceKindIsRejectedBeforeAnyServiceCall(String kind) throws Exception {
        mvc.perform(get("/api/resources/page").param("resourceKind", kind))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/resources/admin/page").param("resourceKind", kind))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void accessModeAndIsbnSearchBindForPublicAndAdminPages() throws Exception {
        mvc.perform(get("/api/resources/page").param("accessMode", "CATALOG_ONLY").param("keyword", "9780306406157"))
                .andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<ResourcePageQuery> query = ArgumentCaptor.forClass(ResourcePageQuery.class);
        verify(service).page(eq(7L), query.capture());
        assertEquals("CATALOG_ONLY", query.getValue().getAccessMode());
        assertEquals("9780306406157", query.getValue().getKeyword());
        mvc.perform(get("/api/resources/admin/page").param("accessMode", "OPEN_RESOURCE"))
                .andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<ResourceAdminPageQuery> adminQuery = ArgumentCaptor.forClass(ResourceAdminPageQuery.class);
        verify(service).adminPage(eq(7L), adminQuery.capture());
        assertEquals("OPEN_RESOURCE", adminQuery.getValue().getAccessMode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"catalog_only", "FULL_TEXT", "ALL", ""})
    void invalidAccessModeIsRejectedBeforeAnyServiceCall(String mode) throws Exception {
        mvc.perform(get("/api/resources/page").param("accessMode", mode))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/resources/admin/page").param("accessMode", mode))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void bookmarkRequiresBooleanAndUsesAuthenticatedOwner() throws Exception {
        mvc.perform(put("/api/resources/11/bookmark").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
        mvc.perform(put("/api/resources/11/bookmark").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarked\":false,\"userId\":999}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).bookmark(7L, 11L, false);
    }

    @Test
    void importReturnsOnlyPrivateMaterialId() throws Exception {
        when(service.importMaterial(7L, 11L)).thenReturn(91L);
        mvc.perform(post("/api/resources/11/import").param("userId", "999"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.materialId").value(91));
        verify(service).importMaterial(7L, 11L);
    }

    @Test
    void recommendationsPassExplicitPointAndDefaultLimit() throws Exception {
        when(service.recommendations(7L, "链表", 3)).thenReturn(List.of());
        mvc.perform(get("/api/resources/recommendations").param("knowledgePoint", "链表"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).recommendations(7L, "链表", 3);
    }

    @Test
    void adminOperationsRetainInterceptorGuardAndValidateBody() throws Exception {
        assertNotNull(StudyResourceController.class.getMethod("adminPage", ResourceAdminPageQuery.class)
                .getAnnotation(RequireAdmin.class));
        assertNotNull(StudyResourceController.class.getMethod("updateStatus", Long.class, ResourceStatusRequest.class)
                .getAnnotation(RequireAdmin.class));
        assertNotNull(StudyResourceController.class.getMethod("adminDetail", Long.class).getAnnotation(RequireAdmin.class));
        mvc.perform(put("/api/resources/admin/11/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void realAuthInterceptorDeniesAnonymousAndRegularUsersBeforeAdminControllerExecutes() throws Exception {
        JwtTokenUtil jwt = mock(JwtTokenUtil.class);
        SysUserMapper users = mock(SysUserMapper.class);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("7");
        when(jwt.parseToken("unit-test-token")).thenReturn(claims);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setStatus(1);
        user.setRoleCode("USER");
        when(users.selectById(7L)).thenReturn(user);
        MockMvc guarded = MockMvcBuilders.standaloneSetup(new StudyResourceController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(new AuthInterceptor(jwt, users)).build();
        guarded.perform(get("/api/resources/admin/page")).andExpect(jsonPath("$.code").value(401));
        guarded.perform(get("/api/resources/admin/page").header("Authorization", "Bearer unit-test-token"))
                .andExpect(jsonPath("$.code").value(403));
        guarded.perform(get("/api/resources/admin/11").header("Authorization", "Bearer unit-test-token"))
                .andExpect(jsonPath("$.code").value(403));
        guarded.perform(put("/api/resources/admin/11/status").header("Authorization", "Bearer unit-test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(service);
        user.setRoleCode("ADMIN");
        guarded.perform(get("/api/resources/admin/11").header("Authorization", "Bearer unit-test-token"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).adminDetail(7L, 11L);
    }
}
