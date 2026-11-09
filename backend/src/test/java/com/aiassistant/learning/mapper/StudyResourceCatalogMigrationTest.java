package com.aiassistant.learning.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.resource.ResourcePageQuery;
import com.aiassistant.learning.dto.resource.ResourceStatusRequest;
import com.aiassistant.learning.entity.StudyResource;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.service.KnowledgeMasteryService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.impl.StudyResourceServiceImpl;
import com.aiassistant.learning.vo.resource.ResourceVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs the domestic catalogue seed against the real schema, mapper SQL and resource service. */
class StudyResourceCatalogMigrationTest {
    private static final String SEED = "V25__seed_domestic_textbook_catalog.sql";
    private static final String OPEN_BOOK_SEED = "V28__seed_complete_open_books.sql";
    private static final String FOUNDATION_SEED = "V29__seed_foundation_open_books.sql";

    @Test
    void foundationBatchPreservesCompleteBooksMultipleVolumesAndExistingEdits() throws Exception {
        Path project = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().getParent();
        JsonNode records = new ObjectMapper().readTree(Files.readString(project.resolve("db/catalog/github-foundation-books.json"))).get("records");
        assertTrue(records.size() >= 10, "The expansion must contain a useful collection of complete books");
        try (CatalogDatabase database = new CatalogDatabase()) {
            database.migrate(OPEN_BOOK_SEED);
            int originalCount = database.count("");
            database.migrate(FOUNDATION_SEED);
            assertEquals(originalCount + records.size(), database.count(""));
            Set<String> subjects = new HashSet<>();
            for (JsonNode reference : records) {
                String slug = reference.get("slug").asText();
                String url = "/open-books/" + slug + "/book.pdf";
                Long id = database.jdbc.queryForObject("SELECT id FROM study_resource WHERE document_url=?", Long.class, url);
                StudyResource book = database.resources.findById(id);
                Path folder = project.resolve("frontend/public/open-books").resolve(slug);
                assertEquals(reference.get("title").asText(), book.getTitle());
                assertEquals(reference.get("author").asText(), book.getAuthor());
                assertEquals(reference.get("publisher").asText(), book.getPublisher());
                assertEquals(reference.get("licenseCode").asText(), book.getLicenseCode());
                assertEquals(reference.get("pageCount").asInt(), book.getPageCount());
                assertEquals(Files.readString(folder.resolve("body.txt")), book.getContentText());
                assertEquals(reference.get("textSha256").asText(), sha256(book.getContentText().getBytes(StandardCharsets.UTF_8)));
                assertEquals(reference.get("sha256").asText(), sha256(Files.readAllBytes(folder.resolve("book.pdf"))));
                assertEquals("INTERNAL", book.getResourceType());
                assertEquals("PUBLISHED", book.getStatus());
                assertTrue(Files.readString(folder.resolve("ATTRIBUTION.md")).contains(book.getLicenseUrl()));
                assertDoesNotThrow(() -> database.service.updateStatus(9L, id, publishedStatus()));
                subjects.add(book.getSubject());
            }
            assertTrue(subjects.containsAll(List.of("高等数学", "线性代数", "概率论与数理统计", "大学物理", "大学英语")));
            String firstUrl = "/open-books/" + records.get(0).get("slug").asText() + "/book.pdf";
            database.jdbc.update("UPDATE study_resource SET title='管理员保留书名',status='WITHDRAWN' WHERE document_url=?", firstUrl);
            database.migrate(FOUNDATION_SEED);
            assertEquals(originalCount + records.size(), database.count(""), "Repeated seed must not duplicate volumes");
            assertEquals("管理员保留书名", database.jdbc.queryForObject("SELECT title FROM study_resource WHERE document_url=?", String.class, firstUrl));
            assertEquals("WITHDRAWN", database.jdbc.queryForObject("SELECT status FROM study_resource WHERE document_url=?", String.class, firstUrl));
            assertEquals(4, database.count("WHERE document_url IN ('/open-books/hello-algo-zh-complete/book.pdf','/open-books/thinkpython2-cn/book.pdf','/open-books/linear-book/book.pdf','/open-books/thinkstats2/book.pdf')"));
        }
    }

