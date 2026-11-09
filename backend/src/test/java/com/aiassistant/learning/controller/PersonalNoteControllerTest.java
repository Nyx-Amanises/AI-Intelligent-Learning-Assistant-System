package com.aiassistant.learning.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.note.PersonalNotePageQuery;
import com.aiassistant.learning.dto.note.PersonalNoteUpdateRequest;
import com.aiassistant.learning.interceptor.AuthInterceptor;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.PersonalNoteService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.note.PersonalNoteVO;
import com.aiassistant.learning.vo.page.PageVO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PersonalNoteControllerTest {
    private final PersonalNoteService service = mock(PersonalNoteService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        UserContext.setCurrentUserId(7L);
        mvc = MockMvcBuilders.standaloneSetup(new PersonalNoteController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void cleanup() { UserContext.clear(); }

    @Test
    void pageBindsGroupAndUsesAuthenticatedIdentityRatherThanUserParameter() throws Exception {
        when(service.page(eq(7L), any())).thenReturn(PageVO.<PersonalNoteVO>builder()
                .current(1L).size(20L).total(0L).pages(0L).records(List.of()).build());
        mvc.perform(get("/api/notes/page").param("userId", "999").param("sourceGroup", "READING"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.size").value(20));
        ArgumentCaptor<PersonalNotePageQuery> query = ArgumentCaptor.forClass(PersonalNotePageQuery.class);
        verify(service).page(eq(7L), query.capture());
        assertEquals("READING", query.getValue().getSourceGroup());
        assertEquals(1L, query.getValue().getPageNum());
    }

    @Test
    void partialUpdateKeepsOmittedFieldsAbsentAndBindsExplicitNullNotebook() throws Exception {
        mvc.perform(put("/api/notes/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true,\"version\":3,\"userId\":999}"))
                .andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<PersonalNoteUpdateRequest> patch = ArgumentCaptor.forClass(PersonalNoteUpdateRequest.class);
        verify(service).update(eq(7L), eq(11L), patch.capture());
        assertNull(patch.getValue().getContentJson());
        assertNull(patch.getValue().getContentText());
        assertNull(patch.getValue().getTitle());
        assertFalse(patch.getValue().isNotebookIdProvided());
        clearInvocations(service);
        mvc.perform(put("/api/notes/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notebookId\":null,\"version\":4}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).update(eq(7L), eq(11L), patch.capture());
        assertTrue(patch.getValue().isNotebookIdProvided());
    }

    @Test
    void conflictRetainsControlledBusinessCodeAndMessage() throws Exception {
        when(service.update(eq(7L), eq(11L), any())).thenThrow(new BusinessException(409, "笔记版本冲突，请重新加载后再保存"));
        mvc.perform(put("/api/notes/11").contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true,\"version\":1}"))
                .andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.message").value("笔记版本冲突，请重新加载后再保存"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1", "not-a-number"})
    void invalidPagingNeverExecutesQuery(String size) throws Exception {
        mvc.perform(get("/api/notes/page").param("pageSize", size)).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void missingVersionAndInvalidCreateKeyFailValidation() throws Exception {
        mvc.perform(put("/api/notes/11").contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientKey\":\"not-a-uuid\",\"contentJson\":\"{\\\"type\\\":\\\"doc\\\"}\",\"contentText\":\"测试\"}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void notebookRoutesAreUnambiguousAndAnonymousUsersAreBlockedBeforeEveryController() throws Exception {
        when(service.notebooks(7L)).thenReturn(List.of());
        mvc.perform(get("/api/notes/notebooks")).andExpect(jsonPath("$.code").value(200));
        verify(service).notebooks(7L);
        clearInvocations(service);
        MockMvc guarded = MockMvcBuilders.standaloneSetup(new PersonalNoteController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(mock(JwtTokenUtil.class), mock(SysUserMapper.class))).build();
        guarded.perform(get("/api/notes/page")).andExpect(jsonPath("$.code").value(401));
        guarded.perform(get("/api/notes/11")).andExpect(jsonPath("$.code").value(401));
        guarded.perform(get("/api/notes/notebooks")).andExpect(jsonPath("$.code").value(401));
        guarded.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(401));
        guarded.perform(delete("/api/notes/notebooks/11")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }
}
