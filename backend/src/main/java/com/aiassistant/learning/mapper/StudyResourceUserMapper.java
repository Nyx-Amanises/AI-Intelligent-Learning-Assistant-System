package com.aiassistant.learning.mapper;

import com.aiassistant.learning.entity.StudyResourceUser;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface StudyResourceUserMapper {
    // A stale/corrupt association must never disclose a deleted material or another user's material ID.
    @Select("""
            <script>
            SELECT u.id, u.user_id, u.resource_id, u.bookmarked, m.id AS material_id
            FROM study_resource_user u
            LEFT JOIN study_material m ON m.id = u.material_id AND m.user_id = #{userId} AND m.deleted = 0
            WHERE u.user_id = #{userId} AND u.resource_id IN
              <foreach collection="resourceIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
            </script>
            """)
    List<StudyResourceUser> findVisibleLinks(@Param("userId") Long userId,
            @Param("resourceIds") List<Long> resourceIds);

    @Insert("""
            INSERT INTO study_resource_user (user_id, resource_id, bookmarked)
            VALUES (#{userId}, #{resourceId}, FALSE)
            ON DUPLICATE KEY UPDATE id = id
            """)
    int ensureLink(@Param("userId") Long userId, @Param("resourceId") Long resourceId);

    @Select("""
            SELECT * FROM study_resource_user
            WHERE user_id = #{userId} AND resource_id = #{resourceId} FOR UPDATE
            """)
    StudyResourceUser lockLink(@Param("userId") Long userId, @Param("resourceId") Long resourceId);

    @Update("""
            UPDATE study_resource_user SET bookmarked = #{bookmarked}, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND resource_id = #{resourceId}
            """)
    int setBookmarked(@Param("userId") Long userId, @Param("resourceId") Long resourceId,
            @Param("bookmarked") boolean bookmarked);

    @Update("""
            UPDATE study_resource_user SET material_id = #{materialId}, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND resource_id = #{resourceId}
            """)
    int setMaterialId(@Param("userId") Long userId, @Param("resourceId") Long resourceId,
            @Param("materialId") Long materialId);

    @Update("""
            UPDATE study_material SET resource_attribution = #{attribution}
            WHERE id = #{materialId} AND user_id = #{userId} AND deleted = 0
            """)
    int saveAttribution(@Param("userId") Long userId, @Param("materialId") Long materialId,
            @Param("attribution") String attribution);
}
