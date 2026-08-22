package com.sep.comiverse.unit.service;

import com.sep.comiverse.entity.ReportCategoryEntity;
import com.sep.comiverse.entity.ReportEntity;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportStatus;
import com.sep.comiverse.entity.enums.ReportTargetType;
import com.sep.comiverse.repository.IReportRepository;
import com.sep.comiverse.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private IReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void markAcceptedLeaderTranslationReportsDoneUpdatesOnlyLeaderAcceptedReports() {
        UUID translationId = UUID.randomUUID();
        ReportEntity leaderReport = ReportEntity.builder()
                .targetType(ReportTargetType.CHAPTER_TRANSLATIONS)
                .targetId(translationId)
                .status(ReportStatus.ACCEPTED)
                .category(ReportCategoryEntity.builder()
                        .assignedRole(ReportAssignedRole.PROJECT_LEADER)
                        .build())
                .build();

        when(reportRepository.findByTargetTypeAndTargetIdAndStatusAndCategory_AssignedRoleAndDeletedFalse(
                ReportTargetType.CHAPTER_TRANSLATIONS,
                translationId,
                ReportStatus.ACCEPTED,
                ReportAssignedRole.PROJECT_LEADER
        )).thenReturn(List.of(leaderReport));

        int updated = reportService.markAcceptedLeaderTranslationReportsDone(translationId);

        assertEquals(1, updated);
        assertEquals(ReportStatus.DONE, leaderReport.getStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReportEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(reportRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(ReportStatus.DONE, captor.getValue().get(0).getStatus());
    }

    @Test
    void markAcceptedLeaderTranslationReportsDoneDoesNothingWhenNoLeaderReports() {
        UUID translationId = UUID.randomUUID();
        when(reportRepository.findByTargetTypeAndTargetIdAndStatusAndCategory_AssignedRoleAndDeletedFalse(
                eq(ReportTargetType.CHAPTER_TRANSLATIONS),
                eq(translationId),
                eq(ReportStatus.ACCEPTED),
                eq(ReportAssignedRole.PROJECT_LEADER)
        )).thenReturn(List.of());

        int updated = reportService.markAcceptedLeaderTranslationReportsDone(translationId);

        assertEquals(0, updated);
        verify(reportRepository, never()).saveAll(any());
    }
}
