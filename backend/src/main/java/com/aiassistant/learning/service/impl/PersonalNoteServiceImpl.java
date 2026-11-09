package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.note.*;
import com.aiassistant.learning.entity.NoteNotebook;
import com.aiassistant.learning.entity.PersonalNote;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.mapper.NoteNotebookMapper;
import com.aiassistant.learning.mapper.PersonalNoteMapper;
import com.aiassistant.learning.service.PersonalNoteService;
import com.aiassistant.learning.vo.note.NotebookVO;
import com.aiassistant.learning.vo.note.PersonalNoteVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonalNoteServiceImpl implements PersonalNoteService {
    private static final ObjectMapper DOCUMENT_JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(200000).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final PersonalNoteMapper notes;
    private final NoteNotebookMapper notebooks;
    private final Validator validator;

    @Override
    public PageVO<PersonalNoteVO> page(Long userId, PersonalNotePageQuery query) {
        requireUser(userId);
        validate(query);
        if (query.getKeyword() != null) query.setKeyword(query.getKeyword().strip());
        long total = notes.countPage(userId, query);
        long size = query.getPageSize();
        return PageVO.<PersonalNoteVO>builder().current(query.getPageNum()).size(size).total(total)
                .pages((total + size - 1) / size)
                .records(notes.findPage(userId, query, (query.getPageNum() - 1) * size, size)
                        .stream().map(PersonalNoteServiceImpl::toVO).toList()).build();
    }

    @Override
    public PersonalNoteVO detail(Long userId, Long id) {
        requireUser(userId);
        requireId(id);
        return toVO(requireNote(notes.findOwned(userId, id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PersonalNoteVO create(Long userId, PersonalNoteCreateRequest request) {
        requireUser(userId);
        validate(request);
        String clientKey = request.getClientKey().toLowerCase(Locale.ROOT);
        PersonalNote existing = notes.findByClientKey(userId, clientKey);
        if (existing != null) return existingCreate(existing);
        validateDocument(request.getContentJson());
        PersonalNote note = new PersonalNote();
        BeanUtils.copyProperties(request, note);
        note.setUserId(userId);
        note.setClientKey(clientKey);
        note.setTitle(title(request.getTitle()));
        note.setSourceType(request.getSourceType() == null ? "GENERAL" : request.getSourceType());
        note.setSourceTitle(Objects.requireNonNullElse(request.getSourceTitle(), ""));
        note.setSourceExcerpt(Objects.requireNonNullElse(request.getSourceExcerpt(), ""));
        note.setNotebookId(normalizeNotebook(request.getNotebookId()));
        validateSource(userId, note);
        lockNotebook(userId, note.getNotebookId());
        notes.insertIdempotently(note);
        // Current/locking read sees a winning concurrent insert even with MySQL repeatable-read snapshots.
        PersonalNote saved = notes.lockByClientKey(userId, clientKey);
        if (saved == null) throw new BusinessException(409, "笔记保存冲突，请重试");
        return existingCreate(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PersonalNoteVO update(Long userId, Long id, PersonalNoteUpdateRequest request) {
        requireUser(userId);
        requireId(id);
        validate(request);
        PersonalNote note = requireNote(notes.findOwned(userId, id));
        if (!Objects.equals(note.getVersion(), request.getVersion())) throw conflict();
        requireUnchanged(request.getSourceType(), note.getSourceType());
        requireUnchanged(request.getSourceId(), note.getSourceId());
        requireUnchanged(request.getPracticeSessionId(), note.getPracticeSessionId());
        requireUnchanged(request.getPageNumber(), note.getPageNumber());
        requireUnchanged(request.getSourceTitle(), note.getSourceTitle());
        requireUnchanged(request.getSourceExcerpt(), note.getSourceExcerpt());
        if ((request.getContentJson() == null) != (request.getContentText() == null))
            throw new BusinessException("请同时提交笔记正文与正文纯文本");
        if (request.getContentJson() != null) {
            validateDocument(request.getContentJson());
            note.setContentJson(request.getContentJson());
            note.setContentText(request.getContentText());
        }
        if (request.getTitle() != null) note.setTitle(title(request.getTitle()));
        if (request.getFavorite() != null) note.setFavorite(request.getFavorite());
        if (request.isNotebookIdProvided()) note.setNotebookId(normalizeNotebook(request.getNotebookId()));
        lockNotebook(userId, note.getNotebookId());
        // Source references are deliberately not revalidated: a deleted source must not lock a user's writing.
        if (notes.updateVersioned(note) != 1) throw conflict();
        return toVO(requireNote(notes.findOwned(userId, id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        requireUser(userId);
        requireId(id);
        if (notes.deleteOwned(userId, id) != 1) throw missingNote();
    }

    @Override
    public List<NotebookVO> notebooks(Long userId) {
        requireUser(userId);
        return notebooks.findAllOwned(userId).stream().map(PersonalNoteServiceImpl::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotebookVO createNotebook(Long userId, NotebookWriteRequest request) {
        requireUser(userId);
        validate(request);
        NoteNotebook notebook = new NoteNotebook();
        notebook.setUserId(userId);
        notebook.setName(request.getName().strip());
        notebook.setColor(request.getColor());
        notebooks.insert(notebook);
        return toVO(notebooks.findOwned(userId, notebook.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotebookVO updateNotebook(Long userId, Long id, NotebookWriteRequest request) {
        requireUser(userId);
        requireId(id);
        validate(request);
        NoteNotebook notebook = lockNotebook(userId, id);
        notebook.setName(request.getName().strip());
        notebook.setColor(request.getColor());
        if (notebooks.updateOwned(notebook) != 1) throw missingNotebook();
        return toVO(notebooks.findOwned(userId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotebook(Long userId, Long id) {
        requireUser(userId);
        requireId(id);
        lockNotebook(userId, id);
        notes.unfileNotebook(userId, id);
        if (notebooks.deleteOwned(userId, id) != 1) throw missingNotebook();
    }

    private void validateSource(Long userId, PersonalNote note) {
        String type = note.getSourceType();
        if ("GENERAL".equals(type)) {
            if (note.getSourceId() != null || note.getPracticeSessionId() != null || note.getPageNumber() != null)
                throw new BusinessException("普通笔记不能关联资料或练习位置");
            return;
        }
        requireId(note.getSourceId());
        if (!"QUESTION".equals(type) && note.getPracticeSessionId() != null)
            throw new BusinessException("练习会话只能关联题目笔记");
        switch (type) {
            case "MATERIAL" -> {
                if (notes.countOwnedMaterial(userId, note.getSourceId()) != 1) throw unavailableSource();
            }
            case "RESOURCE" -> {
                if (notes.countPublishedResource(note.getSourceId()) != 1) throw unavailableSource();
            }
            case "QUESTION" -> {
                Long setId = notes.findOwnedQuestionSet(userId, note.getSourceId());
                if (setId == null) throw unavailableSource();
                if (note.getPracticeSessionId() != null) {
                    PracticeSession session = notes.findOwnedSession(userId, note.getPracticeSessionId());
                    if (session == null || !setId.equals(session.getQuestionSetId())
                            || !includesQuestion(session.getQuestionIdsJson(), note.getSourceId()))
                        throw unavailableSource();
                }
            }
            default -> throw new BusinessException("笔记来源类型不正确");
        }
    }

    private static boolean includesQuestion(String selection, Long questionId) {
        if (selection == null) return true;
        try {
            JsonNode ids = DOCUMENT_JSON.readTree(selection);
            if (!ids.isArray()) return false;
            for (JsonNode id : ids) if (id.isIntegralNumber() && id.canConvertToLong() && id.longValue() == questionId) return true;
        } catch (Exception ignored) {
            // Malformed historical selections cannot grant access to a question outside this practice.
        }
        return false;
    }

    private NoteNotebook lockNotebook(Long userId, Long id) {
        if (id == null) return null;
        NoteNotebook notebook = notebooks.lockOwned(userId, id);
        if (notebook == null) throw missingNotebook();
        return notebook;
    }

    private static void validateDocument(String content) {
        try {
            JsonNode doc = DOCUMENT_JSON.readTree(content);
            if (doc == null || !doc.isObject() || !"doc".equals(doc.path("type").asText())
                    || (doc.has("content") && !doc.get("content").isArray()))
                throw new BusinessException("笔记正文格式不正确");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("笔记正文格式不正确或嵌套过深");
        }
    }

    private <T> void validate(T request) {
        if (request == null) throw new BusinessException("请求不能为空");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new BusinessException("笔记参数不正确：" + violations.iterator().next().getMessage());
    }

    private static Long normalizeNotebook(Long id) { return Long.valueOf(0).equals(id) ? null : id; }
    private static String title(String title) { return title == null || title.isBlank() ? "未命名笔记" : title.strip(); }
    private static void requireUnchanged(Object supplied, Object existing) {
        if (supplied != null && !Objects.equals(supplied, existing)) throw new BusinessException("笔记来源不能修改");
    }
    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException(401, "请先登录");
    }
    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new BusinessException("编号不正确");
    }
    private static PersonalNote requireNote(PersonalNote note) {
        if (note == null) throw missingNote();
        return note;
    }
    private static PersonalNoteVO existingCreate(PersonalNote note) {
        if (!Integer.valueOf(0).equals(note.getDeleted())) throw new BusinessException(409, "这条笔记已删除，请新建笔记");
        return toVO(note);
    }
    private static BusinessException missingNote() { return new BusinessException(404, "笔记不存在"); }
    private static BusinessException missingNotebook() { return new BusinessException(404, "笔记本不存在"); }
    private static BusinessException unavailableSource() { return new BusinessException(404, "笔记来源不可用"); }
    private static BusinessException conflict() { return new BusinessException(409, "笔记版本冲突，请重新加载后再保存"); }
    private static PersonalNoteVO toVO(PersonalNote note) {
        PersonalNoteVO result = new PersonalNoteVO();
        BeanUtils.copyProperties(note, result);
        return result;
    }
    private static NotebookVO toVO(NoteNotebook notebook) {
        NotebookVO result = new NotebookVO();
        BeanUtils.copyProperties(notebook, result);
        return result;
    }
}
