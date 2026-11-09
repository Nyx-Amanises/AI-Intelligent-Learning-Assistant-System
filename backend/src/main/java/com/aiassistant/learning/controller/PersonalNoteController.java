package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.note.*;
import com.aiassistant.learning.service.PersonalNoteService;
import com.aiassistant.learning.vo.note.NotebookVO;
import com.aiassistant.learning.vo.note.PersonalNoteVO;
import com.aiassistant.learning.vo.page.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class PersonalNoteController {
    private final PersonalNoteService service;

    @GetMapping("/page")
    public ApiResponse<PageVO<PersonalNoteVO>> page(@Valid PersonalNotePageQuery query) {
        return ApiResponse.success(service.page(UserContext.getCurrentUserId(), query));
    }
    @GetMapping("/{id}")
    public ApiResponse<PersonalNoteVO> detail(@PathVariable @Min(1) Long id) {
        return ApiResponse.success(service.detail(UserContext.getCurrentUserId(), id));
    }
    @PostMapping
    public ApiResponse<PersonalNoteVO> create(@Valid @RequestBody PersonalNoteCreateRequest request) {
        return ApiResponse.success(service.create(UserContext.getCurrentUserId(), request));
    }
    @PutMapping("/{id}")
    public ApiResponse<PersonalNoteVO> update(@PathVariable @Min(1) Long id, @Valid @RequestBody PersonalNoteUpdateRequest request) {
        return ApiResponse.success(service.update(UserContext.getCurrentUserId(), id, request));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Min(1) Long id) {
        service.delete(UserContext.getCurrentUserId(), id);
        return ApiResponse.success(null);
    }
    @GetMapping("/notebooks")
    public ApiResponse<List<NotebookVO>> notebooks() {
        return ApiResponse.success(service.notebooks(UserContext.getCurrentUserId()));
    }
    @PostMapping("/notebooks")
    public ApiResponse<NotebookVO> createNotebook(@Valid @RequestBody NotebookWriteRequest request) {
        return ApiResponse.success(service.createNotebook(UserContext.getCurrentUserId(), request));
    }
    @PutMapping("/notebooks/{id}")
    public ApiResponse<NotebookVO> updateNotebook(@PathVariable @Min(1) Long id, @Valid @RequestBody NotebookWriteRequest request) {
        return ApiResponse.success(service.updateNotebook(UserContext.getCurrentUserId(), id, request));
    }
    @DeleteMapping("/notebooks/{id}")
    public ApiResponse<Void> deleteNotebook(@PathVariable @Min(1) Long id) {
        service.deleteNotebook(UserContext.getCurrentUserId(), id);
        return ApiResponse.success(null);
    }
}
