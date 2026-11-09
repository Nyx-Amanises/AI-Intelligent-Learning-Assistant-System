package com.aiassistant.learning.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.dto.resource.ResourceStatusRequest;
import com.aiassistant.learning.entity.StudyResource;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.service.KnowledgeMasteryService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.impl.StudyResourceServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.aiassistant.learning.common.exception.BusinessException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class StudyResourceMigrationTest {
    private static final Map<String, String> VERIFIED_COVERS = Map.of(
            "https://openstax.org/details/books/calculus-volume-1",
            "https://he.kendallhunt.com/sites/default/files/product_images_small/1501419_0.jpg",
            "https://openstax.org/details/books/university-physics-volume-3",
            "https://he.kendallhunt.com/sites/default/files/product_images_small/1501697.jpg",
            "https://openstax.org/details/books/writing-guide",
            "https://he.kendallhunt.com/sites/default/files/product_images_small/1501337.jpg");

    @Test
    void v18ThroughV24PublishSeventeenCompleteResourcesAndOnlyThreeVerifiedCovers() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:full_catalog_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE study_material (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, deleted INT DEFAULT 0)");
        for (String filename : List.of("V18__create_study_resource_library.sql", "V19__seed_learning_resources.sql",
                "V20__classify_course_resources.sql", "V21__seed_full_course_catalog.sql", "V22__add_resource_cover_url.sql",
                "V23__seed_verified_resource_covers.sql", "V24__add_resource_catalog_metadata.sql")) {
            runMigration(dataSource, filename);
        }
        assertEquals(47, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource", Integer.class));
        assertEquals(47, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE access_mode='OPEN_RESOURCE'", Integer.class));
        assertEquals(17, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE status='PUBLISHED'", Integer.class));
        assertEquals(30, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE status='WITHDRAWN' AND resource_kind='KNOWLEDGE_CARD'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE status='PUBLISHED' AND content_text IS NOT NULL", Integer.class));
        assertEquals(15, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE status='PUBLISHED' AND download_url IS NOT NULL", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE status='PUBLISHED' AND download_url IS NULL AND source_url LIKE 'https://ocw.nthu.edu.tw/%'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM study_material", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource_user", Integer.class));
        assertEquals(VERIFIED_COVERS, jdbc.query("SELECT source_url, cover_url FROM study_resource WHERE cover_url IS NOT NULL",
                result -> {
                    Map<String, String> covers = new LinkedHashMap<>();
                    int rows = 0;
                    while (result.next()) { covers.put(result.getString(1), result.getString(2)); rows++; }
                    assertEquals(3, rows);
                    return covers;
                }));

        // The historical seed assertions above remain about V18-V24; current mapper SQL needs the latest schema.
        runMigration(dataSource, "V27__add_hosted_book_document.sql");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("full-catalog", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(StudyResourceMapper.class);
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            StudyResourceMapper mapper = session.getMapper(StudyResourceMapper.class);
            List<StudyResource> published = mapper.findPage(7L, "PUBLISHED", null, null, false, 0, 50, false, null, null);
            assertEquals(17, published.size());
            assertEquals(VERIFIED_COVERS, published.stream().filter(item -> item.getCoverUrl() != null)
                    .collect(Collectors.toMap(StudyResource::getSourceUrl, StudyResource::getCoverUrl)));
            assertEquals(Set.of("高等数学", "线性代数", "概率论与数理统计", "大学物理", "大学英语"),
                    published.stream().map(StudyResource::getSubject).collect(Collectors.toSet()));
            for (String subject : List.of("高等数学", "线性代数", "概率论与数理统计", "大学物理", "大学英语")) {
                assertTrue(mapper.countPage(7L, "PUBLISHED", null, subject, false, null, null) >= 3, subject);
            }
            assertTrue(published.get(0).getLanguageCode().startsWith("zh"));
            boolean sawNonChinese = false;
            for (StudyResource item : published) {
                assertEquals("OPEN_RESOURCE", item.getAccessMode());
                assertNull(item.getIsbn());
                assertNull(item.getPublisher());
                assertNull(item.getEducationLevel());
                assertNull(item.getPublicationYear());
                if (!item.getLanguageCode().startsWith("zh")) sawNonChinese = true;
                else assertFalse(sawNonChinese, "中文资源应排在非中文资源之前");
            }
            Set<String> officialSourceHosts = Set.of("math.ecnu.edu.cn", "ocw.mit.edu", "openstax.org",
                    "www.math.ucdavis.edu", "math.pku.edu.cn", "math.dartmouth.edu", "ocw.nthu.edu.tw", "cet.neea.edu.cn");
            Set<String> officialDownloadHosts = Set.of("math.ecnu.edu.cn", "ocw.mit.edu", "assets.openstax.org",
                    "www.math.ucdavis.edu", "math.pku.edu.cn", "math.dartmouth.edu", "cet.neea.edu.cn");
            SysUserMapper users = mock(SysUserMapper.class);
            SysUser admin = new SysUser();
            admin.setStatus(1);
            admin.setRoleCode("ADMIN");
            when(users.selectById(9L)).thenReturn(admin);
            StudyMaterialService materials = mock(StudyMaterialService.class);
            StudyResourceUserMapper links = mock(StudyResourceUserMapper.class);
            StudyResourceServiceImpl service = new StudyResourceServiceImpl(mapper, links, materials,
                    mock(KnowledgeMasteryService.class), users);
            for (StudyResource item : published) {
                assertTrue(Set.of("TEXTBOOK", "COURSE_PACK", "REVIEW_PACK").contains(item.getResourceKind()), item.getTitle());
                assertEquals("EXTERNAL", item.getResourceType());
                assertEquals("LINK_ONLY", item.getLicenseCode());
                assertEquals(0, item.getEstimatedMinutes());
                assertFalse(item.getCoverageNote().isBlank());
                assertFalse(item.getLanguageCode().isBlank());
                assertOfficialHttps(item.getSourceUrl(), officialSourceHosts);
                if (item.getDownloadUrl() != null) {
                    assertOfficialHttps(item.getDownloadUrl(), officialDownloadHosts);
                    assertTrue(URI.create(item.getDownloadUrl()).getPath().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(pdf|zip)$"));
                }
                ResourceStatusRequest publish = new ResourceStatusRequest();
                publish.setStatus("PUBLISHED");
                assertDoesNotThrow(() -> service.updateStatus(9L, item.getId(), publish), item.getTitle());
                assertEquals(400, assertThrows(BusinessException.class, () -> service.importMaterial(7L, item.getId())).getCode());
            }
            verifyNoInteractions(materials, links);
        }
    }

    @Test
    void v23IsIdempotentPreservesAssignedCoversAndLeavesAllOtherResourceFieldsUntouched() throws Exception {
        JdbcDataSource dataSource = catalogThroughV22();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String assignedSource = "https://openstax.org/details/books/calculus-volume-1";
        String assignedCover = "https://library.example.com/admin-selected-cover.jpg";
        jdbc.update("UPDATE study_resource SET cover_url=? WHERE source_url=?", assignedCover, assignedSource);
        jdbc.update("UPDATE study_resource SET cover_url='' WHERE source_url=?", "https://openstax.org/details/books/university-physics-volume-3");
        jdbc.update("UPDATE study_resource SET cover_url='https://library.example.com/other-cover.jpg' WHERE id=1");
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM study_resource ORDER BY id");
        runMigration(dataSource, "V23__seed_verified_resource_covers.sql");
        List<Map<String, Object>> after = jdbc.queryForList("SELECT * FROM study_resource ORDER BY id");
        assertEquals(before.size(), after.size());
        for (int index = 0; index < before.size(); index++) {
            Map<String, Object> original = before.get(index);
            Map<String, Object> current = after.get(index);
            String source = (String) original.get("source_url");
            String cover = (String) original.get("cover_url");
            if (source != null && VERIFIED_COVERS.containsKey(source) && (cover == null || cover.isEmpty())) {
                assertEquals(VERIFIED_COVERS.get(source), current.get("cover_url"));
                Map<String, Object> beforeFields = new LinkedHashMap<>(original);
                Map<String, Object> afterFields = new LinkedHashMap<>(current);
                for (String changed : List.of("cover_url", "updated_at")) {
                    beforeFields.remove(changed);
                    afterFields.remove(changed);
                }
                assertEquals(beforeFields, afterFields);
            } else {
                assertEquals(original, current, "已有封面和非匹配资源必须完全保留");
            }
        }
        assertEquals(assignedCover, jdbc.queryForObject("SELECT cover_url FROM study_resource WHERE source_url=?", String.class, assignedSource));
        runMigration(dataSource, "V23__seed_verified_resource_covers.sql");
        assertEquals(after, jdbc.queryForList("SELECT * FROM study_resource ORDER BY id"));
    }

    @Test
    void v23DoesNotAssignCoversToDifferentSourcesOrIncompatibleResourceKindsAndLicenses() throws Exception {
        JdbcDataSource dataSource = catalogThroughV22();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String source = "https://openstax.org/details/books/calculus-volume-1";
        for (String[] fixture : List.of(
                new String[]{source, "INTERNAL", "TEXTBOOK", "LINK_ONLY"},
                new String[]{source, "EXTERNAL", "COURSE_PACK", "LINK_ONLY"},
                new String[]{source, "EXTERNAL", "TEXTBOOK", "CC_BY"},
                new String[]{source + "?edition=different", "EXTERNAL", "TEXTBOOK", "LINK_ONLY"})) {
            jdbc.update("""
                    INSERT INTO study_resource(title,summary,subject,knowledge_points,difficulty_level,estimated_minutes,
                      resource_type,resource_kind,source_name,source_url,author,license_code,license_note,status)
                    VALUES ('相似资料','简介','高等数学','微积分',2,0,?,?,'测试来源',?,'测试作者',?,'测试授权','DRAFT')
                    """, fixture[1], fixture[2], fixture[0], fixture[3]);
        }
        List<Map<String, Object>> unmatched = jdbc.queryForList("SELECT * FROM study_resource WHERE title='相似资料' ORDER BY id");
        runMigration(dataSource, "V23__seed_verified_resource_covers.sql");
        assertEquals(unmatched, jdbc.queryForList("SELECT * FROM study_resource WHERE title='相似资料' ORDER BY id"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE cover_url IS NOT NULL", Integer.class));
    }

    private JdbcDataSource catalogThroughV22() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cover_migration_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        new JdbcTemplate(dataSource).execute("CREATE TABLE study_material (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, deleted INT DEFAULT 0)");
        for (String filename : List.of("V18__create_study_resource_library.sql", "V19__seed_learning_resources.sql",
                "V20__classify_course_resources.sql", "V21__seed_full_course_catalog.sql", "V22__add_resource_cover_url.sql")) {
            runMigration(dataSource, filename);
        }
        return dataSource;
    }

    @Test
    void v24AddsOnlyNullableMetadataAndDefaultsExistingRowsToOpenAccess() throws Exception {
        JdbcDataSource dataSource = catalogThroughV22();
        runMigration(dataSource, "V23__seed_verified_resource_covers.sql");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM study_resource ORDER BY id");
        runMigration(dataSource, "V24__add_resource_catalog_metadata.sql");
        List<Map<String, Object>> after = jdbc.queryForList("SELECT * FROM study_resource ORDER BY id");
        assertEquals(before.size(), after.size());
        for (int index = 0; index < before.size(); index++) {
            Map<String, Object> current = new LinkedHashMap<>(after.get(index));
            assertEquals("OPEN_RESOURCE", current.remove("access_mode"));
            for (String field : List.of("isbn", "publisher", "education_level", "publication_year")) {
                assertTrue(current.containsKey(field));
                assertNull(current.remove(field));
            }
            assertEquals(before.get(index), current, "添加书目字段不能改写既有资源");
        }
    }

    @Test
    void v18ThroughV20WithdrawOnlySeedCardsAndKeepImportedCopiesAndOfficialCourseMetadata() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:course_migration_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE study_material (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL,
                  deleted INT NOT NULL DEFAULT 0, source_text CLOB)
                """);
        runMigration(dataSource, "V18__create_study_resource_library.sql");
        runMigration(dataSource, "V19__seed_learning_resources.sql");
        assertEquals(32, jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE status='PUBLISHED'", Integer.class));
        Long cardId = jdbc.queryForObject("SELECT MIN(id) FROM study_resource WHERE resource_type='INTERNAL'", Long.class);
        String originalText = jdbc.queryForObject("SELECT content_text FROM study_resource WHERE id=?", String.class, cardId);
        jdbc.update("INSERT INTO study_material (id,user_id,source_text,resource_attribution) VALUES (900,7,?,'原始来源快照')", originalText);
        jdbc.update("INSERT INTO study_resource_user (user_id,resource_id,material_id,bookmarked) VALUES (7,?,900,TRUE)", cardId);
        // A similarly named, separately authored resource must not be withdrawn by the seed-specific migration.
        jdbc.update("""
                INSERT INTO study_resource (title,summary,subject,knowledge_points,difficulty_level,estimated_minutes,
                  resource_type,source_name,author,license_code,license_note,content_text,status)
                VALUES ('函数与定义域：先确认输入是否合法','其他来源资料','数学','函数',2,20,
                  'INTERNAL','独立课程组','其他作者','INTERNAL','作者许可','独立正文','PUBLISHED')
                """);
        runMigration(dataSource, "V20__classify_course_resources.sql");
        runMigration(dataSource, "V22__add_resource_cover_url.sql");
        runMigration(dataSource, "V24__add_resource_catalog_metadata.sql");
        assertEquals(30, jdbc.queryForObject("""
                SELECT COUNT(*) FROM study_resource WHERE source_name='知序学习卡片'
                  AND resource_kind='KNOWLEDGE_CARD' AND language_code='zh-CN' AND status='WITHDRAWN'
                """, Integer.class));
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT status FROM study_resource WHERE source_name='独立课程组'", String.class));
        assertEquals(originalText, jdbc.queryForObject("SELECT source_text FROM study_material WHERE id=900 AND user_id=7", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM study_material WHERE id=900", Integer.class));
        assertEquals("原始来源快照", jdbc.queryForObject("SELECT resource_attribution FROM study_material WHERE id=900", String.class));
        assertEquals(900L, jdbc.queryForObject("SELECT material_id FROM study_resource_user WHERE user_id=7 AND resource_id=?", Long.class, cardId));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("SELECT bookmarked FROM study_resource_user WHERE user_id=7 AND resource_id=?", Boolean.class, cardId)));

        runMigration(dataSource, "V27__add_hosted_book_document.sql");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("migration", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(StudyResourceMapper.class);
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            StudyResourceMapper mapper = session.getMapper(StudyResourceMapper.class);
            assertNull(mapper.findPublishedById(cardId));
            assertEquals(30, mapper.countPage(7L, "WITHDRAWN", null, null, false, "KNOWLEDGE_CARD", null));
            assertEquals(0, mapper.countPage(7L, "PUBLISHED", null, null, true, "KNOWLEDGE_CARD", null));
            List<StudyResource> textbooks = mapper.findPage(7L, "PUBLISHED", null, null, false, 0, 12, false, "TEXTBOOK", null);
            List<StudyResource> courses = mapper.findPage(7L, "PUBLISHED", null, null, false, 0, 12, false, "COURSE_PACK", null);
            assertEquals(1, textbooks.size());
            assertEquals(1, courses.size());
            assertEquals("OpenStax", textbooks.get(0).getSourceName());
            assertEquals("MIT OpenCourseWare", courses.get(0).getSourceName());
            SysUserMapper users = mock(SysUserMapper.class);
            SysUser admin = new SysUser();
            admin.setStatus(1);
            admin.setRoleCode("ADMIN");
            when(users.selectById(9L)).thenReturn(admin);
            StudyResourceServiceImpl service = new StudyResourceServiceImpl(mapper, mock(StudyResourceUserMapper.class),
                    mock(StudyMaterialService.class), mock(KnowledgeMasteryService.class), users);
            for (StudyResource official : List.of(textbooks.get(0), courses.get(0))) {
                assertEquals("en", official.getLanguageCode());
                assertEquals(0, official.getEstimatedMinutes());
                assertFalse(official.getCoverageNote().isBlank());
                assertNull(official.getDownloadUrl());
                assertEquals("LINK_ONLY", official.getLicenseCode());
                assertNull(mapper.findById(official.getId()).getContentText());
                ResourceStatusRequest publish = new ResourceStatusRequest();
                publish.setStatus("PUBLISHED");
                assertDoesNotThrow(() -> service.updateStatus(9L, official.getId(), publish));
            }
        }
    }

    @Test
    void v27AddsNullableDocumentMetadataWithoutChangingAnyExistingResources() throws Exception {
        JdbcDataSource dataSource = catalogThroughV22();
        runMigration(dataSource, "V24__add_resource_catalog_metadata.sql");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var before = jdbc.queryForList("SELECT * FROM study_resource ORDER BY id");
        runMigration(dataSource, "V27__add_hosted_book_document.sql");
        var after = jdbc.queryForList("SELECT * FROM study_resource ORDER BY id");
        assertEquals(before.size(), after.size());
        for (int index = 0; index < before.size(); index++) {
            var current = new LinkedHashMap<>(after.get(index));
            assertTrue(current.containsKey("document_url"));
            assertTrue(current.containsKey("page_count"));
            assertNull(current.remove("document_url"));
            assertNull(current.remove("page_count"));
            assertEquals(before.get(index), current);
        }
    }

    private void runMigration(JdbcDataSource dataSource, String filename) throws Exception {
        try (Connection connection = dataSource.getConnection();
                var migration = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                        "/db/migration/" + filename)), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, migration);
        }
    }

    private static void assertOfficialHttps(String value, Set<String> officialHosts) {
        URI uri = URI.create(value);
        assertEquals("https", uri.getScheme());
        assertNull(uri.getUserInfo());
        assertTrue(officialHosts.contains(uri.getHost()), value);
    }
}
