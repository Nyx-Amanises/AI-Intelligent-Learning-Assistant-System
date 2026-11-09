package com.aiassistant.learning.mapper;

import com.aiassistant.learning.dto.note.PersonalNotePageQuery;
import com.aiassistant.learning.entity.PersonalNote;
import com.aiassistant.learning.entity.PracticeSession;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PersonalNoteMapper {
    String JOIN = " FROM personal_note n LEFT JOIN note_notebook b ON b.id = n.notebook_id AND b.user_id = n.user_id AND b.deleted = 0 ";
    String FILTER = """
            WHERE n.user_id = #{userId} AND n.deleted = 0
            <if test="q.keyword != null and q.keyword != ''">
              AND (INSTR(LOWER(n.title), LOWER(#{q.keyword})) > 0 OR INSTR(LOWER(n.content_text), LOWER(#{q.keyword})) > 0)
            </if>
            <if test="q.notebookId != null">
              <choose><when test="q.notebookId == 0">AND n.notebook_id IS NULL</when>
              <otherwise>AND n.notebook_id = #{q.notebookId}</otherwise></choose>
            </if>
            <choose>
              <when test="q.sourceType != null">AND n.source_type = #{q.sourceType}</when>
              <when test="q.sourceGroup == 'READING'">AND n.source_type IN ('MATERIAL', 'RESOURCE')</when>
              <when test="q.sourceGroup == 'QUESTION'">AND n.source_type = 'QUESTION'</when>
            </choose>
            <if test="q.sourceId != null">AND n.source_id = #{q.sourceId}</if>
            <if test="q.favorite != null">AND n.favorite = #{q.favorite}</if>
            """;

    @Select("<script>SELECT COUNT(*) FROM personal_note n " + FILTER + "</script>")
    long countPage(@Param("userId") Long userId, @Param("q") PersonalNotePageQuery query);

    @Select("<script>SELECT n.*, b.name AS notebook_name " + JOIN + FILTER
            + " ORDER BY n.favorite DESC, n.updated_at DESC, n.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<PersonalNote> findPage(@Param("userId") Long userId, @Param("q") PersonalNotePageQuery query,
            @Param("offset") long offset, @Param("size") long size);

    @Select("SELECT n.*, b.name AS notebook_name " + JOIN + " WHERE n.id = #{id} AND n.user_id = #{userId} AND n.deleted = 0")
    PersonalNote findOwned(@Param("userId") Long userId, @Param("id") Long id);

    // Include deleted records: delayed autosaves must not recreate a deliberately deleted note.
    @Select("SELECT n.*, b.name AS notebook_name " + JOIN + " WHERE n.user_id = #{userId} AND n.client_key = #{clientKey}")
    PersonalNote findByClientKey(@Param("userId") Long userId, @Param("clientKey") String clientKey);

    @Select("SELECT * FROM personal_note WHERE user_id = #{userId} AND client_key = #{clientKey} FOR UPDATE")
    PersonalNote lockByClientKey(@Param("userId") Long userId, @Param("clientKey") String clientKey);

    @Insert("""
            INSERT INTO personal_note (user_id, client_key, notebook_id, title, content_json, content_text,
                source_type, source_id, practice_session_id, page_number, source_title, source_excerpt, favorite)
            VALUES (#{n.userId}, #{n.clientKey}, #{n.notebookId}, #{n.title}, #{n.contentJson}, #{n.contentText},
                #{n.sourceType}, #{n.sourceId}, #{n.practiceSessionId}, #{n.pageNumber}, #{n.sourceTitle}, #{n.sourceExcerpt}, #{n.favorite})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotently(@Param("n") PersonalNote note);

    @Update("""
            UPDATE personal_note SET title = #{n.title}, content_json = #{n.contentJson}, content_text = #{n.contentText},
                notebook_id = #{n.notebookId}, favorite = #{n.favorite}, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{n.id} AND user_id = #{n.userId} AND deleted = 0 AND version = #{n.version}
            """)
    int updateVersioned(@Param("n") PersonalNote note);

    @Update("""
            UPDATE personal_note SET deleted = 1, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
            """)
    int deleteOwned(@Param("userId") Long userId, @Param("id") Long id);

    @Update("""
            UPDATE personal_note SET notebook_id = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND notebook_id = #{notebookId} AND deleted = 0
            """)
    int unfileNotebook(@Param("userId") Long userId, @Param("notebookId") Long notebookId);

    @Select("SELECT COUNT(*) FROM study_material WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int countOwnedMaterial(@Param("userId") Long userId, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM study_resource WHERE id = #{id} AND status = 'PUBLISHED'")
    int countPublishedResource(@Param("id") Long id);

    @Select("""
            SELECT q.question_set_id FROM question_item q JOIN question_set s ON s.id = q.question_set_id
            WHERE q.id = #{id} AND s.user_id = #{userId} AND s.deleted = 0
            """)
    Long findOwnedQuestionSet(@Param("userId") Long userId, @Param("id") Long id);

    @Select("SELECT id, user_id, question_set_id, question_ids_json FROM practice_session WHERE id = #{id} AND user_id = #{userId}")
    PracticeSession findOwnedSession(@Param("userId") Long userId, @Param("id") Long id);
}
