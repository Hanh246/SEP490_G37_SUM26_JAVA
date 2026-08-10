package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.HandoverTaskRequest;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.TranslatorChapterSettlementEntity;
import com.sep.comiverse.entity.TranslatorEarningEntryEntity;
import com.sep.comiverse.entity.enums.PageStatus;
import com.sep.comiverse.entity.enums.TranslatorSettlementStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ICreatorPayoutCurrencyRepository;
import com.sep.comiverse.repository.ICreatorPayoutSettingRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslatorPaymentServiceTest {

    @Mock private ITeamTaskRepository taskRepository;
    @Mock private IPageTranslationRepository pageRepository;
    @Mock private ITaskHandoverRepository handoverRepository;
    @Mock private ITranslatorChapterSettlementRepository settlementRepository;
    @Mock private ITranslatorEarningEntryRepository earningRepository;
    @Mock private ICreatorPayoutSettingRepository payoutSettingRepository;
    @Mock private ICreatorPayoutCurrencyRepository payoutCurrencyRateRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private TranslatorPaymentService service;

    @BeforeEach
    void setUp() {
        CreatorPayoutSettingsService settingsService = new CreatorPayoutSettingsService(
                payoutSettingRepository,
                payoutCurrencyRateRepository,
                jdbcTemplate
        );
        service = new TranslatorPaymentService(
                taskRepository,
                pageRepository,
                handoverRepository,
                settlementRepository,
                earningRepository,
                settingsService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "payoutTimeZone", "Asia/Ho_Chi_Minh");
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
        request.setReason("Translator A stopped before finishing the chapter");

        service.handover(task, request, UUID.randomUUID());

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
    void settlementSplitsChapterRewardByCreditedPagesAndCoefficient() {
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

        service.settleApprovedTask(task);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TranslatorEarningEntryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(earningRepository).saveAll(captor.capture());
        List<TranslatorEarningEntryEntity> earnings = captor.getValue();
        assertEquals(10, earnings.size());

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

        assertEquals(409, exception.getCode());
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
