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

@Validated
@RestController
@RequestMapping("/api/material")
public class StudyMaterialController {

    private final StudyMaterialService studyMaterialService;

    public StudyMaterialController(StudyMaterialService studyMaterialService) {
        this.studyMaterialService = studyMaterialService;
    }

    @PostMapping("/text")
    public ApiResponse<Long> createTextMaterial(@Valid @RequestBody MaterialCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("新增资料成功", studyMaterialService.createTextMaterial(userId, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> uploadMaterial(MultipartFile file) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("上传资料成功", studyMaterialService.uploadMaterial(userId, file));
    }

    @GetMapping("/page")
    public ApiResponse<PageVO<MaterialPageVO>> page(MaterialPageQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(studyMaterialService.pageMaterials(userId, query));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaterialDetailVO> detail(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(studyMaterialService.getMaterialDetail(userId, id));
    }

    @PutMapping("/{id}/title")
    public ApiResponse<Void> rename(@PathVariable Long id, @Valid @RequestBody MaterialRenameRequest request) {
        Long userId = UserContext.getCurrentUserId();
        studyMaterialService.renameMaterial(userId, id, request.getTitle());
        return ApiResponse.success("资料名称已更新", null);
    }

    @PostMapping("/{id}/parse")
    public ApiResponse<Void> parse(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        studyMaterialService.parseMaterial(userId, id);
        return ApiResponse.success("资料解析成功", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        studyMaterialService.deleteMaterial(userId, id);
        return ApiResponse.success("删除资料成功", null);
    }
}