    @Test
    void v25AddsOneThousandVerifiedBookRecordsWithoutChangingTheExistingOpenResources() throws Exception {
        try (CatalogDatabase database = new CatalogDatabase()) {
            var original = database.rows();
            database.seed();
            assertEquals(1047, database.count(""));
            assertEquals(1017, database.count("WHERE status='PUBLISHED'"));
            assertEquals(1000, database.count("WHERE access_mode='CATALOG_ONLY' AND status='PUBLISHED'"));
            assertEquals(17, database.count("WHERE access_mode='OPEN_RESOURCE' AND status='PUBLISHED'"));
            assertEquals(30, database.count("WHERE status='WITHDRAWN' AND resource_kind='KNOWLEDGE_CARD'"));
            assertEquals(original, database.jdbc.queryForList(
                    "SELECT * FROM study_resource WHERE access_mode='OPEN_RESOURCE' ORDER BY id"));
            assertEquals(0, database.jdbc.queryForObject("SELECT COUNT(*) FROM study_material", Integer.class));
            assertEquals(0, database.jdbc.queryForObject("SELECT COUNT(*) FROM study_resource_user", Integer.class));

            List<StudyResource> books = database.books();
            assertEquals(1000, books.size());
            assertEquals(1000, books.stream().map(StudyResource::getIsbn).distinct().count());
            assertEquals(1000, books.stream().map(StudyResource::getSourceUrl).distinct().count());
            assertEquals(Map.of("高等教育出版社", 20L, "机械工业出版社", 980L), books.stream()
                    .collect(Collectors.groupingBy(StudyResource::getPublisher, Collectors.counting())));
            for (StudyResource book : books) {
                assertRequiredText(book.getTitle(), 200, "title", book);
                assertRequiredText(book.getSummary(), 500, "summary", book);
                assertRequiredText(book.getSubject(), 100, "subject", book);
                assertRequiredText(book.getAuthor(), 200, "author", book);
                assertRequiredText(book.getPublisher(), 200, "publisher", book);
                assertRequiredText(book.getSourceName(), 200, "sourceName", book);
                assertRequiredText(book.getCoverageNote(), 1000, "coverageNote", book);
                assertRequiredText(book.getLicenseNote(), 1000, "licenseNote", book);
                assertRequiredText(book.getSourceUrl(), 1000, "sourceUrl", book);
                assertEquals("本科", book.getEducationLevel(), book.getTitle());
                assertEquals("CATALOG_ONLY", book.getAccessMode());
                assertEquals("EXTERNAL", book.getResourceType());
                assertEquals("TEXTBOOK", book.getResourceKind());
                assertEquals("LINK_ONLY", book.getLicenseCode());
                assertEquals("PUBLISHED", book.getStatus());
                assertTrue(Set.of("zh", "zh-CN", "en").contains(book.getLanguageCode()));
                assertEquals(0, book.getEstimatedMinutes());
                assertNotNull(book.getDifficultyLevel());
                assertTrue(book.getDifficultyLevel() >= 1 && book.getDifficultyLevel() <= 5);
                assertTrue(book.getKnowledgePoints() == null || book.getKnowledgePoints().isBlank(),
                        "书目分类不能编造成书内知识点：" + book.getTitle());
                assertNull(book.getDownloadUrl());
                StudyResource stored = database.resources.findById(book.getId());
                assertNull(stored.getContentText());
                assertNull(stored.getReviewedBy());
                assertNull(stored.getReviewedAt());
                assertTrue(book.getPublicationYear() == null
                        || book.getPublicationYear() >= 1 && book.getPublicationYear() <= 9999);
                assertValidIsbn(book.getIsbn());
                assertOfficialBookSource(book);

                // Seeding PUBLISHED must not bypass the same structural requirements as an admin publication.
                ResourceStatusRequest request = new ResourceStatusRequest();
                request.setStatus("PUBLISHED");
                assertDoesNotThrow(() -> database.service.updateStatus(9L, book.getId(), request), book.getTitle());
            }
        }
    }

