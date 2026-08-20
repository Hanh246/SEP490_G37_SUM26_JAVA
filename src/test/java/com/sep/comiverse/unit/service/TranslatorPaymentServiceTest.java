package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.HandoverTaskRequest;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.TranslatorChapterSettlementEntity;
import com.sep.comiverse.entity.TranslatorEarningEntryEntity;
import com.sep.comiverse.entity.enums.PageStatus;
import com.sep.comiverse.entity.enums.TranslatorEarningEntryType;
import com.sep.comiverse.entity.enums.TranslatorSettlementStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.ITaskHandoverRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.ITranslatorChapterSettlementRepository;
import com.sep.comiverse.repository.ITranslatorEarningEntryRepository;
import com.sep.comiverse.service.CreatorPayoutSettingsService;
import com.sep.comiverse.service.TranslatorPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslatorPaymentServiceTest {

    @Mock private ITeamTaskRepository taskRepository;
    @Mock private IPageTranslationRepository pageRepository;
    @Mock private ITaskHandoverRepository handoverRepository;
    @Mock private ITranslatorChapterSettlementRepository settlementRepository;
    @Mock private ITranslatorEarningEntryRepository earningRepository;
    @Mock private CreatorPayoutSettingsService payoutSettingsService;

    private TranslatorPaymentService service;

    @BeforeEach
    void setUp() {
        service = new TranslatorPaymentService(
                taskRepository,
                pageRepository,
                handoverRepository,
                settlementRepository,
                earningRepository,
                payoutSettingsService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "payoutTimeZone", "Asia/Ho_Chi_Minh");
    }

    @Test
    void defaultPageRateAndChapterRewardUseMergedPayoutSettingsEntity() {
        CreatorPayoutSettingEntity settings = CreatorPayoutSettingEntity.builder()
                .translatorTaskRateUsd(new BigDecimal("1.25"))
                .build();
        when(payoutSettingsService.currentSettings()).thenReturn(settings);

        assertEquals(new BigDecimal("1.25"), service.defaultPageRateUsd());
        assertEquals(new BigDecimal("6.25"), service.deriveChapterRewardUsd(5));
    }

    @Test
    void deriveChapterRewardRejectsZeroPages() {
        CustomException error = assertThrows(CustomException.class, () -> service.deriveChapterRewardUsd(0));

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals("A translation task must contain at least one page", error.getMessage());
    }

    @Test
    void initializePageAssignmentsCreditsCurrentAssigneeAndAppliesDefaults() {
        UUID translatorId = UUID.randomUUID();
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(UUID.randomUUID())
                .projectTeamId(UUID.randomUUID())
                .assigneeId(translatorId)
                .build();
        PageTranslationEntity first = PageTranslationEntity.builder()
                .taskId(task)
                .pageNumber(1)
                .imageUrl("https://example.test/1.jpg")
                .status(null)
                .build();
        PageTranslationEntity second = PageTranslationEntity.builder()
                .taskId(task)
                .pageNumber(2)
                .imageUrl("https://example.test/2.jpg")
                .status(PageStatus.DONE)
                .build();
        List<PageTranslationEntity> pages = List.of(first, second);

        service.initializePageAssignments(task, pages);

        assertEquals(translatorId, first.getAssignedTranslatorId());
        assertEquals(new BigDecimal("1.00"), first.getResponsibilityFactor());
        assertEquals(PageStatus.TODO, first.getStatus());
        assertEquals(translatorId, second.getAssignedTranslatorId());
        assertEquals(PageStatus.DONE, second.getStatus());
        verify(pageRepository).saveAll(pages);
    }

    @Test
    void handoverKeepsAcceptedPagesAndReassignsOnlyRemainingPages() {
        UUID taskId = UUID.randomUUID();
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(taskId)
                .projectTeamId(UUID.randomUUID())
                .assigneeId(translatorA)
                .status("in_progress")
                .build();
        List<PageTranslationEntity> pages = pages(task, 4, translatorA);
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId)).thenReturn(pages);
        when(handoverRepository.save(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, com.sep.comiverse.entity.TaskHandoverEntity.class);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        HandoverTaskRequest request = new HandoverTaskRequest();
        request.setNewAssigneeId(translatorB);
        request.setCompletedPageNumbers(List.of(1, 2));
        request.setResponsibilityFactor(new BigDecimal("0.80"));

        var response = service.handover(task, request, UUID.randomUUID());

        assertNotNull(response.getHandoverId());
        assertEquals(List.of(1, 2), response.getCompletedPageNumbers());
        assertEquals(2, response.getAcceptedPageCount());
        assertEquals(2, response.getReassignedPageCount());
        assertEquals(translatorA, pages.get(0).getAssignedTranslatorId());
        assertEquals(PageStatus.DONE, pages.get(0).getStatus());
        assertEquals(0, new BigDecimal("0.80").compareTo(pages.get(0).getResponsibilityFactor()));
        assertEquals(translatorA, pages.get(1).getAssignedTranslatorId());
        assertEquals(translatorB, pages.get(2).getAssignedTranslatorId());
        assertEquals(PageStatus.TODO, pages.get(2).getStatus());
        assertEquals(translatorB, pages.get(3).getAssignedTranslatorId());
        assertEquals(translatorB, task.getAssigneeId());
        verify(pageRepository).saveAll(pages);
        verify(taskRepository).save(task);
    }

    @Test
    void handoverRejectsPageAlreadyCreditedToAnotherTranslator() {
        UUID taskId = UUID.randomUUID();
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();
        UUID translatorC = UUID.randomUUID();
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(taskId)
                .projectTeamId(UUID.randomUUID())
                .assigneeId(translatorA)
                .status("in_progress")
                .build();
        List<PageTranslationEntity> pages = pages(task, 2, translatorA);
        pages.get(0).setAssignedTranslatorId(translatorC);
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId)).thenReturn(pages);

        HandoverTaskRequest request = new HandoverTaskRequest();
        request.setNewAssigneeId(translatorB);
        request.setCompletedPageNumbers(List.of(1));
        request.setResponsibilityFactor(BigDecimal.ONE);

        CustomException error = assertThrows(CustomException.class,
                () -> service.handover(task, request, UUID.randomUUID()));

        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        assertEquals("Page 1 belongs to another translator and cannot be credited again", error.getMessage());
        verify(pageRepository, never()).saveAll(any());
        verify(taskRepository, never()).save(any());
        verify(handoverRepository, never()).save(any());
    }

    @Test
    void updatePageStatusSetsAndClearsCompletedAt() {
        PageTranslationEntity page = PageTranslationEntity.builder()
                .pageNumber(1)
                .imageUrl("https://example.test/1.jpg")
                .status(PageStatus.TODO)
                .build();
        when(pageRepository.save(page)).thenReturn(page);

        service.updatePageStatus(page, PageStatus.DONE);
        assertNotNull(page.getCompletedAt());

        service.updatePageStatus(page, PageStatus.TODO);
        assertNull(page.getCompletedAt());
        verify(pageRepository, org.mockito.Mockito.times(2)).save(page);
    }

    @Test
    void validateReadyForReviewReportsIncompletePagesBeforeUnassignedPages() {
        UUID taskId = UUID.randomUUID();
        TeamTaskEntity task = TeamTaskEntity.builder().id(taskId).projectTeamId(UUID.randomUUID()).build();
        List<PageTranslationEntity> pages = pages(task, 2, UUID.randomUUID());
        pages.get(1).setStatus(PageStatus.TODO);
        pages.get(1).setAssignedTranslatorId(null);
        pages.get(0).setStatus(PageStatus.DONE);
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId)).thenReturn(pages);

        CustomException error = assertThrows(CustomException.class, () -> service.validateReadyForReview(taskId));

        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        assertEquals("All pages must be marked DONE before review. Incomplete pages: [2]", error.getMessage());
    }

    @Test
    void settlementUsesUnifiedEarningLedgerAndSplitsRewardByCreditedPages() {
        UUID taskId = UUID.randomUUID();
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();
        ChapterEntity chapter = new ChapterEntity();
        chapter.setId(UUID.randomUUID());
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(taskId)
                .projectTeamId(UUID.randomUUID())
                .chapter(chapter)
                .status("completed")
                .completedAt(Instant.now())
                .chapterRewardUsd(new BigDecimal("100.00"))
                .build();
        List<PageTranslationEntity> pages = pages(task, 10, translatorA);
        for (PageTranslationEntity page : pages) {
            page.setStatus(PageStatus.DONE);
            page.setCompletedAt(Instant.now());
        }
        for (int index = 6; index < 10; index++) {
            pages.get(index).setAssignedTranslatorId(translatorB);
        }

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId, TranslatorSettlementStatus.ACTIVE)).thenReturn(Optional.empty());
        when(settlementRepository.findFirstByTaskIdOrderByVersionNoDesc(taskId)).thenReturn(Optional.empty());
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId)).thenReturn(pages);
        when(settlementRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TranslatorChapterSettlementEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        TranslatorChapterSettlementEntity settlement = service.settleApprovedTask(task);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TranslatorEarningEntryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(earningRepository).saveAll(captor.capture());
        List<TranslatorEarningEntryEntity> earnings = captor.getValue();
        assertEquals(10, earnings.size());
        assertEquals(TranslatorSettlementStatus.ACTIVE, settlement.getStatus());
        assertEquals(new BigDecimal("100.00"), settlement.getTotalChapterRewardUsd());
        assertEquals(10, settlement.getTotalPages());
        assertNotNull(task.getSettledAt());

        var totals = earnings.stream().collect(Collectors.groupingBy(
                TranslatorEarningEntryEntity::getTranslatorId,
                Collectors.reducing(
                        BigDecimal.ZERO,
                        TranslatorEarningEntryEntity::getAmountUsd,
                        BigDecimal::add
                )
        ));
        assertEquals(0, new BigDecimal("60.00").compareTo(totals.get(translatorA)));
        assertEquals(0, new BigDecimal("40.00").compareTo(totals.get(translatorB)));
        assertEquals(0, new BigDecimal("100.00").compareTo(
                earnings.stream()
                        .map(TranslatorEarningEntryEntity::getGrossAmountUsd)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        ));
        for (TranslatorEarningEntryEntity earning : earnings) {
            assertEquals(TranslatorEarningEntryType.PAGE_EARNING, earning.getEntryType());
            assertEquals(settlement.getId(), earning.getSettlementId());
            assertEquals(taskId, earning.getTaskId());
            assertEquals(chapter.getId(), earning.getChapterId());
        }
        verify(taskRepository).save(task);
    }

    @Test
    void settlementDerivesRewardFromConfiguredPageRateWhenTaskHasNoReward() {
        UUID taskId = UUID.randomUUID();
        ChapterEntity chapter = new ChapterEntity();
        chapter.setId(UUID.randomUUID());
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(taskId)
                .projectTeamId(UUID.randomUUID())
                .chapter(chapter)
                .status("done")
                .completedAt(Instant.now())
                .chapterRewardUsd(null)
                .build();
        List<PageTranslationEntity> pages = pages(task, 3, UUID.randomUUID());
        pages.forEach(page -> page.setStatus(PageStatus.DONE));

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId, TranslatorSettlementStatus.ACTIVE)).thenReturn(Optional.empty());
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId)).thenReturn(pages);
        when(settlementRepository.findFirstByTaskIdOrderByVersionNoDesc(taskId)).thenReturn(Optional.empty());
        when(payoutSettingsService.currentSettings()).thenReturn(CreatorPayoutSettingEntity.builder()
                .translatorTaskRateUsd(new BigDecimal("1.20"))
                .build());
        when(settlementRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TranslatorChapterSettlementEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        service.settleApprovedTask(task);

        assertEquals(new BigDecimal("3.60"), task.getChapterRewardUsd());
        verify(earningRepository).saveAll(any());
    }

    @Test
    void settlementReturnsExistingActiveVersionWithoutDuplicatingLedgerRows() {
        UUID taskId = UUID.randomUUID();
        ChapterEntity chapter = new ChapterEntity();
        chapter.setId(UUID.randomUUID());
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(taskId)
                .projectTeamId(UUID.randomUUID())
                .chapter(chapter)
                .status("published")
                .completedAt(Instant.now())
                .build();
        TranslatorChapterSettlementEntity existing = TranslatorChapterSettlementEntity.builder()
                .taskId(taskId)
                .chapterId(chapter.getId())
                .projectTeamId(task.getProjectTeamId())
                .versionNo(1)
                .settlementMonth("2026-08")
                .settledAt(Instant.now())
                .totalChapterRewardUsd(new BigDecimal("10.00"))
                .totalPages(10)
                .pageRateUsd(new BigDecimal("1.000000"))
                .status(TranslatorSettlementStatus.ACTIVE)
                .build();
        existing.setId(UUID.randomUUID());
        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId, TranslatorSettlementStatus.ACTIVE)).thenReturn(Optional.of(existing));

        TranslatorChapterSettlementEntity result = service.settleApprovedTask(task);

        assertEquals(existing.getId(), result.getId());
        verify(pageRepository, never()).findByTaskId_IdOrderByPageNumberAsc(any());
        verify(earningRepository, never()).saveAll(any());
        verify(taskRepository, never()).save(any());
    }


    @Test
    void settlementIsRejectedBeforeWholeChapterApproval() {
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(UUID.randomUUID())
                .projectTeamId(UUID.randomUUID())
                .chapter(new ChapterEntity())
                .status("under_review")
                .chapterRewardUsd(new BigDecimal("100.00"))
                .build();

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        assertEquals("Translator earnings can be settled only after the whole chapter is approved", exception.getMessage());
        verify(settlementRepository, never()).saveAndFlush(any());
        verify(earningRepository, never()).saveAll(any());
    }

    @Test
    void reverseLatestSettlementWritesNegativeLedgerAdjustmentsAndMarksSettlementReversed() {
        UUID taskId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();
        TranslatorChapterSettlementEntity settlement = TranslatorChapterSettlementEntity.builder()
                .taskId(taskId)
                .chapterId(UUID.randomUUID())
                .projectTeamId(UUID.randomUUID())
                .versionNo(1)
                .settlementMonth("2026-08")
                .settledAt(Instant.now())
                .totalChapterRewardUsd(new BigDecimal("100.00"))
                .totalPages(2)
                .pageRateUsd(new BigDecimal("50.000000"))
                .status(TranslatorSettlementStatus.ACTIVE)
                .build();
        settlement.setId(settlementId);
        List<TranslatorEarningEntryEntity> pageEarnings = List.of(
                pageEarning(settlementId, taskId, translatorA, new BigDecimal("60.00")),
                pageEarning(settlementId, taskId, translatorB, new BigDecimal("40.00"))
        );
        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId, TranslatorSettlementStatus.ACTIVE)).thenReturn(Optional.of(settlement));
        when(earningRepository.findAllBySettlementIdAndEntryType(
                settlementId, TranslatorEarningEntryType.PAGE_EARNING)).thenReturn(pageEarnings);

        service.reverseLatestSettlement(taskId, " Chapter was re-opened ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TranslatorEarningEntryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(earningRepository).saveAll(captor.capture());
        List<TranslatorEarningEntryEntity> adjustments = captor.getValue();
        assertEquals(2, adjustments.size());
        var totals = adjustments.stream().collect(Collectors.toMap(
                TranslatorEarningEntryEntity::getTranslatorId,
                TranslatorEarningEntryEntity::getAmountUsd
        ));
        assertEquals(new BigDecimal("-60.00"), totals.get(translatorA));
        assertEquals(new BigDecimal("-40.00"), totals.get(translatorB));
        adjustments.forEach(item -> {
            assertEquals(TranslatorEarningEntryType.REVERSAL_ADJUSTMENT, item.getEntryType());
            assertEquals("Chapter was re-opened", item.getReason());
            assertEquals(settlementId, item.getSettlementId());
        });
        assertEquals(TranslatorSettlementStatus.REVERSED, settlement.getStatus());
        assertEquals("Chapter was re-opened", settlement.getReversalReason());
        assertNotNull(settlement.getReversedAt());
        verify(settlementRepository).save(settlement);
    }

    private TranslatorEarningEntryEntity pageEarning(
            UUID settlementId,
            UUID taskId,
            UUID translatorId,
            BigDecimal amount
    ) {
        TranslatorEarningEntryEntity entry = TranslatorEarningEntryEntity.builder()
                .entryType(TranslatorEarningEntryType.PAGE_EARNING)
                .translatorId(translatorId)
                .taskId(taskId)
                .settlementId(settlementId)
                .entryMonth("2026-08")
                .grossAmountUsd(amount)
                .amountUsd(amount)
                .responsibilityFactor(BigDecimal.ONE.setScale(2))
                .build();
        entry.setId(UUID.randomUUID());
        return entry;
    }

    private List<PageTranslationEntity> pages(TeamTaskEntity task, int count, UUID translatorId) {
        List<PageTranslationEntity> pages = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= count; pageNumber++) {
            PageTranslationEntity page = PageTranslationEntity.builder()
                    .taskId(task)
                    .pageNumber(pageNumber)
                    .imageUrl("https://example.test/" + pageNumber + ".jpg")
                    .assignedTranslatorId(translatorId)
                    .responsibilityFactor(new BigDecimal("1.00"))
                    .status(PageStatus.TODO)
                    .bubbles("[]")
                    .build();
            page.setId(UUID.randomUUID());
            pages.add(page);
        }
        return pages;
    }
}
