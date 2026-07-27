package com.sep.comiverse.controller;

import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.plugin.crud.SubmissionCrudPlugin;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerNotificationTest {

    @Mock
    private SubmissionCrudPlugin crudPlugin;
    @Mock
    private NotificationService notificationService;
    @Mock
    private IProjectTeamRepository projectTeamRepository;

    private SubmissionController controller;

    @BeforeEach
    void setUp() {
        controller = new SubmissionController(crudPlugin);
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
        ReflectionTestUtils.setField(controller, "projectTeamRepository", projectTeamRepository);
    }

    @Test
    void authorSubmissionOutcomeUsesAuthorPreference() {
        UUID authorId = UUID.randomUUID();
        SubmissionEntity submission = SubmissionEntity.builder()
                .authorId(authorId)
                .queueType("author")
                .title("Author Comic")
                .build();

        ReflectionTestUtils.invokeMethod(controller, "notifySubmissionOwner", submission, true, null);

        verify(notificationService).notifyUser(
                eq(authorId),
                eq("Submission approved"),
                contains("Author Comic"),
                eq("UPDATE"),
                eq(NotificationPreferenceKey.SUBMISSION_STATUS)
        );
    }

    @Test
    void translatorSubmissionOutcomeUsesTeamUpdatePreference() {
        UUID leaderId = UUID.randomUUID();
        ProjectTeamEntity team = ProjectTeamEntity.builder()
                .title("Team Alpha")
                .leaderId(leaderId)
                .build();
        when(projectTeamRepository.findByTitleIgnoreCase("Team Alpha")).thenReturn(Optional.of(team));
        SubmissionEntity submission = SubmissionEntity.builder()
                .queueType("translator")
                .submittedBy("Team Alpha")
                .title("Translated Comic")
                .chapter("Chapter 2")
                .build();

        ReflectionTestUtils.invokeMethod(controller, "notifySubmissionOwner", submission, false, "Fix lettering");

        verify(notificationService).notifyUser(
                eq(leaderId),
                eq("Submission needs changes"),
                contains("Fix lettering"),
                eq("WARNING"),
                eq(NotificationPreferenceKey.TEAM_UPDATES)
        );
    }
}
