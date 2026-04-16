package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.material.MaterialPageQuery;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

public interface StudyMaterialService extends IService<StudyMaterial> {

    Long createTextMaterial(Long userId, MaterialCreateRequest request);

    Long uploadMaterial(Long userId, MultipartFile file);

    PageVO<MaterialPageVO> pageMaterials(Long userId, MaterialPageQuery query);

    MaterialDetailVO getMaterialDetail(Long userId, Long materialId);

    void parseMaterial(Long userId, Long materialId);

    void deleteMaterial(Long userId, Long materialId);
}