    @Test
    void repeatedSeedPreservesAdminEditsWithdrawalAndUserBookmarks() throws Exception {
        try (CatalogDatabase database = new CatalogDatabase()) {
            database.seed();
            Long id = database.books().get(0).getId();
            database.jdbc.update("""
                    UPDATE study_resource SET title='管理员已核对的书名', summary='管理员的目录说明',
                      status='WITHDRAWN', review_note='暂时下架复核', reviewed_by=9, reviewed_at=CURRENT_TIMESTAMP,
                      cover_url='https://library.example.com/selected-cover.jpg' WHERE id=?
                    """, id);
            database.jdbc.update("INSERT INTO study_material(id,user_id,deleted) VALUES(900,7,0)");
            database.jdbc.update("""
                    INSERT INTO study_resource_user(user_id,resource_id,material_id,bookmarked)
                    VALUES(7,?,900,TRUE)
                    """, id);
            var before = database.rows();
            var associations = database.jdbc.queryForList("SELECT * FROM study_resource_user ORDER BY id");
            var materials = database.jdbc.queryForList("SELECT * FROM study_material ORDER BY id");
            database.seed();
            assertEquals(before, database.rows());
            assertEquals(associations, database.jdbc.queryForList("SELECT * FROM study_resource_user ORDER BY id"));
            assertEquals(materials, database.jdbc.queryForList("SELECT * FROM study_material ORDER BY id"));
            assertEquals(1047, database.count(""));
        }
    }

