package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.material.MaterialPageQuery;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学习资料业务接口。
 *
 * <p>学习资料是系统中很多功能的基础：资料上传后可以解析成片段，
 * 再用于摘要生成、题目生成、RAG 检索和学习分析。</p>
 */
public interface StudyMaterialService extends IService<StudyMaterial> {

    /**
     * 手动创建文本资料。
     *
     * @param userId 当前登录用户 ID
     * @param request 文本资料请求参数
     * @return 新资料 ID
     */
    Long createTextMaterial(Long userId, MaterialCreateRequest request);

    /**
     * 上传文件资料。
     *
     * @param userId 当前登录用户 ID
     * @param file 前端上传的文件
     * @return 新资料 ID
     */
    Long uploadMaterial(Long userId, MultipartFile file);

    /**
     * 分页查询资料列表。
     *
     * @param userId 当前登录用户 ID
     * @param query 分页和筛选条件
     * @return 分页资料列表
     */
    PageVO<MaterialPageVO> pageMaterials(Long userId, MaterialPageQuery query);

    /**
     * 给 AI 助手使用的资料搜索。
     *
     * @param userId 当前登录用户 ID
     * @param keyword 搜索关键词
     * @param limit 最多返回条数
     * @return 匹配的资料列表
     */
    List<MaterialPageVO> searchAssistantMaterials(Long userId, String keyword, int limit);

    /**
     * 给 AI 助手使用的资料浏览。
     *
     * @param userId 当前登录用户 ID
     * @param keyword 可选关键词
     * @param limit 最多返回条数
     * @return 简化的分页资料结果
     */
    PageVO<MaterialPageVO> browseAssistantMaterials(Long userId, String keyword, int limit);

    /**
     * 给 AI 助手使用的资料浏览，可选择只返回已经向量化的资料。
     *
     * @param userId 当前登录用户 ID
     * @param keyword 可选关键词
     * @param limit 最多返回条数
     * @param embeddingReadyOnly 是否只返回已有向量片段的资料
     * @return 简化的分页资料结果
     */
    PageVO<MaterialPageVO> browseAssistantMaterials(Long userId, String keyword, int limit, boolean embeddingReadyOnly);

    /**
     * 查询资料详情。
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     * @return 资料详情
     */
    MaterialDetailVO getMaterialDetail(Long userId, Long materialId);

    /**
     * 修改资料标题。
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     * @param title 新标题
     */
    void renameMaterial(Long userId, Long materialId, String title);

    /**
     * 解析资料文件并生成文本分段。
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     */
    void parseMaterial(Long userId, Long materialId);

    /**
     * 删除资料及其分段、向量数据。
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     */
    void deleteMaterial(Long userId, Long materialId);
}
