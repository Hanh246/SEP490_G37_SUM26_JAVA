package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.CreateReportRequest;
import com.sep.comiverse.dto.request.ProcessReportRequest;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ReportCategoryEntity;
import com.sep.comiverse.entity.ReportEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ReportAction;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportStatus;
import com.sep.comiverse.entity.enums.ReportTargetType;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.*;
import com.sep.comiverse.service.NotificationService;
import com.sep.comiverse.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private IReportRepository reportRepository;
    @Mock private IReportCategoryRepository reportCategoryRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IComicRepository comicRepository;
    @Mock private IChapterRepository chapterRepository;
    @Mock private IChapterTranslationRepository chapterTranslationRepository;
    @Mock private IProjectTeamRepository projectTeamRepository;
    @Mock private ITeamMessageRepository teamMessageRepository;
    @Mock private ITeamTaskRepository teamTaskRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ComicCrudPlugin comicCrudPlugin;
    @Mock private ChapterCrudPlugin chapterCrudPlugin;
    @Mock private NotificationService notificationService;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(
                reportRepository,
                reportCategoryRepository,
                userRepository,
                comicRepository,
                chapterRepository,
                chapterTranslationRepository,
                projectTeamRepository,
                teamMessageRepository,
                teamTaskRepository,
                messagingTemplate,
                comicCrudPlugin,
                chapterCrudPlugin,
                notificationService
        );
        lenient().when(reportRepository.save(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity report = invocation.getArgument(0);
            if (report.getId() == null) report.setId(UUID.randomUUID());
            return report;
        });
    }

    @Test
    void createReport_validComic_createsPendingReportAndNotifiesAssignedStaff() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ReportCategoryEntity category = category(ReportAssignedRole.MODERATOR, ReportTargetType.COMIC);
        UserEntity reporter = user("READER");
        reporter.setId(reporterId);
        ComicEntity comic = comic(targetId);
        CreateReportRequest request = CreateReportRequest.builder()
                .categoryId(category.getId())
                .targetType(ReportTargetType.COMIC)
                .targetId(targetId)
                .descriptionText("  Broken chapter links  ")
                .build();

        when(reportCategoryRepository.findByIdAndDeletedFalse(category.getId())).thenReturn(Optional.of(category));
        when(comicRepository.findById(targetId)).thenReturn(Optional.of(comic));
        when(reportRepository.existsByReporter_IdAndTargetTypeAndTargetIdAndStatusInAndDeletedFalse(
                eq(reporterId), eq(ReportTargetType.COMIC), eq(targetId), anyCollection())).thenReturn(false);
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));

        var response = service.createReport(reporterId, request);

        assertEquals(ReportStatus.PENDING, response.getStatus());
        assertEquals(ReportTargetType.COMIC, response.getTargetType());
        assertEquals(targetId, response.getTargetId());
        assertEquals("Broken chapter links", response.getDescriptionText());
        verify(reportRepository).save(argThat(report ->
                report.getStatus() == ReportStatus.PENDING
                        && report.getReporter() == reporter
                        && "Broken chapter links".equals(report.getDescriptionText())));
        verify(notificationService).notifyRoles(
                argThat(roles -> roles.contains("MODERATOR") && roles.contains("ADMIN")),
                eq("New Issue Report"), contains("ComiVerse Report Test"), eq("REPORT_SUBMITTED"), any());
    }

    @Test
    void createReport_duplicatePendingReport_isRejectedBeforeSecondPersistence() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ReportCategoryEntity category = category(ReportAssignedRole.MODERATOR, ReportTargetType.COMIC);
        CreateReportRequest request = CreateReportRequest.builder()
                .categoryId(category.getId())
                .targetType(ReportTargetType.COMIC)
                .targetId(targetId)
                .build();

        when(reportCategoryRepository.findByIdAndDeletedFalse(category.getId())).thenReturn(Optional.of(category));
        when(comicRepository.findById(targetId)).thenReturn(Optional.of(comic(targetId)));
        when(reportRepository.existsByReporter_IdAndTargetTypeAndTargetIdAndStatusInAndDeletedFalse(
                eq(reporterId), eq(ReportTargetType.COMIC), eq(targetId), anyCollection())).thenReturn(true);

        CustomException error = assertThrows(CustomException.class, () -> service.createReport(reporterId, request));

        assertEquals(400, error.getCode());
        verify(reportRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void processReport_pendingComicAccept_movesToAcceptedAndUnpublishesComic() {
        UUID reportId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        UserEntity reporter = user("READER");
        UserEntity moderator = user("MODERATOR");
        ReportEntity report = report(reportId, reporter, category(ReportAssignedRole.MODERATOR, ReportTargetType.COMIC),
                ReportTargetType.COMIC, comicId, ReportStatus.PENDING);
        ComicEntity comic = comic(comicId);
        ProcessReportRequest request = ProcessReportRequest.builder()
                .action(ReportAction.ACCEPT)
                .resolutionNote("  Violates content policy  ")
                .build();

        when(reportRepository.findByIdWithDetails(reportId)).thenReturn(Optional.of(report));
        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));

        var response = service.processReport(reportId, moderator, request);

        assertEquals(ReportStatus.ACCEPTED, response.getStatus());
        assertEquals(ReportStatus.ACCEPTED, report.getStatus());
        assertSame(moderator, report.getHandler());
        assertNotNull(report.getResolvedAt());
        assertEquals("Violates content policy", report.getResolutionNote());
        assertEquals(ComicModerationStatus.UNPUBLISHED, comic.getModerationStatus());
        assertEquals("Violates content policy", comic.getRejectionReason());
        verify(comicRepository).save(comic);
        verify(comicCrudPlugin).evictComicCache(comicId);
        verify(notificationService, atLeastOnce()).notifyUser(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void processReport_pendingComicReject_movesToRejectedWithoutRevokingContent() {
        UUID reportId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        UserEntity reporter = user("READER");
        UserEntity moderator = user("MODERATOR");
        ReportEntity report = report(reportId, reporter, category(ReportAssignedRole.MODERATOR, ReportTargetType.COMIC),
                ReportTargetType.COMIC, comicId, ReportStatus.PENDING);
        ComicEntity comic = comic(comicId);
        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
        when(reportRepository.findByIdWithDetails(reportId)).thenReturn(Optional.of(report));
        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));

        var response = service.processReport(reportId, moderator,
                ProcessReportRequest.builder().action(ReportAction.REJECT).resolutionNote("Not a violation").build());

        assertEquals(ReportStatus.REJECTED, response.getStatus());
        assertEquals(ReportStatus.REJECTED, report.getStatus());
        assertEquals(ComicModerationStatus.PUBLISHED, comic.getModerationStatus());
        verify(comicRepository, never()).save(any(ComicEntity.class));
        verify(notificationService).notifyUser(
                eq(reporter.getId()), eq("Report Rejected"), contains("Not a violation"), eq("REPORT_REJECTED"), any());
    }

    @Test
    void processReport_alreadyResolvedReport_rejectsInvalidStateTransition() {
        UUID reportId = UUID.randomUUID();
        ReportEntity report = report(reportId, user("READER"),
                category(ReportAssignedRole.MODERATOR, ReportTargetType.COMIC),
                ReportTargetType.COMIC, UUID.randomUUID(), ReportStatus.ACCEPTED);
        when(reportRepository.findByIdWithDetails(reportId)).thenReturn(Optional.of(report));

        CustomException error = assertThrows(CustomException.class, () ->
                service.processReport(reportId, user("MODERATOR"),
                        ProcessReportRequest.builder().action(ReportAction.REJECT).resolutionNote("x").build()));

        assertEquals(400, error.getCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void processTranslationReport_requiresAssignedLeaderAndAcceptResolutionNote() {
        UUID reportId = UUID.randomUUID();
        UUID translationId = UUID.randomUUID();
        ReportCategoryEntity category = category(ReportAssignedRole.PROJECT_LEADER, ReportTargetType.CHAPTER_TRANSLATIONS);
        ReportEntity report = report(reportId, user("READER"), category,
                ReportTargetType.CHAPTER_TRANSLATIONS, translationId, ReportStatus.PENDING);
        UserEntity moderator = user("MODERATOR");
        when(reportRepository.findByIdWithDetails(reportId)).thenReturn(Optional.of(report));

        assertEquals(403, assertThrows(CustomException.class, () ->
                service.processReport(reportId, moderator,
                        ProcessReportRequest.builder().action(ReportAction.ACCEPT).resolutionNote("fix").build())).getCode());

        UserEntity leader = user("PROJECT_LEADER");
        when(chapterTranslationRepository.isUserLeaderOfTranslation(translationId, leader.getId())).thenReturn(false);
        assertEquals(403, assertThrows(CustomException.class, () ->
                service.processReport(reportId, leader,
                        ProcessReportRequest.builder().action(ReportAction.ACCEPT).resolutionNote("fix").build())).getCode());
    }

    private ReportCategoryEntity category(ReportAssignedRole role, ReportTargetType targetType) {
        ReportCategoryEntity category = ReportCategoryEntity.builder()
                .name("Policy violation")
                .assignedRole(role)
                .targetTypes(List.of(targetType))
                .isActive(true)
                .build();
        category.setId(UUID.randomUUID());
        category.setDeleted(false);
        return category;
    }

    private ReportEntity report(UUID id, UserEntity reporter, ReportCategoryEntity category,
                                ReportTargetType targetType, UUID targetId, ReportStatus status) {
        ReportEntity report = ReportEntity.builder()
                .reporter(reporter)
                .category(category)
                .targetType(targetType)
                .targetId(targetId)
                .status(status)
                .descriptionText("reader report")
                .build();
        report.setId(id);
        report.setDeleted(false);
        return report;
    }

    private UserEntity user(String roleName) {
        UserEntity user = UserEntity.builder()
                .username(roleName.toLowerCase())
                .email(roleName.toLowerCase() + "@example.com")
                .fullName(roleName + " User")
                .role(RoleEntity.builder().roleName(roleName).build())
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private ComicEntity comic(UUID id) {
        ComicEntity comic = ComicEntity.builder()
                .title("ComiVerse Report Test")
                .authorId(UUID.randomUUID())
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build();
        comic.setId(id);
        comic.setDeleted(false);
        return comic;
    }
}