    @Test
    void existingMatchingIsbnOrOfficialSourceSkipsInsertionWithoutOverwritingTheExistingRecord() throws Exception {
        List<StudyResource> references;
        try (CatalogDatabase source = new CatalogDatabase()) {
            source.seed();
            references = source.books().subList(0, 2);
        }
        try (CatalogDatabase database = new CatalogDatabase()) {
            StudyResource sameIsbn = references.get(0);
            StudyResource sameSource = references.get(1);
            database.insertExisting(sameIsbn.getIsbn(), "https://library.example.com/curated-book");
            database.insertExisting(null, sameSource.getSourceUrl());
            var before = database.jdbc.queryForList("SELECT * FROM study_resource WHERE source_name='管理员书目' ORDER BY id");
            database.seed();
            assertEquals(1047, database.count(""));
            assertEquals(998, database.count("WHERE access_mode='CATALOG_ONLY' AND status='PUBLISHED'"));
            assertEquals(before, database.jdbc.queryForList("SELECT * FROM study_resource WHERE source_name='管理员书目' ORDER BY id"));
            assertEquals(1, database.jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE isbn=?", Integer.class, sameIsbn.getIsbn()));
            assertEquals(0, database.jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE source_url=?", Integer.class, sameIsbn.getSourceUrl()));
            assertEquals(1, database.jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE source_url=?", Integer.class, sameSource.getSourceUrl()));
            assertEquals(0, database.jdbc.queryForObject("SELECT COUNT(*) FROM study_resource WHERE isbn=?", Integer.class, sameSource.getIsbn()));
            var allRows = database.rows();
            database.seed();
            assertEquals(allRows, database.rows());
        }
    }

    @Test
    void realApiPaginationReachesEveryCatalogBookAndKeepsOpenResourceFilterIndependent() throws Exception {
        try (CatalogDatabase database = new CatalogDatabase()) {
            database.seed();
            ResourcePageQuery query = new ResourcePageQuery();
            query.setAccessMode("CATALOG_ONLY");
            query.setResourceKind("TEXTBOOK");
            List<Long> visited = new ArrayList<>();
            for (long current = 1; current <= 84; current++) {
                query.setCurrent(current);
                var page = database.service.page(7L, query);
                assertEquals(1000, page.getTotal());
                assertEquals(84, page.getPages());
                assertEquals(current == 84 ? 4 : 12, page.getRecords().size());
                for (ResourceVO book : page.getRecords()) {
                    assertEquals("CATALOG_ONLY", book.getAccessMode());
                    assertTrue(book.getKnowledgePoints().isEmpty());
                    assertNull(book.getContentText());
                    assertNull(book.getDownloadUrl());
                    visited.add(book.getId());
                }
            }
            assertEquals(1000, new HashSet<>(visited).size());
            assertEquals(database.books().stream().map(StudyResource::getId).toList(), visited);
            query.setCurrent(85L);
            assertTrue(database.service.page(7L, query).getRecords().isEmpty());

            ResourcePageQuery open = new ResourcePageQuery();
            open.setAccessMode("OPEN_RESOURCE");
            assertEquals(17, database.service.page(7L, open).getTotal());
            open.setCurrent(2L);
            assertEquals(5, database.service.page(7L, open).getRecords().size());
            assertEquals(1017, database.service.page(7L, new ResourcePageQuery()).getTotal());

            Long bookmarkedId = visited.get(visited.size() - 1);
            database.links.ensureLink(7L, bookmarkedId);
            database.links.setBookmarked(7L, bookmarkedId, true);
            query.setCurrent(1L);
            query.setBookmarkedOnly(true);
            assertEquals(1, database.service.page(7L, query).getTotal());
            assertEquals(bookmarkedId, database.service.page(7L, query).getRecords().get(0).getId());
            assertEquals(0, database.service.page(8L, query).getTotal());
        }
    }

    @Test
    void isbnAuthorAndPublisherSearchReturnRealCatalogMetadataInPageAndDetail() throws Exception {
        try (CatalogDatabase database = new CatalogDatabase()) {
            database.seed();
            List<StudyResource> books = database.books();
            Map<String, StudyResource> examples = new LinkedHashMap<>();
            for (StudyResource book : books) examples.putIfAbsent(book.getPublisher(), book);
            for (StudyResource book : examples.values()) {
                ResourcePageQuery query = new ResourcePageQuery();
                query.setAccessMode("CATALOG_ONLY");
                query.setKeyword(book.getIsbn());
                var page = database.service.page(7L, query);
                assertEquals(1, page.getTotal());
                assertEquals(book.getId(), page.getRecords().get(0).getId());
                assertCatalogVo(book, page.getRecords().get(0));
                assertCatalogVo(book, database.service.detail(7L, book.getId()));

                List<StudyResource> authored = database.resources.findPage(7L, "PUBLISHED", book.getAuthor(), null,
                        false, 0, 1000, false, "TEXTBOOK", "CATALOG_ONLY");
                assertTrue(authored.stream().anyMatch(match -> match.getId().equals(book.getId())));
                assertEquals(authored.size(), database.resources.countPage(7L, "PUBLISHED", book.getAuthor(), null,
                        false, "TEXTBOOK", "CATALOG_ONLY"));
                query.setKeyword(book.getPublisher());
                assertEquals("高等教育出版社".equals(book.getPublisher()) ? 20 : 980,
                        database.service.page(7L, query).getTotal());
                assertThrows(BusinessException.class, () -> database.service.importMaterial(7L, book.getId()));
            }
        }
    }

    @Test
    void v26WithdrawsOnlyTheExactBatchAndKeepsOriginalResourcesAndPrivateData() throws Exception {
        try (CatalogDatabase database = new CatalogDatabase()) {
            var original = database.rows();
            database.seed();
            Long bookId = database.books().get(0).getId();
            database.jdbc.update("INSERT INTO study_material(id,user_id,deleted,resource_attribution) VALUES(900,7,0,'用户保留的来源')");
            database.jdbc.update("""
                    INSERT INTO study_resource_user(user_id,resource_id,material_id,bookmarked)
                    VALUES(7,?,900,TRUE)
                    """, bookId);
            var materials = database.jdbc.queryForList("SELECT * FROM study_material ORDER BY id");
            var associations = database.jdbc.queryForList("SELECT * FROM study_resource_user ORDER BY id");

            database.migrate("V26__withdraw_domestic_catalog_batch.sql");

            assertEquals(1047, database.count(""));
            assertEquals(17, database.count("WHERE status='PUBLISHED'"));
            assertEquals(1000, database.count("WHERE access_mode='CATALOG_ONLY' AND status='WITHDRAWN'"));
            assertEquals(original, database.jdbc.queryForList(
                    "SELECT * FROM study_resource WHERE access_mode='OPEN_RESOURCE' ORDER BY id"));
            assertEquals(materials, database.jdbc.queryForList("SELECT * FROM study_material ORDER BY id"));
            assertEquals(associations, database.jdbc.queryForList("SELECT * FROM study_resource_user ORDER BY id"));
            assertEquals(17, database.service.page(7L, new ResourcePageQuery()).getTotal());
            ResourcePageQuery catalog = new ResourcePageQuery();
            catalog.setAccessMode("CATALOG_ONLY");
            assertEquals(0, database.service.page(7L, catalog).getTotal());
            assertThrows(BusinessException.class, () -> database.service.detail(7L, bookId));
            var withdrawnRows = database.rows();
            database.migrate("V26__withdraw_domestic_catalog_batch.sql");
            assertEquals(withdrawnRows, database.rows());
        }
    }

    @Test
    void v26PreservesExistingMatchingCatalogAndIndependentlyAddedPublisherRecords() throws Exception {
        List<StudyResource> samples;
        try (CatalogDatabase source = new CatalogDatabase()) {
            source.seed();
            samples = source.books().subList(0, 2);
        }
        try (CatalogDatabase database = new CatalogDatabase()) {
            // V25 skipped these pre-existing records; V26 must not infer ownership from ISBN alone.
            database.insertExisting(samples.get(0).getIsbn(), samples.get(0).getSourceUrl());
            database.insertExisting(samples.get(1).getIsbn(), "https://library.example.com/independently-curated");
            database.jdbc.update("UPDATE study_resource SET status='PUBLISHED' WHERE source_name='管理员书目'");
            var preexisting = database.jdbc.queryForList("SELECT * FROM study_resource WHERE source_name='管理员书目' ORDER BY id");
            database.seed();
            database.insertExisting("9780306406157", "https://www.cmpedu.com/books/book/999999999.htm");
            database.jdbc.update("""
                    UPDATE study_resource SET source_name='机械工业出版社官方教材目录',status='PUBLISHED'
                    WHERE isbn='9780306406157'
                    """);
            var independent = database.jdbc.queryForList("SELECT * FROM study_resource WHERE isbn='9780306406157'");

            database.migrate("V26__withdraw_domestic_catalog_batch.sql");

            assertEquals(998, database.count("WHERE access_mode='CATALOG_ONLY' AND status='WITHDRAWN'"));
            assertEquals(preexisting, database.jdbc.queryForList("SELECT * FROM study_resource WHERE source_name='管理员书目' ORDER BY id"));
            assertEquals(independent, database.jdbc.queryForList("SELECT * FROM study_resource WHERE isbn='9780306406157'"));
        }
    }

    @Test
    void v28PublishesFourCompleteBooksWhoseBodiesAndDocumentsMatchTheBundledOriginals() throws Exception {
        try (CatalogDatabase database = new CatalogDatabase()) {
            database.seed();
            database.migrate("V26__withdraw_domestic_catalog_batch.sql");
            var original = database.rows();
            database.migrate(OPEN_BOOK_SEED);

            assertEquals(1051, database.count(""));
            assertEquals(21, database.count("WHERE status='PUBLISHED'"));
            assertEquals(4, database.count("WHERE status='PUBLISHED' AND document_url IS NOT NULL"));
            assertEquals(17, database.count("WHERE status='PUBLISHED' AND document_url IS NULL"));
            assertEquals(0, database.count("WHERE status='PUBLISHED' AND access_mode='CATALOG_ONLY'"));
            assertEquals(original, database.jdbc.queryForList("SELECT * FROM study_resource WHERE document_url IS NULL ORDER BY id"));

            Path project = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().getParent();
            JsonNode records = new ObjectMapper().readTree(Files.readString(project.resolve("db/catalog/github-open-books.json"))).get("records");
            assertEquals(4, records.size());
            int pages = 0;
            for (JsonNode reference : records) {
                String slug = reference.get("slug").asText();
                String documentUrl = "/open-books/" + slug + "/book.pdf";
                Long id = database.jdbc.queryForObject("SELECT id FROM study_resource WHERE document_url=?", Long.class, documentUrl);
                StudyResource book = database.resources.findById(id);
                assertEquals("PUBLISHED", book.getStatus());
                assertEquals("INTERNAL", book.getResourceType());
                assertEquals("TEXTBOOK", book.getResourceKind());
                assertEquals("OPEN_RESOURCE", book.getAccessMode());
                assertEquals(reference.get("title").asText(), book.getTitle());
                assertEquals(reference.get("author").asText(), book.getAuthor());
                assertEquals(reference.get("publisher").asText(), book.getPublisher());
                assertEquals(reference.get("licenseCode").asText(), book.getLicenseCode());
                assertEquals(reference.get("licenseUrl").asText(), book.getLicenseUrl());
                assertEquals(reference.get("sourceUrl").asText(), book.getSourceUrl());
                assertEquals(reference.get("licenseNote").asText(), book.getLicenseNote());
                assertEquals(reference.get("pageCount").asInt(), book.getPageCount());
                Path folder = project.resolve("frontend/public/open-books").resolve(slug);
                String fullBody = Files.readString(folder.resolve("body.txt"));
                assertTrue(fullBody.equals(book.getContentText()), "SQL must retain the complete body, including backslashes: " + slug);
                assertEquals(reference.get("textSha256").asText(), sha256(book.getContentText().getBytes(StandardCharsets.UTF_8)));
                byte[] document = Files.readAllBytes(folder.resolve("book.pdf"));
                assertEquals(reference.get("sha256").asText(), sha256(document));
                try (var pdf = Loader.loadPDF(document)) {
                    assertEquals(book.getPageCount(), pdf.getNumberOfPages());
                }
                assertTrue(Files.size(folder.resolve("cover.webp")) > 100);
                assertTrue(Files.readString(folder.resolve("ATTRIBUTION.md")).contains(book.getLicenseUrl()));
                assertEquals("/open-books/" + slug + "/cover.webp", book.getCoverUrl());
                assertDoesNotThrow(() -> database.service.updateStatus(9L, id, publishedStatus()));
                ResourceVO detail = database.service.detail(7L, id);
                assertTrue(fullBody.equals(detail.getContentText()), "Detail must expose the full hosted body: " + slug);
                assertEquals(documentUrl, detail.getDocumentUrl());
                assertEquals(book.getPageCount(), detail.getPageCount());
                pages += book.getPageCount();
            }
            assertEquals(1347, pages);
            var visible = database.service.page(7L, new ResourcePageQuery()).getRecords();
            assertTrue(visible.subList(0, 4).stream().allMatch(book -> book.getDocumentUrl() != null));
            assertTrue(visible.subList(4, visible.size()).stream().allMatch(book -> book.getDocumentUrl() == null));
            assertTrue(database.service.recommendations(7L, null, 4).stream().allMatch(book -> book.getDocumentUrl() != null));
        }
    }

    @Test
    void v28BooksImportTheirCompleteBodiesOnceAndKeepSourceAndLicenseAttribution() throws Exception {
        try (CatalogDatabase database = new CatalogDatabase()) {
            database.migrate(OPEN_BOOK_SEED);
            List<StudyResource> books = database.resources.findPage(7L, "PUBLISHED", null, null, false, 0, 4, false, null, null);
            long nextId = 900;
            for (StudyResource summary : books) {
                StudyResource book = database.resources.findById(summary.getId());
                long materialId = nextId++;
                when(database.materials.createTextMaterial(eq(7L), any())).thenAnswer(invocation -> {
                    database.jdbc.update("INSERT INTO study_material(id,user_id,deleted) VALUES(?,7,0)", materialId);
                    return materialId;
                });
                StudyMaterial owned = new StudyMaterial();
                owned.setId(materialId);
                owned.setUserId(7L);
                owned.setDeleted(0);
                when(database.materials.getById(materialId)).thenReturn(owned);
                assertEquals(materialId, database.service.importMaterial(7L, book.getId()));
                assertEquals(materialId, database.service.importMaterial(7L, book.getId()));
                String attribution = database.jdbc.queryForObject("SELECT resource_attribution FROM study_material WHERE id=?", String.class, materialId);
                assertNotNull(attribution);
                assertTrue(attribution.contains(book.getAuthor()));
                assertTrue(attribution.contains(book.getSourceUrl()));
                assertTrue(attribution.contains(book.getLicenseUrl()));
                assertTrue(attribution.contains(book.getLicenseCode()));
                assertTrue(attribution.contains(book.getDocumentUrl()));
            }
            ArgumentCaptor<MaterialCreateRequest> requests = ArgumentCaptor.forClass(MaterialCreateRequest.class);
            verify(database.materials, times(4)).createTextMaterial(eq(7L), requests.capture());
            for (MaterialCreateRequest request : requests.getAllValues()) {
                StudyResource source = books.stream().filter(book -> book.getTitle().equals(request.getTitle())).findFirst().orElseThrow();
                assertTrue(database.resources.findById(source.getId()).getContentText().equals(request.getContentText()),
                        "Import must keep every chapter, not a summary: " + request.getTitle());
            }
            assertEquals(4, database.jdbc.queryForObject("SELECT COUNT(*) FROM study_material", Integer.class));
            assertEquals(4, database.jdbc.queryForObject("SELECT COUNT(*) FROM study_resource_user WHERE user_id=7 AND material_id IS NOT NULL", Integer.class));
        }
    }

    @Test
    void repeatedV28SeedPreservesAdminChangesWithdrawalAndPrivateAssociations() throws Exception {
        try (CatalogDatabase database = new CatalogDatabase()) {
            database.migrate(OPEN_BOOK_SEED);
            Long id = database.jdbc.queryForObject("SELECT MIN(id) FROM study_resource WHERE document_url IS NOT NULL", Long.class);
            database.jdbc.update("""
                    UPDATE study_resource SET title='管理员修订的标题',status='WITHDRAWN',
                      review_note='管理员保留的审核说明',reviewed_by=9,reviewed_at=CURRENT_TIMESTAMP WHERE id=?
                    """, id);
            database.jdbc.update("INSERT INTO study_material(id,user_id,deleted,resource_attribution) VALUES(900,7,0,'独立保存的来源')");
            database.jdbc.update("INSERT INTO study_resource_user(user_id,resource_id,material_id,bookmarked) VALUES(7,?,900,TRUE)", id);
            var rows = database.rows();
            var materials = database.jdbc.queryForList("SELECT * FROM study_material ORDER BY id");
            var associations = database.jdbc.queryForList("SELECT * FROM study_resource_user ORDER BY id");
            database.migrate(OPEN_BOOK_SEED);
            assertEquals(rows, database.rows());
            assertEquals(materials, database.jdbc.queryForList("SELECT * FROM study_material ORDER BY id"));
            assertEquals(associations, database.jdbc.queryForList("SELECT * FROM study_resource_user ORDER BY id"));
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static ResourceStatusRequest publishedStatus() {
        ResourceStatusRequest status = new ResourceStatusRequest();
        status.setStatus("PUBLISHED");
        return status;
    }

    private static void assertCatalogVo(StudyResource source, ResourceVO actual) {
        assertEquals("CATALOG_ONLY", actual.getAccessMode());
        assertEquals(source.getIsbn(), actual.getIsbn());
        assertEquals(source.getPublisher(), actual.getPublisher());
        assertEquals(source.getEducationLevel(), actual.getEducationLevel());
        assertEquals(source.getPublicationYear(), actual.getPublicationYear());
        assertEquals(source.getAuthor(), actual.getAuthor());
        assertEquals(source.getSourceUrl(), actual.getSourceUrl());
        assertTrue(actual.getKnowledgePoints().isEmpty());
        assertNull(actual.getContentText());
        assertNull(actual.getDownloadUrl());
        assertNull(actual.getMaterialId());
    }

    private static void assertRequiredText(String value, int max, String field, StudyResource book) {
        assertNotNull(value, field + ": " + book.getId());
        assertFalse(value.isBlank(), field + ": " + book.getId());
        assertTrue(value.length() <= max, field + ": " + book.getId());
    }

    private static void assertValidIsbn(String isbn) {
        assertNotNull(isbn);
        assertTrue(isbn.matches("97[89][0-9]{10}"), isbn);
        int sum = 0;
        for (int index = 0; index < isbn.length(); index++) sum += (isbn.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        assertEquals(0, sum % 10, isbn);
    }

    private static void assertOfficialBookSource(StudyResource book) {
        URI uri = URI.create(book.getSourceUrl());
        assertEquals("https", uri.getScheme());
        assertNull(uri.getUserInfo());
        assertEquals(-1, uri.getPort());
        if ("高等教育出版社".equals(book.getPublisher())) {
            assertEquals("www.hep.com.cn", uri.getHost());
            assertTrue(uri.getPath().startsWith("/mgcjiaocai/show/"), book.getSourceUrl());
            assertTrue(uri.getPath().length() > "/mgcjiaocai/show/".length());
        } else {
            assertEquals("机械工业出版社", book.getPublisher());
            assertEquals("www.cmpedu.com", uri.getHost());
            assertTrue(uri.getPath().matches("/books/book/[0-9]+\\.htm"), book.getSourceUrl());
        }
    }

    private static class CatalogDatabase implements AutoCloseable {
        final JdbcDataSource dataSource = new JdbcDataSource();
        final JdbcTemplate jdbc;
        final SqlSession session;
        final StudyResourceMapper resources;
        final StudyResourceUserMapper links;
        final StudyMaterialService materials = mock(StudyMaterialService.class);
        final StudyResourceServiceImpl service;

        CatalogDatabase() throws Exception {
            dataSource.setURL("jdbc:h2:mem:domestic_catalog_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE study_material(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,deleted INT DEFAULT 0)");
            for (String migration : List.of("V18__create_study_resource_library.sql", "V19__seed_learning_resources.sql",
                    "V20__classify_course_resources.sql", "V21__seed_full_course_catalog.sql", "V22__add_resource_cover_url.sql",
                    "V23__seed_verified_resource_covers.sql", "V24__add_resource_catalog_metadata.sql",
                    "V27__add_hosted_book_document.sql")) {
                migrate(migration);
            }
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setEnvironment(new Environment("domestic-catalog", new JdbcTransactionFactory(), dataSource));
            configuration.addMapper(StudyResourceMapper.class);
            configuration.addMapper(StudyResourceUserMapper.class);
            session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
            resources = session.getMapper(StudyResourceMapper.class);
            links = session.getMapper(StudyResourceUserMapper.class);
            SysUserMapper users = mock(SysUserMapper.class);
            SysUser admin = new SysUser();
            admin.setRoleCode("ADMIN");
            admin.setStatus(1);
            when(users.selectById(9L)).thenReturn(admin);
            service = new StudyResourceServiceImpl(resources, links, materials,
                    mock(KnowledgeMasteryService.class), users);
        }

        void seed() throws Exception { migrate(SEED); }

        List<StudyResource> books() {
            return resources.findPage(7L, "PUBLISHED", null, null, false, 0, 1000, false, "TEXTBOOK", "CATALOG_ONLY");
        }

        List<Map<String, Object>> rows() {
            return jdbc.queryForList("SELECT * FROM study_resource ORDER BY id");
        }

        int count(String where) {
            return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM study_resource " + where, Integer.class));
        }

        void insertExisting(String isbn, String sourceUrl) {
            jdbc.update("""
                    INSERT INTO study_resource(title,summary,subject,knowledge_points,difficulty_level,estimated_minutes,
                      resource_type,resource_kind,access_mode,source_name,source_url,isbn,author,license_code,license_note,
                      status,review_note,reviewed_by)
                    VALUES('管理员书名','已有目录说明','计算机','',3,0,'EXTERNAL','TEXTBOOK','CATALOG_ONLY',
                      '管理员书目',?,?,'原始作者','LINK_ONLY','前往原页面','WITHDRAWN','管理员审核记录',9)
                    """, sourceUrl, isbn);
        }

        void migrate(String filename) throws Exception {
            try (Connection connection = dataSource.getConnection();
                    var migration = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                            "/db/migration/" + filename), "Missing migration: " + filename), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, migration);
            }
            // JDBC fixtures and migrations run outside the mapper session's first-level cache.
            if (session != null) session.clearCache();
        }

        @Override
        public void close() {
            session.close();
            jdbc.execute("DROP ALL OBJECTS");
        }
    }
}
