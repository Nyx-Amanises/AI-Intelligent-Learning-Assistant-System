package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.material.MaterialPageQuery;
import com.aiassistant.learning.dto.material.MaterialRenameRequest;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学习资料管理接口。
 *
 * <p>这个 Controller 负责资料的新增、上传、分页查询、详情、重命名、解析和删除。
 * 每个接口都会先从 {@link UserContext} 获取当前登录用户 ID，确保用户只能操作自己的资料。</p>
 */
@Validated
@RestController
@RequestMapping("/api/material")
public class StudyMaterialController {

    private final StudyMaterialService studyMaterialService;

    /**
     * 构造方法注入学习资料服务。
     *
     * @param studyMaterialService 学习资料业务服务
     */
    public StudyMaterialController(StudyMaterialService studyMaterialService) {
        this.studyMaterialService = studyMaterialService;
    }

    /**
     * 手动创建文本资料。
     *
     * <p>适合用户直接粘贴一段学习内容。创建成功后会立即生成一个资料分段。</p>
     *
     * @param request 前端提交的文本资料信息
     * @return 新创建资料的 ID
     */
    @PostMapping("/text")
    public ApiResponse<Long> createTextMaterial(@Valid @RequestBody MaterialCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("新增资料成功", studyMaterialService.createTextMaterial(userId, request));
    }

    /**
     * 上传文件资料。
     *
     * <p>当前支持 PDF、DOCX、TXT。上传后只保存文件和资料记录，
     * 真正解析内容需要调用 parse 接口。</p>
     *
     * @param file 前端 multipart/form-data 表单中的文件
     * @return 新创建资料的 ID
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> uploadMaterial(MultipartFile file) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("上传资料成功", studyMaterialService.uploadMaterial(userId, file));
    }

    /**
     * 分页查询当前用户的资料列表。
     *
     * @param query 分页条件和筛选条件
     * @return 资料分页结果
     */
    @GetMapping("/page")
    public ApiResponse<PageVO<MaterialPageVO>> page(MaterialPageQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(studyMaterialService.pageMaterials(userId, query));
    }

    /**
     * 查询资料详情。
     *
     * @param id 资料 ID
     * @return 资料详情和资料分段列表
     */
    @GetMapping("/{id}")
    public ApiResponse<MaterialDetailVO> detail(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(studyMaterialService.getMaterialDetail(userId, id));
    }

    /**
     * 修改资料标题。
     *
     * @param id 资料 ID
     * @param request 新标题请求参数
     * @return 修改成功提示
     */
    @PutMapping("/{id}/title")
    public ApiResponse<Void> rename(@PathVariable Long id, @Valid @RequestBody MaterialRenameRequest request) {
        Long userId = UserContext.getCurrentUserId();
        studyMaterialService.renameMaterial(userId, id, request.getTitle());
        return ApiResponse.success("资料名称已更新", null);
    }

    /**
     * 解析上传的资料文件。
     *
     * <p>解析会读取文件内容并切分成多个片段，为后续总结、检索和向量化做准备。</p>
     *
     * @param id 资料 ID
     * @return 解析成功提示
     */
    @PostMapping("/{id}/parse")
    public ApiResponse<Void> parse(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        studyMaterialService.parseMaterial(userId, id);
        return ApiResponse.success("资料解析成功", null);
    }

    /**
     * 删除资料。
     *
     * <p>删除时会同步清理该资料的文本分段和向量库中的相关向量。</p>
     *
     * @param id 资料 ID
     * @return 删除成功提示
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        studyMaterialService.deleteMaterial(userId, id);
        return ApiResponse.success("删除资料成功", null);
    }
}
