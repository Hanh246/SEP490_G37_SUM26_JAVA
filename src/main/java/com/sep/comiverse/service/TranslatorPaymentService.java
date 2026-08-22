package com.sep.comiverse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.HandoverTaskRequest;
import com.sep.comiverse.dto.response.TaskHandoverResponse;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.entity.enums.PageStatus;
import com.sep.comiverse.entity.enums.TranslatorEarningEntryType;
import com.sep.comiverse.entity.enums.TranslatorSettlementStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TranslatorPaymentService {

    private final ITeamTaskRepository taskRepository;
    private final IPageTranslationRepository pageRepository;
    private final ITaskHandoverRepository handoverRepository;
    private final ITranslatorChapterSettlementRepository settlementRepository;
    private final ITranslatorEarningEntryRepository earningRepository;
    private final CreatorPayoutSettingsService payoutSettingsService;
    private final ObjectMapper objectMapper;

    @Value("${payout.time-zone:Asia/Ho_Chi_Minh}")
    private String payoutTimeZone;

    public BigDecimal defaultPageRateUsd() {
        return normalizeMoney(payoutSettingsService.currentSettings().getTranslatorTaskRateUsd());
    }

    public BigDecimal deriveChapterRewardUsd(int pageCount) {
        if (pageCount <= 0) {
            throw new CustomException(400, "A translation task must contain at least one page", HttpStatus.BAD_REQUEST);
        }
        return normalizeMoney(defaultPageRateUsd().multiply(BigDecimal.valueOf(pageCount)));
    }

    @Transactional
    public void initializePageAssignments(TeamTaskEntity task, List<PageTranslationEntity> pages) {
        if (task == null || pages == null) return;
        for (PageTranslationEntity page : pages) {
            page.setAssignedTranslatorId(task.getAssigneeId());
            page.setResponsibilityFactor(BigDecimal.ONE.setScale(2));
            if (page.getStatus() == null) page.setStatus(PageStatus.TODO);
        }
        pageRepository.saveAll(pages);
    }

    @Transactional
    public TaskHandoverResponse handover(
            TeamTaskEntity task,
            HandoverTaskRequest request,
            UUID performedById
    ) {
        if (task == null) {
            throw new CustomException(404, "Task not found", HttpStatus.NOT_FOUND);
        }
        if (task.getCompletedAt() != null || isCompleted(task.getStatus())) {
            throw new CustomException(409, "A completed task cannot be handed over", HttpStatus.CONFLICT);
        }
        UUID previousTranslatorId = task.getAssigneeId();
        UUID nextTranslatorId = request.getNewAssigneeId();
        if (previousTranslatorId == null) {
            throw new CustomException(409, "The task does not have a current assignee", HttpStatus.CONFLICT);
        }
        if (previousTranslatorId.equals(nextTranslatorId)) {
            throw new CustomException(400, "New assignee must be different from the current assignee", HttpStatus.BAD_REQUEST);
        }
        BigDecimal factor = normalizeFactor(request.getResponsibilityFactor());

        List<PageTranslationEntity> pages = pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId());
        if (pages.isEmpty()) {
            throw new CustomException(409, "The task has no translation pages", HttpStatus.CONFLICT);
        }

        Set<Integer> acceptedNumbers = new TreeSet<>(Optional.ofNullable(request.getCompletedPageNumbers())
                .orElseGet(List::of));
        Map<Integer, PageTranslationEntity> byNumber = pages.stream()
                .collect(Collectors.toMap(PageTranslationEntity::getPageNumber, Function.identity()));
        for (Integer pageNumber : acceptedNumbers) {
            PageTranslationEntity page = byNumber.get(pageNumber);
            if (page == null) {
                throw new CustomException(400, "Page " + pageNumber + " does not exist in this task", HttpStatus.BAD_REQUEST);
            }
            UUID owner = page.getAssignedTranslatorId();
            if (owner != null && !previousTranslatorId.equals(owner)) {
                throw new CustomException(
                        409,
                        "Page " + pageNumber + " belongs to another translator and cannot be credited again",
                        HttpStatus.CONFLICT
                );
            }
        }

        Instant now = Instant.now();
        int reassignedCount = 0;
        for (PageTranslationEntity page : pages) {
            UUID owner = page.getAssignedTranslatorId();
            boolean belongsToCurrent = owner == null || previousTranslatorId.equals(owner);
            if (!belongsToCurrent) continue;

            if (acceptedNumbers.contains(page.getPageNumber())) {
                page.setAssignedTranslatorId(previousTranslatorId);
                page.setResponsibilityFactor(factor);
                page.setStatus(PageStatus.DONE);
                if (page.getCompletedAt() == null) page.setCompletedAt(now);
            } else {
                page.setAssignedTranslatorId(nextTranslatorId);
                page.setResponsibilityFactor(BigDecimal.ONE.setScale(2));
                page.setStatus(PageStatus.TODO);
                page.setCompletedAt(null);
                reassignedCount++;
            }
        }
        pageRepository.saveAll(pages);

        task.setAssigneeId(nextTranslatorId);
        task.setStatus("in_progress");
        task.setCompletedAt(null);
        task.setRejectionReason(null);
        taskRepository.save(task);

        TaskHandoverEntity handover = TaskHandoverEntity.builder()
                .taskId(task.getId())
                .fromTranslatorId(previousTranslatorId)
                .toTranslatorId(nextTranslatorId)
                .performedById(performedById)
                .handedOverAt(now)
                .responsibilityFactor(factor)
                .acceptedPageCount(acceptedNumbers.size())
                .reassignedPageCount(reassignedCount)
                .acceptedPageNumbers(toJson(acceptedNumbers))
                .reason(resolveHandoverReason(request.getReason()))
                .build();
        handover.setDeleted(false);
        TaskHandoverEntity saved = handoverRepository.save(handover);

        return TaskHandoverResponse.builder()
                .handoverId(saved.getId())
                .taskId(task.getId())
                .fromTranslatorId(previousTranslatorId)
                .toTranslatorId(nextTranslatorId)
                .completedPageNumbers(new ArrayList<>(acceptedNumbers))
                .acceptedPageCount(acceptedNumbers.size())
                .reassignedPageCount(reassignedCount)
                .responsibilityFactor(factor)
                .handedOverAt(now)
                .build();
    }

    @Transactional
    public PageTranslationEntity updatePageStatus(PageTranslationEntity page, PageStatus status) {
        if (page == null) {
            throw new CustomException(404, "Page not found", HttpStatus.NOT_FOUND);
        }
        if (status == null) {
            throw new CustomException(400, "Page status is required", HttpStatus.BAD_REQUEST);
        }
        page.setStatus(status);
        page.setCompletedAt(status == PageStatus.DONE ? Instant.now() : null);
        return pageRepository.save(page);
    }

    @Transactional(readOnly = true)
    public void validateReadyForReview(UUID taskId) {
        List<PageTranslationEntity> pages = pageRepository.findByTaskId_IdOrderByPageNumberAsc(taskId);
        if (pages.isEmpty()) {
            throw new CustomException(409, "The task has no pages to review", HttpStatus.CONFLICT);
        }
        List<Integer> incomplete = pages.stream()
                .filter(page -> page.getStatus() != PageStatus.DONE)
                .map(PageTranslationEntity::getPageNumber)
                .toList();
        if (!incomplete.isEmpty()) {
            throw new CustomException(
                    409,
                    "All pages must be marked DONE before review. Incomplete pages: " + incomplete,
                    HttpStatus.CONFLICT
            );
        }
        List<Integer> unassigned = pages.stream()
                .filter(page -> page.getAssignedTranslatorId() == null)
                .map(PageTranslationEntity::getPageNumber)
                .toList();
        if (!unassigned.isEmpty()) {
            throw new CustomException(
                    409,
                    "Every page must have a credited translator. Unassigned pages: " + unassigned,
                    HttpStatus.CONFLICT
            );
        }
    }

    @Transactional
    public TranslatorChapterSettlementEntity settleApprovedTask(TeamTaskEntity task) {
        if (task == null || task.getId() == null || task.getChapter() == null) {
            throw new CustomException(409, "Task must be linked to a chapter before settlement", HttpStatus.CONFLICT);
        }
        if (!isCompleted(task.getStatus()) || task.getCompletedAt() == null) {
            throw new CustomException(
                    409,
                    "Translator earnings can be settled only after the whole chapter is approved",
                    HttpStatus.CONFLICT
            );
        }
        Optional<TranslatorChapterSettlementEntity> active = settlementRepository
                .findFirstByTaskIdAndStatusOrderByVersionNoDesc(task.getId(), TranslatorSettlementStatus.ACTIVE);
        if (active.isPresent()) return active.get();

        List<PageTranslationEntity> pages = pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId());
        validateSettlementPages(pages);

        int version = settlementRepository.findFirstByTaskIdOrderByVersionNoDesc(task.getId())
                .map(item -> item.getVersionNo() + 1)
                .orElse(1);
        BigDecimal totalReward = task.getChapterRewardUsd();
        if (totalReward == null || totalReward.signum() <= 0) {
            totalReward = deriveChapterRewardUsd(pages.size());
            task.setChapterRewardUsd(totalReward);
        } else {
            totalReward = normalizeMoney(totalReward);
        }

        Instant now = Instant.now();
        String month = YearMonth.from(now.atZone(payoutZone())).toString();
        BigDecimal pageRate = totalReward.divide(
                BigDecimal.valueOf(pages.size()),
                6,
                RoundingMode.HALF_UP
        );

        TranslatorChapterSettlementEntity settlement = TranslatorChapterSettlementEntity.builder()
                .taskId(task.getId())
                .chapterId(task.getChapter().getId())
                .projectTeamId(task.getProjectTeamId())
                .versionNo(version)
                .settlementMonth(month)
                .settledAt(now)
                .totalChapterRewardUsd(totalReward)
                .totalPages(pages.size())
                .pageRateUsd(pageRate)
                .status(TranslatorSettlementStatus.ACTIVE)
                .build();
        settlement.setDeleted(false);
        settlement = settlementRepository.saveAndFlush(settlement);

        BigDecimal allocatedGross = BigDecimal.ZERO.setScale(2);
        List<TranslatorEarningEntryEntity> earnings = new ArrayList<>();
        for (int index = 0; index < pages.size(); index++) {
            PageTranslationEntity page = pages.get(index);
            BigDecimal gross = index == pages.size() - 1
                    ? normalizeMoney(totalReward.subtract(allocatedGross))
                    : normalizeMoney(pageRate);
            allocatedGross = allocatedGross.add(gross);
            BigDecimal factor = normalizeFactor(page.getResponsibilityFactor());
            BigDecimal net = normalizeMoney(gross.multiply(factor));

            TranslatorEarningEntryEntity earning = TranslatorEarningEntryEntity.builder()
                    .entryType(TranslatorEarningEntryType.PAGE_EARNING)
                    .settlementId(settlement.getId())
                    .taskId(task.getId())
                    .chapterId(task.getChapter().getId())
                    .pageId(page.getId())
                    .pageNumber(page.getPageNumber())
                    .translatorId(page.getAssignedTranslatorId())
                    .entryMonth(month)
                    .responsibilityFactor(factor)
                    .grossAmountUsd(gross)
                    .amountUsd(net)
                    .build();
            earning.setDeleted(false);
            earnings.add(earning);
        }
        earningRepository.saveAll(earnings);
        task.setSettledAt(now);
        taskRepository.save(task);
        return settlement;
    }

    @Transactional
    public void reverseLatestSettlement(UUID taskId, String reason) {
        TranslatorChapterSettlementEntity settlement = settlementRepository
                .findFirstByTaskIdAndStatusOrderByVersionNoDesc(taskId, TranslatorSettlementStatus.ACTIVE)
                .orElse(null);
        if (settlement == null) return;

        String normalizedReason = StringUtils.hasText(reason)
                ? reason.trim()
                : "Translation settlement reversed";
        String adjustmentMonth = YearMonth.now(payoutZone()).toString();
        Map<UUID, BigDecimal> totals = earningRepository
                .findAllBySettlementIdAndEntryType(
                        settlement.getId(),
                        TranslatorEarningEntryType.PAGE_EARNING
                )
                .stream()
                .collect(Collectors.groupingBy(
                        TranslatorEarningEntryEntity::getTranslatorId,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TranslatorEarningEntryEntity::getAmountUsd,
                                BigDecimal::add
                        )
                ));

        List<TranslatorEarningEntryEntity> adjustments = totals.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue().signum() != 0)
                .map(entry -> {
                    TranslatorEarningEntryEntity adjustment = TranslatorEarningEntryEntity.builder()
                            .entryType(TranslatorEarningEntryType.REVERSAL_ADJUSTMENT)
                            .translatorId(entry.getKey())
                            .taskId(taskId)
                            .settlementId(settlement.getId())
                            .chapterId(settlement.getChapterId())
                            .entryMonth(adjustmentMonth)
                            .amountUsd(normalizeMoney(entry.getValue().negate()))
                            .reason(normalizedReason)
                            .build();
                    adjustment.setDeleted(false);
                    return adjustment;
                })
                .toList();
        earningRepository.saveAll(adjustments);

        settlement.setStatus(TranslatorSettlementStatus.REVERSED);
        settlement.setReversedAt(Instant.now());
        settlement.setReversalReason(normalizedReason);
        settlementRepository.save(settlement);
    }

    @Transactional
    public int backfillCompletedTasks() {
        int count = 0;
        for (TeamTaskEntity task : taskRepository.findAllCompletedForSettlementBackfill()) {
            if (task.getChapter() == null || task.getAssigneeId() == null) continue;
            if (settlementRepository.findFirstByTaskIdOrderByVersionNoDesc(task.getId()).isPresent()) continue;
            List<PageTranslationEntity> pages = pageRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId());
            if (pages.isEmpty()) continue;
            for (PageTranslationEntity page : pages) {
                if (page.getAssignedTranslatorId() == null) page.setAssignedTranslatorId(task.getAssigneeId());
                if (page.getResponsibilityFactor() == null) page.setResponsibilityFactor(BigDecimal.ONE.setScale(2));
                page.setStatus(PageStatus.DONE);
                if (page.getCompletedAt() == null) page.setCompletedAt(task.getCompletedAt());
            }
            pageRepository.saveAll(pages);
            settleApprovedTask(task);
            count++;
        }
        return count;
    }

    private void validateSettlementPages(List<PageTranslationEntity> pages) {
        if (pages == null || pages.isEmpty()) {
            throw new CustomException(409, "Cannot settle a chapter without pages", HttpStatus.CONFLICT);
        }
        for (PageTranslationEntity page : pages) {
            if (page.getStatus() != PageStatus.DONE) {
                throw new CustomException(
                        409,
                        "Page " + page.getPageNumber() + " is not completed",
                        HttpStatus.CONFLICT
                );
            }
            if (page.getAssignedTranslatorId() == null) {
                throw new CustomException(
                        409,
                        "Page " + page.getPageNumber() + " has no credited translator",
                        HttpStatus.CONFLICT
                );
            }
        }
    }

    private BigDecimal normalizeFactor(BigDecimal value) {
        BigDecimal factor = value == null ? BigDecimal.ONE : value;
        if (factor.compareTo(BigDecimal.ZERO) < 0 || factor.compareTo(BigDecimal.ONE) > 0) {
            throw new CustomException(400, "Responsibility factor K must be between 0.00 and 1.00", HttpStatus.BAD_REQUEST);
        }
        return factor.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isCompleted(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return Set.of("completed", "complete", "done", "published").contains(normalized);
    }

    private ZoneId payoutZone() {
        try {
            return ZoneId.of(payoutTimeZone);
        } catch (RuntimeException ignored) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }

    private String resolveHandoverReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Reassigned";
        }
        return reason.trim();
    }

    private String toJson(Collection<Integer> pageNumbers) {
        try {
            return objectMapper.writeValueAsString(pageNumbers);
        } catch (JsonProcessingException ex) {
            throw new CustomException(500, "Could not save handover page list", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
