package com.aiassistant.learning.mapper;

import com.aiassistant.learning.entity.StudyResource;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface StudyResourceMapper {
    String COLUMNS = "SELECT r.id, r.title, r.summary, r.subject, r.knowledge_points, r.difficulty_level, "
            + "r.estimated_minutes, r.resource_type, r.source_name, r.source_url, r.author, "
            + "r.resource_kind, r.language_code, r.coverage_note, r.download_url, r.cover_url, "
            + "r.document_url, r.page_count, "
            + "r.access_mode, r.isbn, r.publisher, r.education_level, r.publication_year, "
            + "r.license_code, r.license_url, r.license_note, r.status FROM study_resource r ";
    String FILTERS = """
            <where>
              <if test="status != null">AND r.status = #{status}</if>
              <if test="keyword != null">
                AND (INSTR(LOWER(r.title), LOWER(#{keyword})) &gt; 0
                  OR INSTR(LOWER(r.knowledge_points), LOWER(#{keyword})) &gt; 0
                  OR INSTR(LOWER(r.summary), LOWER(#{keyword})) &gt; 0
                  OR INSTR(LOWER(r.isbn), LOWER(#{keyword})) &gt; 0
                  OR INSTR(LOWER(r.author), LOWER(#{keyword})) &gt; 0
                  OR INSTR(LOWER(r.publisher), LOWER(#{keyword})) &gt; 0)
              </if>
              <if test="subject != null">AND r.subject = #{subject}</if>
              <if test="resourceKind != null">AND r.resource_kind = #{resourceKind}</if>
              <if test="accessMode != null">AND r.access_mode = #{accessMode}</if>
              <if test="bookmarkedOnly">
                AND EXISTS (SELECT 1 FROM study_resource_user u WHERE u.resource_id = r.id
                  AND u.user_id = #{userId} AND u.bookmarked = TRUE)
              </if>
            </where>
            """;

    @Select("<script>" + COLUMNS + FILTERS + """
             ORDER BY <choose><when test="admin">r.id DESC</when>
               <otherwise>CASE WHEN r.document_url IS NOT NULL AND r.document_url &lt;&gt; '' THEN 0 ELSE 1 END ASC,
                 CASE WHEN r.resource_kind = 'KNOWLEDGE_CARD' THEN 1 ELSE 0 END ASC,
                 CASE WHEN LOWER(r.language_code) LIKE 'zh%' THEN 0 ELSE 1 END ASC, r.id ASC</otherwise></choose>
             LIMIT #{size} OFFSET #{offset}</script>
            """)
    List<StudyResource> findPage(@Param("userId") Long userId, @Param("status") String status,
            @Param("keyword") String keyword, @Param("subject") String subject,
            @Param("bookmarkedOnly") boolean bookmarkedOnly, @Param("offset") long offset,
            @Param("size") long size, @Param("admin") boolean admin, @Param("resourceKind") String resourceKind,
            @Param("accessMode") String accessMode);

    @Select("<script>SELECT COUNT(*) FROM study_resource r " + FILTERS + "</script>")
    long countPage(@Param("userId") Long userId, @Param("status") String status,
            @Param("keyword") String keyword, @Param("subject") String subject,
            @Param("bookmarkedOnly") boolean bookmarkedOnly, @Param("resourceKind") String resourceKind,
            @Param("accessMode") String accessMode);

    @Select("SELECT * FROM study_resource WHERE id = #{id} AND status = 'PUBLISHED'")
    StudyResource findPublishedById(@Param("id") Long id);

    @Select("SELECT * FROM study_resource WHERE id = #{id}")
    StudyResource findById(@Param("id") Long id);

    @Select("SELECT * FROM study_resource WHERE id = #{id} FOR UPDATE")
    StudyResource lockById(@Param("id") Long id);

    @Select("<script>" + COLUMNS + """
            WHERE r.status = 'PUBLISHED'
              <if test="terms != null and !terms.isEmpty()">
                AND <foreach collection="terms" item="term" open="(" close=")" separator=" OR ">
                  (INSTR(LOWER(r.knowledge_points), LOWER(#{term})) &gt; 0
                    OR INSTR(LOWER(r.title), LOWER(#{term})) &gt; 0)
                </foreach>
              </if>
            ORDER BY
              CASE WHEN r.document_url IS NOT NULL AND r.document_url &lt;&gt; '' THEN 0 ELSE 1 END ASC,
              CASE WHEN r.resource_kind = 'KNOWLEDGE_CARD' THEN 1 ELSE 0 END ASC,
              CASE WHEN LOWER(r.language_code) LIKE 'zh%' THEN 0 ELSE 1 END ASC,
              <if test="terms != null and !terms.isEmpty()">
                (0 <foreach collection="terms" item="term">
                  + CASE WHEN LOCATE(CONCAT(',', LOWER(#{term}), ','),
                      CONCAT(',', LOWER(REPLACE(r.knowledge_points, ', ', ',')), ',')) &gt; 0
                      THEN 20 ELSE 0 END
                  + CASE WHEN INSTR(LOWER(r.knowledge_points), LOWER(#{term})) &gt; 0 THEN 10 ELSE 0 END
                  + CASE WHEN INSTR(LOWER(r.title), LOWER(#{term})) &gt; 0 THEN 5 ELSE 0 END
                </foreach>) DESC,
              </if>
              r.id ASC LIMIT #{limit}
            </script>
            """)
    List<StudyResource> findRecommendations(@Param("terms") List<String> terms, @Param("limit") int limit);

    @Update("""
            UPDATE study_resource SET status = #{status}, review_note = #{reviewNote},
              reviewed_by = #{reviewedBy}, reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status,
            @Param("reviewNote") String reviewNote, @Param("reviewedBy") Long reviewedBy);
}
