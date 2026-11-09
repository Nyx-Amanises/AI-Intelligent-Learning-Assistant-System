package com.aiassistant.learning.mapper;

import com.aiassistant.learning.entity.NoteNotebook;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface NoteNotebookMapper {
    String WITH_COUNT = """
            SELECT b.*, (SELECT COUNT(*) FROM personal_note n
              WHERE n.notebook_id = b.id AND n.user_id = b.user_id AND n.deleted = 0) AS note_count
            FROM note_notebook b
            """;

    @Select(WITH_COUNT + " WHERE b.user_id = #{userId} AND b.deleted = 0 ORDER BY b.updated_at DESC, b.id DESC")
    List<NoteNotebook> findAllOwned(@Param("userId") Long userId);

    @Select(WITH_COUNT + " WHERE b.id = #{id} AND b.user_id = #{userId} AND b.deleted = 0")
    NoteNotebook findOwned(@Param("userId") Long userId, @Param("id") Long id);

    // All assignments and deletion acquire this lock before writing note rows.
    @Select("SELECT * FROM note_notebook WHERE id = #{id} AND user_id = #{userId} AND deleted = 0 FOR UPDATE")
    NoteNotebook lockOwned(@Param("userId") Long userId, @Param("id") Long id);

    @Insert("INSERT INTO note_notebook (user_id, name, color) VALUES (#{userId}, #{name}, #{color})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NoteNotebook notebook);

    @Update("""
            UPDATE note_notebook SET name = #{name}, color = #{color}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
            """)
    int updateOwned(NoteNotebook notebook);

    @Update("""
            UPDATE note_notebook SET deleted = 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
            """)
    int deleteOwned(@Param("userId") Long userId, @Param("id") Long id);
}
