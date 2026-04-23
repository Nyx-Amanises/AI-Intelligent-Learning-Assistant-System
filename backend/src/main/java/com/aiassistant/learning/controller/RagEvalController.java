package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.rag.RagEvalDatasetCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalDatasetPageQuery;
import com.aiassistant.learning.dto.rag.RagEvalRunRequest;
import com.aiassistant.learning.dto.rag.RagEvalSampleBatchCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalSampleUpdateRequest;
import com.aiassistant.learning.service.RagEvalService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.rag.CmrcImportResultVO;
import com.aiassistant.learning.vo.rag.RagEvalDatasetVO;
import com.aiassistant.learning.vo.rag.RagEvalRunVO;
import com.aiassistant.learning.vo.rag.RagEvalSampleVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/rag/eval")
public class RagEvalController {

    /** RAG 检索评测业务服务。 */
    private final RagEvalService ragEvalService;

    public RagEvalController(RagEvalService ragEvalService) {
        this.ragEvalService = ragEvalService;
    }

    /**
     * 导入 CMRC2018 阅读理解数据，并自动生成资料、资料分段和 RAG 评测样本。
     */
    @PostMapping(value = "/cmrc2018/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CmrcImportResultVO> importCmrc2018(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String materialTitle,
            @RequestParam(required = false) String datasetName,
            @RequestParam(required = false) String splitName,
            @RequestParam(required = false) Integer maxSamples,
            @RequestParam(required = false, defaultValue = "false") Boolean submitEmbeddingTask
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("CMRC2018 数据集已导入", ragEvalService.importCmrc2018(
                userId,
                file,
                materialTitle,
                datasetName,
                splitName,
                maxSamples,
                submitEmbeddingTask
        ));
    }

    /**
     * 新建一个评测集。
     */
    @PostMapping("/datasets")
    public ApiResponse<RagEvalDatasetVO> createDataset(@Valid @RequestBody RagEvalDatasetCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("评测集已创建", ragEvalService.createDataset(userId, request));
    }

    /**
     * 分页查询当前用户的评测集。
     */
    @GetMapping("/datasets")
    public ApiResponse<PageVO<RagEvalDatasetVO>> pageDatasets(@Valid RagEvalDatasetPageQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(ragEvalService.pageDatasets(userId, query));
    }

    /**
     * 查询单个评测集详情。
     */
    @GetMapping("/datasets/{datasetId}")
    public ApiResponse<RagEvalDatasetVO> getDataset(@PathVariable Long datasetId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(ragEvalService.getDataset(userId, datasetId));
    }

    /**
     * 删除评测集以及它下面的样本、运行记录和运行明细。
     */
    @DeleteMapping("/datasets/{datasetId}")
    public ApiResponse<Void> deleteDataset(@PathVariable Long datasetId) {
        Long userId = UserContext.getCurrentUserId();
        ragEvalService.deleteDataset(userId, datasetId);
        return ApiResponse.success("评测集已删除", null);
    }

    /**
     * 批量添加评测样本。
     */
    @PostMapping("/datasets/{datasetId}/samples")
    public ApiResponse<List<RagEvalSampleVO>> addSamples(
            @PathVariable Long datasetId,
            @Valid @RequestBody RagEvalSampleBatchCreateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("评测样本已添加", ragEvalService.addSamples(userId, datasetId, request));
    }

    /**
     * 查询某个评测集下的全部样本。
     */
    @GetMapping("/datasets/{datasetId}/samples")
    public ApiResponse<List<RagEvalSampleVO>> listSamples(@PathVariable Long datasetId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(ragEvalService.listSamples(userId, datasetId));
    }

    /**
     * 更新评测样本的查询文本、标注答案或元信息。
     */
    @PutMapping("/datasets/{datasetId}/samples/{sampleId}")
    public ApiResponse<RagEvalSampleVO> updateSample(
            @PathVariable Long datasetId,
            @PathVariable Long sampleId,
            @Valid @RequestBody RagEvalSampleUpdateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("评测样本已更新", ragEvalService.updateSample(userId, datasetId, sampleId, request));
    }

    /**
     * 删除某条评测样本，并刷新评测集样本数。
     */
    @DeleteMapping("/datasets/{datasetId}/samples/{sampleId}")
    public ApiResponse<Void> deleteSample(@PathVariable Long datasetId, @PathVariable Long sampleId) {
        Long userId = UserContext.getCurrentUserId();
        ragEvalService.deleteSample(userId, datasetId, sampleId);
        return ApiResponse.success("评测样本已删除", null);
    }

    /**
     * 运行一次评测集，按样本逐条调用 RAG 检索并计算命中率、召回率、MRR 等指标。
     */
    @PostMapping("/datasets/{datasetId}/runs")
    public ApiResponse<RagEvalRunVO> runDataset(
            @PathVariable Long datasetId,
            @Valid @RequestBody(required = false) RagEvalRunRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        RagEvalRunRequest resolvedRequest = request == null ? new RagEvalRunRequest() : request;
        return ApiResponse.success("评测运行完成", ragEvalService.runDataset(userId, datasetId, resolvedRequest));
    }

    /**
     * 查询某次评测运行的汇总指标和样本级明细。
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<RagEvalRunVO> getRun(@PathVariable Long runId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(ragEvalService.getRun(userId, runId));
    }
}
