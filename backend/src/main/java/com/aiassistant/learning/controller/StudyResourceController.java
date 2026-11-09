package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.common.security.RequireAdmin;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.resource.ResourceAdminPageQuery;
import com.aiassistant.learning.dto.resource.ResourceBookmarkRequest;
import com.aiassistant.learning.dto.resource.ResourcePageQuery;
import com.aiassistant.learning.dto.resource.ResourceStatusRequest;
import com.aiassistant.learning.service.StudyResourceService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.resource.ResourceImportVO;
import com.aiassistant.learning.vo.resource.ResourceVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class StudyResourceController {
    private final StudyResourceService resourceService;

    @GetMapping("/page")
    public ApiResponse<PageVO<ResourceVO>> page(@Valid ResourcePageQuery query) {
        return ApiResponse.success(resourceService.page(UserContext.getCurrentUserId(), query));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResourceVO> detail(@PathVariable @Min(1) Long id) {
        return ApiResponse.success(resourceService.detail(UserContext.getCurrentUserId(), id));
    }

    @PutMapping("/{id}/bookmark")
    public ApiResponse<Void> bookmark(@PathVariable @Min(1) Long id, @Valid @RequestBody ResourceBookmarkRequest request) {
        resourceService.bookmark(UserContext.getCurrentUserId(), id, request.getBookmarked());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/import")
    public ApiResponse<ResourceImportVO> importMaterial(@PathVariable @Min(1) Long id) {
        return ApiResponse.success(new ResourceImportVO(resourceService.importMaterial(UserContext.getCurrentUserId(), id)));
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<ResourceVO>> recommendations(
            @RequestParam(required = false) @Size(max = 100) String knowledgePoint,
            @RequestParam(defaultValue = "3") @Min(1) @Max(12) int limit) {
        return ApiResponse.success(resourceService.recommendations(UserContext.getCurrentUserId(), knowledgePoint, limit));
    }

    @RequireAdmin
    @GetMapping("/admin/page")
    public ApiResponse<PageVO<ResourceVO>> adminPage(@Valid ResourceAdminPageQuery query) {
        return ApiResponse.success(resourceService.adminPage(UserContext.getCurrentUserId(), query));
    }

    @RequireAdmin
    @GetMapping("/admin/{id}")
    public ApiResponse<ResourceVO> adminDetail(@PathVariable @Min(1) Long id) {
        return ApiResponse.success(resourceService.adminDetail(UserContext.getCurrentUserId(), id));
    }

    @RequireAdmin
    @PutMapping("/admin/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable @Min(1) Long id, @Valid @RequestBody ResourceStatusRequest request) {
        resourceService.updateStatus(UserContext.getCurrentUserId(), id, request);
        return ApiResponse.success(null);
    }
}
