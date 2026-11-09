package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.security.UserRoles;
import com.aiassistant.learning.dto.mastery.KnowledgeMasteryQuery;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.resource.ResourceAdminPageQuery;
import com.aiassistant.learning.dto.resource.ResourcePageQuery;
import com.aiassistant.learning.dto.resource.ResourceStatusRequest;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.entity.StudyResource;
import com.aiassistant.learning.entity.StudyResourceUser;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.StudyResourceMapper;
import com.aiassistant.learning.mapper.StudyResourceUserMapper;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.KnowledgeMasteryService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.StudyResourceService;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryItemVO;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryOverviewVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.resource.ResourceVO;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyResourceServiceImpl implements StudyResourceService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final Set<String> STATUSES = Set.of(PUBLISHED, "WITHDRAWN", "DRAFT");
    private static final Set<String> RESOURCE_KINDS = Set.of("TEXTBOOK", "COURSE_PACK", "REVIEW_PACK", "KNOWLEDGE_CARD");
    private static final String OPEN_RESOURCE = "OPEN_RESOURCE";
    private static final String CATALOG_ONLY = "CATALOG_ONLY";
    private static final Set<String> ACCESS_MODES = Set.of(OPEN_RESOURCE, CATALOG_ONLY);
    // INTERNAL describes hosting; the licence separately records the actual rights in the work.
    private static final Set<String> HOSTED_LICENSES = Set.of("INTERNAL", "CC-BY-4.0", "CC-BY-SA-4.0",
            "CC-BY-NC-SA-4.0", "CC-BY-NC-3.0", "CC-BY-SA-3.0-US", "GFDL-1.2-or-later", "GFDL-1.3-or-later");
    private final StudyResourceMapper resourceMapper;
    private final StudyResourceUserMapper userResourceMapper;
    private final StudyMaterialService materialService;
    private final KnowledgeMasteryService masteryService;
    private final SysUserMapper userMapper;

    @Override
    public PageVO<ResourceVO> page(Long userId, ResourcePageQuery query) {
        requireUser(userId);
        validatePage(query);
        return queryPage(userId, query, PUBLISHED, query.getSubject(), Boolean.TRUE.equals(query.getBookmarkedOnly()), false);
    }

    @Override
    public ResourceVO detail(Long userId, Long resourceId) {
        requireUser(userId);
        requireId(resourceId);
        StudyResource resource = requirePublished(resourceMapper.findPublishedById(resourceId));
        return toVO(resource, visibleLinks(userId, List.of(resource)).get(resourceId), true, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bookmark(Long userId, Long resourceId, boolean bookmarked) {
        requireUser(userId);
        requireId(resourceId);
        if (bookmarked) {
            // Use the same resource lock as publication/import so withdrawal cannot race with adding a bookmark.
            requirePublished(resourceMapper.lockById(resourceId));
            userResourceMapper.ensureLink(userId, resourceId);
        }
        // Unbookmarking remains possible after withdrawal and does not reveal resource metadata.
        userResourceMapper.setBookmarked(userId, resourceId, bookmarked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importMaterial(Long userId, Long resourceId) {
        requireUser(userId);
        requireId(resourceId);
        StudyResource resource = requirePublished(resourceMapper.lockById(resourceId));
        if (!isImportable(resource)) {
            throw new BusinessException("该资源仅支持前往原站学习，不能导入");
        }
        validatePublication(resource);
        // The transaction, row locks and unique (user_id, resource_id) key make concurrent retries idempotent.
        userResourceMapper.ensureLink(userId, resourceId);
        StudyResourceUser link = userResourceMapper.lockLink(userId, resourceId);
        if (link == null || !userId.equals(link.getUserId()) || !resourceId.equals(link.getResourceId())) {
            throw new BusinessException(409, "导入记录暂时不可用，请重试");
        }
        if (link.getMaterialId() != null) {
            StudyMaterial existing = materialService.getById(link.getMaterialId());
            if (existing != null && userId.equals(existing.getUserId()) && Integer.valueOf(0).equals(existing.getDeleted())) {
                return existing.getId();
            }
        }
        String attribution = attribution(resource);
        MaterialCreateRequest request = new MaterialCreateRequest();
        request.setTitle(resource.getTitle());
        request.setMaterialType("TEXT");
        request.setTags(truncate(resource.getKnowledgePoints(), 255));
        request.setDifficultyLevel(resource.getDifficultyLevel());
        request.setDescription(truncate("来源：" + resource.getSourceName() + "；作者：" + resource.getAuthor()
                + "；授权：" + resource.getLicenseCode()
                + (resource.getSourceUrl() == null ? "" : "；原文：" + resource.getSourceUrl()), 500));
        // Keep attribution out of teaching chunks: RAG, summaries and quizzes should only use the lesson body.
        request.setContentText(resource.getContentText());
        Long materialId = materialService.createTextMaterial(userId, request);
        if (userResourceMapper.saveAttribution(userId, materialId, attribution) != 1) {
            throw new BusinessException(409, "资源来源保存失败，请重试");
        }
        if (userResourceMapper.setMaterialId(userId, resourceId, materialId) != 1) {
            throw new BusinessException(409, "导入记录保存失败，请重试");
        }
        return materialId;
    }

    @Override
    public List<ResourceVO> recommendations(Long userId, String knowledgePoint, int limit) {
        requireUser(userId);
        String explicitPoint = optionalText(knowledgePoint, 100, "知识点");
        if (limit < 1 || limit > 12) throw new BusinessException("推荐数量应为1至12");
        boolean personalized = explicitPoint == null;
        List<String> terms;
        if (explicitPoint != null) {
            terms = List.of(explicitPoint);
        } else {
            KnowledgeMasteryOverviewVO overview = masteryService.overview(userId, new KnowledgeMasteryQuery());
            List<KnowledgeMasteryItemVO> points = overview == null || overview.getWeakestPoints() == null
                    ? List.of() : overview.getWeakestPoints();
            terms = points.stream().filter(Objects::nonNull)
                    .filter(point -> Boolean.TRUE.equals(point.getAssessmentEligible()))
                    .filter(point -> point.getMasteryPercent() != null && point.getMasteryPercent() < 70)
                    .map(KnowledgeMasteryItemVO::getKnowledgePoint).filter(Objects::nonNull).map(String::strip)
                    .filter(point -> !point.isEmpty() && point.length() <= 100).distinct().limit(5).toList();
        }
        List<StudyResource> resources = resourceMapper.findRecommendations(terms, limit);
        if (resources.isEmpty() && !terms.isEmpty() && personalized) {
            resources = resourceMapper.findRecommendations(List.of(), limit);
            terms = List.of();
        }
        Map<Long, StudyResourceUser> links = visibleLinks(userId, resources);
        List<String> matchingTerms = terms;
        return resources.stream().map(resource -> {
            String matched = matchingTerms.stream().filter(term -> matches(resource, term)).findFirst().orElse(null);
            String reason = matched == null ? "精选资源" : (personalized ? "薄弱知识点：" : "知识点：") + matched;
            return toVO(resource, links.get(resource.getId()), false, reason);
        }).toList();
    }

    @Override
    public PageVO<ResourceVO> adminPage(Long userId, ResourceAdminPageQuery query) {
        requireAdmin(userId);
        validatePage(query);
        if (query.getStatus() != null) requireStatus(query.getStatus());
        return queryPage(userId, query, query.getStatus(), null, false, true);
    }

    @Override
    public ResourceVO adminDetail(Long userId, Long resourceId) {
        requireAdmin(userId);
        requireId(resourceId);
        StudyResource resource = resourceMapper.findById(resourceId);
        if (resource == null) throw new BusinessException(404, "资源不存在");
        ResourceVO detail = toVO(resource, visibleLinks(userId, List.of(resource)).get(resourceId), true, null);
        // Reviewers must also see an invalid external body so it can be removed before publication.
        detail.setContentText(resource.getContentText());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Long resourceId, ResourceStatusRequest request) {
        requireAdmin(userId);
        requireId(resourceId);
        if (request == null) throw new BusinessException("缺少审核信息");
        requireStatus(request.getStatus());
        String note = optionalText(request.getReviewNote(), 1000, "审核备注");
        StudyResource resource = resourceMapper.lockById(resourceId);
        if (resource == null) throw new BusinessException(404, "资源不存在");
        if (PUBLISHED.equals(request.getStatus())) validatePublication(resource);
        if (resourceMapper.updateStatus(resourceId, request.getStatus(), note, userId) != 1) {
            throw new BusinessException(409, "资源状态更新失败，请重试");
        }
    }

    private PageVO<ResourceVO> queryPage(Long userId, ResourcePageQuery query, String status,
            String subject, boolean bookmarkedOnly, boolean admin) {
        long size = query.getSize();
        long total = resourceMapper.countPage(userId, status, query.getKeyword(), subject, bookmarkedOnly,
                query.getResourceKind(), query.getAccessMode());
        List<StudyResource> resources = resourceMapper.findPage(userId, status, query.getKeyword(), subject,
                bookmarkedOnly, (query.getCurrent() - 1) * size, size, admin, query.getResourceKind(), query.getAccessMode());
        Map<Long, StudyResourceUser> links = visibleLinks(userId, resources);
        return PageVO.<ResourceVO>builder().current(query.getCurrent()).size(size).total(total)
                .pages(total / size + (total % size == 0 ? 0 : 1))
                .records(resources.stream().map(resource -> toVO(resource, links.get(resource.getId()), false, null)).toList())
                .build();
    }

    private Map<Long, StudyResourceUser> visibleLinks(Long userId, List<StudyResource> resources) {
        if (resources.isEmpty()) return Map.of();
        return userResourceMapper.findVisibleLinks(userId, resources.stream().map(StudyResource::getId).toList())
                .stream().filter(link -> userId.equals(link.getUserId()))
                .collect(Collectors.toMap(StudyResourceUser::getResourceId, Function.identity()));
    }

    private ResourceVO toVO(StudyResource resource, StudyResourceUser link, boolean detail, String reason) {
        return ResourceVO.builder().id(resource.getId()).title(resource.getTitle()).summary(resource.getSummary())
                .subject(resource.getSubject()).knowledgePoints(splitPoints(resource.getKnowledgePoints()))
                .difficultyLevel(resource.getDifficultyLevel()).estimatedMinutes(resource.getEstimatedMinutes())
                .resourceType(resource.getResourceType()).sourceName(resource.getSourceName()).sourceUrl(resource.getSourceUrl())
                .resourceKind(resource.getResourceKind()).languageCode(resource.getLanguageCode())
                .accessMode(accessMode(resource)).isbn(resource.getIsbn()).publisher(resource.getPublisher())
                .educationLevel(resource.getEducationLevel()).publicationYear(resource.getPublicationYear())
                .coverageNote(resource.getCoverageNote()).downloadUrl(resource.getDownloadUrl()).coverUrl(resource.getCoverUrl())
                .documentUrl(resource.getDocumentUrl()).pageCount(resource.getPageCount())
                .author(resource.getAuthor()).licenseCode(resource.getLicenseCode()).licenseUrl(resource.getLicenseUrl())
                .licenseNote(resource.getLicenseNote()).status(resource.getStatus())
                .contentText(detail && "INTERNAL".equals(resource.getResourceType()) ? resource.getContentText() : null)
                .bookmarked(link != null && Boolean.TRUE.equals(link.getBookmarked()))
                .materialId(link == null ? null : link.getMaterialId()).recommendationReason(reason).build();
    }

    private static List<String> splitPoints(String points) {
        return points == null ? List.of() : Arrays.stream(points.split(",")).map(String::strip)
                .filter(point -> !point.isEmpty()).distinct().toList();
    }

    private static boolean matches(StudyResource resource, String term) {
        String search = term.toLowerCase(Locale.ROOT);
        return (resource.getTitle() != null && resource.getTitle().toLowerCase(Locale.ROOT).contains(search))
                || (resource.getKnowledgePoints() != null && resource.getKnowledgePoints().toLowerCase(Locale.ROOT).contains(search));
    }

    private void requireAdmin(Long userId) {
        requireUser(userId);
        SysUser user = userMapper.selectById(userId);
        if (!UserRoles.isAdmin(user) || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(403, "仅管理员可以审核资源");
        }
    }

    private static void requireUser(Long userId) {
        if (userId == null || userId < 1) throw new BusinessException(401, "请先登录");
    }

    private static void requireId(Long resourceId) {
        if (resourceId == null || resourceId < 1) throw new BusinessException("资源编号不正确");
    }

    private static StudyResource requirePublished(StudyResource resource) {
        if (resource == null || !PUBLISHED.equals(resource.getStatus())) throw new BusinessException(404, "资源不存在或已下架");
        return resource;
    }

    private static void requireStatus(String status) {
        if (status == null || !STATUSES.contains(status)) throw new BusinessException("资源状态不正确");
    }

    private static void validatePage(ResourcePageQuery query) {
        if (query == null || query.getCurrent() == null || query.getCurrent() < 1 || query.getCurrent() > 1000000
                || query.getSize() == null || query.getSize() < 1 || query.getSize() > 50 || query.getBookmarkedOnly() == null) {
            throw new BusinessException("分页参数不正确");
        }
        query.setKeyword(normalizeIsbnKeyword(optionalText(query.getKeyword(), 100, "关键词")));
        query.setSubject(optionalText(query.getSubject(), 100, "学科"));
        if (query.getResourceKind() != null) requireResourceKind(query.getResourceKind());
        if (query.getAccessMode() != null) requireAccessMode(query.getAccessMode());
    }

    private static String normalizeIsbnKeyword(String keyword) {
        if (keyword == null || !keyword.matches("[0-9\\s-]+")) return keyword;
        String compact = keyword.replaceAll("[\\s-]", "");
        return compact.matches("97[89][0-9]{10}") ? compact : keyword;
    }

    private static void requireResourceKind(String kind) {
        if (kind == null || !RESOURCE_KINDS.contains(kind)) throw new BusinessException("资源类别不正确");
    }

    private static void requireAccessMode(String mode) {
        if (!ACCESS_MODES.contains(mode)) throw new BusinessException("资源访问方式不正确");
    }

    private static String accessMode(StudyResource resource) {
        return resource.getAccessMode() == null ? OPEN_RESOURCE : resource.getAccessMode();
    }

    private static String optionalText(String text, int maxLength, String label) {
        if (text == null) return null;
        if (text.length() > maxLength) throw new BusinessException(label + "长度不能超过" + maxLength + "个字符");
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static void requiredText(String text, int maxLength, String label) {
        if (optionalText(text, maxLength, label) == null) throw new BusinessException("发布前请补全" + label);
    }

    private static void validatePublication(StudyResource resource) {
        requireResourceKind(resource.getResourceKind());
        requireAccessMode(accessMode(resource));
        validateCatalogMetadata(resource);
        if (!"KNOWLEDGE_CARD".equals(resource.getResourceKind())) {
            requiredText(resource.getLanguageCode(), 35, "资料语言");
            requiredText(resource.getCoverageNote(), 1000, "课程覆盖范围");
        }
        String language = optionalText(resource.getLanguageCode(), 35, "资料语言");
        if (language != null && !language.matches("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")) {
            throw new BusinessException("资料语言请使用 zh-CN、en 等语言代码");
        }
        optionalText(resource.getCoverageNote(), 1000, "课程覆盖范围");
        requiredText(resource.getTitle(), 200, "标题");
        requiredText(resource.getSummary(), 500, "简介");
        requiredText(resource.getSubject(), 100, "学科");
        if (CATALOG_ONLY.equals(accessMode(resource))) {
            // Publisher catalogue categories do not establish the book's internal knowledge points.
            optionalText(resource.getKnowledgePoints(), 500, "知识点");
        } else {
            requiredText(resource.getKnowledgePoints(), 500, "知识点");
            if (splitPoints(resource.getKnowledgePoints()).isEmpty()) throw new BusinessException("发布前请补全知识点");
        }
        requiredText(resource.getSourceName(), 200, "来源");
        requiredText(resource.getAuthor(), 200, "作者");
        requiredText(resource.getLicenseNote(), 1000, "使用说明");
        int minimumMinutes = "KNOWLEDGE_CARD".equals(resource.getResourceKind()) ? 1 : 0;
        if (resource.getDifficultyLevel() == null || resource.getDifficultyLevel() < 1 || resource.getDifficultyLevel() > 5
                || resource.getEstimatedMinutes() == null || resource.getEstimatedMinutes() < minimumMinutes
                || resource.getEstimatedMinutes() > 10080) {
            throw new BusinessException("发布前请填写有效的难度和预计学习时长");
        }
        if (isImportable(resource)) {
            // Complete hosted textbooks may exceed a million characters. Keep
            // the existing limit for other resources and validate the PDF path below.
            requiredText(resource.getContentText(), resource.getDocumentUrl() == null ? 1000000 : 5000000, "资料正文");
            if (!"INTERNAL".equals(resource.getLicenseCode())) {
                requiredText(resource.getSourceUrl(), 1000, "原作来源链接");
                requiredText(resource.getLicenseUrl(), 1000, "开放许可依据链接");
            }
        } else if ("EXTERNAL".equals(resource.getResourceType()) && "LINK_ONLY".equals(resource.getLicenseCode())) {
            requiredText(resource.getSourceUrl(), 1000, "原站链接");
            if (resource.getContentText() != null && !resource.getContentText().isBlank()) {
                throw new BusinessException("仅链接资源不能收录原站正文");
            }
        } else {
            throw new BusinessException("资源类型与授权不匹配，请使用自编资料、已核验开放许可的本站资料或原站链接");
        }
        validateHostedDocument(resource);
        if (resource.getSourceUrl() != null && !resource.getSourceUrl().isBlank()) validateHttpsUrl(resource.getSourceUrl());
        if (resource.getLicenseUrl() != null && !resource.getLicenseUrl().isBlank()) validateHttpsUrl(resource.getLicenseUrl());
        if (resource.getDownloadUrl() != null && !resource.getDownloadUrl().isBlank()) validateHttpsUrl(resource.getDownloadUrl());
    }

    private static void validateCatalogMetadata(StudyResource resource) {
        optionalText(resource.getPublisher(), 200, "出版社");
        optionalText(resource.getEducationLevel(), 40, "教育层次");
        if (resource.getPublicationYear() != null
                && (resource.getPublicationYear() < 1 || resource.getPublicationYear() > 9999)) {
            throw new BusinessException("出版年不正确，未知时请留空");
        }
        String isbn = resource.getIsbn();
        if (isbn != null && !isbn.isBlank()) {
            if (!isbn.matches("97[89][0-9]{10}")) throw new BusinessException("ISBN 必须是有效的13位 ISBN");
            int checksum = 0;
            for (int index = 0; index < isbn.length(); index++) {
                checksum += (isbn.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
            }
            if (checksum % 10 != 0) throw new BusinessException("ISBN 校验位不正确");
        }
        if (!CATALOG_ONLY.equals(accessMode(resource))) return;
        if (!"EXTERNAL".equals(resource.getResourceType()) || !"LINK_ONLY".equals(resource.getLicenseCode())
                || !"TEXTBOOK".equals(resource.getResourceKind())) {
            throw new BusinessException("教材书目仅支持外部教材的官方页面链接");
        }
        if (resource.getDownloadUrl() != null && !resource.getDownloadUrl().isBlank()
                || resource.getContentText() != null && !resource.getContentText().isBlank()) {
            throw new BusinessException("教材书目不能收录正文或下载链接");
        }
    }

    private static boolean isImportable(StudyResource resource) {
        return "INTERNAL".equals(resource.getResourceType()) && resource.getLicenseCode() != null
                && HOSTED_LICENSES.contains(resource.getLicenseCode());
    }

    private static void validateHostedDocument(StudyResource resource) {
        if (resource.getPageCount() != null && resource.getPageCount() < 1) {
            throw new BusinessException("教材页数必须为正数，未知时请留空");
        }
        String documentUrl = optionalText(resource.getDocumentUrl(), 255, "本站教材路径");
        if (documentUrl == null) return;
        if (!isImportable(resource) || !OPEN_RESOURCE.equals(accessMode(resource))
                || !documentUrl.equals(resource.getDocumentUrl())
                || !documentUrl.matches("^/open-books/[a-z0-9-]+/book\\.pdf$")) {
            throw new BusinessException("本站教材仅支持已核验许可的完整 PDF 路径");
        }
    }

    /** Validate links without resolving DNS or fetching third-party content. Literal/private/local hosts are disallowed. */
    private static void validateHttpsUrl(String value) {
        if (value.length() > 1000) throw new BusinessException("资源链接过长");
        try {
            URI uri = new URI(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean publicDomain = host.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
                    && !host.matches(".*\\.(localhost|local|internal|lan|home|test|invalid|example)$");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || !publicDomain
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new BusinessException("资源链接必须是公开网站的 HTTPS 地址");
            }
        } catch (URISyntaxException exception) {
            throw new BusinessException("资源链接格式不正确");
        }
    }

    private static String attribution(StudyResource resource) {
        StringBuilder text = new StringBuilder("来源：").append(resource.getSourceName())
                .append("\n作者：").append(resource.getAuthor())
                .append("\n授权：").append(resource.getLicenseCode())
                .append("\n资源类别：").append(resource.getResourceKind())
                .append("\n使用说明：").append(resource.getLicenseNote());
        if (resource.getLanguageCode() != null && !resource.getLanguageCode().isBlank()) text.append("\n资料语言：").append(resource.getLanguageCode());
        if (resource.getCoverageNote() != null && !resource.getCoverageNote().isBlank()) text.append("\n覆盖范围：").append(resource.getCoverageNote());
        if (resource.getDownloadUrl() != null && !resource.getDownloadUrl().isBlank()) text.append("\n官方下载：").append(resource.getDownloadUrl());
        if (resource.getDocumentUrl() != null && !resource.getDocumentUrl().isBlank()) text.append("\n本站原文件：").append(resource.getDocumentUrl());
        if (resource.getSourceUrl() != null && !resource.getSourceUrl().isBlank()) text.append("\n原文链接：").append(resource.getSourceUrl());
        if (resource.getLicenseUrl() != null && !resource.getLicenseUrl().isBlank()) text.append("\n授权链接：").append(resource.getLicenseUrl());
        return text.toString();
    }

    private static String truncate(String value, int length) {
        if (value == null || value.length() <= length) return value;
        int end = Character.isHighSurrogate(value.charAt(length - 1)) ? length - 1 : length;
        return value.substring(0, end);
    }
}
