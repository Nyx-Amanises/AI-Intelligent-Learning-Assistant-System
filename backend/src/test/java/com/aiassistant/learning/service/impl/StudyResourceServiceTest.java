package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
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
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryItemVO;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryOverviewVO;
import com.aiassistant.learning.vo.resource.ResourceVO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class StudyResourceServiceTest {
    private final StudyResourceMapper resources = mock(StudyResourceMapper.class);
    private final StudyResourceUserMapper links = mock(StudyResourceUserMapper.class);
    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final KnowledgeMasteryService mastery = mock(KnowledgeMasteryService.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final StudyResourceServiceImpl service = new StudyResourceServiceImpl(resources, links, materials, mastery, users);
    private StudyResource resource;

    @BeforeEach
    void prepareResource() {
        resource = new StudyResource();
        resource.setId(11L);
        resource.setTitle("链表基础");
        resource.setSummary("学习链表与节点");
        resource.setSubject("计算机");
        resource.setKnowledgePoints("链表, 节点");
        resource.setDifficultyLevel(2);
        resource.setEstimatedMinutes(15);
        resource.setResourceType("INTERNAL");
        resource.setResourceKind("KNOWLEDGE_CARD");
        resource.setLanguageCode("zh-CN");
        resource.setCoverageNote("链表与节点基础");
        resource.setSourceName("学习助手自编资源");
        resource.setAuthor("学习助手内容团队");
        resource.setLicenseCode("INTERNAL");
        resource.setLicenseNote("仅供在本系统内个人学习使用，请保留来源。");
        resource.setContentText("每个节点包含数据与指向下一个节点的引用。");
        resource.setStatus("PUBLISHED");
    }

    @Test
    void publicPageCombinesPublishedStatusKeywordSubjectAndCurrentUserBookmarkFilter() {
        ResourcePageQuery query = new ResourcePageQuery();
        query.setKeyword(" 链表 ");
        query.setSubject(" 计算机 ");
        query.setBookmarkedOnly(true);
        query.setResourceKind("KNOWLEDGE_CARD");
        query.setAccessMode("OPEN_RESOURCE");
        when(resources.countPage(7L, "PUBLISHED", "链表", "计算机", true, "KNOWLEDGE_CARD", "OPEN_RESOURCE")).thenReturn(1L);
        when(resources.findPage(7L, "PUBLISHED", "链表", "计算机", true, 0, 12, false, "KNOWLEDGE_CARD", "OPEN_RESOURCE")).thenReturn(List.of(resource));
        StudyResourceUser link = link(7L, 11L, 99L);
        link.setBookmarked(true);
        when(links.findVisibleLinks(7L, List.of(11L))).thenReturn(List.of(link));

        var result = service.page(7L, query);

        assertEquals(1L, result.getTotal());
        assertEquals(1L, result.getPages());
        ResourceVO item = result.getRecords().get(0);
        assertEquals(List.of("链表", "节点"), item.getKnowledgePoints());
        assertTrue(item.isBookmarked());
        assertEquals(99L, item.getMaterialId());
        assertNull(item.getContentText());
        assertEquals("KNOWLEDGE_CARD", item.getResourceKind());
        assertEquals("OPEN_RESOURCE", item.getAccessMode());
        assertEquals("zh-CN", item.getLanguageCode());
        assertEquals(resource.getCoverageNote(), item.getCoverageNote());
        verify(resources).findPage(7L, "PUBLISHED", "链表", "计算机", true, 0, 12, false, "KNOWLEDGE_CARD", "OPEN_RESOURCE");
    }

    @Test
    void detailIncludesInternalBodyButNeverUsesAnotherUsersAssociation() {
        when(resources.findPublishedById(11L)).thenReturn(resource);
        when(links.findVisibleLinks(7L, List.of(11L))).thenReturn(List.of(link(8L, 11L, 555L)));
        ResourceVO result = service.detail(7L, 11L);
        assertEquals(resource.getContentText(), result.getContentText());
        assertNull(result.getMaterialId());
        assertFalse(result.isBookmarked());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "WITHDRAWN"})
    void unavailableResourcesCannotBeReadBookmarkedOrImported(String status) {
        resource.setStatus(status);
        when(resources.findPublishedById(11L)).thenReturn(resource);
        when(resources.lockById(11L)).thenReturn(resource);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.detail(7L, 11L)).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.bookmark(7L, 11L, true)).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L)).getCode());
        verifyNoInteractions(links, materials);
    }

    @Test
    void missingResourceIsUnavailable() {
        assertEquals(404, assertThrows(BusinessException.class, () -> service.detail(7L, 11L)).getCode());
    }

    @Test
    void withdrawnResourceCanStillBeUnbookmarkedWithoutReadingIt() {
        service.bookmark(7L, 11L, false);
        verify(links).setBookmarked(7L, 11L, false);
        verifyNoInteractions(resources, materials);
        verify(links, never()).ensureLink(anyLong(), anyLong());
    }

    @Test
    void addingBookmarkLocksVisibilityBeforeWritingOnlyCurrentUsersLink() {
        when(resources.lockById(11L)).thenReturn(resource);
        service.bookmark(7L, 11L, true);
        InOrder order = inOrder(resources, links);
        order.verify(resources).lockById(11L);
        order.verify(links).ensureLink(7L, 11L);
        order.verify(links).setBookmarked(7L, 11L, true);
    }

    @Test
    void externalLinkOnlyResourceCannotBeImportedEvenWhenItHasText() {
        resource.setResourceType("EXTERNAL");
        resource.setLicenseCode("LINK_ONLY");
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verifyNoInteractions(links, materials);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CC-BY", "CC0", "LINK_ONLY", ""})
    void importDoesNotGrantPermissionForUnapprovedLicenseCodes(String license) {
        resource.setLicenseCode(license);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verifyNoInteractions(links, materials);
    }

    @Test
    void repeatedImportReusesOwnedMaterialAndPreservesCompleteAttribution() {
        resource.setSummary("概".repeat(500));
        resource.setSourceUrl("https://learning.example.com/original?version=1");
        resource.setLicenseUrl("https://learning.example.com/license");
        resource.setDownloadUrl("https://learning.example.com/full-course.pdf");
        resource.setResourceKind("COURSE_PACK");
        resource.setLicenseNote("说".repeat(1000));
        StudyResourceUser link = link(7L, 11L, null);
        prepareImport(link, 91L);
        when(links.setMaterialId(7L, 11L, 91L)).thenAnswer(invocation -> {
            link.setMaterialId(91L);
            return 1;
        });
        when(materials.getById(91L)).thenReturn(material(91L, 7L, 0));

        assertEquals(91L, service.importMaterial(7L, 11L));
        assertEquals(91L, service.importMaterial(7L, 11L));

        ArgumentCaptor<MaterialCreateRequest> request = ArgumentCaptor.forClass(MaterialCreateRequest.class);
        verify(materials, times(1)).createTextMaterial(eq(7L), request.capture());
        assertEquals("TEXT", request.getValue().getMaterialType());
        assertTrue(request.getValue().getDescription().length() <= 500);
        assertEquals(resource.getContentText(), request.getValue().getContentText());
        assertTrue(request.getValue().getDescription().contains(resource.getSourceName()));
        ArgumentCaptor<String> attribution = ArgumentCaptor.forClass(String.class);
        verify(links).saveAttribution(eq(7L), eq(91L), attribution.capture());
        assertTrue(attribution.getValue().contains(resource.getSourceUrl()));
        assertTrue(attribution.getValue().contains(resource.getLicenseUrl()));
        assertTrue(attribution.getValue().contains(resource.getLicenseNote()));
        assertTrue(attribution.getValue().contains(resource.getAuthor()));
        assertTrue(attribution.getValue().contains(resource.getResourceKind()));
        assertTrue(attribution.getValue().contains(resource.getLanguageCode()));
        assertTrue(attribution.getValue().contains(resource.getCoverageNote()));
        assertTrue(attribution.getValue().contains(resource.getDownloadUrl()));
        InOrder order = inOrder(resources, links, materials);
        order.verify(resources).lockById(11L);
        order.verify(links).ensureLink(7L, 11L);
        order.verify(links).lockLink(7L, 11L);
        order.verify(materials).createTextMaterial(eq(7L), any());
        order.verify(links).setMaterialId(7L, 11L, 91L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CC-BY-4.0", "CC-BY-SA-4.0", "CC-BY-NC-SA-4.0", "CC-BY-NC-3.0", "CC-BY-SA-3.0-US"})
    void verifiedHostedBookCanBePublishedReadAndImportedInFullWithItsActualLicense(String license) {
        prepareAdmin();
        hostedOpenBook(license);
        resource.setDocumentUrl("/open-books/open-textbook/book.pdf");
        resource.setPageCount(248);
        String completeBook = "# 第一章\n" + "这是完整教材正文，保留示例、练习和证明。\n".repeat(10000) + "# 最后一章\n全文结束。";
        resource.setContentText(completeBook);
        StudyResourceUser association = link(7L, 11L, null);
        prepareImport(association, 91L);
        when(resources.findPublishedById(11L)).thenReturn(resource);
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        when(links.setMaterialId(7L, 11L, 91L)).thenAnswer(invocation -> {
            association.setMaterialId(91L);
            return 1;
        });
        when(materials.getById(91L)).thenReturn(material(91L, 7L, 0));

        assertDoesNotThrow(() -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        assertEquals(completeBook, service.detail(7L, 11L).getContentText());
        assertEquals(license, service.detail(7L, 11L).getLicenseCode());
        assertEquals(resource.getDocumentUrl(), service.detail(7L, 11L).getDocumentUrl());
        assertEquals(248, service.detail(7L, 11L).getPageCount());
        assertEquals(91L, service.importMaterial(7L, 11L));
        assertEquals(91L, service.importMaterial(7L, 11L));

        ArgumentCaptor<MaterialCreateRequest> request = ArgumentCaptor.forClass(MaterialCreateRequest.class);
        verify(materials, times(1)).createTextMaterial(eq(7L), request.capture());
        assertEquals(completeBook, request.getValue().getContentText());
        assertTrue(request.getValue().getDescription().contains(license));
        ArgumentCaptor<String> attribution = ArgumentCaptor.forClass(String.class);
        verify(links).saveAttribution(eq(7L), eq(91L), attribution.capture());
        assertTrue(attribution.getValue().contains(resource.getAuthor()));
        assertTrue(attribution.getValue().contains(resource.getSourceUrl()));
        assertTrue(attribution.getValue().contains(resource.getLicenseUrl()));
        assertTrue(attribution.getValue().contains(resource.getLicenseNote()));
        assertTrue(attribution.getValue().contains("授权：" + license));
        assertTrue(attribution.getValue().contains(resource.getDocumentUrl()));
        verify(resources).updateStatus(11L, "PUBLISHED", null, 7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sourceUrl", "licenseUrl", "licenseNote", "author", "contentText"})
    void hostedOpenBookRequiresProvenanceLicenseAndCompleteBodyBeforePublicationOrImport(String missing) {
        prepareAdmin();
        hostedOpenBook("CC-BY-SA-4.0");
        switch (missing) {
            case "sourceUrl" -> resource.setSourceUrl(" ");
            case "licenseUrl" -> resource.setLicenseUrl(null);
            case "licenseNote" -> resource.setLicenseNote("");
            case "author" -> resource.setAuthor(null);
            case "contentText" -> resource.setContentText(" ");
            default -> throw new AssertionError(missing);
        }
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
        verifyNoInteractions(links, materials);
    }

    @Test
    void fullBookOverTheSupportedLimitIsRejectedInsteadOfTruncated() {
        hostedOpenBook("CC-BY-4.0");
        resource.setContentText("文".repeat(1000001));
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verifyNoInteractions(links, materials);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/book.pdf", "//example.com/book.pdf", "/open-books/../book.pdf",
            "/open-books/book/../../book.pdf", "/open-books/book/%62ook.pdf", "/open-books/book/book.html",
            "/open-books/book/book.pdf?download=true", "/open-books/book/book.pdf#page=1",
            " /open-books/book/book.pdf", "/other-files/book.pdf", "javascript:alert(1)"})
    void hostedDocumentMustUseTheStrictLocalBookPath(String path) {
        prepareAdmin();
        hostedOpenBook("CC-BY-4.0");
        resource.setDocumentUrl(path);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verifyNoInteractions(links, materials);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void documentPageCountCannotClaimNonpositivePages(int pages) {
        hostedOpenBook("CC-BY-SA-4.0");
        resource.setDocumentUrl("/open-books/open-textbook/book.pdf");
        resource.setPageCount(pages);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verifyNoInteractions(links, materials);
    }

    @Test
    void externalLinkOnlyBookCannotAttachALocalDocument() {
        prepareAdmin();
        external("https://example.com/book");
        resource.setDocumentUrl("/open-books/open-textbook/book.pdf");
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    void openLicenseDoesNotTurnAnExternalLinkIntoAHostedBook() {
        hostedOpenBook("CC-BY-4.0");
        resource.setResourceType("EXTERNAL");
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verifyNoInteractions(links, materials);
    }

    private void hostedOpenBook(String license) {
        resource.setResourceType("INTERNAL");
        resource.setResourceKind("TEXTBOOK");
        resource.setLicenseCode(license);
        resource.setSourceName("作者维护的 GitHub 教材项目");
        resource.setSourceUrl("https://github.com/book-author/open-textbook");
        resource.setLicenseUrl("https://github.com/book-author/open-textbook/blob/main/LICENSE");
        resource.setLicenseNote("保留作者署名、来源和许可证；本次仅转换存储格式，使用范围遵循原许可。");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void deletedOrMissingImportedMaterialIsRecreated(int deleted) {
        prepareImport(link(7L, 11L, 91L), 92L);
        when(materials.getById(91L)).thenReturn(deleted == 0 ? null : material(91L, 7L, deleted));
        assertEquals(92L, service.importMaterial(7L, 11L));
        verify(materials).createTextMaterial(eq(7L), any());
        verify(links).setMaterialId(7L, 11L, 92L);
    }

    @Test
    void corruptAssociationToAnotherUsersMaterialIsReplacedWithPrivateImport() {
        prepareImport(link(7L, 11L, 91L), 92L);
        when(materials.getById(91L)).thenReturn(material(91L, 8L, 0));
        assertEquals(92L, service.importMaterial(7L, 11L));
        verify(materials).createTextMaterial(eq(7L), any());
        verify(links).setMaterialId(7L, 11L, 92L);
    }

    @Test
    void eachUserImportsIntoTheirOwnMaterial() {
        when(resources.lockById(11L)).thenReturn(resource);
        when(links.lockLink(7L, 11L)).thenReturn(link(7L, 11L, null));
        when(links.lockLink(8L, 11L)).thenReturn(link(8L, 11L, null));
        when(materials.createTextMaterial(eq(7L), any())).thenReturn(91L);
        when(materials.createTextMaterial(eq(8L), any())).thenReturn(92L);
        when(links.setMaterialId(anyLong(), eq(11L), anyLong())).thenReturn(1);
        when(links.saveAttribution(anyLong(), anyLong(), anyString())).thenReturn(1);
        assertEquals(91L, service.importMaterial(7L, 11L));
        assertEquals(92L, service.importMaterial(8L, 11L));
        verify(links).setMaterialId(7L, 11L, 91L);
        verify(links).setMaterialId(8L, 11L, 92L);
    }

    @Test
    void failedAssociationWriteFailsImportSoItsTransactionCanRollBack() {
        prepareImport(link(7L, 11L, null), 91L);
        when(links.setMaterialId(7L, 11L, 91L)).thenReturn(0);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L)).getCode());
    }

    @Test
    void explicitKnowledgePointIsUsedWithoutReadingMastery() {
        when(resources.findRecommendations(List.of("链表"), 3)).thenReturn(List.of(resource));
        var result = service.recommendations(7L, " 链表 ", 3);
        assertEquals("知识点：链表", result.get(0).getRecommendationReason());
        assertNull(result.get(0).getContentText());
        verifyNoInteractions(mastery);
    }

    @Test
    void onlyAssessedWeakPointsDrivePersonalizedRecommendations() {
        when(mastery.overview(eq(7L), any())).thenReturn(KnowledgeMasteryOverviewVO.builder().weakestPoints(List.of(
                point("待评估", false, 0), point("已掌握", true, 90), point("链表", true, 50))).build());
        when(resources.findRecommendations(List.of("链表"), 3)).thenReturn(List.of(resource));
        var result = service.recommendations(7L, null, 3);
        assertEquals("薄弱知识点：链表", result.get(0).getRecommendationReason());
        verify(resources).findRecommendations(List.of("链表"), 3);
    }

    @Test
    void insufficientAssessmentUsesHonestFeaturedFallback() {
        when(mastery.overview(eq(7L), any())).thenReturn(KnowledgeMasteryOverviewVO.builder()
                .weakestPoints(List.of(point("链表", false, 0))).build());
        when(resources.findRecommendations(List.of(), 3)).thenReturn(List.of(resource));
        assertEquals("精选资源", service.recommendations(7L, null, 3).get(0).getRecommendationReason());
        verify(resources).findRecommendations(List.of(), 3);
    }

    @Test
    void unmatchedAssessedWeakPointFallsBackToFeaturedWithoutClaimingItsTopicMatches() {
        when(mastery.overview(eq(7L), any())).thenReturn(KnowledgeMasteryOverviewVO.builder()
                .weakestPoints(List.of(point("不存在", true, 20))).build());
        when(resources.findRecommendations(List.of("不存在"), 3)).thenReturn(List.of());
        when(resources.findRecommendations(List.of(), 3)).thenReturn(List.of(resource));
        assertEquals("精选资源", service.recommendations(7L, null, 3).get(0).getRecommendationReason());
    }

    @Test
    void adminDetailCanReviewWithdrawnBodyButRequiresAdminRole() {
        assertEquals(403, assertThrows(BusinessException.class, () -> service.adminDetail(7L, 11L)).getCode());
        verifyNoInteractions(resources);
        prepareAdmin();
        resource.setStatus("WITHDRAWN");
        resource.setResourceType("EXTERNAL");
        when(resources.findById(11L)).thenReturn(resource);
        assertEquals(resource.getContentText(), service.adminDetail(7L, 11L).getContentText());
        assertEquals("WITHDRAWN", service.adminDetail(7L, 11L).getStatus());
        verify(resources, never()).findPublishedById(anyLong());
    }

    @Test
    void explicitUnmatchedKnowledgePointReturnsEmptyInsteadOfUnrelatedLessons() {
        when(resources.findRecommendations(List.of("不存在"), 3)).thenReturn(List.of());
        assertTrue(service.recommendations(7L, "不存在", 3).isEmpty());
        verify(resources, never()).findRecommendations(List.of(), 3);
    }

    @Test
    void regularAndDisabledUsersCannotReadAdminPageOrChangeStatus() {
        SysUser user = new SysUser();
        user.setStatus(1);
        user.setRoleCode("USER");
        when(users.selectById(7L)).thenReturn(user);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.adminPage(7L, new ResourceAdminPageQuery())).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.updateStatus(7L, 11L, status("PUBLISHED"))).getCode());
        user.setRoleCode("ADMIN");
        user.setStatus(0);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.updateStatus(7L, 11L, status("PUBLISHED"))).getCode());
        verifyNoInteractions(resources);
    }

    @Test
    void adminCanSeeDraftsAndPublishValidInternalResource() {
        prepareAdmin();
        ResourceAdminPageQuery query = new ResourceAdminPageQuery();
        query.setStatus("DRAFT");
        query.setResourceKind("COURSE_PACK");
        query.setAccessMode("OPEN_RESOURCE");
        when(resources.findPage(7L, "DRAFT", null, null, false, 0, 12, true, "COURSE_PACK", "OPEN_RESOURCE")).thenReturn(List.of(resource));
        service.adminPage(7L, query);
        verify(resources).findPage(7L, "DRAFT", null, null, false, 0, 12, true, "COURSE_PACK", "OPEN_RESOURCE");
        verify(resources).countPage(7L, "DRAFT", null, null, false, "COURSE_PACK", "OPEN_RESOURCE");
        when(resources.lockById(11L)).thenReturn(resource);
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        service.updateStatus(7L, 11L, status("PUBLISHED"));
        verify(resources).updateStatus(11L, "PUBLISHED", null, 7L);
    }

    @Test
    void incompleteMetadataCannotBePublishedButCanBeWithdrawn() {
        prepareAdmin();
        resource.setAuthor(null);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), eq("PUBLISHED"), any(), anyLong());
        when(resources.updateStatus(11L, "WITHDRAWN", null, 7L)).thenReturn(1);
        service.updateStatus(7L, 11L, status("WITHDRAWN"));
        verify(resources).updateStatus(11L, "WITHDRAWN", null, 7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/a", "https://localhost/a", "https://127.0.0.1/a",
            "https://192.168.1.1/a", "https://[::1]/a", "https://service.internal/a",
            "https://user:password@example.com/a", "javascript:alert(1)", "https://example.com:8080/a", "not a URL"})
    void unsafeExternalSourceUrlsCannotBePublished(String url) {
        prepareAdmin();
        external(url);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    void linkOnlyResourceCanBePublishedWithOriginalHttpsUrlAndNoCopiedBody() {
        prepareAdmin();
        external("https://developer.mozilla.org/zh-CN/docs/Web");
        when(resources.lockById(11L)).thenReturn(resource);
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        service.updateStatus(7L, 11L, status("PUBLISHED"));
        verify(resources).updateStatus(11L, "PUBLISHED", null, 7L);
    }

    @Test
    void copiedExternalBodyAndMissingInternalBodyAreRejectedAtPublication() {
        prepareAdmin();
        external("https://developer.mozilla.org/zh-CN/docs/Web");
        resource.setContentText("复制的正文");
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        resource.setResourceType("INTERNAL");
        resource.setLicenseCode("INTERNAL");
        resource.setContentText(" ");
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"published", "HIDDEN", "", "ALL"})
    void statusChangesRejectUnknownValues(String value) {
        prepareAdmin();
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status(value)));
        verifyNoInteractions(resources);
    }

    @Test
    void invalidQueriesFailBeforeDatabaseAccess() {
        ResourcePageQuery query = new ResourcePageQuery();
        query.setCurrent(Long.MAX_VALUE);
        assertThrows(BusinessException.class, () -> service.page(7L, query));
        query.setCurrent(1L);
        query.setSize(51L);
        assertThrows(BusinessException.class, () -> service.page(7L, query));
        query.setSize(12L);
        query.setKeyword("长".repeat(101));
        assertThrows(BusinessException.class, () -> service.page(7L, query));
        assertThrows(BusinessException.class, () -> service.recommendations(7L, "长".repeat(101), 3));
        assertThrows(BusinessException.class, () -> service.recommendations(7L, null, 13));
        assertEquals(401, assertThrows(BusinessException.class, () -> service.page(null, query)).getCode());
        verifyNoInteractions(resources, mastery);
    }

    @ParameterizedTest
    @ValueSource(strings = {"工程-实践", "第 13 章", "978-0-306", "2026-11-07", "ISBN 978-0-306-40615-7"})
    void isbnNormalizationPreservesOtherSearchPunctuation(String keyword) {
        ResourcePageQuery query = new ResourcePageQuery();
        query.setKeyword(keyword);
        service.page(7L, query);
        verify(resources).countPage(7L, "PUBLISHED", keyword, null, false, null, null);
        verify(resources).findPage(7L, "PUBLISHED", keyword, null, false, 0, 12, false, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOOK", "textbook", "", "UNKNOWN"})
    void unknownResourceKindsCannotBeSearchedOrPublished(String kind) {
        ResourcePageQuery query = new ResourcePageQuery();
        query.setResourceKind(kind);
        assertThrows(BusinessException.class, () -> service.page(7L, query));
        prepareAdmin();
        ResourceAdminPageQuery adminQuery = new ResourceAdminPageQuery();
        adminQuery.setResourceKind(kind);
        assertThrows(BusinessException.class, () -> service.adminPage(7L, adminQuery));
        resource.setResourceKind(kind);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEXTBOOK", "COURSE_PACK", "REVIEW_PACK"})
    void completeResourceKindsRequireLanguageAndCoverageBeforePublishing(String kind) {
        prepareAdmin();
        resource.setResourceKind(kind);
        resource.setEstimatedMinutes(0);
        resource.setLanguageCode(null);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        resource.setLanguageCode("en");
        resource.setCoverageNote("  ");
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        resource.setCoverageNote("完整课程：链表、树和图");
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        assertDoesNotThrow(() -> service.updateStatus(7L, 11L, status("PUBLISHED")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh CN", "zh_CN", "中文", "en<script>", "a"})
    void malformedLanguageCodesCannotBePublished(String language) {
        prepareAdmin();
        resource.setResourceKind("TEXTBOOK");
        resource.setLanguageCode(language);
        when(resources.lockById(11L)).thenReturn(resource);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED"))).getCode());
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/book.pdf", "javascript:alert(1)", "https://localhost/book.pdf",
            "https://192.168.1.1/book.pdf", "https://user:password@example.com/book.pdf", "invalid-url"})
    void downloadUrlUsesSameSafeHttpsValidationAsSourceUrl(String url) {
        prepareAdmin();
        resource.setResourceKind("TEXTBOOK");
        resource.setDownloadUrl(url);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    void officialDownloadIsExposedInDetailButDoesNotAuthorizeImportOfExternalTextbook() {
        prepareAdmin();
        external("https://openstax.org/details/books/calculus-volume-1");
        resource.setResourceKind("TEXTBOOK");
        resource.setLanguageCode("en");
        resource.setDownloadUrl("https://assets.openstax.org/official-book.pdf");
        when(resources.lockById(11L)).thenReturn(resource);
        when(resources.findPublishedById(11L)).thenReturn(resource);
        when(resources.findById(11L)).thenReturn(resource);
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        assertDoesNotThrow(() -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        ResourceVO result = service.detail(7L, 11L);
        assertEquals("TEXTBOOK", result.getResourceKind());
        assertEquals("en", result.getLanguageCode());
        assertEquals(resource.getCoverageNote(), result.getCoverageNote());
        assertEquals(resource.getDownloadUrl(), result.getDownloadUrl());
        assertEquals(resource.getDownloadUrl(), service.adminDetail(7L, 11L).getDownloadUrl());
        assertNull(result.getContentText());
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verifyNoInteractions(materials);
    }

    @Test
    void onlyCompleteResourcesMayUseZeroForUnknownDuration() {
        prepareAdmin();
        when(resources.lockById(11L)).thenReturn(resource);
        resource.setEstimatedMinutes(0);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.updateStatus(7L, 11L, status("PUBLISHED"))).getCode());
        resource.setResourceKind("TEXTBOOK");
        resource.setEstimatedMinutes(-1);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.updateStatus(7L, 11L, status("PUBLISHED"))).getCode());
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
        resource.setEstimatedMinutes(0);
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        assertDoesNotThrow(() -> service.updateStatus(7L, 11L, status("PUBLISHED")));
    }

    @Test
    void catalogDetailPreservesMetadataWithoutInventingYearOrEnablingImport() {
        catalogResource();
        resource.setIsbn("9780306406157");
        resource.setPublisher("机械工业出版社");
        resource.setEducationLevel("本科");
        when(resources.findPublishedById(11L)).thenReturn(resource);
        when(resources.findById(11L)).thenReturn(resource);
        when(resources.lockById(11L)).thenReturn(resource);
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        prepareAdmin();
        assertDoesNotThrow(() -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        for (ResourceVO detail : List.of(service.detail(7L, 11L), service.adminDetail(7L, 11L))) {
            assertEquals("CATALOG_ONLY", detail.getAccessMode());
            assertEquals("9780306406157", detail.getIsbn());
            assertEquals("机械工业出版社", detail.getPublisher());
            assertEquals("本科", detail.getEducationLevel());
            assertNull(detail.getPublicationYear());
            assertNull(detail.getContentText());
            assertNull(detail.getDownloadUrl());
        }
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verifyNoInteractions(materials);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTERNAL", "LICENSE", "COURSE_PACK", "DOWNLOAD", "BODY"})
    void catalogPublicationRejectsNonCatalogTypesOrCopiedContent(String invalidField) {
        catalogResource();
        switch (invalidField) {
            case "INTERNAL" -> {
                resource.setResourceType("INTERNAL");
                resource.setLicenseCode("INTERNAL");
                resource.setContentText("自编内容也不能冒充外部书目");
            }
            case "LICENSE" -> resource.setLicenseCode("CC-BY");
            case "COURSE_PACK" -> resource.setResourceKind("COURSE_PACK");
            case "DOWNLOAD" -> resource.setDownloadUrl("https://learning.example.com/book.pdf");
            case "BODY" -> resource.setContentText("复制的教材内容");
            default -> fail("Unknown test field");
        }
        prepareAdmin();
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        assertThrows(BusinessException.class, () -> service.importMaterial(7L, 11L));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
        verifyNoInteractions(materials);
    }

    @ParameterizedTest
    @ValueSource(strings = {"catalog_only", "FULL_TEXT", "", "ALL"})
    void invalidAccessModesCannotBeSearchedOrPublished(String mode) {
        ResourcePageQuery query = new ResourcePageQuery();
        query.setAccessMode(mode);
        assertThrows(BusinessException.class, () -> service.page(7L, query));
        prepareAdmin();
        ResourceAdminPageQuery adminQuery = new ResourceAdminPageQuery();
        adminQuery.setAccessMode(mode);
        assertThrows(BusinessException.class, () -> service.adminPage(7L, adminQuery));
        verifyNoInteractions(resources);
        resource.setAccessMode(mode);
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9780306406158", "0306406152", "978030640615X", "97803064061570", "978030640615 ", "4006381333931"})
    void invalidIsbnFormatOrChecksumPreventsPublication(String isbn) {
        catalogResource();
        resource.setIsbn(isbn);
        prepareAdmin();
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"9780306406157", "9791090636071"})
    void missingOrValidIsbnDoesNotInventPublicationMetadata(String isbn) {
        catalogResource();
        resource.setIsbn(isbn);
        prepareAdmin();
        when(resources.lockById(11L)).thenReturn(resource);
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        assertDoesNotThrow(() -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        assertNull(resource.getPublicationYear());
        assertNull(resource.getPublisher());
        assertNull(resource.getEducationLevel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHER", "EDUCATION_LEVEL", "YEAR_ZERO", "YEAR_TOO_LONG", "KNOWLEDGE_POINTS"})
    void invalidBibliographicFieldLengthsOrYearsPreventPublication(String invalidField) {
        catalogResource();
        switch (invalidField) {
            case "PUBLISHER" -> resource.setPublisher("社".repeat(201));
            case "EDUCATION_LEVEL" -> resource.setEducationLevel("学".repeat(41));
            case "YEAR_ZERO" -> resource.setPublicationYear(0);
            case "YEAR_TOO_LONG" -> resource.setPublicationYear(10000);
            case "KNOWLEDGE_POINTS" -> resource.setKnowledgePoints("点".repeat(501));
            default -> fail("Unknown test field");
        }
        prepareAdmin();
        when(resources.lockById(11L)).thenReturn(resource);
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void catalogMayOmitUnknownKnowledgePointsWhileOpenResourcesStillRequireThem(String points) {
        catalogResource();
        resource.setKnowledgePoints(points);
        prepareAdmin();
        when(resources.lockById(11L)).thenReturn(resource);
        when(resources.updateStatus(11L, "PUBLISHED", null, 7L)).thenReturn(1);
        assertDoesNotThrow(() -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        resource.setAccessMode("OPEN_RESOURCE");
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, times(1)).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    private void catalogResource() {
        external("https://learning.example.com/catalog/book");
        resource.setAccessMode("CATALOG_ONLY");
        resource.setResourceKind("TEXTBOOK");
        resource.setSummary("大学教材书目信息");
        resource.setCoverageNote("参考出版社课程分类");
        resource.setKnowledgePoints("");
        resource.setLicenseNote("仅展示书目和官方页面，不提供全文。");
        resource.setEstimatedMinutes(0);
    }

    @Test
    void completeHostedBookCanImportMoreThanOneMillionCharactersWithoutTruncation() {
        resource.setResourceKind("TEXTBOOK");
        resource.setDocumentUrl("/open-books/foundation-test/book.pdf");
        resource.setPageCount(700);
        String fullBook = "A complete textbook paragraph. ".repeat(40000);
        resource.setContentText(fullBook);
        prepareImport(link(7L, 11L, null), 91L);

        assertEquals(91L, service.importMaterial(7L, 11L));
        ArgumentCaptor<MaterialCreateRequest> request = ArgumentCaptor.forClass(MaterialCreateRequest.class);
        verify(materials).createTextMaterial(eq(7L), request.capture());
        assertEquals(fullBook, request.getValue().getContentText());
    }

    @Test
    void largerBodyAllowanceStillRequiresAValidHostedDocumentAndHasAnUpperBound() {
        prepareAdmin();
        when(resources.lockById(11L)).thenReturn(resource);
        resource.setContentText("x".repeat(1000001));
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        resource.setDocumentUrl("https://example.com/not-hosted.pdf");
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        resource.setDocumentUrl("/open-books/foundation-test/book.pdf");
        resource.setContentText("x".repeat(5000001));
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 11L, status("PUBLISHED")));
        verify(resources, never()).updateStatus(anyLong(), anyString(), any(), anyLong());
    }

    private void prepareImport(StudyResourceUser link, Long createdId) {
        when(resources.lockById(11L)).thenReturn(resource);
        when(links.lockLink(7L, 11L)).thenReturn(link);
        when(materials.createTextMaterial(eq(7L), any())).thenReturn(createdId);
        when(links.setMaterialId(7L, 11L, createdId)).thenReturn(1);
        when(links.saveAttribution(eq(7L), eq(createdId), anyString())).thenReturn(1);
    }

    private void prepareAdmin() {
        SysUser user = new SysUser();
        user.setStatus(1);
        user.setRoleCode("ADMIN");
        when(users.selectById(7L)).thenReturn(user);
    }

    private void external(String url) {
        resource.setResourceType("EXTERNAL");
        resource.setLicenseCode("LINK_ONLY");
        resource.setSourceUrl(url);
        resource.setContentText(null);
    }

    private static StudyResourceUser link(Long userId, Long resourceId, Long materialId) {
        StudyResourceUser link = new StudyResourceUser();
        link.setUserId(userId);
        link.setResourceId(resourceId);
        link.setMaterialId(materialId);
        link.setBookmarked(false);
        return link;
    }

    private static StudyMaterial material(Long id, Long userId, int deleted) {
        StudyMaterial material = new StudyMaterial();
        material.setId(id);
        material.setUserId(userId);
        material.setDeleted(deleted);
        return material;
    }

    private static KnowledgeMasteryItemVO point(String name, boolean assessed, int percent) {
        return KnowledgeMasteryItemVO.builder().knowledgePoint(name).assessmentEligible(assessed).masteryPercent(percent).build();
    }

    private static ResourceStatusRequest status(String value) {
        ResourceStatusRequest request = new ResourceStatusRequest();
        request.setStatus(value);
        return request;
    }
}
