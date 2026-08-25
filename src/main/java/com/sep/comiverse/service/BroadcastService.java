package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.BroadcastRequest;
import com.sep.comiverse.dto.response.BroadcastAudiencePreviewResponse;
import com.sep.comiverse.dto.response.BroadcastResponse;
import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.entity.NotificationEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.ProjectTeamMemberEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.BroadcastAudienceType;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.push.NotificationPushBatchEvent;
import com.sep.comiverse.service.push.NotificationPushEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("INFO", "WARNING", "UPDATE", "MAINTENANCE");

    private final INotificationRepository notificationRepository;
    private final IUserRepository userRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final NotificationPreferenceService notificationPreferenceService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public BroadcastAudiencePreviewResponse previewAudience(BroadcastRequest request) {
        AudienceSelection selection = resolveAudience(request);
        long enabledCount = enabledRecipients(selection.recipients()).size();
        return BroadcastAudiencePreviewResponse.builder()
                .audienceType(selection.type())
                .audienceLabel(selection.label())
                .matchedRecipientCount(selection.recipients().size())
                .enabledRecipientCount(enabledCount)
                .optedOutCount(selection.recipients().size() - enabledCount)
                .build();
    }

    @Transactional
    public BroadcastResponse sendBroadcast(BroadcastRequest request) {
        validateAnnouncementType(request.getType());
        AudienceSelection selection = resolveAudience(request);
        UUID broadcastId = UUID.randomUUID();
        String type = request.getType().trim().toUpperCase(Locale.ROOT);
        String title = request.getTitle().trim();
        String message = request.getMessage().trim();

        List<UserEntity> enabledRecipients = enabledRecipients(selection.recipients());
        requireRecipients(
                enabledRecipients,
                "No selected recipient currently allows system broadcasts."
        );

        List<NotificationEntity> notifications = enabledRecipients.stream()
                .map(user -> NotificationEntity.builder()
                        .user(user)
                        .title(title)
                        .message(message)
                        .type(type)
                        .broadcastId(broadcastId)
                        .targetRoles(selection.label())
                        .isRead(false)
                        .build())
                .toList();

        List<NotificationEntity> savedNotifications = notificationRepository.saveAll(notifications);
        List<NotificationPushEvent> deliveries = savedNotifications.stream()
                .map(notification -> new NotificationPushEvent(
                        notification.getUser().getId(),
                        toNotificationResponse(notification)
                ))
                .toList();
        eventPublisher.publishEvent(new NotificationPushBatchEvent(deliveries));

        return BroadcastResponse.builder()
                .id(broadcastId)
                .type(type)
                .title(title)
                .message(message)
                .audienceType(selection.type())
                .audienceLabel(selection.label())
                .targetRoles(selection.label())
                .recipientCount(savedNotifications.size())
                .sentAt(new Date())
                .build();
    }

    public List<BroadcastResponse> getBroadcastHistory() {
        return notificationRepository.findBroadcastSummaries().stream()
                .map(row -> {
                    String audienceLabel = (String) row[4];
                    return BroadcastResponse.builder()
                            .id((UUID) row[0])
                            .type((String) row[1])
                            .title((String) row[2])
                            .message((String) row[3])
                            .audienceType(inferAudienceType(audienceLabel))
                            .audienceLabel(audienceLabel)
                            .targetRoles(audienceLabel)
                            .recipientCount((Long) row[5])
                            .sentAt(toDate(row[6]))
                            .build();
                })
                .toList();
    }

    @Transactional
    public void revokeBroadcast(UUID broadcastId) {
        notificationRepository.softDeleteByBroadcastId(broadcastId);
    }

    private AudienceSelection resolveAudience(BroadcastRequest request) {
        List<String> roles = normalizeRoles(request.getTargetRoles());
        BroadcastAudienceType audienceType = request.getAudienceType();
        if (audienceType == null) {
            audienceType = roles.contains("ALL")
                    ? BroadcastAudienceType.ALL
                    : BroadcastAudienceType.ROLES;
        }

        return switch (audienceType) {
            case ALL -> {
                List<UserEntity> recipients = findActiveUsers(null);
                requireRecipients(recipients, "No active users are available.");
                yield new AudienceSelection(recipients, BroadcastAudienceType.ALL, "ALL USERS");
            }
            case ROLES -> resolveRoleAudience(roles);
            case USERS -> resolveUserAudience(request.getTargetUserIds());
            case PROJECT_TEAMS -> resolveTeamAudience(request.getTargetTeamIds());
        };
    }

    private AudienceSelection resolveRoleAudience(List<String> roles) {
        if (roles.isEmpty() || roles.contains("ALL")) {
            throw badRequest("Select at least one specific role, or use All users.");
        }
        List<UserEntity> recipients = findActiveUsers((root, query, cb) ->
                cb.upper(root.get("role").get("roleName")).in(roles));
        requireRecipients(recipients, "No active users found for the selected roles.");
        return new AudienceSelection(recipients, BroadcastAudienceType.ROLES, String.join(", ", roles));
    }

    private AudienceSelection resolveUserAudience(List<UUID> requestedIds) {
        List<UUID> userIds = distinctIds(requestedIds);
        if (userIds.isEmpty()) {
            throw badRequest("Select at least one user.");
        }
        List<UserEntity> recipients = findActiveUsers((root, query, cb) -> root.get("id").in(userIds));
        if (recipients.size() != userIds.size()) {
            throw badRequest("One or more selected users are no longer active. Refresh the selection and try again.");
        }
        String label = recipients.size() == 1 ? "1 SPECIFIC USER" : recipients.size() + " SPECIFIC USERS";
        return new AudienceSelection(recipients, BroadcastAudienceType.USERS, label);
    }

    private AudienceSelection resolveTeamAudience(List<UUID> requestedIds) {
        List<UUID> teamIds = distinctIds(requestedIds);
        if (teamIds.isEmpty()) {
            throw badRequest("Select at least one project team.");
        }
        List<ProjectTeamEntity> teams = projectTeamRepository.findAllWithMembersByIdIn(teamIds);
        if (teams.size() != teamIds.size()) {
            throw badRequest("One or more selected project teams are no longer available. Refresh the selection and try again.");
        }

        LinkedHashSet<UUID> recipientIds = new LinkedHashSet<>();
        for (ProjectTeamEntity team : teams) {
            if (team.getLeaderId() != null) {
                recipientIds.add(team.getLeaderId());
            }
            if (team.getMembers() != null) {
                for (ProjectTeamMemberEntity member : team.getMembers()) {
                    if (member.getUser() != null && member.getUser().getId() != null) {
                        recipientIds.add(member.getUser().getId());
                    }
                }
            }
        }
        if (recipientIds.isEmpty()) {
            throw badRequest("The selected project team has no members.");
        }

        List<UserEntity> recipients = findActiveUsers((root, query, cb) -> root.get("id").in(recipientIds));
        requireRecipients(recipients, "The selected project team has no active members.");
        String label = teams.size() == 1
                ? "PROJECT TEAM: " + compactTeamName(teams.getFirst())
                : teams.size() + " PROJECT TEAMS";
        return new AudienceSelection(recipients, BroadcastAudienceType.PROJECT_TEAMS, label);
    }

    private List<UserEntity> findActiveUsers(Specification<UserEntity> audienceSpec) {
        Specification<UserEntity> activeSpec = (root, query, cb) -> cb.and(
                cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted"))),
                cb.equal(cb.upper(root.get("status")), "ACTIVE")
        );
        if (audienceSpec != null) {
            activeSpec = activeSpec.and(audienceSpec);
        }
        return userRepository.findAll(activeSpec);
    }

    private List<UserEntity> enabledRecipients(List<UserEntity> recipients) {
        return notificationPreferenceService.filterEnabled(
                recipients,
                NotificationPreferenceKey.SYSTEM_BROADCASTS
        );
    }

    private void validateAnnouncementType(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(type)) {
            throw badRequest("Type must be INFO, WARNING, UPDATE, or MAINTENANCE.");
        }
    }

    private List<String> normalizeRoles(List<String> rawRoles) {
        if (rawRoles == null) {
            return List.of();
        }
        return rawRoles.stream()
                .filter(roles -> roles != null && !roles.isBlank())
                .map(role -> role.trim()
                        .toUpperCase(Locale.ROOT)
                        .replace('-', '_')
                        .replace(' ', '_'))
                .distinct()
                .toList();
    }

    private List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(ids.stream()
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private String compactTeamName(ProjectTeamEntity team) {
        String name = team.getTitle();
        if (name == null || name.isBlank()) {
            name = team.getComicName();
        }
        if (name == null || name.isBlank()) {
            name = "Untitled";
        }
        name = name.trim();
        return name.length() <= 75 ? name : name.substring(0, 72) + "...";
    }

    private BroadcastAudienceType inferAudienceType(String label) {
        String value = label == null ? "" : label.toUpperCase(Locale.ROOT);
        if (value.equals("ALL") || value.equals("ALL USERS")) {
            return BroadcastAudienceType.ALL;
        }
        if (value.contains("SPECIFIC USER")) {
            return BroadcastAudienceType.USERS;
        }
        if (value.contains("PROJECT TEAM")) {
            return BroadcastAudienceType.PROJECT_TEAMS;
        }
        return BroadcastAudienceType.ROLES;
    }

    private Date toDate(Object value) {
        if (value instanceof Instant instant) {
            return Date.from(instant);
        }
        return value instanceof Date date ? date : null;
    }

    private void requireRecipients(List<UserEntity> recipients, String message) {
        if (recipients == null || recipients.isEmpty()) {
            throw badRequest(message);
        }
    }

    private CustomException badRequest(String message) {
        return new CustomException(400, message, HttpStatus.BAD_REQUEST);
    }

    private NotificationResponse toNotificationResponse(NotificationEntity notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .actionUrl(notification.getActionUrl())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private record AudienceSelection(
            List<UserEntity> recipients,
            BroadcastAudienceType type,
            String label
    ) {
    }
}
