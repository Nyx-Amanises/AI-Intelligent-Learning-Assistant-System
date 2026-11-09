package com.aiassistant.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.note.*;
import com.aiassistant.learning.mapper.NoteNotebookMapper;
import com.aiassistant.learning.mapper.PersonalNoteMapper;
import com.aiassistant.learning.service.impl.PersonalNoteServiceImpl;
import com.aiassistant.learning.vo.note.PersonalNoteVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Real V30 migration, SQL, rollback and concurrent requests in an isolated database. */
class PersonalNoteDatabaseTest {
    private JdbcTemplate jdbc;
    private PersonalNoteMapper notes;
    private NoteNotebookMapper notebooks;
    private PersonalNoteService service;
    private DataSourceTransactionManager transactions;
    private ValidatorFactory validation;

    @BeforeEach
    void database() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:notes_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(source);
        try (Connection connection = source.getConnection(); var migration = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/V30__create_personal_notes.sql")),
                StandardCharsets.UTF_8)) {
            RunScript.execute(connection, migration);
        }
        jdbc.execute("CREATE TABLE study_material (id BIGINT PRIMARY KEY, user_id BIGINT, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE study_resource (id BIGINT PRIMARY KEY, status VARCHAR(20))");
        jdbc.execute("CREATE TABLE question_set (id BIGINT PRIMARY KEY, user_id BIGINT, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE question_item (id BIGINT PRIMARY KEY, question_set_id BIGINT)");
        jdbc.execute("CREATE TABLE practice_session (id BIGINT PRIMARY KEY, user_id BIGINT, question_set_id BIGINT, question_ids_json VARCHAR(1000))");
        jdbc.update("INSERT INTO study_material VALUES (11,7,0),(12,8,0),(13,7,1)");
        jdbc.update("INSERT INTO study_resource VALUES (21,'PUBLISHED'),(22,'DRAFT'),(23,'WITHDRAWN')");
        jdbc.update("INSERT INTO question_set VALUES (31,7,0),(32,8,0),(33,7,1),(34,7,0)");
        jdbc.update("INSERT INTO question_item VALUES (41,31),(42,32),(43,33),(44,31)");
        jdbc.update("INSERT INTO practice_session VALUES (51,7,31,NULL),(52,8,31,NULL),(53,7,34,NULL),(54,7,31,'[44]')");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PersonalNoteMapper.class);
        configuration.addMapper(NoteNotebookMapper.class);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setConfiguration(configuration);
        factory.setDataSource(source);
        factory.setTransactionFactory(new SpringManagedTransactionFactory());
        SqlSessionTemplate session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        notes = session.getMapper(PersonalNoteMapper.class);
        notebooks = session.getMapper(NoteNotebookMapper.class);
        transactions = new DataSourceTransactionManager(source);
        validation = Validation.buildDefaultValidatorFactory();
        service = transactional(notes, notebooks);
    }

    @AfterEach
    void closeValidator() { validation.close(); }

    private PersonalNoteService transactional(PersonalNoteMapper noteMapper, NoteNotebookMapper notebookMapper) {
        var target = new PersonalNoteServiceImpl(noteMapper, notebookMapper, validation.getValidator());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (PersonalNoteService) factory.getProxy();
    }

    @Test
    void userIsolationAppliesToDetailUpdateDeleteAndPage() {
        PersonalNoteVO own = service.create(7L, create("私有正文"));
        PersonalNoteVO other = service.create(8L, create("他人正文"));
        assertCode(404, () -> service.detail(8L, own.getId()));
        assertCode(404, () -> service.update(8L, own.getId(), favorite(own.getVersion(), true)));
        assertCode(404, () -> service.delete(8L, own.getId()));
        var page = service.page(7L, new PersonalNotePageQuery());
        assertEquals(1, page.getTotal());
        assertEquals(List.of(own.getId()), page.getRecords().stream().map(PersonalNoteVO::getId).toList());
        assertEquals("他人正文", service.detail(8L, other.getId()).getContentText());
        assertCode(401, () -> service.detail(null, own.getId()));
        assertCode(401, () -> service.notebooks(0L));
    }

    @Test
    void idempotencyNeverOverwritesAndIsScopedToUserIncludingDeletedNotes() {
        var request = create("初稿");
        var first = service.create(7L, request);
        request.setContentText("重试不应覆盖");
        var repeated = service.create(7L, request);
        assertEquals(first.getId(), repeated.getId());
        assertEquals("初稿", repeated.getContentText());
        assertNotEquals(first.getId(), service.create(8L, request).getId());
        service.delete(7L, first.getId());
        assertCode(409, () -> service.create(7L, request));
        assertEquals(0, service.page(7L, new PersonalNotePageQuery()).getTotal());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM personal_note", Integer.class));
    }

    @Test
    void simultaneousCreateRetriesHaveOneIdentity() throws Exception {
        CountDownLatch bothChecked = new CountDownLatch(2);
        PersonalNoteMapper synchronizedNotes = mock(PersonalNoteMapper.class, AdditionalAnswers.delegatesTo(notes));
        doAnswer(call -> {
            var found = notes.findByClientKey(call.getArgument(0), call.getArgument(1));
            bothChecked.countDown();
            assertTrue(bothChecked.await(4, TimeUnit.SECONDS));
            return found;
        }).when(synchronizedNotes).findByClientKey(anyLong(), anyString());
        var racing = transactional(synchronizedNotes, notebooks);
        var request = create("只创建一次");
        var workers = Executors.newFixedThreadPool(2);
        try {
            var one = workers.submit(() -> racing.create(7L, request));
            var two = workers.submit(() -> racing.create(7L, request));
            assertEquals(one.get(8, TimeUnit.SECONDS).getId(), two.get(8, TimeUnit.SECONDS).getId());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personal_note", Integer.class));
        } finally { workers.shutdownNow(); }
    }

    @Test
    void competingAutosavesOnlyOneVersionWinsWithoutLostUpdate() throws Exception {
        var note = service.create(7L, create("初稿"));
        CountDownLatch start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var one = workers.submit(() -> { start.await(); return saveOutcome(note, "第一窗口"); });
            var two = workers.submit(() -> { start.await(); return saveOutcome(note, "第二窗口"); });
            start.countDown();
            assertEquals(List.of(200, 409), List.of(one.get(8, TimeUnit.SECONDS), two.get(8, TimeUnit.SECONDS)).stream().sorted().toList());
            var saved = service.detail(7L, note.getId());
            assertEquals(2, saved.getVersion());
            assertTrue(List.of("第一窗口", "第二窗口").contains(saved.getContentText()));
            assertEquals(saved.getContentText(), new ObjectMapper().readTree(saved.getContentJson()).at("/content/0/content/0/text").asText());
        } finally { workers.shutdownNow(); }
    }

    private int saveOutcome(PersonalNoteVO note, String text) {
        try { service.update(7L, note.getId(), edit(note.getVersion(), text)); return 200; }
        catch (BusinessException exception) { return exception.getCode(); }
    }

    @Test
    void favoritePatchKeepsContentAndOmittedNotebookWhileExplicitNullUnfiles() throws Exception {
        long notebook = notebook(7L, "复习");
        var request = create("含代码 === 的正文");
        request.setNotebookId(notebook);
        var note = service.create(7L, request);
        ObjectMapper json = new ObjectMapper();
        var patch = json.readValue("{\"favorite\":true,\"version\":1}", PersonalNoteUpdateRequest.class);
        assertFalse(patch.isNotebookIdProvided());
        var pinned = service.update(7L, note.getId(), patch);
        assertTrue(pinned.getFavorite());
        assertEquals(notebook, pinned.getNotebookId());
        assertEquals(request.getContentJson(), pinned.getContentJson());
        assertEquals(request.getContentText(), pinned.getContentText());
        var unfile = json.readValue("{\"notebookId\":null,\"version\":2}", PersonalNoteUpdateRequest.class);
        assertTrue(unfile.isNotebookIdProvided());
        assertNull(service.update(7L, note.getId(), unfile).getNotebookId());
    }

    @Test
    void sourceCreationRequiresOwnedMaterialPublishedResourceOwnedQuestionAndMatchingSession() {
        for (long id : new long[]{12, 13, 999}) assertCode(404, () -> service.create(7L, source("MATERIAL", id)));
        for (long id : new long[]{22, 23, 999}) assertCode(404, () -> service.create(7L, source("RESOURCE", id)));
        for (long id : new long[]{42, 43, 999}) assertCode(404, () -> service.create(7L, source("QUESTION", id)));
        for (long session : new long[]{52, 53, 54, 999}) {
            var request = source("QUESTION", 41);
            request.setPracticeSessionId(session);
            assertCode(404, () -> service.create(7L, request));
        }
        var valid = source("QUESTION", 41);
        valid.setPracticeSessionId(51L);
        assertEquals(51L, service.create(7L, valid).getPracticeSessionId());
        assertNotNull(service.create(7L, source("MATERIAL", 11)).getId());
        assertNotNull(service.create(7L, source("RESOURCE", 21)).getId());
    }

    @Test
    void sourceCannotBeReassignedAndOldNotesRemainEditableAfterSourceDisappears() {
        var request = source("RESOURCE", 21);
        var note = service.create(7L, request);
        jdbc.update("UPDATE study_resource SET status='WITHDRAWN' WHERE id=21");
        assertEquals(note.getId(), service.create(7L, request).getId());
        var updated = service.update(7L, note.getId(), edit(note.getVersion(), "自己的笔记仍可编辑"));
        assertEquals("自己的笔记仍可编辑", updated.getContentText());
        var changed = favorite(updated.getVersion(), true);
        changed.setSourceId(22L);
        assertCode(400, () -> service.update(7L, note.getId(), changed));
        assertEquals(21L, service.detail(7L, note.getId()).getSourceId());
    }

    @Test
    void notebookOwnershipAndCountsAreIsolatedAndDeletionPreservesNotes() {
        long own = notebook(7L, "我的笔记本"), other = notebook(8L, "别人的笔记本");
        var bad = create("不允许归档到他人的笔记本");
        bad.setNotebookId(other);
        assertCode(404, () -> service.create(7L, bad));
        assertCode(404, () -> service.updateNotebook(7L, other, notebookRequest("改名")));
        assertCode(404, () -> service.deleteNotebook(7L, other));
        var request = create("完整正文");
        request.setNotebookId(own);
        var note = service.create(7L, request);
        assertEquals(1, service.notebooks(7L).size());
        assertEquals(1, service.notebooks(7L).get(0).getNoteCount());
        assertEquals(0, service.notebooks(8L).get(0).getNoteCount());
        service.deleteNotebook(7L, own);
        assertTrue(service.notebooks(7L).isEmpty());
        var preserved = service.detail(7L, note.getId());
        assertNull(preserved.getNotebookId());
        assertEquals("完整正文", preserved.getContentText());
        assertEquals(2, preserved.getVersion());
        assertCode(409, () -> service.update(7L, note.getId(), favorite(1, true)));
        var unfiled = new PersonalNotePageQuery();
        unfiled.setNotebookId(0L);
        assertEquals(1, service.page(7L, unfiled).getTotal());
    }

    @Test
    void notebookDeleteFailureRollsBackUnfilingAndVersionBump() {
        long notebook = notebook(7L, "仍应存在");
        var request = create("不应被改动");
        request.setNotebookId(notebook);
        var note = service.create(7L, request);
        NoteNotebookMapper failing = mock(NoteNotebookMapper.class, AdditionalAnswers.delegatesTo(notebooks));
        doThrow(new IllegalStateException("database write failed")).when(failing).deleteOwned(7L, notebook);
        assertThrows(IllegalStateException.class, () -> transactional(notes, failing).deleteNotebook(7L, notebook));
        var unchanged = service.detail(7L, note.getId());
        assertEquals(notebook, unchanged.getNotebookId());
        assertEquals(1, unchanged.getVersion());
        assertEquals(1, service.notebooks(7L).size());
    }

    @Test
    void deletingNotebookCannotRaceWithNewAssignmentAndLeaveDanglingNote() throws Exception {
        long notebook = notebook(7L, "即将删除");
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        NoteNotebookMapper holding = mock(NoteNotebookMapper.class, AdditionalAnswers.delegatesTo(notebooks));
        doAnswer(call -> {
            var value = notebooks.lockOwned(7L, notebook);
            locked.countDown();
            assertTrue(release.await(4, TimeUnit.SECONDS));
            return value;
        }).when(holding).lockOwned(7L, notebook);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var deleting = workers.submit(() -> transactional(notes, holding).deleteNotebook(7L, notebook));
            assertTrue(locked.await(4, TimeUnit.SECONDS));
            var request = create("并发归档");
            request.setNotebookId(notebook);
            var assigning = workers.submit(() -> {
                try { service.create(7L, request); return 200; }
                catch (BusinessException exception) { return exception.getCode(); }
            });
            release.countDown();
            deleting.get(8, TimeUnit.SECONDS);
            assertEquals(404, assigning.get(8, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personal_note WHERE notebook_id=? AND deleted=0", Integer.class, notebook));
        } finally { release.countDown(); workers.shutdownNow(); }
    }

    @Test
    void searchUsesLiteralTitleOrBodyAndGroupFiltersWithStablePagination() {
        service.create(7L, create("%_literal正文"));
        service.create(8L, create("%_literal他人正文"));
        service.create(7L, source("MATERIAL", 11));
        service.create(7L, source("RESOURCE", 21));
        service.create(7L, source("QUESTION", 41));
        var query = new PersonalNotePageQuery();
        query.setKeyword("%_literal");
        assertEquals(1, service.page(7L, query).getTotal());
        query.setKeyword("' OR 1=1 --");
        assertEquals(0, service.page(7L, query).getTotal());
        query.setKeyword(null);
        query.setSourceGroup("READING");
        query.setPageSize(1L);
        assertEquals(2, service.page(7L, query).getTotal());
        assertEquals(2, service.page(7L, query).getPages());
        var first = service.page(7L, query).getRecords().get(0).getId();
        query.setPageNum(2L);
        assertNotEquals(first, service.page(7L, query).getRecords().get(0).getId());
        query.setPageNum(1L);
        query.setSourceType("QUESTION");
        assertEquals("QUESTION", service.page(7L, query).getRecords().get(0).getSourceType());
        query.setFavorite(true);
        assertEquals(0, service.page(7L, query).getTotal());
    }

    @Test
    void pinnedNotesStayAheadOfNewerUnpinnedNotesAcrossPageBoundaries() {
        var oldPin = service.create(7L, create("较早置顶"));
        var sameTimePin = service.create(7L, create("同一时间置顶，以编号排序"));
        var newerPin = service.create(7L, create("较新置顶"));
        var regular = service.create(7L, create("四月普通笔记"));
        var olderRegular = service.create(7L, create("三月普通笔记"));
        var newestRegular = service.create(7L, create("五月普通笔记"));
        for (var pin : List.of(oldPin, sameTimePin, newerPin)) {
            service.update(7L, pin.getId(), favorite(pin.getVersion(), true));
        }
        jdbc.update("UPDATE personal_note SET updated_at='2026-01-01 12:00:00' WHERE id IN (?,?)", oldPin.getId(), sameTimePin.getId());
        jdbc.update("UPDATE personal_note SET updated_at='2026-02-01 12:00:00' WHERE id=?", newerPin.getId());
        jdbc.update("UPDATE personal_note SET updated_at='2026-04-01 12:00:00' WHERE id=?", regular.getId());
        jdbc.update("UPDATE personal_note SET updated_at='2026-03-01 12:00:00' WHERE id=?", olderRegular.getId());
        jdbc.update("UPDATE personal_note SET updated_at='2026-05-01 12:00:00' WHERE id=?", newestRegular.getId());
        var query = new PersonalNotePageQuery();
        query.setPageSize(2L);
        var expected = List.of(
                List.of(newerPin.getId(), sameTimePin.getId()),
                List.of(oldPin.getId(), newestRegular.getId()),
                List.of(regular.getId(), olderRegular.getId()));
        for (int index = 0; index < expected.size(); index++) {
            query.setPageNum((long) index + 1);
            var page = service.page(7L, query);
            assertEquals(6, page.getTotal());
            assertEquals(3, page.getPages());
            assertEquals(expected.get(index), page.getRecords().stream().map(PersonalNoteVO::getId).toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{}", "{\"type\":\"paragraph\"}", "{\"type\":\"doc\",\"content\":{}}", "{\"type\":\"doc\"}{}", "not json"})
    void malformedDocumentsNeverPersist(String document) {
        var request = create("测试");
        request.setContentJson(document);
        assertCode(400, () -> service.create(7L, request));
        assertEquals(0, service.page(7L, new PersonalNotePageQuery()).getTotal());
    }

    @Test
    void oversizedAndDeeplyNestedContentAndUnsafeNotebookTokensAreRejected() {
        var request = create("测试");
        request.setContentJson("{\"type\":\"doc\",\"content\":" + "[".repeat(65) + "]".repeat(65) + "}");
        assertCode(400, () -> service.create(7L, request));
        request.setContentJson(" ".repeat(200001));
        assertCode(400, () -> service.create(7L, request));
        var invalid = notebookRequest("颜色");
        invalid.setColor("url(javascript:alert(1))");
        assertCode(400, () -> service.createNotebook(7L, invalid));
        var query = new PersonalNotePageQuery();
        query.setPageSize(101L);
        assertCode(400, () -> service.page(7L, query));
    }

    private long notebook(Long user, String name) { return service.createNotebook(user, notebookRequest(name)).getId(); }
    private static NotebookWriteRequest notebookRequest(String name) {
        var request = new NotebookWriteRequest(); request.setName(name); return request;
    }
    private static PersonalNoteCreateRequest create(String text) {
        var request = new PersonalNoteCreateRequest();
        request.setClientKey(UUID.randomUUID().toString());
        request.setTitle("学习笔记");
        request.setContentJson(document(text));
        request.setContentText(text);
        return request;
    }
    private static PersonalNoteCreateRequest source(String type, long id) {
        var request = create(type + " 正文"); request.setSourceType(type); request.setSourceId(id); return request;
    }
    private static PersonalNoteUpdateRequest favorite(int version, boolean value) {
        var request = new PersonalNoteUpdateRequest(); request.setVersion(version); request.setFavorite(value); return request;
    }
    private static PersonalNoteUpdateRequest edit(int version, String text) {
        var request = new PersonalNoteUpdateRequest(); request.setVersion(version);
        request.setContentJson(document(text)); request.setContentText(text); return request;
    }
    private static String document(String text) {
        try { return "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":"
                + new ObjectMapper().writeValueAsString(text) + "}]}]}"; }
        catch (Exception exception) { throw new IllegalArgumentException(exception); }
    }
    private static void assertCode(int code, Runnable action) {
        assertEquals(code, assertThrows(BusinessException.class, action::run).getCode());
    }
}
