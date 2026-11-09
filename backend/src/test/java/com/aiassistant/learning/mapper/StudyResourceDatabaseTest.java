package com.aiassistant.learning.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.resource.ResourcePageQuery;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.entity.StudyResource;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.service.KnowledgeMasteryService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.StudyResourceService;
import com.aiassistant.learning.service.impl.StudyResourceServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Exercises real migration/mapper SQL and transaction locks in an isolated H2 database using MySQL mode. */
class StudyResourceDatabaseTest {
    private JdbcTemplate jdbc;
    private StudyResourceMapper resources;
    private StudyResourceUserMapper links;
    private StudyMaterialMapper materialSources;
    private StudyResourceService service;
    private DataSourceTransactionManager transactionManager;
    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final AtomicLong materialIds = new AtomicLong(100);

    @BeforeEach
    void createIsolatedDatabase() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:resources_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE study_material (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL,
                  deleted INT NOT NULL DEFAULT 0, title VARCHAR(200), source_text CLOB, description VARCHAR(500))
                """);
        try (Connection connection = dataSource.getConnection();
                var migration = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                        "/db/migration/V18__create_study_resource_library.sql")), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, migration);
        }
        try (Connection connection = dataSource.getConnection();
                var migration = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                        "/db/migration/V20__classify_course_resources.sql")), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, migration);
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        try (Connection connection = dataSource.getConnection();
                var migration = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                        "/db/migration/V22__add_resource_cover_url.sql")), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, migration);
        }
        try (Connection connection = dataSource.getConnection();
                var migration = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                        "/db/migration/V24__add_resource_catalog_metadata.sql")), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, migration);
        }
        configuration.setMapUnderscoreToCamelCase(true);
        try (Connection connection = dataSource.getConnection();
                var migration = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                        "/db/migration/V27__add_hosted_book_document.sql")), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, migration);
        }
        configuration.addMapper(StudyResourceMapper.class);
        configuration.addMapper(StudyResourceUserMapper.class);
        configuration.addMapper(StudyMaterialMapper.class);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setConfiguration(configuration);
        factory.setDataSource(dataSource);
        factory.setTransactionFactory(new SpringManagedTransactionFactory());
        SqlSessionTemplate session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        resources = session.getMapper(StudyResourceMapper.class);
        links = session.getMapper(StudyResourceUserMapper.class);
        materialSources = session.getMapper(StudyMaterialMapper.class);
        transactionManager = new DataSourceTransactionManager(dataSource);
        service = transactionalService(links);
        when(materials.createTextMaterial(anyLong(), any())).thenAnswer(invocation -> {
            Long id = materialIds.incrementAndGet();
            Long userId = invocation.getArgument(0);
            MaterialCreateRequest request = invocation.getArgument(1);
            jdbc.update("INSERT INTO study_material (id, user_id, title, source_text, description) VALUES (?, ?, ?, ?, ?)",
                    id, userId, request.getTitle(), request.getContentText(), request.getDescription());
            return id;
        });
        when(materials.getById(anyLong())).thenAnswer(invocation -> {
            List<StudyMaterial> found = jdbc.query("SELECT id, user_id, deleted FROM study_material WHERE id = ? AND deleted = 0",
                    (row, index) -> {
                        StudyMaterial material = new StudyMaterial();
                        material.setId(row.getLong("id"));
                        material.setUserId(row.getLong("user_id"));
                        material.setDeleted(row.getInt("deleted"));
                        return material;
                    }, invocation.<Long>getArgument(0));
            return found.isEmpty() ? null : found.get(0);
        });
        insertResource(1, "链表基础", "链表,节点", "INTERNAL", "PUBLISHED");
        insertResource(2, "外部课程", "链表", "EXTERNAL", "PUBLISHED");
        insertResource(3, "待审核链表", "链表", "INTERNAL", "DRAFT");
        insertResource(4, "节点练习", "节点", "INTERNAL", "PUBLISHED");
    }

    @Test
    void optionalCoverMetadataIsReturnedByDetailPageAndRecommendations() {
        jdbc.update("UPDATE study_resource SET cover_url=? WHERE id=1", "/resource-covers/test.jpg");
        assertEquals("/resource-covers/test.jpg", service.detail(7L, 1L).getCoverUrl());
        assertEquals("/resource-covers/test.jpg", resources.findPage(7L, "PUBLISHED", null, null, false, 0, 12, false, null, null).get(0).getCoverUrl());
        assertEquals("/resource-covers/test.jpg", resources.findRecommendations(List.of(), 12).get(0).getCoverUrl());
        assertNull(service.detail(7L, 2L).getCoverUrl());
    }

    @Test
    void hostedDocumentMetadataSurvivesActualMapperAndApiResponses() {
        jdbc.update("UPDATE study_resource SET document_url=?,page_count=? WHERE id=1", "/open-books/test-book/book.pdf", 248);
        var detail = service.detail(7L, 1L);
        assertEquals("/open-books/test-book/book.pdf", detail.getDocumentUrl());
        assertEquals(248, detail.getPageCount());
        var page = service.page(7L, new ResourcePageQuery()).getRecords().get(0);
        assertEquals(detail.getDocumentUrl(), page.getDocumentUrl());
        assertEquals(detail.getPageCount(), page.getPageCount());
        var recommended = resources.findRecommendations(List.of(), 12).get(0);
        assertEquals(detail.getDocumentUrl(), recommended.getDocumentUrl());
        assertEquals(detail.getPageCount(), recommended.getPageCount());
        assertNull(service.detail(7L, 2L).getDocumentUrl());
        assertNull(service.detail(7L, 2L).getPageCount());
    }

    @Test
    void materialSourceDocumentRequiresBothOwnedMaterialAndOwnedImportLink() {
        jdbc.update("UPDATE study_resource SET document_url='/open-books/source/book.pdf' WHERE id IN (1,2,4)");
        jdbc.update("INSERT INTO study_material (id,user_id,deleted) VALUES (101,7,0),(102,8,0),(103,7,1),(104,7,0)");
        jdbc.update("INSERT INTO study_resource_user (user_id,resource_id,material_id) VALUES (7,1,101),(8,4,101),(7,2,102),(7,4,103)");

        StudyResource document = materialSources.findPublishedSourceDocument(7L, 101L);

        assertEquals(1L, document.getId());
        assertEquals("/open-books/source/book.pdf", document.getDocumentUrl());
        assertNull(materialSources.findPublishedSourceDocument(8L, 101L), "a corrupt link cannot grant another user's material");
        assertNull(materialSources.findPublishedSourceDocument(7L, 102L), "the imported material must also belong to this user");
        assertNull(materialSources.findPublishedSourceDocument(7L, 103L), "deleted materials cannot expose a source");
        assertNull(materialSources.findPublishedSourceDocument(7L, 104L), "owning a material without importing a resource is insufficient");
        assertNull(materialSources.findPublishedSourceDocument(8L, 102L), "another user's import link cannot expose a resource");
    }

    @Test
    void materialSourceDocumentExcludesUnpublishedResourcesAndMissingPdfAddresses() {
        jdbc.update("INSERT INTO study_material (id,user_id,deleted) VALUES (101,7,0)");
        jdbc.update("INSERT INTO study_resource_user (user_id,resource_id,material_id) VALUES (7,1,101)");
        jdbc.update("UPDATE study_resource SET document_url='/open-books/source/book.pdf' WHERE id=1");
        for (String status : List.of("DRAFT", "WITHDRAWN")) {
            jdbc.update("UPDATE study_resource SET status=? WHERE id=1", status);
            assertNull(materialSources.findPublishedSourceDocument(7L, 101L), status + " resource must not be exposed");
        }
        jdbc.update("UPDATE study_resource SET status='PUBLISHED',document_url=NULL WHERE id=1");
        assertNull(materialSources.findPublishedSourceDocument(7L, 101L));
        for (String url : List.of("", "   ")) {
            jdbc.update("UPDATE study_resource SET document_url=? WHERE id=1", url);
            assertNull(materialSources.findPublishedSourceDocument(7L, 101L), "a blank document address is not a PDF source");
        }
    }

    @Test
    void completeSourceTextLookupPreservesOriginalTextAndEnforcesOwnershipAndDeletion() {
        jdbc.update("INSERT INTO study_material (id,user_id,deleted,source_text) VALUES (101,7,0,?),(102,8,0,?),(103,7,1,?)",
                "第一行\n\n第二行，保持原始换行。", "他人的私有内容", "已删除资料");

        assertEquals("第一行\n\n第二行，保持原始换行。", materialSources.findOwnedSourceText(7L, 101L));
        assertNull(materialSources.findOwnedSourceText(8L, 101L));
        assertNull(materialSources.findOwnedSourceText(7L, 102L));
        assertNull(materialSources.findOwnedSourceText(7L, 103L));
        assertNull(materialSources.findOwnedSourceText(7L, 999L));
    }

    @Test
    void hostedCompleteBooksLeadPublicListsAndRecommendationsWhileAdminKeepsNewestFirst() {
        jdbc.update("UPDATE study_resource SET document_url='/open-books/test-book/book.pdf',page_count=248 WHERE id=4");
        assertEquals(4L, service.page(7L, new ResourcePageQuery()).getRecords().get(0).getId());
        assertEquals(4L, resources.findRecommendations(List.of(), 12).get(0).getId());
        jdbc.update("UPDATE study_resource SET document_url=NULL,page_count=NULL WHERE id=4");
        jdbc.update("UPDATE study_resource SET document_url='/open-books/test-book/book.pdf',page_count=248 WHERE id=1");
        assertEquals(1L, service.page(7L, new ResourcePageQuery()).getRecords().get(0).getId());
        assertEquals(4L, resources.findPage(7L, null, null, null, false, 0, 12, true, null, null).get(0).getId());
    }

    @Test
    void publicPagingRunsActualSqlWithVisibilityFiltersAndChineseFirstOrdering() {
        links.ensureLink(7L, 1L);
        links.setBookmarked(7L, 1L, true);
        links.ensureLink(8L, 4L);
        links.setBookmarked(8L, 4L, true);
        assertEquals(List.of(1L, 4L, 2L), resources.findPage(7L, "PUBLISHED", null, null, false, 0, 12, false, null, null)
                .stream().map(StudyResource::getId).toList());
        assertEquals(List.of(4L, 3L, 2L, 1L), resources.findPage(7L, null, null, null, false, 0, 12, true, null, null)
                .stream().map(StudyResource::getId).toList());
        assertEquals(1L, resources.countPage(7L, "PUBLISHED", "链表", "计算机", true, null, null));
        assertEquals(List.of(1L), resources.findPage(7L, "PUBLISHED", "链表", "计算机", true, 0, 12, false, null, null)
                .stream().map(StudyResource::getId).toList());
        assertEquals(0L, resources.countPage(8L, "PUBLISHED", "链表", null, true, null, null));
        assertEquals(0L, resources.countPage(7L, "PUBLISHED", "' OR 1=1 --", null, false, null, null));
        assertNull(resources.findPublishedById(3L));
        assertNotNull(resources.findById(3L).getContentText());
        assertNull(resources.findPage(7L, "PUBLISHED", null, null, false, 0, 12, false, null, null).get(0).getContentText());
    }

    @Test
    void visibleAssociationSqlNeverExposesOtherUsersOrDeletedMaterials() {
        jdbc.update("INSERT INTO study_material (id,user_id,deleted) VALUES (91,8,0),(92,7,1),(93,7,0)");
        links.ensureLink(7L, 1L);
        links.ensureLink(7L, 2L);
        links.ensureLink(7L, 4L);
        links.setMaterialId(7L, 1L, 91L);
        links.setMaterialId(7L, 2L, 92L);
        links.setMaterialId(7L, 4L, 93L);
        var visible = links.findVisibleLinks(7L, List.of(1L, 2L, 4L));
        assertEquals(3, visible.size());
        assertNull(visible.stream().filter(link -> link.getResourceId() == 1L).findFirst().orElseThrow().getMaterialId());
        assertNull(visible.stream().filter(link -> link.getResourceId() == 2L).findFirst().orElseThrow().getMaterialId());
        assertEquals(93L, visible.stream().filter(link -> link.getResourceId() == 4L).findFirst().orElseThrow().getMaterialId());
        assertEquals(0, links.saveAttribution(8L, 93L, "越权"));
        assertEquals(0, links.saveAttribution(7L, 92L, "已删除"));
    }

    @Test
    void recommendationsExecuteTokenRankingAndPublishedFilter() {
        insertResource(5, "链表延伸", "数组", "INTERNAL", "PUBLISHED");
        insertResource(6, "进阶读物", "链表,指针", "INTERNAL", "PUBLISHED");
        var recommended = resources.findRecommendations(List.of("链表"), 12).stream().map(StudyResource::getId).toList();
        assertEquals(List.of(1L, 6L, 5L, 2L), recommended);
        assertTrue(resources.findRecommendations(List.of("不存在"), 3).isEmpty());
        assertEquals(List.of(1L, 4L, 5L), resources.findRecommendations(List.of(), 3).stream().map(StudyResource::getId).toList());
    }

    @Test
    void resourceKindFiltersCombineWithVisibilityBookmarksAndAdminSearch() {
        links.ensureLink(7L, 1L);
        links.setBookmarked(7L, 1L, true);
        links.ensureLink(7L, 2L);
        links.setBookmarked(7L, 2L, true);
        assertEquals(1, resources.countPage(7L, "PUBLISHED", "链表", "计算机", true, "COURSE_PACK", null));
        assertEquals(List.of(1L), resources.findPage(7L, "PUBLISHED", "链表", "计算机", true, 0, 12, false, "COURSE_PACK", null)
                .stream().map(StudyResource::getId).toList());
        assertEquals(List.of(3L), resources.findPage(9L, "DRAFT", null, null, false, 0, 12, true, "COURSE_PACK", null)
                .stream().map(StudyResource::getId).toList());
        var textbook = resources.findPage(7L, "PUBLISHED", null, null, true, 0, 12, false, "TEXTBOOK", null).get(0);
        assertEquals(2L, textbook.getId());
        assertEquals("en", textbook.getLanguageCode());
        assertEquals("完整数据结构课程", textbook.getCoverageNote());
        assertEquals("https://learning.example.com/course.pdf", textbook.getDownloadUrl());
        assertEquals(0, resources.countPage(7L, "PUBLISHED", null, null, false, "REVIEW_PACK", null));
    }

    @Test
    void catalogMetadataAndSearchWorkWithRealSqlAndKeepOpenResourcesAccessible() {
        jdbc.update("""
                UPDATE study_resource SET access_mode='CATALOG_ONLY', isbn='9780306406157',
                  publisher='机械工业出版社', author='目录测试作者', education_level='本科', publication_year=2025,
                  download_url=NULL WHERE id=2
                """);
        var detail = service.detail(7L, 2L);
        assertEquals("CATALOG_ONLY", detail.getAccessMode());
        assertEquals("9780306406157", detail.getIsbn());
        assertEquals("机械工业出版社", detail.getPublisher());
        assertEquals("本科", detail.getEducationLevel());
        assertEquals(2025, detail.getPublicationYear());
        assertNull(detail.getDownloadUrl());
        assertNull(detail.getContentText());
        assertEquals("OPEN_RESOURCE", service.detail(7L, 1L).getAccessMode());
        assertNull(service.detail(7L, 1L).getIsbn());
        assertNull(service.detail(7L, 1L).getPublicationYear());
        var recommendation = resources.findRecommendations(List.of("链表"), 12).stream()
                .filter(item -> item.getId().equals(2L)).findFirst().orElseThrow();
        assertEquals("9780306406157", recommendation.getIsbn());
        assertEquals("CATALOG_ONLY", recommendation.getAccessMode());
        for (String keyword : List.of("9780306406157", "978-0-306-40615-7", "978 0 306 40615 7",
                "030640615", "目录测试作者", "机械工业出版社")) {
            ResourcePageQuery query = new ResourcePageQuery();
            query.setKeyword(keyword);
            query.setAccessMode("CATALOG_ONLY");
            query.setResourceKind("TEXTBOOK");
            query.setSubject("计算机");
            var page = service.page(7L, query);
            assertEquals(1L, page.getTotal(), keyword);
            assertEquals(List.of(2L), page.getRecords().stream().map(item -> item.getId()).toList(), keyword);
            assertEquals("机械工业出版社", page.getRecords().get(0).getPublisher());
            assertEquals("本科", page.getRecords().get(0).getEducationLevel());
            assertEquals(2025, page.getRecords().get(0).getPublicationYear());
        }
        assertEquals(3, service.page(7L, new ResourcePageQuery()).getTotal());
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 2L));
        verify(materials, never()).createTextMaterial(anyLong(), any());
    }

    @Test
    void accessModeCountAndPageCombineVisibilityPaginationAndPrivateBookmarks() {
        jdbc.update("""
                UPDATE study_resource SET access_mode='CATALOG_ONLY', resource_type='EXTERNAL',
                  resource_kind='TEXTBOOK', license_code='LINK_ONLY', download_url=NULL, content_text=NULL
                WHERE id IN (2,3)
                """);
        links.ensureLink(7L, 2L);
        links.setBookmarked(7L, 2L, true);
        assertEquals(1, resources.countPage(7L, "PUBLISHED", "链表", "计算机", true, "TEXTBOOK", "CATALOG_ONLY"));
        assertEquals(List.of(2L), resources.findPage(7L, "PUBLISHED", "链表", "计算机", true, 0, 1, false, "TEXTBOOK", "CATALOG_ONLY")
                .stream().map(StudyResource::getId).toList());
        assertTrue(resources.findPage(7L, "PUBLISHED", "链表", "计算机", true, 1, 1, false, "TEXTBOOK", "CATALOG_ONLY").isEmpty());
        assertEquals(0, resources.countPage(8L, "PUBLISHED", null, null, true, "TEXTBOOK", "CATALOG_ONLY"));
        assertTrue(resources.findPage(8L, "PUBLISHED", null, null, true, 0, 12, false, "TEXTBOOK", "CATALOG_ONLY").isEmpty());
        assertEquals(2, resources.countPage(9L, null, null, null, false, "TEXTBOOK", "CATALOG_ONLY"));
        assertEquals(List.of(3L), resources.findPage(9L, "DRAFT", null, null, false, 0, 12, true, "TEXTBOOK", "CATALOG_ONLY")
                .stream().map(StudyResource::getId).toList());
        assertEquals(2, resources.countPage(7L, "PUBLISHED", null, null, false, null, "OPEN_RESOURCE"));
        assertEquals(List.of(1L, 4L), resources.findPage(7L, "PUBLISHED", null, null, false, 0, 12, false, null, "OPEN_RESOURCE")
                .stream().map(StudyResource::getId).toList());
    }

    @Test
    void republishedKnowledgeCardsDoNotOutrankCompleteResourcesInPagesOrRecommendations() {
        jdbc.update("UPDATE study_resource SET resource_kind='KNOWLEDGE_CARD' WHERE id=1");
        assertEquals(List.of(4L, 2L, 1L), resources.findPage(7L, "PUBLISHED", null, null, false, 0, 12, false, null, null)
                .stream().map(StudyResource::getId).toList());
        assertEquals(List.of(2L, 1L), resources.findRecommendations(List.of("链表"), 3)
                .stream().map(StudyResource::getId).toList());
    }

    @Test
    void concurrentDuplicateImportsUseOneMaterialAndUniqueAssociation() throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = workers.submit(() -> { start.await(); return service.importMaterial(7L, 1L); });
            var second = workers.submit(() -> { start.await(); return service.importMaterial(7L, 1L); });
            start.countDown();
            Long firstId = first.get(10, TimeUnit.SECONDS);
            assertEquals(firstId, second.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM study_material", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource_user WHERE user_id=7 AND resource_id=1", Integer.class));
            verify(materials, times(1)).createTextMaterial(eq(7L), any());
            assertEquals("教学正文", jdbc.queryForObject("SELECT source_text FROM study_material WHERE id=?", String.class, firstId));
            String attribution = jdbc.queryForObject("SELECT resource_attribution FROM study_material WHERE id=?", String.class, firstId);
            assertTrue(attribution.contains("https://learning.example.com/original"));
            assertTrue(attribution.contains("测试作者"));
            assertTrue(attribution.contains("COURSE_PACK"));
            assertTrue(attribution.contains("zh-CN"));
            assertTrue(attribution.contains("完整数据结构课程"));
            assertTrue(attribution.contains("https://learning.example.com/course.pdf"));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void importIsPrivateAndRecreatesDeletedCopyUsingTheRealAssociationTable() {
        Long own = service.importMaterial(7L, 1L);
        Long other = service.importMaterial(8L, 1L);
        assertNotEquals(own, other);
        assertEquals(7L, jdbc.queryForObject("SELECT user_id FROM study_material WHERE id=?", Long.class, own));
        assertEquals(8L, jdbc.queryForObject("SELECT user_id FROM study_material WHERE id=?", Long.class, other));
        jdbc.update("UPDATE study_material SET deleted=1 WHERE id=?", own);
        Long replacement = service.importMaterial(7L, 1L);
        assertNotEquals(own, replacement);
        assertEquals(other, service.importMaterial(8L, 1L));
        assertEquals(replacement, links.findVisibleLinks(7L, List.of(1L)).get(0).getMaterialId());
    }

    @Test
    void failingAssociationWriteRollsBackMaterialAttributionAndLinkInsert() {
        StudyResourceUserMapper failingLinks = mock(StudyResourceUserMapper.class, AdditionalAnswers.delegatesTo(links));
        doReturn(0).when(failingLinks).setMaterialId(anyLong(), anyLong(), anyLong());
        StudyResourceService failingService = transactionalService(failingLinks);
        assertThrows(BusinessException.class, () -> failingService.importMaterial(7L, 1L));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM study_material", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource_user", Integer.class));
    }

    @Test
    void withdrawnBookmarksCanBeRemovedAndCannotAppearInPublicPage() {
        service.bookmark(7L, 1L, true);
        resources.updateStatus(1L, "WITHDRAWN", "停止发布", 9L);
        ResourcePageQuery query = new ResourcePageQuery();
        query.setBookmarkedOnly(true);
        assertTrue(service.page(7L, query).getRecords().isEmpty());
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 1L));
        service.bookmark(7L, 1L, false);
        assertFalse(links.findVisibleLinks(7L, List.of(1L)).get(0).getBookmarked());
    }

    private StudyResourceService transactionalService(StudyResourceUserMapper userLinks) {
        SysUserMapper users = mock(SysUserMapper.class);
        SysUser admin = new SysUser();
        admin.setId(9L);
        admin.setStatus(1);
        admin.setRoleCode("ADMIN");
        when(users.selectById(9L)).thenReturn(admin);
        StudyResourceServiceImpl target = new StudyResourceServiceImpl(resources, userLinks, materials,
                mock(KnowledgeMasteryService.class), users);
        TransactionInterceptor transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(transactions);
        return (StudyResourceService) proxy.getProxy();
    }

    private void insertResource(long id, String title, String points, String type, String status) {
        jdbc.update("""
                INSERT INTO study_resource (id,title,summary,subject,knowledge_points,difficulty_level,estimated_minutes,
                  resource_type,source_name,source_url,author,license_code,license_note,content_text,status,
                  resource_kind,language_code,coverage_note,download_url)
                VALUES (?,?,'课程简介','计算机',?,2,15,?,'测试来源','https://learning.example.com/original',
                  '测试作者',?,'仅供个人学习',?,?,?,?,'完整数据结构课程','https://learning.example.com/course.pdf')
                """, id, title, points, type, "INTERNAL".equals(type) ? "INTERNAL" : "LINK_ONLY",
                "INTERNAL".equals(type) ? "教学正文" : null, status, "INTERNAL".equals(type) ? "COURSE_PACK" : "TEXTBOOK",
                "INTERNAL".equals(type) ? "zh-CN" : "en");
    }
}
