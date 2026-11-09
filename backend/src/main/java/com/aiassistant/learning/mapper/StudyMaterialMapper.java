package com.aiassistant.learning.mapper;

import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.entity.StudyResource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 学习资料表数据库访问接口。
 *
 * <p>继承 {@link BaseMapper} 后，MyBatis-Plus 自动提供常用的增删改查方法。</p>
 */
public interface StudyMaterialMapper extends BaseMapper<StudyMaterial> {
    @Select("""
            SELECT r.id, r.document_url
            FROM study_resource_user u
            INNER JOIN study_material m ON m.id = u.material_id AND m.user_id = u.user_id AND m.deleted = 0
            INNER JOIN study_resource r ON r.id = u.resource_id
            WHERE u.user_id = #{userId} AND u.material_id = #{materialId}
              AND r.status = 'PUBLISHED' AND r.document_url IS NOT NULL AND TRIM(r.document_url) <> ''
            ORDER BY r.id ASC LIMIT 1
            """)
    StudyResource findPublishedSourceDocument(@Param("userId") Long userId, @Param("materialId") Long materialId);

    @Select("SELECT source_text FROM study_material WHERE id = #{materialId} AND user_id = #{userId} AND deleted = 0")
    String findOwnedSourceText(@Param("userId") Long userId, @Param("materialId") Long materialId);

    @Select("SELECT id, user_id, cover_url FROM study_material WHERE id = #{id} AND user_id = #{userId} AND deleted = 0 FOR UPDATE")
    StudyMaterial lockOwnedCover(@Param("userId") Long userId, @Param("id") Long id);

    @Update("UPDATE study_material SET cover_url = #{coverKey}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int updateCover(@Param("userId") Long userId, @Param("id") Long id, @Param("coverKey") String coverKey);
}
