package com.aiassistant.learning.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudyResourceMapperTest {
    private final MybatisConfiguration configuration = new MybatisConfiguration();

    @BeforeEach
    void parseMapperStatements() {
        configuration.addMapper(StudyResourceMapper.class);
        configuration.addMapper(StudyResourceUserMapper.class);
    }

    @Test
    void publicSearchBindsValuesAndScopesBookmarksToCurrentUserInBothQueries() {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 7L);
        params.put("status", "PUBLISHED");
        params.put("keyword", "' OR 1=1 --");
        params.put("subject", "计算机");
        params.put("bookmarkedOnly", true);
        params.put("offset", 0L);
        params.put("size", 12L);
        params.put("admin", false);
        params.put("resourceKind", "TEXTBOOK");
        params.put("accessMode", "CATALOG_ONLY");
        for (String method : List.of("findPage", "countPage")) {
            BoundSql bound = sql(StudyResourceMapper.class, method, params);
            String statement = normalized(bound);
            assertTrue(statement.contains("r.status = ?"));
            assertTrue(statement.contains("r.subject = ?"));
            assertTrue(statement.contains("r.resource_kind = ?"));
            assertTrue(statement.contains("r.access_mode = ?"));
            assertTrue(statement.contains("u.resource_id = r.id AND u.user_id = ? AND u.bookmarked = TRUE"));
            assertTrue(statement.contains("INSTR(LOWER(r.title), LOWER(?)) > 0"));
            for (String column : List.of("isbn", "author", "publisher")) {
                assertTrue(statement.contains("INSTR(LOWER(r." + column + "), LOWER(?)) > 0"));
            }
            assertFalse(statement.contains("OR 1=1"));
            assertFalse(statement.contains("content_text"));
            assertTrue(bound.getParameterMappings().stream().anyMatch(mapping -> mapping.getProperty().equals("userId")));
            assertTrue(bound.getParameterMappings().stream().anyMatch(mapping -> mapping.getProperty().equals("accessMode")));
        }
    }

    @Test
    void exposedMaterialIdsAreJoinedAgainstOwnerAndLogicalDeletion() {
        String statement = normalized(sql(StudyResourceUserMapper.class, "findVisibleLinks",
                Map.of("userId", 7L, "resourceIds", List.of(11L, 12L))));
        assertTrue(statement.contains("m.id AS material_id"));
        assertTrue(statement.contains("m.id = u.material_id AND m.user_id = ? AND m.deleted = 0"));
        assertTrue(statement.contains("WHERE u.user_id = ? AND u.resource_id IN"));
    }

    @Test
    void emptyRecommendationTermsGenerateFeaturedQueryAndMatchesRankTagsBeforeTitles() {
        String featured = normalized(sql(StudyResourceMapper.class, "findRecommendations", Map.of("terms", List.of(), "limit", 3)));
        assertTrue(featured.contains("WHERE r.status = 'PUBLISHED' ORDER BY"));
        assertTrue(featured.contains("r.id ASC LIMIT ?"));
        String matched = normalized(sql(StudyResourceMapper.class, "findRecommendations", Map.of("terms", List.of("链表", "节点"), "limit", 3)));
        assertTrue(matched.contains("WHERE r.status = 'PUBLISHED' AND"));
        assertTrue(matched.contains("LOCATE"));
        assertTrue(matched.contains("THEN 20 ELSE 0 END"));
        assertTrue(matched.contains("THEN 5 ELSE 0 END"));
        assertFalse(matched.contains("链表"));
        assertFalse(matched.contains("content_text"));
    }

    @Test
    void importsUseDatabaseLocksAndIdempotentAssociationInsert() {
        assertTrue(normalized(sql(StudyResourceMapper.class, "lockById", Map.of("id", 11L))).endsWith("FOR UPDATE"));
        String lockedLink = normalized(sql(StudyResourceUserMapper.class, "lockLink", Map.of("userId", 7L, "resourceId", 11L)));
        assertTrue(lockedLink.contains("WHERE user_id = ? AND resource_id = ? FOR UPDATE"));
        assertTrue(normalized(sql(StudyResourceUserMapper.class, "ensureLink", Map.of("userId", 7L, "resourceId", 11L)))
                .contains("ON DUPLICATE KEY UPDATE"));
        String update = normalized(sql(StudyResourceUserMapper.class, "setMaterialId",
                Map.of("userId", 7L, "resourceId", 11L, "materialId", 91L)));
        assertTrue(update.contains("WHERE user_id = ? AND resource_id = ?"));
    }

    private BoundSql sql(Class<?> mapper, String method, Map<String, Object> params) {
        return configuration.getMappedStatement(mapper.getName() + "." + method).getBoundSql(params);
    }

    private static String normalized(BoundSql sql) {
        return sql.getSql().replaceAll("\\s+", " ").strip();
    }
}
