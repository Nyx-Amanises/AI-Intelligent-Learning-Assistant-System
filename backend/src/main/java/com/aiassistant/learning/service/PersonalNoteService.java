package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.note.*;
import com.aiassistant.learning.vo.note.NotebookVO;
import com.aiassistant.learning.vo.note.PersonalNoteVO;
import com.aiassistant.learning.vo.page.PageVO;
import java.util.List;

public interface PersonalNoteService {
    PageVO<PersonalNoteVO> page(Long userId, PersonalNotePageQuery query);
    PersonalNoteVO detail(Long userId, Long id);
    PersonalNoteVO create(Long userId, PersonalNoteCreateRequest request);
    PersonalNoteVO update(Long userId, Long id, PersonalNoteUpdateRequest request);
    void delete(Long userId, Long id);
    List<NotebookVO> notebooks(Long userId);
    NotebookVO createNotebook(Long userId, NotebookWriteRequest request);
    NotebookVO updateNotebook(Long userId, Long id, NotebookWriteRequest request);
    void deleteNotebook(Long userId, Long id);
}
