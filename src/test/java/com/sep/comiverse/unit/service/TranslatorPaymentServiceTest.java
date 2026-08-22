package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.HandoverTaskRequest;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.TaskHandoverEntity;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    // ===== default rate / chapter reward =====

    @Test
    void defaultPageRateUsdUsesConfiguredTranslatorRate() {
        when(payoutSettingsService.currentSettings())
                .thenReturn(settingsWithRate("1.25"));

        assertEquals(new BigDecimal("1.25"), service.defaultPageRateUsd());
    }

    @Test
    void defaultPageRateUsdNormalizesMissingRateToZero() {
        when(payoutSettingsService.currentSettings())
                .thenReturn(CreatorPayoutSettingEntity.builder()
                        .translatorTaskRateUsd(null)
                        .build());

        assertEquals(new BigDecimal("0.00"), service.defaultPageRateUsd());
    }

    @Test
    void deriveChapterRewardUsdMultipliesMinimumValidPageCountByConfiguredRate() {
        when(payoutSettingsService.currentSettings())
                .thenReturn(settingsWithRate("1.25"));

        assertEquals(new BigDecimal("1.25"), service.deriveChapterRewardUsd(1));
    }

    @Test
    void deriveChapterRewardUsdMultipliesMinPlusOnePageCountByConfiguredRate() {
        when(payoutSettingsService.currentSettings())
                .thenReturn(settingsWithRate("1.25"));

        assertEquals(new BigDecimal("2.50"), service.deriveChapterRewardUsd(2));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void deriveChapterRewardUsdRejectsNonPositivePageCount(int pageCount) {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.deriveChapterRewardUsd(pageCount)
        );

        assertEquals(400, error.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verifyNoInteractions(payoutSettingsService);
    }

    // ===== initialize page assignments =====

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
        assertEquals(new BigDecimal("1.00"), second.getResponsibilityFactor());
        assertEquals(PageStatus.DONE, second.getStatus());

        verify(pageRepository).saveAll(pages);
    }

    @Test
    void initializePageAssignmentsIgnoresNullTask() {
        service.initializePageAssignments(null, List.of());

        verifyNoInteractions(pageRepository);
    }

    @Test
    void initializePageAssignmentsIgnoresNullPages() {
        service.initializePageAssignments(
                TeamTaskEntity.builder().id(UUID.randomUUID()).build(),
                null
        );

        verifyNoInteractions(pageRepository);
    }

    // ===== handover =====

    @Test
    void handoverKeepsAcceptedPagesAndReassignsOnlyRemainingPages() {
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 4, translatorA);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        stubHandoverSave();

        HandoverTaskRequest request = handoverRequest(
                translatorB,
                List.of(1, 2),
                new BigDecimal("0.80"));

        UUID performedById = UUID.randomUUID();
        var response = service.handover(task, request, performedById);

        assertNotNull(response.getHandoverId());
        assertEquals(List.of(1, 2), response.getCompletedPageNumbers());
        assertEquals(2, response.getAcceptedPageCount());
        assertEquals(2, response.getReassignedPageCount());

        assertEquals(translatorA, pages.get(0).getAssignedTranslatorId());
        assertEquals(PageStatus.DONE, pages.get(0).getStatus());
        assertEquals(new BigDecimal("0.80"), pages.get(0).getResponsibilityFactor());
        assertNotNull(pages.get(0).getCompletedAt());

        assertEquals(translatorA, pages.get(1).getAssignedTranslatorId());
        assertEquals(translatorB, pages.get(2).getAssignedTranslatorId());
        assertEquals(PageStatus.TODO, pages.get(2).getStatus());
        assertNull(pages.get(2).getCompletedAt());
        assertEquals(translatorB, pages.get(3).getAssignedTranslatorId());

        assertEquals(translatorB, task.getAssigneeId());
        assertEquals("in_progress", task.getStatus());
        assertNull(task.getCompletedAt());
        assertNull(task.getRejectionReason());

        verify(pageRepository).saveAll(pages);
        verify(taskRepository).save(task);

        ArgumentCaptor<TaskHandoverEntity> handoverCaptor =
                ArgumentCaptor.forClass(TaskHandoverEntity.class);
        verify(handoverRepository).save(handoverCaptor.capture());

        TaskHandoverEntity audit = handoverCaptor.getValue();
        assertEquals(task.getId(), audit.getTaskId());
        assertEquals(translatorA, audit.getFromTranslatorId());
        assertEquals(translatorB, audit.getToTranslatorId());
        assertEquals(performedById, audit.getPerformedById());
        assertEquals(new BigDecimal("0.80"), audit.getResponsibilityFactor());
        assertEquals(2, audit.getAcceptedPageCount());
        assertEquals(2, audit.getReassignedPageCount());
        assertNotNull(audit.getHandedOverAt());
        assertFalse(Boolean.TRUE.equals(audit.getDeleted()));
    }

    @Test
    void handoverRejectsMissingTask() {
        HandoverTaskRequest request = handoverRequest(
                UUID.randomUUID(),
                List.of(),
                BigDecimal.ONE);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(null, request, UUID.randomUUID())
        );

        assertEquals(404, error.getCode());
        verifyNoInteractions(pageRepository, taskRepository, handoverRepository);
    }

    @Test
    void handoverRejectsTaskWithCompletedAt() {
        TeamTaskEntity task = handoverTask(UUID.randomUUID());
        task.setCompletedAt(Instant.now());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(
                        task,
                        handoverRequest(
                                UUID.randomUUID(),
                                List.of(),
                                BigDecimal.ONE),
                        UUID.randomUUID()
                )
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(pageRepository, taskRepository, handoverRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "complete", "done", "published", " COMPLETED "})
    void handoverRejectsCompletedTaskStatus(String completedStatus) {
        TeamTaskEntity task = handoverTask(UUID.randomUUID());
        task.setStatus(completedStatus);
        task.setCompletedAt(null);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(
                        task,
                        handoverRequest(
                                UUID.randomUUID(),
                                List.of(),
                                BigDecimal.ONE),
                        UUID.randomUUID()
                )
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(pageRepository, taskRepository, handoverRepository);
    }

    @Test
    void handoverRejectsTaskWithoutCurrentAssignee() {
        TeamTaskEntity task = handoverTask(null);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(
                        task,
                        handoverRequest(
                                UUID.randomUUID(),
                                List.of(),
                                BigDecimal.ONE),
                        UUID.randomUUID()
                )
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(pageRepository, taskRepository, handoverRepository);
    }

    @Test
    void handoverRejectsSameNewAssignee() {
        UUID translator = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translator);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(
                        task,
                        handoverRequest(
                                translator,
                                List.of(),
                                BigDecimal.ONE),
                        UUID.randomUUID()
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(pageRepository, taskRepository, handoverRepository);
    }

    @Test
    void handoverRejectsTaskWithoutPages() {
        UUID translatorA = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(List.of());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(
                        task,
                        handoverRequest(
                                UUID.randomUUID(),
                                List.of(),
                                BigDecimal.ONE),
                        UUID.randomUUID()
                )
        );

        assertEquals(409, error.getCode());
        verify(taskRepository, never()).save(any());
        verify(handoverRepository, never()).save(any());
    }

    @Test
    void handoverRejectsAcceptedPageNumberNotInTask() {
        UUID translatorA = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages(task, 2, translatorA));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(
                        task,
                        handoverRequest(
                                UUID.randomUUID(),
                                List.of(3),
                                BigDecimal.ONE),
                        UUID.randomUUID()
                )
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("Page 3"));
        verify(pageRepository, never()).saveAll(any());
        verify(taskRepository, never()).save(any());
        verify(handoverRepository, never()).save(any());
    }

    @Test
    void handoverRejectsPageAlreadyCreditedToAnotherTranslator() {
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();
        UUID translatorC = UUID.randomUUID();

        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 2, translatorA);
        pages.get(0).setAssignedTranslatorId(translatorC);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(
                        task,
                        handoverRequest(
                                translatorB,
                                List.of(1),
                                BigDecimal.ONE),
                        UUID.randomUUID()
                )
        );

        assertEquals(409, error.getCode());
        assertEquals(
                "Page 1 belongs to another translator and cannot be credited again",
                error.getMessage()
        );
        verify(pageRepository, never()).saveAll(any());
        verify(taskRepository, never()).save(any());
        verify(handoverRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.01"})
    void handoverRejectsResponsibilityFactorOutsideInclusiveRange(String factor) {
        TeamTaskEntity task = handoverTask(UUID.randomUUID());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.handover(
                        task,
                        handoverRequest(
                                UUID.randomUUID(),
                                List.of(),
                                new BigDecimal(factor)),
                        UUID.randomUUID()
                )
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("between 0.00 and 1.00"));
        verifyNoInteractions(pageRepository, taskRepository, handoverRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "0.01", "0.99", "1.00"})
    void handoverAcceptsResponsibilityFactorBoundaryValues(String factor) {
        UUID translatorA = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 1, translatorA);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        stubHandoverSave();

        service.handover(
                task,
                handoverRequest(
                        UUID.randomUUID(),
                        List.of(1),
                        new BigDecimal(factor)),
                UUID.randomUUID()
        );

        assertEquals(
                new BigDecimal(factor).setScale(2),
                pages.get(0).getResponsibilityFactor()
        );
    }

    @Test
    void handoverDefaultsMissingResponsibilityFactorToOne() {
        UUID translatorA = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 1, translatorA);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        stubHandoverSave();

        service.handover(
                task,
                handoverRequest(
                        UUID.randomUUID(),
                        List.of(1),
                        null),
                UUID.randomUUID()
        );

        assertEquals(new BigDecimal("1.00"), pages.get(0).getResponsibilityFactor());
    }

    @Test
    void handoverTreatsMissingCompletedPageNumbersAsNoAcceptedPages() {
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 2, translatorA);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        stubHandoverSave();

        HandoverTaskRequest request = handoverRequest(
                translatorB,
                null,
                BigDecimal.ONE);

        var response = service.handover(task, request, UUID.randomUUID());

        assertEquals(0, response.getAcceptedPageCount());
        assertEquals(2, response.getReassignedPageCount());
        assertEquals(List.of(), response.getCompletedPageNumbers());
        assertEquals(translatorB, pages.get(0).getAssignedTranslatorId());
        assertEquals(translatorB, pages.get(1).getAssignedTranslatorId());
    }

    @Test
    void handoverLeavesPagesOwnedByAnotherTranslatorUnchangedWhenNotAccepted() {
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();
        UUID translatorC = UUID.randomUUID();

        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 2, translatorA);
        pages.get(1).setAssignedTranslatorId(translatorC);
        pages.get(1).setStatus(PageStatus.DONE);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        stubHandoverSave();

        var response = service.handover(
                task,
                handoverRequest(
                        translatorB,
                        List.of(),
                        BigDecimal.ONE),
                UUID.randomUUID()
        );

        assertEquals(1, response.getReassignedPageCount());
        assertEquals(translatorB, pages.get(0).getAssignedTranslatorId());
        assertEquals(translatorC, pages.get(1).getAssignedTranslatorId());
        assertEquals(PageStatus.DONE, pages.get(1).getStatus());
    }

    @Test
    void handoverAllowsPreviouslyUnassignedAcceptedPageToBeCreditedToCurrentTranslator() {
        UUID translatorA = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 1, translatorA);
        pages.get(0).setAssignedTranslatorId(null);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        stubHandoverSave();

        service.handover(
                task,
                handoverRequest(
                        UUID.randomUUID(),
                        List.of(1),
                        new BigDecimal("0.75")),
                UUID.randomUUID()
        );

        assertEquals(translatorA, pages.get(0).getAssignedTranslatorId());
        assertEquals(PageStatus.DONE, pages.get(0).getStatus());
        assertEquals(new BigDecimal("0.75"), pages.get(0).getResponsibilityFactor());
    }

    @Test
    void handoverPreservesExistingCompletedAtForAcceptedPage() {
        UUID translatorA = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 1, translatorA);
        Instant completedAt = Instant.parse("2026-08-01T00:00:00Z");
        pages.get(0).setCompletedAt(completedAt);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        stubHandoverSave();

        service.handover(
                task,
                handoverRequest(
                        UUID.randomUUID(),
                        List.of(1),
                        BigDecimal.ONE),
                UUID.randomUUID()
        );

        assertEquals(completedAt, pages.get(0).getCompletedAt());
    }

    @Test
    void handoverDeduplicatesAndSortsAcceptedPageNumbers() {
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();

        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 3, translatorA);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        stubHandoverSave();

        var response = service.handover(
                task,
                handoverRequest(
                        translatorB,
                        List.of(3, 1, 2, 1, 3),
                        new BigDecimal("0.90")),
                UUID.randomUUID()
        );

        assertEquals(List.of(1, 2, 3), response.getCompletedPageNumbers());
        assertEquals(3, response.getAcceptedPageCount());
        assertEquals(0, response.getReassignedPageCount());

        pages.forEach(page -> {
            assertEquals(translatorA, page.getAssignedTranslatorId());
            assertEquals(PageStatus.DONE, page.getStatus());
            assertEquals(new BigDecimal("0.90"), page.getResponsibilityFactor());
        });

        ArgumentCaptor<TaskHandoverEntity> captor =
                ArgumentCaptor.forClass(TaskHandoverEntity.class);
        verify(handoverRepository).save(captor.capture());

        TaskHandoverEntity audit = captor.getValue();
        assertEquals(3, audit.getAcceptedPageCount());
        assertEquals(0, audit.getReassignedPageCount());
        assertEquals("[1,2,3]", audit.getAcceptedPageNumbers());
    }

    @Test
    void handoverJsonSerializationFailureReturnsInternalServerError() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        TranslatorPaymentService failingService = new TranslatorPaymentService(
                taskRepository,
                pageRepository,
                handoverRepository,
                settlementRepository,
                earningRepository,
                payoutSettingsService,
                failingMapper
        );
        ReflectionTestUtils.setField(
                failingService,
                "payoutTimeZone",
                "Asia/Ho_Chi_Minh"
        );

        UUID translatorA = UUID.randomUUID();
        TeamTaskEntity task = handoverTask(translatorA);
        List<PageTranslationEntity> pages = pages(task, 1, translatorA);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failed") {});

        CustomException error = assertThrows(
                CustomException.class,
                () -> failingService.handover(
                        task,
                        handoverRequest(
                                UUID.randomUUID(),
                                List.of(1),
                                BigDecimal.ONE),
                        UUID.randomUUID()
                )
        );

        assertEquals(500, error.getCode());
        assertTrue(error.getMessage().contains("handover page list"));

        // The direct unit call reaches these repository methods before JSON serialization.
        // Transactional database rollback must be verified by an integration test.
        verify(pageRepository).saveAll(pages);
        verify(taskRepository).save(task);
        verify(handoverRepository, never()).save(any());
    }

    // ===== update page status =====

    @Test
    void updatePageStatusRejectsMissingPage() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updatePageStatus(null, PageStatus.DONE)
        );

        assertEquals(404, error.getCode());
        verifyNoInteractions(pageRepository);
    }

    @Test
    void updatePageStatusRejectsMissingStatus() {
        PageTranslationEntity page = page(
                TeamTaskEntity.builder().id(UUID.randomUUID()).build(),
                1,
                UUID.randomUUID()
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updatePageStatus(page, null)
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(pageRepository);
    }

    @Test
    void updatePageStatusDoneSetsCompletedAt() {
        PageTranslationEntity page = page(
                TeamTaskEntity.builder().id(UUID.randomUUID()).build(),
                1,
                UUID.randomUUID()
        );
        page.setStatus(PageStatus.TODO);
        when(pageRepository.save(page)).thenReturn(page);

        PageTranslationEntity result =
                service.updatePageStatus(page, PageStatus.DONE);

        assertSame(page, result);
        assertEquals(PageStatus.DONE, page.getStatus());
        assertNotNull(page.getCompletedAt());
        verify(pageRepository).save(page);
    }

    @Test
    void updatePageStatusTodoClearsCompletedAt() {
        PageTranslationEntity page = page(
                TeamTaskEntity.builder().id(UUID.randomUUID()).build(),
                1,
                UUID.randomUUID()
        );
        page.setStatus(PageStatus.DONE);
        page.setCompletedAt(Instant.now());
        when(pageRepository.save(page)).thenReturn(page);

        service.updatePageStatus(page, PageStatus.TODO);

        assertEquals(PageStatus.TODO, page.getStatus());
        assertNull(page.getCompletedAt());
        verify(pageRepository).save(page);
    }

    // ===== validate ready for review =====

    @Test
    void validateReadyForReviewRejectsTaskWithoutPages() {
        UUID taskId = UUID.randomUUID();
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId))
                .thenReturn(List.of());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.validateReadyForReview(taskId)
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("no pages"));
    }

    @Test
    void validateReadyForReviewReportsIncompletePagesBeforeUnassignedPages() {
        UUID taskId = UUID.randomUUID();
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(taskId)
                .projectTeamId(UUID.randomUUID())
                .build();

        List<PageTranslationEntity> pages = pages(task, 2, UUID.randomUUID());
        pages.get(0).setStatus(PageStatus.DONE);
        pages.get(1).setStatus(PageStatus.TODO);
        pages.get(1).setAssignedTranslatorId(null);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId))
                .thenReturn(pages);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.validateReadyForReview(taskId)
        );

        assertEquals(409, error.getCode());
        assertEquals(
                "All pages must be marked DONE before review. Incomplete pages: [2]",
                error.getMessage()
        );
    }

    @Test
    void validateReadyForReviewRejectsUnassignedDonePages() {
        UUID taskId = UUID.randomUUID();
        TeamTaskEntity task = TeamTaskEntity.builder().id(taskId).build();
        List<PageTranslationEntity> pages = pages(task, 2, UUID.randomUUID());
        pages.forEach(page -> page.setStatus(PageStatus.DONE));
        pages.get(1).setAssignedTranslatorId(null);

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId))
                .thenReturn(pages);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.validateReadyForReview(taskId)
        );

        assertEquals(409, error.getCode());
        assertEquals(
                "Every page must have a credited translator. Unassigned pages: [2]",
                error.getMessage()
        );
    }

    @Test
    void validateReadyForReviewAllowsAllDoneAndAssignedPages() {
        UUID taskId = UUID.randomUUID();
        TeamTaskEntity task = TeamTaskEntity.builder().id(taskId).build();
        List<PageTranslationEntity> pages = pages(task, 2, UUID.randomUUID());
        pages.forEach(page -> page.setStatus(PageStatus.DONE));

        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId))
                .thenReturn(pages);

        assertDoesNotThrow(() -> service.validateReadyForReview(taskId));
    }

    // ===== settlement validation / lifecycle =====

    @Test
    void settlementRejectsMissingTask() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(null)
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(
                settlementRepository,
                pageRepository,
                earningRepository,
                taskRepository
        );
    }

    @Test
    void settlementRejectsTaskWithoutId() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));
        task.setId(null);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(
                settlementRepository,
                pageRepository,
                earningRepository,
                taskRepository
        );
    }

    @Test
    void settlementRejectsTaskWithoutChapter() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));
        task.setChapter(null);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(
                settlementRepository,
                pageRepository,
                earningRepository,
                taskRepository
        );
    }

    @Test
    void settlementRejectsInvalidStatusEvenWhenCompletedAtExists() {
        TeamTaskEntity task = completedTask("under_review", new BigDecimal("10.00"));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("whole chapter is approved"));
        verifyNoInteractions(settlementRepository, pageRepository, earningRepository);
    }

    @Test
    void settlementRejectsCompletedStatusWithoutCompletedAt() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));
        task.setCompletedAt(null);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("whole chapter is approved"));
        verifyNoInteractions(settlementRepository, pageRepository, earningRepository);
    }

    @Test
    void settlementReturnsExistingActiveVersionWithoutDuplicatingLedgerRows() {
        TeamTaskEntity task = completedTask("published", null);
        TranslatorChapterSettlementEntity existing = settlement(
                task,
                1,
                TranslatorSettlementStatus.ACTIVE,
                "10.00",
                10
        );

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                task.getId(),
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.of(existing));

        TranslatorChapterSettlementEntity result =
                service.settleApprovedTask(task);

        assertSame(existing, result);
        verify(pageRepository, never())
                .findByTaskId_IdOrderByPageNumberAsc(any());
        verify(earningRepository, never()).saveAll(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void settlementRejectsChapterWithoutPages() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                task.getId(),
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(List.of());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("without pages"));
        verify(settlementRepository, never()).saveAndFlush(any());
        verify(earningRepository, never()).saveAll(any());
    }

    @Test
    void settlementRejectsIncompletePage() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));
        List<PageTranslationEntity> pages =
                pages(task, 2, UUID.randomUUID());
        pages.get(0).setStatus(PageStatus.DONE);
        pages.get(1).setStatus(PageStatus.TODO);

        stubNoActiveSettlement(task);
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("Page 2 is not completed"));
        verify(settlementRepository, never()).saveAndFlush(any());
        verify(earningRepository, never()).saveAll(any());
    }

    @Test
    void settlementRejectsUnassignedPage() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));
        List<PageTranslationEntity> pages =
                pages(task, 2, UUID.randomUUID());
        pages.forEach(page -> page.setStatus(PageStatus.DONE));
        pages.get(1).setAssignedTranslatorId(null);

        stubNoActiveSettlement(task);
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("Page 2 has no credited translator"));
        verify(settlementRepository, never()).saveAndFlush(any());
        verify(earningRepository, never()).saveAll(any());
    }

    // ===== settlement financial behavior =====

    @Test
    void settlementUsesUnifiedEarningLedgerAndSplitsRewardByCreditedPages() {
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();

        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("100.00")
        );
        List<PageTranslationEntity> pages = pages(task, 10, translatorA);
        for (PageTranslationEntity page : pages) {
            page.setStatus(PageStatus.DONE);
            page.setCompletedAt(Instant.now());
        }
        for (int index = 6; index < 10; index++) {
            pages.get(index).setAssignedTranslatorId(translatorB);
        }

        stubNewSettlement(task, pages, Optional.empty());

        TranslatorChapterSettlementEntity settlement =
                service.settleApprovedTask(task);

        List<TranslatorEarningEntryEntity> earnings = capturedEarnings();
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

        assertEquals(new BigDecimal("60.00"), totals.get(translatorA));
        assertEquals(new BigDecimal("40.00"), totals.get(translatorB));
        assertEquals(
                new BigDecimal("100.00"),
                earnings.stream()
                        .map(TranslatorEarningEntryEntity::getGrossAmountUsd)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        for (TranslatorEarningEntryEntity earning : earnings) {
            assertEquals(TranslatorEarningEntryType.PAGE_EARNING, earning.getEntryType());
            assertEquals(settlement.getId(), earning.getSettlementId());
            assertEquals(task.getId(), earning.getTaskId());
            assertEquals(task.getChapter().getId(), earning.getChapterId());
        }

        verify(taskRepository).save(task);
    }

    @Test
    void settlementDerivesRewardFromConfiguredPageRateWhenTaskHasNoReward() {
        TeamTaskEntity task = completedTask("done", null);
        List<PageTranslationEntity> pages =
                pages(task, 3, UUID.randomUUID());
        pages.forEach(page -> page.setStatus(PageStatus.DONE));

        when(payoutSettingsService.currentSettings())
                .thenReturn(settingsWithRate("1.20"));
        stubNewSettlement(task, pages, Optional.empty());

        service.settleApprovedTask(task);

        assertEquals(new BigDecimal("3.60"), task.getChapterRewardUsd());
        verify(earningRepository).saveAll(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00"})
    void settlementDerivesRewardWhenTaskRewardIsNonPositive(String taskReward) {
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal(taskReward)
        );
        List<PageTranslationEntity> pages =
                pages(task, 2, UUID.randomUUID());
        pages.forEach(page -> page.setStatus(PageStatus.DONE));

        when(payoutSettingsService.currentSettings())
                .thenReturn(settingsWithRate("1.50"));
        stubNewSettlement(task, pages, Optional.empty());

        service.settleApprovedTask(task);

        assertEquals(new BigDecimal("3.00"), task.getChapterRewardUsd());
    }

    @Test
    void settlementNormalizesPositiveChapterRewardToTwoDecimals() {
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("10.005")
        );
        List<PageTranslationEntity> pages =
                pages(task, 1, UUID.randomUUID());
        pages.get(0).setStatus(PageStatus.DONE);

        stubNewSettlement(task, pages, Optional.empty());

        TranslatorChapterSettlementEntity settlement =
                service.settleApprovedTask(task);

        assertEquals(new BigDecimal("10.01"), settlement.getTotalChapterRewardUsd());
        assertEquals(new BigDecimal("10.01"), capturedEarnings().get(0).getGrossAmountUsd());
    }

    @Test
    void settlementAssignsRoundingRemainderToLastPage() {
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("10.00")
        );
        List<PageTranslationEntity> pages =
                pages(task, 3, UUID.randomUUID());
        pages.forEach(page -> page.setStatus(PageStatus.DONE));

        stubNewSettlement(task, pages, Optional.empty());

        TranslatorChapterSettlementEntity settlement =
                service.settleApprovedTask(task);

        assertEquals(
                new BigDecimal("3.333333"),
                settlement.getPageRateUsd()
        );

        List<TranslatorEarningEntryEntity> earnings = capturedEarnings();
        assertEquals(
                List.of(
                        new BigDecimal("3.33"),
                        new BigDecimal("3.33"),
                        new BigDecimal("3.34")
                ),
                earnings.stream()
                        .map(TranslatorEarningEntryEntity::getGrossAmountUsd)
                        .toList()
        );
        assertEquals(
                new BigDecimal("10.00"),
                earnings.stream()
                        .map(TranslatorEarningEntryEntity::getGrossAmountUsd)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    @Test
    void settlementAppliesResponsibilityFactorToNetEarning() {
        UUID translator = UUID.randomUUID();
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("10.00")
        );
        List<PageTranslationEntity> pages = pages(task, 1, translator);
        pages.get(0).setStatus(PageStatus.DONE);
        pages.get(0).setResponsibilityFactor(new BigDecimal("0.80"));

        stubNewSettlement(task, pages, Optional.empty());

        service.settleApprovedTask(task);

        TranslatorEarningEntryEntity earning = capturedEarnings().get(0);
        assertEquals(new BigDecimal("10.00"), earning.getGrossAmountUsd());
        assertEquals(new BigDecimal("0.80"), earning.getResponsibilityFactor());
        assertEquals(new BigDecimal("8.00"), earning.getAmountUsd());
        assertEquals(translator, earning.getTranslatorId());
    }

    @Test
    void settlementRoundsResponsibilityFactorToTwoDecimals() {
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("10.00")
        );
        List<PageTranslationEntity> pages =
                pages(task, 1, UUID.randomUUID());
        pages.get(0).setStatus(PageStatus.DONE);
        pages.get(0).setResponsibilityFactor(new BigDecimal("0.805"));

        stubNewSettlement(task, pages, Optional.empty());

        service.settleApprovedTask(task);

        TranslatorEarningEntryEntity earning = capturedEarnings().get(0);
        assertEquals(new BigDecimal("0.81"), earning.getResponsibilityFactor());
        assertEquals(new BigDecimal("10.00"), earning.getGrossAmountUsd());
        assertEquals(new BigDecimal("8.10"), earning.getAmountUsd());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.01"})
    void settlementRejectsResponsibilityFactorOutsideInclusiveRange(String factor) {
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("10.00")
        );
        List<PageTranslationEntity> pages =
                pages(task, 1, UUID.randomUUID());
        pages.get(0).setStatus(PageStatus.DONE);
        pages.get(0).setResponsibilityFactor(new BigDecimal(factor));

        stubNewSettlement(task, pages, Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.settleApprovedTask(task)
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("between 0.00 and 1.00"));
        verify(earningRepository, never()).saveAll(any());
        verify(taskRepository, never()).save(task);
    }

    @Test
    void settlementDefaultsMissingResponsibilityFactorToOne() {
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("10.00")
        );
        List<PageTranslationEntity> pages =
                pages(task, 1, UUID.randomUUID());
        pages.get(0).setStatus(PageStatus.DONE);
        pages.get(0).setResponsibilityFactor(null);

        stubNewSettlement(task, pages, Optional.empty());

        service.settleApprovedTask(task);

        TranslatorEarningEntryEntity earning = capturedEarnings().get(0);
        assertEquals(new BigDecimal("1.00"), earning.getResponsibilityFactor());
        assertEquals(new BigDecimal("10.00"), earning.getAmountUsd());
    }

    @Test
    void settlementIncrementsVersionAfterPreviousReversedSettlement() {
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("10.00")
        );
        List<PageTranslationEntity> pages =
                pages(task, 1, UUID.randomUUID());
        pages.get(0).setStatus(PageStatus.DONE);

        TranslatorChapterSettlementEntity previous = settlement(
                task,
                2,
                TranslatorSettlementStatus.REVERSED,
                "10.00",
                1
        );

        stubNewSettlement(task, pages, Optional.of(previous));

        TranslatorChapterSettlementEntity result =
                service.settleApprovedTask(task);

        assertEquals(3, result.getVersionNo());
        assertEquals(TranslatorSettlementStatus.ACTIVE, result.getStatus());
    }

    // ===== settlement reversal =====

    @Test
    void reverseLatestSettlementDoesNothingWhenNoActiveSettlementExists() {
        UUID taskId = UUID.randomUUID();
        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId,
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.empty());

        service.reverseLatestSettlement(taskId, "reason");

        verifyNoInteractions(earningRepository);
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void reverseLatestSettlementWritesNegativeLedgerAdjustmentsAndMarksSettlementReversed() {
        UUID taskId = UUID.randomUUID();
        UUID translatorA = UUID.randomUUID();
        UUID translatorB = UUID.randomUUID();

        TranslatorChapterSettlementEntity settlement =
                activeSettlement(taskId, "100.00", 2);
        List<TranslatorEarningEntryEntity> pageEarnings = List.of(
                pageEarning(
                        settlement.getId(),
                        taskId,
                        translatorA,
                        new BigDecimal("60.00")
                ),
                pageEarning(
                        settlement.getId(),
                        taskId,
                        translatorB,
                        new BigDecimal("40.00")
                )
        );

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId,
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.of(settlement));
        when(earningRepository.findAllBySettlementIdAndEntryType(
                settlement.getId(),
                TranslatorEarningEntryType.PAGE_EARNING
        )).thenReturn(pageEarnings);

        service.reverseLatestSettlement(taskId, " Chapter was re-opened ");

        List<TranslatorEarningEntryEntity> adjustments = capturedEarnings();
        assertEquals(2, adjustments.size());

        var totals = adjustments.stream().collect(Collectors.toMap(
                TranslatorEarningEntryEntity::getTranslatorId,
                TranslatorEarningEntryEntity::getAmountUsd
        ));

        assertEquals(new BigDecimal("-60.00"), totals.get(translatorA));
        assertEquals(new BigDecimal("-40.00"), totals.get(translatorB));

        adjustments.forEach(item -> {
            assertEquals(
                    TranslatorEarningEntryType.REVERSAL_ADJUSTMENT,
                    item.getEntryType()
            );
            assertEquals("Chapter was re-opened", item.getReason());
            assertEquals(settlement.getId(), item.getSettlementId());
            assertEquals(taskId, item.getTaskId());
        });

        assertEquals(TranslatorSettlementStatus.REVERSED, settlement.getStatus());
        assertEquals("Chapter was re-opened", settlement.getReversalReason());
        assertNotNull(settlement.getReversedAt());
        verify(settlementRepository).save(settlement);
    }

    @Test
    void reverseLatestSettlementSecondCallDoesNotCreateDuplicateAdjustments() {
        UUID taskId = UUID.randomUUID();
        UUID translatorId = UUID.randomUUID();

        TranslatorChapterSettlementEntity settlement =
                activeSettlement(taskId, "10.00", 1);

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId,
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.of(settlement), Optional.empty());

        when(earningRepository.findAllBySettlementIdAndEntryType(
                settlement.getId(),
                TranslatorEarningEntryType.PAGE_EARNING
        )).thenReturn(List.of(
                pageEarning(
                        settlement.getId(),
                        taskId,
                        translatorId,
                        new BigDecimal("10.00")
                )
        ));

        service.reverseLatestSettlement(taskId, "reopen");
        service.reverseLatestSettlement(taskId, "reopen");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TranslatorEarningEntryEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(earningRepository, times(1)).saveAll(captor.capture());

        List<TranslatorEarningEntryEntity> adjustments = captor.getValue();
        assertEquals(1, adjustments.size());
        assertEquals(
                TranslatorEarningEntryType.REVERSAL_ADJUSTMENT,
                adjustments.get(0).getEntryType()
        );
        assertEquals(new BigDecimal("-10.00"), adjustments.get(0).getAmountUsd());
        assertEquals(TranslatorSettlementStatus.REVERSED, settlement.getStatus());

        verify(settlementRepository, times(1)).save(settlement);
        verify(settlementRepository, times(2))
                .findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                        taskId,
                        TranslatorSettlementStatus.ACTIVE
                );
    }

    @Test
    void reverseLatestSettlementSkipsZeroAmountAdjustments() {
        UUID taskId = UUID.randomUUID();
        UUID zeroTranslator = UUID.randomUUID();
        UUID paidTranslator = UUID.randomUUID();

        TranslatorChapterSettlementEntity settlement =
                activeSettlement(taskId, "10.00", 2);

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId,
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.of(settlement));

        when(earningRepository.findAllBySettlementIdAndEntryType(
                settlement.getId(),
                TranslatorEarningEntryType.PAGE_EARNING
        )).thenReturn(List.of(
                pageEarning(
                        settlement.getId(),
                        taskId,
                        zeroTranslator,
                        BigDecimal.ZERO.setScale(2)
                ),
                pageEarning(
                        settlement.getId(),
                        taskId,
                        paidTranslator,
                        new BigDecimal("10.00")
                )
        ));

        service.reverseLatestSettlement(taskId, "reopen");

        List<TranslatorEarningEntryEntity> adjustments = capturedEarnings();
        assertEquals(1, adjustments.size());
        assertEquals(paidTranslator, adjustments.get(0).getTranslatorId());
        assertEquals(new BigDecimal("-10.00"), adjustments.get(0).getAmountUsd());

        assertEquals(TranslatorSettlementStatus.REVERSED, settlement.getStatus());
        assertNotNull(settlement.getReversedAt());
        verify(settlementRepository).save(settlement);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void reverseLatestSettlementUsesDefaultReasonWhenReasonMissing(String reason) {
        UUID taskId = UUID.randomUUID();
        UUID translatorId = UUID.randomUUID();

        TranslatorChapterSettlementEntity settlement =
                activeSettlement(taskId, "10.00", 1);

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId,
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.of(settlement));
        when(earningRepository.findAllBySettlementIdAndEntryType(
                settlement.getId(),
                TranslatorEarningEntryType.PAGE_EARNING
        )).thenReturn(List.of(
                pageEarning(
                        settlement.getId(),
                        taskId,
                        translatorId,
                        new BigDecimal("10.00")
                )
        ));

        service.reverseLatestSettlement(taskId, reason);

        TranslatorEarningEntryEntity adjustment = capturedEarnings().get(0);
        assertEquals("Translation settlement reversed", adjustment.getReason());
        assertEquals(
                "Translation settlement reversed",
                settlement.getReversalReason()
        );
    }

    @Test
    void reverseLatestSettlementAggregatesMultiplePageEarningsPerTranslator() {
        UUID taskId = UUID.randomUUID();
        UUID translatorId = UUID.randomUUID();

        TranslatorChapterSettlementEntity settlement =
                activeSettlement(taskId, "50.00", 2);

        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                taskId,
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.of(settlement));
        when(earningRepository.findAllBySettlementIdAndEntryType(
                settlement.getId(),
                TranslatorEarningEntryType.PAGE_EARNING
        )).thenReturn(List.of(
                pageEarning(
                        settlement.getId(),
                        taskId,
                        translatorId,
                        new BigDecimal("30.00")
                ),
                pageEarning(
                        settlement.getId(),
                        taskId,
                        translatorId,
                        new BigDecimal("20.00")
                )
        ));

        service.reverseLatestSettlement(taskId, "reopen");

        List<TranslatorEarningEntryEntity> adjustments = capturedEarnings();
        assertEquals(1, adjustments.size());
        assertEquals(translatorId, adjustments.get(0).getTranslatorId());
        assertEquals(new BigDecimal("-50.00"), adjustments.get(0).getAmountUsd());
    }

    // ===== completed-task settlement backfill =====

    @Test
    void backfillCompletedTasksSkipsTaskWithoutChapter() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));
        task.setChapter(null);
        when(taskRepository.findAllCompletedForSettlementBackfill())
                .thenReturn(List.of(task));

        assertEquals(0, service.backfillCompletedTasks());

        verifyNoInteractions(pageRepository, settlementRepository, earningRepository);
    }

    @Test
    void backfillCompletedTasksSkipsTaskWithoutAssignee() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));
        task.setAssigneeId(null);
        when(taskRepository.findAllCompletedForSettlementBackfill())
                .thenReturn(List.of(task));

        assertEquals(0, service.backfillCompletedTasks());

        verifyNoInteractions(pageRepository, settlementRepository, earningRepository);
    }

    @Test
    void backfillCompletedTasksSkipsTaskWithExistingSettlementVersion() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));
        TranslatorChapterSettlementEntity existing = settlement(
                task,
                1,
                TranslatorSettlementStatus.REVERSED,
                "10.00",
                1
        );

        when(taskRepository.findAllCompletedForSettlementBackfill())
                .thenReturn(List.of(task));
        when(settlementRepository.findFirstByTaskIdOrderByVersionNoDesc(task.getId()))
                .thenReturn(Optional.of(existing));

        assertEquals(0, service.backfillCompletedTasks());

        verify(pageRepository, never())
                .findByTaskId_IdOrderByPageNumberAsc(any());
        verify(earningRepository, never()).saveAll(any());
    }

    @Test
    void backfillCompletedTasksSkipsTaskWithoutPages() {
        TeamTaskEntity task = completedTask("completed", new BigDecimal("10.00"));

        when(taskRepository.findAllCompletedForSettlementBackfill())
                .thenReturn(List.of(task));
        when(settlementRepository.findFirstByTaskIdOrderByVersionNoDesc(task.getId()))
                .thenReturn(Optional.empty());
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(List.of());

        assertEquals(0, service.backfillCompletedTasks());

        verify(earningRepository, never()).saveAll(any());
        verify(settlementRepository, never()).saveAndFlush(any());
    }

    @Test
    void backfillCompletedTasksInitializesLegacyPagesAndSettlesTask() {
        UUID translatorId = UUID.randomUUID();
        TeamTaskEntity task = completedTask(
                "completed",
                new BigDecimal("10.00")
        );
        task.setAssigneeId(translatorId);
        Instant completedAt = Instant.parse("2026-08-01T08:00:00Z");
        task.setCompletedAt(completedAt);

        PageTranslationEntity first = page(task, 1, null);
        first.setResponsibilityFactor(null);
        first.setStatus(PageStatus.TODO);
        first.setCompletedAt(null);

        PageTranslationEntity second = page(task, 2, translatorId);
        second.setResponsibilityFactor(new BigDecimal("0.75"));
        second.setStatus(PageStatus.TODO);
        Instant existingCompletedAt = Instant.parse("2026-07-31T08:00:00Z");
        second.setCompletedAt(existingCompletedAt);

        List<PageTranslationEntity> pages = List.of(first, second);

        when(taskRepository.findAllCompletedForSettlementBackfill())
                .thenReturn(List.of(task));
        when(settlementRepository.findFirstByTaskIdOrderByVersionNoDesc(task.getId()))
                .thenReturn(Optional.empty());
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                task.getId(),
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(settlementRepository.saveAndFlush(
                any(TranslatorChapterSettlementEntity.class)
        )).thenAnswer(invocation -> {
            TranslatorChapterSettlementEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        int count = service.backfillCompletedTasks();

        assertEquals(1, count);

        assertEquals(translatorId, first.getAssignedTranslatorId());
        assertEquals(new BigDecimal("1.00"), first.getResponsibilityFactor());
        assertEquals(PageStatus.DONE, first.getStatus());
        assertEquals(completedAt, first.getCompletedAt());

        assertEquals(translatorId, second.getAssignedTranslatorId());
        assertEquals(new BigDecimal("0.75"), second.getResponsibilityFactor());
        assertEquals(PageStatus.DONE, second.getStatus());
        assertEquals(existingCompletedAt, second.getCompletedAt());

        verify(pageRepository).saveAll(pages);
        verify(settlementRepository).saveAndFlush(
                any(TranslatorChapterSettlementEntity.class)
        );
        verify(earningRepository).saveAll(any());
        verify(taskRepository).save(task);
    }

    // ===== helpers =====

    private CreatorPayoutSettingEntity settingsWithRate(String rate) {
        return CreatorPayoutSettingEntity.builder()
                .translatorTaskRateUsd(new BigDecimal(rate))
                .build();
    }

    private TeamTaskEntity handoverTask(UUID assigneeId) {
        return TeamTaskEntity.builder()
                .id(UUID.randomUUID())
                .projectTeamId(UUID.randomUUID())
                .assigneeId(assigneeId)
                .status("in_progress")
                .build();
    }

    private HandoverTaskRequest handoverRequest(
            UUID newAssigneeId,
            List<Integer> completedPageNumbers,
            BigDecimal factor) {
        HandoverTaskRequest request = new HandoverTaskRequest();
        request.setNewAssigneeId(newAssigneeId);
        request.setCompletedPageNumbers(completedPageNumbers);
        request.setResponsibilityFactor(factor);
        return request;
    }

    private void stubHandoverSave() {
        when(handoverRepository.save(any(TaskHandoverEntity.class)))
                .thenAnswer(invocation -> {
                    TaskHandoverEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });
    }

    private TeamTaskEntity completedTask(
            String status,
            BigDecimal chapterReward
    ) {
        ChapterEntity chapter = new ChapterEntity();
        chapter.setId(UUID.randomUUID());

        return TeamTaskEntity.builder()
                .id(UUID.randomUUID())
                .projectTeamId(UUID.randomUUID())
                .chapter(chapter)
                .status(status)
                .assigneeId(UUID.randomUUID())
                .completedAt(Instant.now())
                .chapterRewardUsd(chapterReward)
                .build();
    }

    private TranslatorChapterSettlementEntity settlement(
            TeamTaskEntity task,
            int version,
            TranslatorSettlementStatus status,
            String reward,
            int totalPages
    ) {
        TranslatorChapterSettlementEntity settlement =
                TranslatorChapterSettlementEntity.builder()
                        .taskId(task.getId())
                        .chapterId(task.getChapter().getId())
                        .projectTeamId(task.getProjectTeamId())
                        .versionNo(version)
                        .settlementMonth("2026-08")
                        .settledAt(Instant.now())
                        .totalChapterRewardUsd(new BigDecimal(reward))
                        .totalPages(totalPages)
                        .pageRateUsd(
                                new BigDecimal(reward)
                                        .divide(
                                                BigDecimal.valueOf(totalPages),
                                                6,
                                                java.math.RoundingMode.HALF_UP
                                        )
                        )
                        .status(status)
                        .build();
        settlement.setId(UUID.randomUUID());
        return settlement;
    }

    private TranslatorChapterSettlementEntity activeSettlement(
            UUID taskId,
            String reward,
            int totalPages
    ) {
        TranslatorChapterSettlementEntity settlement =
                TranslatorChapterSettlementEntity.builder()
                        .taskId(taskId)
                        .chapterId(UUID.randomUUID())
                        .projectTeamId(UUID.randomUUID())
                        .versionNo(1)
                        .settlementMonth("2026-08")
                        .settledAt(Instant.now())
                        .totalChapterRewardUsd(new BigDecimal(reward))
                        .totalPages(totalPages)
                        .pageRateUsd(
                                new BigDecimal(reward)
                                        .divide(
                                                BigDecimal.valueOf(totalPages),
                                                6,
                                                java.math.RoundingMode.HALF_UP
                                        )
                        )
                        .status(TranslatorSettlementStatus.ACTIVE)
                        .build();
        settlement.setId(UUID.randomUUID());
        return settlement;
    }

    private void stubNoActiveSettlement(TeamTaskEntity task) {
        when(settlementRepository.findFirstByTaskIdAndStatusOrderByVersionNoDesc(
                task.getId(),
                TranslatorSettlementStatus.ACTIVE
        )).thenReturn(Optional.empty());
    }

    private void stubNewSettlement(
            TeamTaskEntity task,
            List<PageTranslationEntity> pages,
            Optional<TranslatorChapterSettlementEntity> latestVersion
    ) {
        stubNoActiveSettlement(task);
        when(pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(pages);
        when(settlementRepository.findFirstByTaskIdOrderByVersionNoDesc(task.getId()))
                .thenReturn(latestVersion);
        when(settlementRepository.saveAndFlush(
                any(TranslatorChapterSettlementEntity.class)
        )).thenAnswer(invocation -> {
            TranslatorChapterSettlementEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
    }

    @SuppressWarnings("unchecked")
    private List<TranslatorEarningEntryEntity> capturedEarnings() {
        ArgumentCaptor<List<TranslatorEarningEntryEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(earningRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private TranslatorEarningEntryEntity pageEarning(
            UUID settlementId,
            UUID taskId,
            UUID translatorId,
            BigDecimal amount
    ) {
        TranslatorEarningEntryEntity entry =
                TranslatorEarningEntryEntity.builder()
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

    private PageTranslationEntity page(
            TeamTaskEntity task,
            int pageNumber,
            UUID translatorId
    ) {
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
        return page;
    }

    private List<PageTranslationEntity> pages(
            TeamTaskEntity task,
            int count,
            UUID translatorId
    ) {
        List<PageTranslationEntity> pages = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= count; pageNumber++) {
            pages.add(page(task, pageNumber, translatorId));
        }
        return pages;
    }
}
