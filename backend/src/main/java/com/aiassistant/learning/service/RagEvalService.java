package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.rag.RagEvalDatasetCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalDatasetPageQuery;
import com.aiassistant.learning.dto.rag.RagEvalRunRequest;
import com.aiassistant.learning.dto.rag.RagEvalSampleBatchCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalSampleUpdateRequest;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.rag.RagEvalDatasetVO;
import com.aiassistant.learning.vo.rag.RagEvalRunVO;
import com.aiassistant.learning.vo.rag.RagEvalSampleVO;
import com.aiassistant.learning.vo.rag.CmrcImportResultVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface RagEvalService {

    RagEvalDatasetVO createDataset(Long userId, RagEvalDatasetCreateRequest request);

    PageVO<RagEvalDatasetVO> pageDatasets(Long userId, RagEvalDatasetPageQuery query);

    RagEvalDatasetVO getDataset(Long userId, Long datasetId);

    void deleteDataset(Long userId, Long datasetId);

    List<RagEvalSampleVO> addSamples(Long userId, Long datasetId, RagEvalSampleBatchCreateRequest request);

    List<RagEvalSampleVO> listSamples(Long userId, Long datasetId);

    RagEvalSampleVO updateSample(Long userId, Long datasetId, Long sampleId, RagEvalSampleUpdateRequest request);

    void deleteSample(Long userId, Long datasetId, Long sampleId);

    RagEvalRunVO runDataset(Long userId, Long datasetId, RagEvalRunRequest request);

    RagEvalRunVO getRun(Long userId, Long runId);

    CmrcImportResultVO importCmrc2018(
            Long userId,
            MultipartFile file,
            String materialTitle,
            String datasetName,
            String splitName,
            Integer maxSamples,
            Boolean submitEmbeddingTask
    );
}
