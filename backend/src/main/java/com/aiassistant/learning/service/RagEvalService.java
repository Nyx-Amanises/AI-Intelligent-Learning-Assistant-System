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

/**
 * RAG 检索评测服务接口。
 *
 * <p>这里定义评测集管理、样本管理、评测运行和 CMRC2018 数据导入等能力。</p>
 */
public interface RagEvalService {

    /**
     * 创建一个新的评测集。
     */
    RagEvalDatasetVO createDataset(Long userId, RagEvalDatasetCreateRequest request);

    /**
     * 分页查询当前用户的评测集。
     */
    PageVO<RagEvalDatasetVO> pageDatasets(Long userId, RagEvalDatasetPageQuery query);

    /**
     * 查询评测集详情。
     */
    RagEvalDatasetVO getDataset(Long userId, Long datasetId);

    /**
     * 删除评测集，以及它下面的样本和运行记录。
     */
    void deleteDataset(Long userId, Long datasetId);

    /**
     * 批量添加评测样本。
     */
    List<RagEvalSampleVO> addSamples(Long userId, Long datasetId, RagEvalSampleBatchCreateRequest request);

    /**
     * 查询某个评测集下的所有样本。
     */
    List<RagEvalSampleVO> listSamples(Long userId, Long datasetId);

    /**
     * 更新单条评测样本。
     */
    RagEvalSampleVO updateSample(Long userId, Long datasetId, Long sampleId, RagEvalSampleUpdateRequest request);

    /**
     * 删除单条评测样本。
     */
    void deleteSample(Long userId, Long datasetId, Long sampleId);

    /**
     * 运行评测集，计算命中率、召回率和 MRR 等指标。
     */
    RagEvalRunVO runDataset(Long userId, Long datasetId, RagEvalRunRequest request);

    /**
     * 查询一次评测运行的汇总和明细。
     */
    RagEvalRunVO getRun(Long userId, Long runId);

    /**
     * 导入 CMRC2018 数据文件，并生成资料、分段和评测样本。
     */
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
