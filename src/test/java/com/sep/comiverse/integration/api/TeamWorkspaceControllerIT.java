package com.sep.comiverse.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ChapterTranslationEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.NotificationEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.ProjectTeamMemberEntity;
import com.sep.comiverse.entity.ReportCategoryEntity;
import com.sep.comiverse.entity.ReportEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.TeamAnnouncementEntity;
import com.sep.comiverse.entity.TeamJoinBanEntity;
import com.sep.comiverse.entity.TeamJoinRequestEntity;
import com.sep.comiverse.entity.TeamMessageEntity;
import com.sep.comiverse.entity.TeamPostCommentEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.TranslatorCooldownEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ChapterTranslationStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.entity.enums.PageStatus;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportStatus;
import com.sep.comiverse.entity.enums.ReportTargetType;
import com.sep.comiverse.integration.support.AbstractIntegrationTest;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IReportCategoryRepository;
import com.sep.comiverse.repository.IReportRepository;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.ITeamAnnouncementRepository;
import com.sep.comiverse.repository.ITeamJoinBanRepository;
import com.sep.comiverse.repository.ITeamJoinRequestRepository;
import com.sep.comiverse.repository.ITeamMessageRepository;
import com.sep.comiverse.repository.ITeamPostCommentRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.ITranslatorCooldownRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.TranslatorPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class TeamWorkspaceControllerIT extends AbstractIntegrationTest {

    private static final String BASE_URL = "/team-workspace";
    private static final String COMIC_NAME = "Workspace IT Comic";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private IProjectTeamRepository projectTeamRepository;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private ITeamAnnouncementRepository announcementRepository;

    @Autowired
    private ITeamPostCommentRepository postCommentRepository;

    @Autowired
    private ITeamMessageRepository messageRepository;

    @Autowired
    private ITeamTaskRepository taskRepository;

    @Autowired
    private IPageTranslationRepository pageTranslationRepository;

    @Autowired
    private IChapterTranslationRepository chapterTranslationRepository;

    @Autowired
    private IReportRepository reportRepository;

    @Autowired
    private IReportCategoryRepository reportCategoryRepository;

    @Autowired
    private ITeamJoinRequestRepository joinRequestRepository;

    @Autowired
    private ITeamJoinBanRepository joinBanRepository;

    @Autowired
    private ITranslatorCooldownRepository cooldownRepository;

    @Autowired
    private INotificationRepository notificationRepository;

    @Autowired
    private TranslatorPaymentService translatorPaymentService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private UserEntity leaderUser;
    private UserEntity otherLeaderUser;
    private UserEntity translatorUser;
    private UserEntity secondTranslatorUser;
    private UserEntity applicantUser;
    private UserEntity readerMemberUser;

    private String leaderToken;
    private String otherLeaderToken;
    private String translatorToken;
    private String secondTranslatorToken;
    private String applicantToken;
    private String readerToken;

    private ProjectTeamEntity team;
    private ProjectTeamEntity otherTeam;
    private ComicEntity comic;
    private ChapterEntity taskedChapter;
    private ChapterEntity backlogChapter;
    private ChapterEntity draftChapter;
    private TeamTaskEntity task;
    private TeamAnnouncementEntity announcement;
    private TeamPostCommentEntity comment;
    private TeamMessageEntity message;
    private TeamJoinRequestEntity pendingRequest;

    @BeforeEach
    void setUp() {
        leaderUser = findOrCreateUser("workspace_leader", "Workspace Leader", "PROJECT_LEADER");
        otherLeaderUser = findOrCreateUser("workspace_other_leader", "Workspace Other Leader", "PROJECT_LEADER");
        translatorUser = findOrCreateUser("workspace_translator", "Workspace Translator", "TRANSLATOR");
        secondTranslatorUser = findOrCreateUser("workspace_translator_two", "Workspace Translator Two", "TRANSLATOR");
        applicantUser = findOrCreateUser("workspace_applicant", "Workspace Applicant", "TRANSLATOR");
        readerMemberUser = findOrCreateUser("workspace_reader_member", "Workspace Reader Member", "READER");

        leaderToken = jwtTokenUtil.generateToken(leaderUser);
        otherLeaderToken = jwtTokenUtil.generateToken(otherLeaderUser);
        translatorToken = jwtTokenUtil.generateToken(translatorUser);
        secondTranslatorToken = jwtTokenUtil.generateToken(secondTranslatorUser);
        applicantToken = jwtTokenUtil.generateToken(applicantUser);
        readerToken = jwtTokenUtil.generateToken(readerMemberUser);

        ComicEntity comicToSave = ComicEntity.builder()
                .title(COMIC_NAME)
                .summary("Comic translated by the workspace IT team")
                .language("vi")
                .cover("http://example.com/workspace_cover.jpg")
                .authorId(UUID.randomUUID())
                .publicationStatus(ComicPublicationStatus.ONGOING)
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build();
        comicToSave.setDeleted(false);
        comic = comicRepository.save(comicToSave);

        team = persistTeam("Workspace IT Team", COMIC_NAME, leaderUser);
        addMember(team, translatorUser);
        addMember(team, secondTranslatorUser);
        addMember(team, readerMemberUser);
        team.setMembersCount(team.getMembers().size());
        team = projectTeamRepository.save(team);

        otherTeam = persistTeam("Workspace IT Other Team", "Workspace IT Other Comic", otherLeaderUser);

        taskedChapter = persistChapter("1", "Chapter One", ChapterStatus.PUBLISHED, 3);
        backlogChapter = persistChapter("2", "Chapter Two", ChapterStatus.PUBLISHED, 2);
        draftChapter = persistChapter("3", "Chapter Three", ChapterStatus.SUBMITTED_FOR_REVIEW, 1);

        task = persistTask(taskedChapter, translatorUser.getId(), "in_progress");
        persistPages(task, translatorUser.getId(), 2, PageStatus.TODO);

        announcement = persistAnnouncement(team.getId(), "Weekly sync notes");
        comment = persistComment(announcement.getId(), "Sounds good to me");
        message = persistMessage(team.getId(), "Hello team");
        pendingRequest = persistJoinRequest(team.getId(), applicantUser, "PENDING");
    }

    // ── helpers ──────────────────────────────────────

    private UserEntity findOrCreateUser(String username, String fullName, String roleName) {
        RoleEntity role = roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName(roleName).build()));
        String email = username + "@example.com";
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username(username)
                        .email(email)
                        .password("Password123!")
                        .fullName(fullName)
                        .status("ACTIVE")
                        .role(role)
                        .build()));
    }

    private ProjectTeamEntity persistTeam(String title, String comicName, UserEntity leader) {
        ProjectTeamEntity entity = ProjectTeamEntity.builder()
                .title(title)
                .comicName(comicName)
                .status("Active")
                .membersCount(0)
                .chaptersCount(0)
                .progress(0)
                .leaderId(leader == null ? null : leader.getId())
                .leaderName(leader == null ? null : leader.getUsername())
                .sourceLang("en")
                .targetLang("vi")
                .priority("High")
                .members(new ArrayList<>())
                .build();
        entity.setDeleted(false);
        return projectTeamRepository.save(entity);
    }

    private void addMember(ProjectTeamEntity target, UserEntity user) {
        ProjectTeamMemberEntity member = ProjectTeamMemberEntity.builder()
                .team(target)
                .user(user)
                .build();
        member.setDeleted(false);
        target.getMembers().add(member);
    }

    private ChapterEntity persistChapter(String number, String title, ChapterStatus status, int pageCount) {
        List<String> images = new ArrayList<>();
        for (int i = 1; i <= pageCount; i++) {
            images.add("http://example.com/" + number + "/page" + i + ".jpg");
        }
        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber(number)
                .title(title)
                .moderationStatus(status)
                .images(images)
                .build();
        chapter.setDeleted(false);
        return chapterRepository.save(chapter);
    }

    private TeamTaskEntity persistTask(ChapterEntity chapter, UUID assigneeId, String status) {
        return taskRepository.save(TeamTaskEntity.builder()
                .projectTeamId(team.getId())
                .chapter(chapter)
                .title("Translate " + (chapter == null ? "chapter" : chapter.getTitle()))
                .status(status)
                .assigneeId(assigneeId)
                .taskType("REGULAR")
                .dueDate("2026-12-31")
                .chapterRewardUsd(new BigDecimal("12.00"))
                .build());
    }

    private List<PageTranslationEntity> persistPages(TeamTaskEntity target, UUID translatorId, int count, PageStatus status) {
        List<PageTranslationEntity> pages = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            PageTranslationEntity page = PageTranslationEntity.builder()
                    .taskId(target)
                    .pageNumber(i)
                    .imageUrl("http://example.com/task/page" + i + ".jpg")
                    .assignedTranslatorId(translatorId)
                    .responsibilityFactor(BigDecimal.ONE.setScale(2))
                    .status(status)
                    .bubbles("[]")
                    .build();
            page.setDeleted(false);
            page.setCompletedAt(status == PageStatus.DONE ? Instant.now() : null);
            pages.add(page);
        }
        return pageTranslationRepository.saveAll(pages);
    }

    private ChapterTranslationEntity persistTranslation(
            ChapterEntity chapter,
            ChapterTranslationStatus status,
            String pagesBubbles
    ) {
        ChapterTranslationEntity translation = ChapterTranslationEntity.builder()
                .chapter(chapter)
                .languageCode("vi")
                .projectTeamId(team.getId())
                .status(status)
                .pagesBubbles(pagesBubbles)
                .build();
        translation.setDeleted(false);
        return chapterTranslationRepository.save(translation);
    }

    private ReportEntity persistAcceptedTranslationReport(UUID translationId, String resolutionNote) {
        ReportCategoryEntity category = ReportCategoryEntity.builder()
                .name("Translation quality " + UUID.randomUUID())
                .description("Created by TeamWorkspaceControllerIT")
                .assignedRole(ReportAssignedRole.PROJECT_LEADER)
                .targetTypes(List.of(ReportTargetType.CHAPTER_TRANSLATIONS))
                .isActive(true)
                .build();
        category.setDeleted(false);
        category = reportCategoryRepository.save(category);

        ReportEntity report = ReportEntity.builder()
                .reporter(readerMemberUser)
                .targetType(ReportTargetType.CHAPTER_TRANSLATIONS)
                .targetId(translationId)
                .category(category)
                .descriptionText("Honorifics are inconsistent")
                .status(ReportStatus.ACCEPTED)
                .handler(leaderUser)
                .resolutionNote(resolutionNote)
                .resolvedAt(Instant.now())
                .build();
        report.setDeleted(false);
        return reportRepository.save(report);
    }

    private void persistTranslatedPages(TeamTaskEntity target, UUID translatorId, String page1Bubbles, String page2Bubbles) {
        List<PageTranslationEntity> pages = persistPages(target, translatorId, 2, PageStatus.DONE);
        pages.get(0).setBubbles(page1Bubbles);
        pages.get(1).setBubbles(page2Bubbles);
        pageTranslationRepository.saveAll(pages);
    }

    private void persistActiveTasks(UUID assigneeId, int count) {
        for (int i = 0; i < count; i++) {
            taskRepository.save(TeamTaskEntity.builder()
                    .projectTeamId(team.getId())
                    .title("Filler task " + i)
                    .status("todo")
                    .assigneeId(assigneeId)
                    .taskType("REGULAR")
                    .build());
        }
    }

    private TeamAnnouncementEntity persistAnnouncement(UUID teamId, String content) {
        return announcementRepository.save(TeamAnnouncementEntity.builder()
                .projectTeamId(teamId)
                .author("Workspace Leader")
                .role("Group Leader")
                .avatar("WL")
                .time("09:00")
                .content(content)
                .likes(0)
                .isPinned(false)
                .isEdited(false)
                .build());
    }

    private TeamPostCommentEntity persistComment(UUID announcementId, String content) {
        return postCommentRepository.save(TeamPostCommentEntity.builder()
                .announcementId(announcementId)
                .author("Workspace Translator")
                .role("Member")
                .avatar("WT")
                .content(content)
                .time("09:05")
                .likes(0)
                .isEdited(false)
                .build());
    }

    private TeamMessageEntity persistMessage(UUID teamId, String text) {
        return messageRepository.save(TeamMessageEntity.builder()
                .projectTeamId(teamId)
                .sender("Workspace Leader")
                .avatar("WL")
                .time("09:10")
                .text(text)
                .build());
    }

    private TeamJoinRequestEntity persistJoinRequest(UUID teamId, UserEntity requester, String status) {
        return joinRequestRepository.save(TeamJoinRequestEntity.builder()
                .projectTeamId(teamId)
                .requesterId(requester.getId())
                .name(requester.getFullName())
                .time("2026-08-01")
                .text("I would like to join this team")
                .roles("Translator")
                .avatar("WA")
                .status(status)
                .build());
    }

    private void persistCooldown(UUID userId, String type, Instant until) {
        cooldownRepository.save(TranslatorCooldownEntity.builder()
                .userId(userId)
                .cooldownType(type)
                .cooldownUntil(until)
                .relatedTeamId(team.getId())
                .build());
    }

    private void persistBan(UUID teamId, UUID userId) {
        joinBanRepository.save(TeamJoinBanEntity.builder()
                .projectTeamId(teamId)
                .userId(userId)
                .bannedBy(leaderUser.getId())
                .reason("Repeated no-shows")
                .build());
    }

    private ObjectNode json() {
        return objectMapper.createObjectNode();
    }

    // ── GET /{teamId}/announcements ──────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-001: GET /team-workspace/{teamId}/announcements - List team announcements should return 200 OK")
    void getAnnouncements() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/announcements", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(announcement.getId().toString())))
                .andExpect(jsonPath("$[0].content", is("Weekly sync notes")))
                .andExpect(jsonPath("$[0].projectTeamId", is(team.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-002: GET /team-workspace/{teamId}/announcements - Unknown team returns an empty list with 200 OK")
    void getAnnouncementsUnknownTeam() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/announcements", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-003: GET /team-workspace/{teamId}/announcements - Missing token should return 401 Unauthorized")
    void getAnnouncementsWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/announcements", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{teamId}/announcements ─────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-004: POST /team-workspace/{teamId}/announcements - Create announcement should return 200 OK with likes defaulted to zero")
    void createAnnouncement() throws Exception {
        ObjectNode body = json();
        body.put("author", "Workspace Leader");
        body.put("role", "Group Leader");
        body.put("avatar", "WL");
        body.put("time", "10:00");
        body.put("content", "Chapter 4 raw files are uploaded");
        body.put("isPinned", false);
        body.put("isEdited", false);

        mockMvc.perform(post(BASE_URL + "/{teamId}/announcements", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.projectTeamId", is(team.getId().toString())))
                .andExpect(jsonPath("$.content", is("Chapter 4 raw files are uploaded")))
                .andExpect(jsonPath("$.likes", is(0)));

        assertThat(announcementRepository.findByProjectTeamId(team.getId())).hasSize(2);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-005: POST /team-workspace/{teamId}/announcements - Provided like count is preserved and returns 200 OK")
    void createAnnouncementKeepsProvidedLikes() throws Exception {
        ObjectNode body = json();
        body.put("author", "Workspace Leader");
        body.put("content", "Imported announcement");
        body.put("likes", 7);
        body.put("imageUrl", "http://example.com/banner.png");
        body.put("isPinned", false);
        body.put("isEdited", false);

        mockMvc.perform(post(BASE_URL + "/{teamId}/announcements", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes", is(7)))
                .andExpect(jsonPath("$.imageUrl", is("http://example.com/banner.png")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-006: POST /team-workspace/{teamId}/announcements - Missing token should return 401 Unauthorized")
    void createAnnouncementWithoutToken() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/announcements", team.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"No auth\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /announcements/{id}/like ─────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-007: PUT /team-workspace/announcements/{id}/like - First like increments the counter and returns 200 OK")
    void likeAnnouncement() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}/like", announcement.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes", is(1)))
                .andExpect(jsonPath("$.likedByUsers", is(translatorUser.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-008: PUT /team-workspace/announcements/{id}/like - Liking twice removes the like and returns 200 OK")
    void unlikeAnnouncement() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}/like", announcement.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE_URL + "/announcements/{id}/like", announcement.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes", is(0)))
                .andExpect(jsonPath("$.likedByUsers", is("")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-009: PUT /team-workspace/announcements/{id}/like - Unknown announcement should return 404 Not Found")
    void likeUnknownAnnouncement() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}/like", UUID.randomUUID())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-010: PUT /team-workspace/announcements/{id}/like - Missing token should return 401 Unauthorized")
    void likeAnnouncementWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}/like", announcement.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /announcements/{id}/pin ──────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-011: PUT /team-workspace/announcements/{id}/pin - Pin an announcement should return 200 OK")
    void pinAnnouncement() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}/pin", announcement.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPinned", is(true)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-012: PUT /team-workspace/announcements/{id}/pin - Pinning twice unpins the announcement and returns 200 OK")
    void unpinAnnouncement() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}/pin", announcement.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE_URL + "/announcements/{id}/pin", announcement.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPinned", is(false)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-013: PUT /team-workspace/announcements/{id}/pin - Unknown announcement should return 404 Not Found")
    void pinUnknownAnnouncement() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}/pin", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-014: PUT /team-workspace/announcements/{id}/pin - Missing token should return 401 Unauthorized")
    void pinAnnouncementWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}/pin", announcement.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /announcements/{id} ──────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-015: PUT /team-workspace/announcements/{id} - Edit content marks the post as edited and returns 200 OK")
    void updateAnnouncementContent() throws Exception {
        ObjectNode body = json();
        body.put("content", "  Updated weekly sync notes  ");

        mockMvc.perform(put(BASE_URL + "/announcements/{id}", announcement.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Updated weekly sync notes")))
                .andExpect(jsonPath("$.isEdited", is(true)))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-016: PUT /team-workspace/announcements/{id} - Update image only keeps the content unchanged and returns 200 OK")
    void updateAnnouncementImageOnly() throws Exception {
        ObjectNode body = json();
        body.put("imageUrl", "http://example.com/new-banner.png");

        mockMvc.perform(put(BASE_URL + "/announcements/{id}", announcement.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl", is("http://example.com/new-banner.png")))
                .andExpect(jsonPath("$.content", is("Weekly sync notes")))
                .andExpect(jsonPath("$.isEdited", is(false)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-017: PUT /team-workspace/announcements/{id} - Blank content is ignored and returns 200 OK")
    void updateAnnouncementWithBlankContent() throws Exception {
        ObjectNode body = json();
        body.put("content", "   ");

        mockMvc.perform(put(BASE_URL + "/announcements/{id}", announcement.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Weekly sync notes")))
                .andExpect(jsonPath("$.isEdited", is(false)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-018: PUT /team-workspace/announcements/{id} - Profanity in the content should return 400 Bad Request")
    void updateAnnouncementWithProfanity() throws Exception {
        ObjectNode body = json();
        body.put("content", "this shit is broken");

        mockMvc.perform(put(BASE_URL + "/announcements/{id}", announcement.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Your post contains inappropriate language.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-019: PUT /team-workspace/announcements/{id} - Unknown announcement should return 404 Not Found")
    void updateUnknownAnnouncement() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Anything\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-020: PUT /team-workspace/announcements/{id} - Missing token should return 401 Unauthorized")
    void updateAnnouncementWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/announcements/{id}", announcement.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /announcements/{id} ───────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-021: DELETE /team-workspace/announcements/{id} - Delete an announcement should return 200 OK")
    void deleteAnnouncement() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/announcements/{id}", announcement.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk());

        assertThat(announcementRepository.findById(announcement.getId())).isEmpty();
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-022: DELETE /team-workspace/announcements/{id} - Unknown announcement should return 404 Not Found")
    void deleteUnknownAnnouncement() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/announcements/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-023: DELETE /team-workspace/announcements/{id} - Missing token should return 401 Unauthorized")
    void deleteAnnouncementWithoutToken() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/announcements/{id}", announcement.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /announcements/{announcementId}/comments ──

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-024: GET /team-workspace/announcements/{announcementId}/comments - List comments should return 200 OK")
    void getComments() throws Exception {
        mockMvc.perform(get(BASE_URL + "/announcements/{announcementId}/comments", announcement.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(comment.getId().toString())))
                .andExpect(jsonPath("$[0].content", is("Sounds good to me")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-025: GET /team-workspace/announcements/{announcementId}/comments - Unknown announcement returns an empty list with 200 OK")
    void getCommentsForUnknownAnnouncement() throws Exception {
        mockMvc.perform(get(BASE_URL + "/announcements/{announcementId}/comments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-026: GET /team-workspace/announcements/{announcementId}/comments - Missing token should return 401 Unauthorized")
    void getCommentsWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/announcements/{announcementId}/comments", announcement.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /announcements/{announcementId}/comments ─

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-027: POST /team-workspace/announcements/{announcementId}/comments - Create comment should return 200 OK")
    void createComment() throws Exception {
        ObjectNode body = json();
        body.put("author", "Workspace Translator");
        body.put("role", "Member");
        body.put("avatar", "WT");
        body.put("content", "I will take chapter 4");
        body.put("time", "11:00");
        body.put("isEdited", false);

        mockMvc.perform(post(BASE_URL + "/announcements/{announcementId}/comments", announcement.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcementId", is(announcement.getId().toString())))
                .andExpect(jsonPath("$.content", is("I will take chapter 4")))
                .andExpect(jsonPath("$.likes", is(0)));

        assertThat(postCommentRepository.findByAnnouncementIdOrderByTimeAsc(announcement.getId())).hasSize(2);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-028: POST /team-workspace/announcements/{announcementId}/comments - Profanity should return 400 Bad Request")
    void createCommentWithProfanity() throws Exception {
        ObjectNode body = json();
        body.put("author", "Workspace Translator");
        body.put("content", "what the fuck is this");
        body.put("isEdited", false);

        mockMvc.perform(post(BASE_URL + "/announcements/{announcementId}/comments", announcement.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Your comment contains inappropriate language.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-029: POST /team-workspace/announcements/{announcementId}/comments - Missing token should return 401 Unauthorized")
    void createCommentWithoutToken() throws Exception {
        mockMvc.perform(post(BASE_URL + "/announcements/{announcementId}/comments", announcement.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"No auth\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /comments/{id}/like ──────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-030: PUT /team-workspace/comments/{id}/like - First like increments the counter and returns 200 OK")
    void likeComment() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}/like", comment.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes", is(1)))
                .andExpect(jsonPath("$.likedByUsers", is(leaderUser.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-031: PUT /team-workspace/comments/{id}/like - Liking twice removes the like and returns 200 OK")
    void unlikeComment() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}/like", comment.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE_URL + "/comments/{id}/like", comment.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes", is(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-032: PUT /team-workspace/comments/{id}/like - Unknown comment should return 404 Not Found")
    void likeUnknownComment() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}/like", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-033: PUT /team-workspace/comments/{id}/like - Missing token should return 401 Unauthorized")
    void likeCommentWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}/like", comment.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /comments/{id} ───────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-034: PUT /team-workspace/comments/{id} - Edit comment should return 200 OK with the edited flag")
    void updateComment() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}", comment.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  Updated comment  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Updated comment")))
                .andExpect(jsonPath("$.isEdited", is(true)))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-035: PUT /team-workspace/comments/{id} - Blank content should return 400 Bad Request")
    void updateCommentWithBlankContent() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}", comment.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Comment content cannot be empty.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-036: PUT /team-workspace/comments/{id} - Profanity should return 400 Bad Request")
    void updateCommentWithProfanity() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}", comment.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"you bitch\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Your comment contains inappropriate language.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-037: PUT /team-workspace/comments/{id} - Unknown comment should return 404 Not Found")
    void updateUnknownComment() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Anything\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-038: PUT /team-workspace/comments/{id} - Missing token should return 401 Unauthorized")
    void updateCommentWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/comments/{id}", comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /comments/{id} ────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-039: DELETE /team-workspace/comments/{id} - Delete a comment should return 200 OK")
    void deleteComment() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/comments/{id}", comment.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk());

        assertThat(postCommentRepository.findById(comment.getId())).isEmpty();
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-040: DELETE /team-workspace/comments/{id} - Unknown comment should return 404 Not Found")
    void deleteUnknownComment() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/comments/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-041: DELETE /team-workspace/comments/{id} - Missing token should return 401 Unauthorized")
    void deleteCommentWithoutToken() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/comments/{id}", comment.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{teamId}/chapter-backlog ────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-042: GET /team-workspace/{teamId}/chapter-backlog - Only published chapters without a task are returned with 200 OK")
    void getChapterBacklog() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/chapter-backlog", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].chapterId", is(backlogChapter.getId().toString())))
                .andExpect(jsonPath("$[0].chapterNumber", is("2")))
                .andExpect(jsonPath("$[0].comicName", is(COMIC_NAME)))
                .andExpect(jsonPath("$[0].pages", is(2)))
                .andExpect(jsonPath("$[0].canCreateTask", is(true)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-043: GET /team-workspace/{teamId}/chapter-backlog - Team without a comic name returns an empty list with 200 OK")
    void getChapterBacklogWithoutComicName() throws Exception {
        ProjectTeamEntity blankTeam = persistTeam("Workspace IT Blank Team", "   ", leaderUser);

        mockMvc.perform(get(BASE_URL + "/{teamId}/chapter-backlog", blankTeam.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-044: GET /team-workspace/{teamId}/chapter-backlog - Comic name without a matching comic returns an empty list with 200 OK")
    void getChapterBacklogWithoutMatchingComic() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/chapter-backlog", otherTeam.getId())
                        .header("Authorization", "Bearer " + otherLeaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-045: GET /team-workspace/{teamId}/chapter-backlog - Unknown team should return 404 Not Found")
    void getChapterBacklogForUnknownTeam() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/chapter-backlog", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Project team not found")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-046: GET /team-workspace/{teamId}/chapter-backlog - Missing token should return 401 Unauthorized")
    void getChapterBacklogWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/chapter-backlog", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── MESSAGES ─────────────────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-047: GET /team-workspace/{teamId}/messages - List group chat messages should return 200 OK")
    void getMessages() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/messages", team.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].text", is("Hello team")))
                .andExpect(jsonPath("$[0].projectTeamId", is(team.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-048: GET /team-workspace/{teamId}/messages - Missing token should return 401 Unauthorized")
    void getMessagesWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/messages", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-049: POST /team-workspace/{teamId}/messages - Send a group chat message should return 200 OK")
    void createMessage() throws Exception {
        ObjectNode body = json();
        body.put("sender", "Workspace Translator");
        body.put("avatar", "WT");
        body.put("time", "12:00");
        body.put("text", "Chapter 2 is ready for review");

        mockMvc.perform(post(BASE_URL + "/{teamId}/messages", team.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.projectTeamId", is(team.getId().toString())))
                .andExpect(jsonPath("$.text", is("Chapter 2 is ready for review")));

        assertThat(messageRepository.findByProjectTeamId(team.getId())).hasSize(2);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-050: POST /team-workspace/{teamId}/messages - Missing token should return 401 Unauthorized")
    void createMessageWithoutToken() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/messages", team.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"No auth\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-051: DELETE /team-workspace/{teamId}/messages/{messageId} - Delete a message should return 200 OK")
    void deleteMessage() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/messages/{messageId}", team.getId(), message.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertThat(messageRepository.findById(message.getId())).isEmpty();
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-052: DELETE /team-workspace/{teamId}/messages/{messageId} - Unknown message should return 404 Not Found")
    void deleteUnknownMessage() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/messages/{messageId}", team.getId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-053: DELETE /team-workspace/{teamId}/messages/{messageId} - Missing token should return 401 Unauthorized")
    void deleteMessageWithoutToken() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/messages/{messageId}", team.getId(), message.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{teamId}/messages/warn ─────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-054: POST /team-workspace/{teamId}/messages/warn - Warn a member by id posts a system message and notifies the member with 200 OK")
    void warnMemberById() throws Exception {
        ObjectNode body = json();
        body.put("memberId", translatorUser.getId().toString());
        body.put("memberName", translatorUser.getFullName());
        body.put("reason", "Missed the agreed deadline");

        mockMvc.perform(post(BASE_URL + "/{teamId}/messages/warn", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender", is("SYSTEM")))
                .andExpect(jsonPath("$.projectTeamId", is(team.getId().toString())))
                .andExpect(jsonPath("$.text", containsString("Missed the agreed deadline")));

        List<NotificationEntity> notifications = notificationRepository.findByUserId(translatorUser.getId());
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.get(0).getType()).isEqualTo("WARNING");
        assertThat(notifications.get(0).getMessage()).contains("Missed the agreed deadline");
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-055: POST /team-workspace/{teamId}/messages/warn - Warn a member resolved by username should return 200 OK")
    void warnMemberByName() throws Exception {
        ObjectNode body = json();
        body.put("memberName", translatorUser.getUsername());
        body.put("reason", "Please follow the glossary");

        mockMvc.perform(post(BASE_URL + "/{teamId}/messages/warn", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", containsString(translatorUser.getUsername())));

        assertThat(notificationRepository.findByUserId(translatorUser.getId())).isNotEmpty();
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-056: POST /team-workspace/{teamId}/messages/warn - Blank reason falls back to the default warning text and returns 200 OK")
    void warnMemberWithBlankReason() throws Exception {
        ObjectNode body = json();
        body.put("memberId", translatorUser.getId().toString());
        body.put("memberName", translatorUser.getFullName());
        body.put("reason", "   ");

        mockMvc.perform(post(BASE_URL + "/{teamId}/messages/warn", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", containsString("Violation of group chat guidelines or translation quality standards")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-057: POST /team-workspace/{teamId}/messages/warn - Unknown member still posts the system message and returns 200 OK")
    void warnUnknownMember() throws Exception {
        ObjectNode body = json();
        body.put("memberId", UUID.randomUUID().toString());
        body.put("memberName", "Ghost Member");
        body.put("reason", "Spamming the chat");

        mockMvc.perform(post(BASE_URL + "/{teamId}/messages/warn", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender", is("SYSTEM")))
                .andExpect(jsonPath("$.text", containsString("Ghost Member")));

        assertThat(messageRepository.findByProjectTeamId(team.getId())).hasSize(2);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-058: POST /team-workspace/{teamId}/messages/warn - Missing token should return 401 Unauthorized")
    void warnMemberWithoutToken() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/messages/warn", team.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberName\":\"Someone\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{teamId}/tasks ──────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-059: GET /team-workspace/{teamId}/tasks - List team tasks with page progress should return 200 OK")
    void getTasks() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(task.getId().toString())))
                .andExpect(jsonPath("$[0].status", is("in_progress")))
                .andExpect(jsonPath("$[0].taskType", is("REGULAR")))
                .andExpect(jsonPath("$[0].assigneeId", is(translatorUser.getId().toString())))
                .andExpect(jsonPath("$[0].totalPages", is(2)))
                .andExpect(jsonPath("$[0].completedPages", is(0)))
                .andExpect(jsonPath("$[0].chapterId", is(taskedChapter.getId().toString())))
                .andExpect(jsonPath("$[0].chapter.chapterNumber", is("1")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-060: GET /team-workspace/{teamId}/tasks - Unknown team returns an empty list with 200 OK")
    void getTasksForUnknownTeam() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/tasks", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-061: GET /team-workspace/{teamId}/tasks - Missing token should return 401 Unauthorized")
    void getTasksWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/tasks", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /tasks/by-chapter/{chapterId} ────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-062: GET /team-workspace/tasks/by-chapter/{chapterId} - List tasks of a chapter should return 200 OK")
    void getTasksByChapter() throws Exception {
        mockMvc.perform(get(BASE_URL + "/tasks/by-chapter/{chapterId}", taskedChapter.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(task.getId().toString())))
                .andExpect(jsonPath("$[0].chapterTitle", is("Chapter One")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-063: GET /team-workspace/tasks/by-chapter/{chapterId} - Chapter without tasks returns an empty list with 200 OK")
    void getTasksByChapterWithoutTasks() throws Exception {
        mockMvc.perform(get(BASE_URL + "/tasks/by-chapter/{chapterId}", backlogChapter.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-064: GET /team-workspace/tasks/by-chapter/{chapterId} - Missing token should return 401 Unauthorized")
    void getTasksByChapterWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/tasks/by-chapter/{chapterId}", taskedChapter.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{teamId}/tasks ─────────────────────────

    private ObjectNode createTaskBody(UUID chapterId, UUID assigneeId) {
        ObjectNode body = json();
        body.put("title", "Translate chapter 2");
        body.put("status", "todo");
        if (assigneeId != null) {
            body.put("assigneeId", assigneeId.toString());
        }
        if (chapterId != null) {
            body.put("chapterId", chapterId.toString());
        }
        body.put("dueDate", "2026-12-31");
        return body;
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-065: POST /team-workspace/{teamId}/tasks - Project Leader creates a task should return 201 Created with derived reward and page set")
    void createTask() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.projectTeamId", is(team.getId().toString())))
                .andExpect(jsonPath("$.status", is("backlog")))
                .andExpect(jsonPath("$.taskType", is("REGULAR")))
                .andExpect(jsonPath("$.assigneeId", is(translatorUser.getId().toString())))
                .andExpect(jsonPath("$.chapterId", is(backlogChapter.getId().toString())))
                .andExpect(jsonPath("$.chapter.id", is(backlogChapter.getId().toString())));

        TeamTaskEntity created = taskRepository.findByChapter_Id(backlogChapter.getId()).get(0);
        assertThat(created.getChapterRewardUsd())
                .isEqualByComparingTo(translatorPaymentService.deriveChapterRewardUsd(2));
        assertThat(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(created.getId())).hasSize(2);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-065b: After creating a task the chapter leaves the backlog and a second create is rejected with 409 Conflict")
    void createTaskRemovesChapterFromSelectionImmediately() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chapterId", is(backlogChapter.getId().toString())));

        mockMvc.perform(get(BASE_URL + "/{teamId}/chapter-backlog", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.chapterId=='" + backlogChapter.getId() + "')]", hasSize(0)));

        mockMvc.perform(get(BASE_URL + "/{teamId}/chapters", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.chapterId=='" + backlogChapter.getId() + "')].canCreateTask", contains(false)));

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("This chapter already has a task in this project")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-066: POST /team-workspace/{teamId}/tasks - Explicit chapter reward is stored with two decimals and returns 201 Created")
    void createTaskWithExplicitReward() throws Exception {
        ObjectNode body = createTaskBody(backlogChapter.getId(), translatorUser.getId());
        body.put("chapterRewardUsd", new BigDecimal("15.555"));

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        TeamTaskEntity created = taskRepository.findByChapter_Id(backlogChapter.getId()).get(0);
        assertThat(created.getChapterRewardUsd()).isEqualByComparingTo(new BigDecimal("15.56"));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-067: POST /team-workspace/{teamId}/tasks - Custom task type is preserved and returns 201 Created")
    void createTaskWithCustomType() throws Exception {
        ObjectNode body = createTaskBody(backlogChapter.getId(), translatorUser.getId());
        body.put("taskType", "URGENT");

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType", is("URGENT")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-068: POST /team-workspace/{teamId}/tasks - Missing chapterId should return 400 Bad Request")
    void createTaskWithoutChapterId() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(null, translatorUser.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("chapterId is required")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-069: POST /team-workspace/{teamId}/tasks - Unknown chapter should return 400 Bad Request")
    void createTaskWithUnknownChapter() throws Exception {
        UUID unknownChapterId = UUID.randomUUID();

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(unknownChapterId, translatorUser.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Chapter not found: " + unknownChapterId)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-070: POST /team-workspace/{teamId}/tasks - Missing assignee should return 201 Created")
    void createTaskWithoutAssignee() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.assigneeId", nullValue()))
                .andExpect(jsonPath("$.status", is("backlog")));

        TeamTaskEntity created = taskRepository.findByChapter_Id(backlogChapter.getId()).get(0);
        assertThat(created.getAssigneeId()).isNull();
        assertThat(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(created.getId()))
                .hasSize(2)
                .allMatch(page -> page.getAssignedTranslatorId() == null);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-071: POST /team-workspace/{teamId}/tasks - Assignee outside the team should return 400 Bad Request")
    void createTaskWithNonMemberAssignee() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), applicantUser.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The assignee must be an approved member of this project team")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-072: POST /team-workspace/{teamId}/tasks - Member without the TRANSLATOR role should return 400 Bad Request")
    void createTaskWithNonTranslatorAssignee() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), readerMemberUser.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Only users with the TRANSLATOR role can be assigned payout-eligible tasks")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-073: POST /team-workspace/{teamId}/tasks - Translator already at the active task limit should return 400 Bad Request")
    void createTaskWhenAssigneeIsAtTaskLimit() throws Exception {
        persistActiveTasks(secondTranslatorUser.getId(), 5);

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), secondTranslatorUser.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("tối đa 5 công việc")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-074: POST /team-workspace/{teamId}/tasks - Creating an already completed task should return 409 Conflict")
    void createTaskWithCompletedStatus() throws Exception {
        ObjectNode body = createTaskBody(backlogChapter.getId(), translatorUser.getId());
        body.put("status", "completed");

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("A task can only become completed after all pages are DONE and the Project Leader approves the review")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-075: POST /team-workspace/{teamId}/tasks - Chapter without pages should return 400 Bad Request")
    void createTaskForChapterWithoutPages() throws Exception {
        ChapterEntity emptyChapter = persistChapter("4", "Chapter Four", ChapterStatus.PUBLISHED, 0);

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(emptyChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Chapter has no pages")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-076: POST /team-workspace/{teamId}/tasks - Translator cannot create tasks and should return 403 Forbidden")
    void createTaskAsTranslator() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only this team's Project Leader can create or assign tasks")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-077: POST /team-workspace/{teamId}/tasks - Leader of another team should return 403 Forbidden")
    void createTaskAsForeignLeader() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + otherLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-078: POST /team-workspace/{teamId}/tasks - Missing token should return 401 Unauthorized")
    void createTaskWithoutToken() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(backlogChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{teamId}/members ────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-079: GET /team-workspace/{teamId}/members - List members including the leader should return 200 OK")
    void getTeamMembers() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/members", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.id=='" + leaderUser.getId() + "')].role", contains("Group Leader")))
                .andExpect(jsonPath("$[?(@.id=='" + translatorUser.getId() + "')].role", contains("Member")))
                .andExpect(jsonPath("$[?(@.id=='" + translatorUser.getId() + "')].online", contains(false)))
                .andExpect(jsonPath("$[?(@.id=='" + translatorUser.getId() + "')].avatar", contains("WT")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-080: GET /team-workspace/{teamId}/members - Team without members or leader returns an empty list with 200 OK")
    void getTeamMembersForEmptyTeam() throws Exception {
        ProjectTeamEntity emptyTeam = persistTeam("Workspace IT Empty Team", COMIC_NAME, null);

        mockMvc.perform(get(BASE_URL + "/{teamId}/members", emptyTeam.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-081: GET /team-workspace/{teamId}/members - Unknown team should return 404 Not Found")
    void getTeamMembersForUnknownTeam() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/members", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-082: GET /team-workspace/{teamId}/members - Missing token should return 401 Unauthorized")
    void getTeamMembersWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/members", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /tasks/{id} ──────────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-083: GET /team-workspace/tasks/{id} - Get task detail should return 200 OK")
    void getTaskById() throws Exception {
        mockMvc.perform(get(BASE_URL + "/tasks/{id}", task.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(task.getId().toString())))
                .andExpect(jsonPath("$.title", is("Translate Chapter One")))
                .andExpect(jsonPath("$.chapter.id", is(taskedChapter.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-084: GET /team-workspace/tasks/{id} - Unknown task should return 404 Not Found")
    void getUnknownTaskById() throws Exception {
        mockMvc.perform(get(BASE_URL + "/tasks/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Task not found")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-085: GET /team-workspace/tasks/{id} - Malformed task id should return 400 Bad Request")
    void getTaskByIdWithMalformedId() throws Exception {
        mockMvc.perform(get(BASE_URL + "/tasks/{id}", "not-a-uuid")
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-086: GET /team-workspace/tasks/{id} - Missing token should return 401 Unauthorized")
    void getTaskByIdWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/tasks/{id}", task.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{teamId}/requests ───────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-087: GET /team-workspace/{teamId}/requests - Only pending requests are returned with 200 OK")
    void getRequests() throws Exception {
        persistJoinRequest(team.getId(), secondTranslatorUser, "REJECTED");

        mockMvc.perform(get(BASE_URL + "/{teamId}/requests", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(pendingRequest.getId().toString())))
                .andExpect(jsonPath("$[0].status", is("PENDING")))
                .andExpect(jsonPath("$[0].requesterId", is(applicantUser.getId().toString())))
                // The workload counters are JPA @Transient, and Hibernate6Module keeps them out of the payload.
                .andExpect(jsonPath("$[0].activeProjectsCount").doesNotExist())
                .andExpect(jsonPath("$[0].activeTasksCount").doesNotExist());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-088: GET /team-workspace/{teamId}/requests - Missing token should return 401 Unauthorized")
    void getRequestsWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/requests", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /requests/by-name ────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-089: GET /team-workspace/requests/by-name - Search requests by applicant name should return 200 OK")
    void getRequestsByName() throws Exception {
        mockMvc.perform(get(BASE_URL + "/requests/by-name")
                        .header("Authorization", "Bearer " + leaderToken)
                        .param("name", applicantUser.getFullName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(pendingRequest.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-090: GET /team-workspace/requests/by-name - Unknown name returns an empty list with 200 OK")
    void getRequestsByUnknownName() throws Exception {
        mockMvc.perform(get(BASE_URL + "/requests/by-name")
                        .header("Authorization", "Bearer " + leaderToken)
                        .param("name", "Nobody At All"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-091: GET /team-workspace/requests/by-name - Missing name parameter is rejected as a server error (documents current behaviour)")
    void getRequestsByNameWithoutParam() throws Exception {
        mockMvc.perform(get(BASE_URL + "/requests/by-name")
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-092: GET /team-workspace/requests/by-name - Missing token should return 401 Unauthorized")
    void getRequestsByNameWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/requests/by-name").param("name", "Anyone"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{teamId}/requests ──────────────────────

    private String joinRequestBody(String text) throws Exception {
        ObjectNode body = json();
        body.put("name", applicantUser.getFullName());
        body.put("time", "2026-08-02");
        body.put("text", text);
        body.put("roles", "Translator");
        body.put("avatar", "WA");
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-093: POST /team-workspace/{teamId}/requests - Translator applies to a team should return 200 OK with a pending request")
    void createRequest() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/requests", otherTeam.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinRequestBody("I have five years of manga translation experience")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.projectTeamId", is(otherTeam.getId().toString())))
                .andExpect(jsonPath("$.requesterId", is(applicantUser.getId().toString())))
                .andExpect(jsonPath("$.status", is("PENDING")));

        assertThat(joinRequestRepository.findByRequesterIdAndStatus(applicantUser.getId(), "PENDING")).hasSize(2);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-094: POST /team-workspace/{teamId}/requests - Applying twice to the same team should return 400 Bad Request")
    void createDuplicateRequest() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/requests", team.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinRequestBody("Applying again")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("You have already applied to this team!")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-095: POST /team-workspace/{teamId}/requests - Banned applicant should return 403 Forbidden")
    void createRequestWhenBanned() throws Exception {
        persistBan(otherTeam.getId(), applicantUser.getId());

        mockMvc.perform(post(BASE_URL + "/{teamId}/requests", otherTeam.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinRequestBody("Let me back in")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You are banned from applying to this team.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-096: POST /team-workspace/{teamId}/requests - Applicant on cooldown should return 429 Too Many Requests")
    void createRequestWhenOnCooldown() throws Exception {
        persistCooldown(applicantUser.getId(), "CANCEL", Instant.now().plusSeconds(7200));

        mockMvc.perform(post(BASE_URL + "/{teamId}/requests", otherTeam.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinRequestBody("Trying again")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("You are on cooldown")))
                .andExpect(jsonPath("$.cooldownType", is("CANCEL")))
                .andExpect(jsonPath("$.cooldownUntil", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-097: POST /team-workspace/{teamId}/requests - Applicant at the five slot limit should return 400 Bad Request")
    void createRequestWhenSlotsExhausted() throws Exception {
        for (int i = 0; i < 4; i++) {
            persistJoinRequest(UUID.randomUUID(), applicantUser, "PENDING");
        }

        mockMvc.perform(post(BASE_URL + "/{teamId}/requests", otherTeam.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinRequestBody("One more team please")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("maximum of 5 active teams/applications")))
                .andExpect(jsonPath("$.pendingApplications", is(5)))
                .andExpect(jsonPath("$.maxSlots", is(5)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-098: POST /team-workspace/{teamId}/requests - Applicant at the active task limit should return 400 Bad Request")
    void createRequestWhenTaskLimitReached() throws Exception {
        persistActiveTasks(applicantUser.getId(), 5);

        mockMvc.perform(post(BASE_URL + "/{teamId}/requests", otherTeam.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinRequestBody("I still have capacity")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.activeTasks", is(5)))
                .andExpect(jsonPath("$.maxTasks", is(5)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-099: POST /team-workspace/{teamId}/requests - Missing token should return 401 Unauthorized")
    void createRequestWithoutToken() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/requests", otherTeam.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinRequestBody("No auth")))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /requests/{id}/decision ──────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-100: PUT /team-workspace/requests/{id}/decision - Approving a request adds the member and returns 200 OK")
    void approveRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/decision", pendingRequest.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.decidedAt", notNullValue()));

        ProjectTeamEntity updated = projectTeamRepository.findById(team.getId()).orElseThrow();
        assertThat(updated.getMembersCount()).isEqualTo(4);
        assertThat(updated.getMembers())
                .anyMatch(member -> member.getUser().getId().equals(applicantUser.getId()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-101: PUT /team-workspace/requests/{id}/decision - Rejecting a request should return 200 OK without adding a member")
    void rejectRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/decision", pendingRequest.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));

        ProjectTeamEntity updated = projectTeamRepository.findById(team.getId()).orElseThrow();
        assertThat(updated.getMembers())
                .noneMatch(member -> member.getUser().getId().equals(applicantUser.getId()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-102: PUT /team-workspace/requests/{id}/decision - Unsupported decision value should return 400 Bad Request")
    void decideRequestWithInvalidDecision() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/decision", pendingRequest.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"maybe\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-103: PUT /team-workspace/requests/{id}/decision - Request that is no longer pending should return 400 Bad Request")
    void decideAlreadyDecidedRequest() throws Exception {
        TeamJoinRequestEntity decided = persistJoinRequest(team.getId(), secondTranslatorUser, "APPROVED");

        mockMvc.perform(put(BASE_URL + "/requests/{id}/decision", decided.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("This request is no longer pending.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-104: PUT /team-workspace/requests/{id}/decision - Applicant already in five teams cannot be approved and returns 400 Bad Request")
    void approveRequestWhenApplicantIsAtTeamLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            persistTeam("Workspace IT Applicant Team " + i, "Comic " + i, applicantUser);
        }

        mockMvc.perform(put(BASE_URL + "/requests/{id}/decision", pendingRequest.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("This translator has already reached the maximum of 5 active teams. Cannot approve.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-105: PUT /team-workspace/requests/{id}/decision - Unknown request should return 404 Not Found")
    void decideUnknownRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/decision", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-106: PUT /team-workspace/requests/{id}/decision - Missing token should return 401 Unauthorized")
    void decideRequestWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/decision", pendingRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /requests/{id}/cancel ────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-107: PUT /team-workspace/requests/{id}/cancel - Applicant cancels their own request and starts a cooldown with 200 OK")
    void cancelRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/cancel", pendingRequest.getId())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("12-hour cooldown")));

        TeamJoinRequestEntity cancelled = joinRequestRepository.findById(pendingRequest.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cooldownRepository.findActiveCooldowns(applicantUser.getId(), Instant.now())).hasSize(1);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-108: PUT /team-workspace/requests/{id}/cancel - Another user cannot cancel the request and gets 403 Forbidden")
    void cancelRequestAsOtherUser() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/cancel", pendingRequest.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You can only cancel your own applications.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-109: PUT /team-workspace/requests/{id}/cancel - Non pending request should return 400 Bad Request")
    void cancelNonPendingRequest() throws Exception {
        TeamJoinRequestEntity rejected = persistJoinRequest(team.getId(), applicantUser, "REJECTED");

        mockMvc.perform(put(BASE_URL + "/requests/{id}/cancel", rejected.getId())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Only pending applications can be cancelled.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-110: PUT /team-workspace/requests/{id}/cancel - Unknown request should return 404 Not Found")
    void cancelUnknownRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/cancel", UUID.randomUUID())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-111: PUT /team-workspace/requests/{id}/cancel - Missing token should return 401 Unauthorized")
    void cancelRequestWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/requests/{id}/cancel", pendingRequest.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{teamId}/ban/{userId} ──────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-112: POST /team-workspace/{teamId}/ban/{userId} - Leader bans a user and auto rejects the pending request with 200 OK")
    void banUser() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Plagiarised translations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("User banned from this team.")));

        assertThat(joinBanRepository.existsByProjectTeamIdAndUserId(team.getId(), applicantUser.getId())).isTrue();
        assertThat(joinRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-113: POST /team-workspace/{teamId}/ban/{userId} - Ban without a body uses the default reason and returns 200 OK")
    void banUserWithoutBody() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        TeamJoinBanEntity ban = joinBanRepository.findByProjectTeamIdAndUserId(team.getId(), applicantUser.getId()).orElseThrow();
        assertThat(ban.getReason()).isEqualTo("No reason provided");
        assertThat(ban.getBannedBy()).isEqualTo(leaderUser.getId());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-114: POST /team-workspace/{teamId}/ban/{userId} - Banning an already banned user should return 400 Bad Request")
    void banAlreadyBannedUser() throws Exception {
        persistBan(team.getId(), applicantUser.getId());

        mockMvc.perform(post(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Again\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("This user is already banned from this team.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-115: POST /team-workspace/{teamId}/ban/{userId} - Non leader should return 403 Forbidden")
    void banUserAsNonLeader() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not allowed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only the team leader can ban members.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-116: POST /team-workspace/{teamId}/ban/{userId} - Unknown team should return 404 Not Found")
    void banUserInUnknownTeam() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/ban/{userId}", UUID.randomUUID(), applicantUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-117: POST /team-workspace/{teamId}/ban/{userId} - Missing token should return 401 Unauthorized")
    void banUserWithoutToken() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), applicantUser.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /{teamId}/ban/{userId} ────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-118: DELETE /team-workspace/{teamId}/ban/{userId} - Leader unbans a user should return 200 OK")
    void unbanUser() throws Exception {
        persistBan(team.getId(), applicantUser.getId());

        mockMvc.perform(delete(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("User unbanned.")));

        assertThat(joinBanRepository.existsByProjectTeamIdAndUserId(team.getId(), applicantUser.getId())).isFalse();
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-119: DELETE /team-workspace/{teamId}/ban/{userId} - Unbanning a user who is not banned is idempotent and returns 200 OK")
    void unbanUserWhoIsNotBanned() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), secondTranslatorUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-120: DELETE /team-workspace/{teamId}/ban/{userId} - Non leader should return 403 Forbidden")
    void unbanUserAsNonLeader() throws Exception {
        persistBan(team.getId(), applicantUser.getId());

        mockMvc.perform(delete(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only the team leader can unban members.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-121: DELETE /team-workspace/{teamId}/ban/{userId} - Unknown team should return 404 Not Found")
    void unbanUserInUnknownTeam() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/ban/{userId}", UUID.randomUUID(), applicantUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-122: DELETE /team-workspace/{teamId}/ban/{userId} - Missing token should return 401 Unauthorized")
    void unbanUserWithoutToken() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/ban/{userId}", team.getId(), applicantUser.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{teamId}/bans ───────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-123: GET /team-workspace/{teamId}/bans - List banned users should return 200 OK")
    void getBannedUsers() throws Exception {
        persistBan(team.getId(), applicantUser.getId());

        mockMvc.perform(get(BASE_URL + "/{teamId}/bans", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is(applicantUser.getId().toString())))
                .andExpect(jsonPath("$[0].reason", is("Repeated no-shows")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-124: GET /team-workspace/{teamId}/bans - Team without bans returns an empty list with 200 OK")
    void getBannedUsersWhenEmpty() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/bans", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-125: GET /team-workspace/{teamId}/bans - Missing token should return 401 Unauthorized")
    void getBannedUsersWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/bans", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /my-application-status ───────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-126: GET /team-workspace/my-application-status - Translator without applications sees five free slots with 200 OK")
    void getMyApplicationStatus() throws Exception {
        mockMvc.perform(get(BASE_URL + "/my-application-status")
                        .header("Authorization", "Bearer " + secondTranslatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinedTeams", is(0)))
                .andExpect(jsonPath("$.pendingApplications", is(0)))
                .andExpect(jsonPath("$.usedSlots", is(0)))
                .andExpect(jsonPath("$.availableSlots", is(5)))
                .andExpect(jsonPath("$.maxSlots", is(5)))
                .andExpect(jsonPath("$.cooldownUntil", is("")))
                .andExpect(jsonPath("$.cooldownType", is("")))
                .andExpect(jsonPath("$.pendingDetails", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-127: GET /team-workspace/my-application-status - Pending applications are counted and detailed with 200 OK")
    void getMyApplicationStatusWithPendingApplication() throws Exception {
        mockMvc.perform(get(BASE_URL + "/my-application-status")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingApplications", is(1)))
                .andExpect(jsonPath("$.usedSlots", is(1)))
                .andExpect(jsonPath("$.availableSlots", is(4)))
                .andExpect(jsonPath("$.pendingDetails", hasSize(1)))
                .andExpect(jsonPath("$.pendingDetails[0].requestId", is(pendingRequest.getId().toString())))
                .andExpect(jsonPath("$.pendingDetails[0].projectTeamId", is(team.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-128: GET /team-workspace/my-application-status - Active cooldown is reported with 200 OK")
    void getMyApplicationStatusWithCooldown() throws Exception {
        persistCooldown(applicantUser.getId(), "LEAVE", Instant.now().plusSeconds(3600));

        mockMvc.perform(get(BASE_URL + "/my-application-status")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cooldownType", is("LEAVE")))
                .andExpect(jsonPath("$.cooldownUntil", not(emptyString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-129: GET /team-workspace/my-application-status - Leader of one team reports a used slot with 200 OK")
    void getMyApplicationStatusAsLeader() throws Exception {
        mockMvc.perform(get(BASE_URL + "/my-application-status")
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinedTeams", is(1)))
                .andExpect(jsonPath("$.availableSlots", is(4)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-130: GET /team-workspace/my-application-status - Missing token should return 401 Unauthorized")
    void getMyApplicationStatusWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/my-application-status"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /requests/{id} ────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-131: DELETE /team-workspace/requests/{id} - Delete a join request should return 200 OK")
    void deleteRequest() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/requests/{id}", pendingRequest.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk());

        assertThat(joinRequestRepository.findById(pendingRequest.getId())).isEmpty();
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-132: DELETE /team-workspace/requests/{id} - Unknown request should return 404 Not Found")
    void deleteUnknownRequest() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/requests/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-133: DELETE /team-workspace/requests/{id} - Missing token should return 401 Unauthorized")
    void deleteRequestWithoutToken() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/requests/{id}", pendingRequest.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /{teamId}/members/{memberId} ──────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-134: DELETE /team-workspace/{teamId}/members/{memberId} - Remove a member applies a leave cooldown and returns 200 OK")
    void removeMember() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/members/{memberId}", team.getId(), translatorUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("24-hour cooldown")));

        ProjectTeamEntity updated = projectTeamRepository.findById(team.getId()).orElseThrow();
        assertThat(updated.getMembersCount()).isEqualTo(2);
        assertThat(cooldownRepository.findActiveCooldowns(translatorUser.getId(), Instant.now()))
                .extracting(TranslatorCooldownEntity::getCooldownType)
                .containsExactly("LEAVE");
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-135: DELETE /team-workspace/{teamId}/members/{memberId} - Removing the group leader should return 400 Bad Request")
    void removeGroupLeader() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/members/{memberId}", team.getId(), leaderUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Cannot remove the Group Leader from the team.")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-136: DELETE /team-workspace/{teamId}/members/{memberId} - User who is not a member should return 404 Not Found")
    void removeNonMember() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/members/{memberId}", team.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-137: DELETE /team-workspace/{teamId}/members/{memberId} - Unknown team should return 404 Not Found")
    void removeMemberFromUnknownTeam() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/members/{memberId}", UUID.randomUUID(), translatorUser.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-138: DELETE /team-workspace/{teamId}/members/{memberId} - Missing token should return 401 Unauthorized")
    void removeMemberWithoutToken() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{teamId}/members/{memberId}", team.getId(), translatorUser.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /tasks/{taskId}/submit-for-review ────────

    private TeamTaskEntity taskWithCompletedPages() {
        TeamTaskEntity readyTask = persistTask(backlogChapter, translatorUser.getId(), "in_progress");
        persistPages(readyTask, translatorUser.getId(), 2, PageStatus.DONE);
        return readyTask;
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-139: PUT /team-workspace/tasks/{taskId}/submit-for-review - Assigned translator submits a finished task and returns 200 OK")
    void submitForReviewAsAssignee() throws Exception {
        TeamTaskEntity readyTask = taskWithCompletedPages();

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", readyTask.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertThat(taskRepository.findById(readyTask.getId()).orElseThrow().getStatus()).isEqualTo("under_review");
        assertThat(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(readyTask.getId()))
                .allMatch(page -> page.getReviewBaselineBubbles() != null);
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-140: PUT /team-workspace/tasks/{taskId}/submit-for-review - Project Leader can submit the task and returns 200 OK")
    void submitForReviewAsLeader() throws Exception {
        TeamTaskEntity readyTask = taskWithCompletedPages();

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", readyTask.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-141: PUT /team-workspace/tasks/{taskId}/submit-for-review - Task with unfinished pages should return 409 Conflict")
    void submitForReviewWithIncompletePages() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", task.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("All pages must be marked DONE before review")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-142: PUT /team-workspace/tasks/{taskId}/submit-for-review - Task without pages should return 409 Conflict")
    void submitForReviewWithoutPages() throws Exception {
        TeamTaskEntity emptyTask = persistTask(backlogChapter, translatorUser.getId(), "in_progress");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", emptyTask.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("The task has no pages to review")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-143: PUT /team-workspace/tasks/{taskId}/submit-for-review - Completed task should return 409 Conflict")
    void submitCompletedTaskForReview() throws Exception {
        TeamTaskEntity completedTask = persistTask(backlogChapter, translatorUser.getId(), "completed");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", completedTask.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("A completed task cannot be submitted for review again")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-144: PUT /team-workspace/tasks/{taskId}/submit-for-review - Translator who is not the assignee should return 403 Forbidden")
    void submitForReviewAsOtherTranslator() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", task.getId())
                        .header("Authorization", "Bearer " + secondTranslatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only the assigned Translator or this team's Project Leader can submit this task for review")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-145: PUT /team-workspace/tasks/{taskId}/submit-for-review - Reader role should return 403 Forbidden")
    void submitForReviewAsReader() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", task.getId())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-146: PUT /team-workspace/tasks/{taskId}/submit-for-review - Unknown task should return 404 Not Found")
    void submitUnknownTaskForReview() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Task not found")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-147: PUT /team-workspace/tasks/{taskId}/submit-for-review - Missing token should return 401 Unauthorized")
    void submitForReviewWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/submit-for-review", task.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /tasks/{taskId} ──────────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-148: PUT /team-workspace/tasks/{taskId} - Leader updates title and due date should return 200 OK")
    void updateTaskTitleAndDueDate() throws Exception {
        ObjectNode body = json();
        body.put("title", "Translate chapter 1 (revised)");
        body.put("dueDate", "2027-01-15");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Translate chapter 1 (revised)")))
                .andExpect(jsonPath("$.dueDate", is("2027-01-15")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-149b: PUT /team-workspace/tasks/{taskId} - First save of a backlog task should move it to in_progress")
    void firstSavePromotesBacklogTaskToInProgress() throws Exception {
        TeamTaskEntity backlogTask = persistTask(backlogChapter, translatorUser.getId(), "backlog");

        ObjectNode body = json();
        body.put("title", "Started translation");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", backlogTask.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Started translation")))
                .andExpect(jsonPath("$.status", is("in_progress")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-149: PUT /team-workspace/tasks/{taskId} - Status change to an open state clears the completion time and returns 200 OK")
    void updateTaskStatus() throws Exception {
        ObjectNode body = json();
        body.put("status", "todo");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("todo")))
                .andExpect(jsonPath("$.completedAt", nullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-150: PUT /team-workspace/tasks/{taskId} - Marking a task completed directly should return 409 Conflict")
    void updateTaskToCompleted() throws Exception {
        ObjectNode body = json();
        body.put("status", "completed");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Use the review approval flow to complete a task after every page is DONE")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-151: PUT /team-workspace/tasks/{taskId} - Update chapter reward should return 200 OK with two decimals")
    void updateTaskReward() throws Exception {
        ObjectNode body = json();
        body.put("chapterRewardUsd", "18.999");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getChapterRewardUsd())
                .isEqualByComparingTo(new BigDecimal("19.00"));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-152: PUT /team-workspace/tasks/{taskId} - Non positive chapter reward should return 400 Bad Request")
    void updateTaskWithNonPositiveReward() throws Exception {
        ObjectNode body = json();
        body.put("chapterRewardUsd", "0");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Chapter reward must be greater than zero")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-153: PUT /team-workspace/tasks/{taskId} - Malformed chapter reward should return 400 Bad Request")
    void updateTaskWithMalformedReward() throws Exception {
        ObjectNode body = json();
        body.put("chapterRewardUsd", "twenty dollars");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid chapter reward")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-154: PUT /team-workspace/tasks/{taskId} - Changing the reward of a settled task should return 409 Conflict")
    void updateRewardOfSettledTask() throws Exception {
        task.setSettledAt(Instant.now());
        taskRepository.save(task);

        ObjectNode body = json();
        body.put("chapterRewardUsd", "20.00");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("A settled chapter reward cannot be changed")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-155: PUT /team-workspace/tasks/{taskId} - Re-sending the current assignee should return 200 OK")
    void updateTaskWithSameAssignee() throws Exception {
        ObjectNode body = json();
        body.put("assigneeId", translatorUser.getId().toString());

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId", is(translatorUser.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-155b: PUT /team-workspace/tasks/{taskId} - First assignee on an unassigned task should return 200 OK and assign pages")
    void updateTaskWithFirstAssignee() throws Exception {
        TeamTaskEntity unassignedTask = persistTask(backlogChapter, null, "todo");
        persistPages(unassignedTask, null, 2, PageStatus.TODO);

        ObjectNode body = json();
        body.put("assigneeId", translatorUser.getId().toString());

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", unassignedTask.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId", is(translatorUser.getId().toString())));

        assertThat(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(unassignedTask.getId()))
                .allMatch(page -> translatorUser.getId().equals(page.getAssignedTranslatorId()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-156: PUT /team-workspace/tasks/{taskId} - Switching assignee without the handover endpoint should return 409 Conflict")
    void updateTaskWithDifferentAssignee() throws Exception {
        ObjectNode body = json();
        body.put("assigneeId", secondTranslatorUser.getId().toString());

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Use the handover endpoint when changing an assignee so completed pages and coefficient K are preserved")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-157: PUT /team-workspace/tasks/{taskId} - Malformed assignee id should return 400 Bad Request")
    void updateTaskWithMalformedAssignee() throws Exception {
        ObjectNode body = json();
        body.put("assigneeId", "not-a-uuid");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid assignee ID format")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-158: PUT /team-workspace/tasks/{taskId} - Clearing the assignee should return 400 Bad Request")
    void updateTaskWithBlankAssignee() throws Exception {
        ObjectNode body = json();
        body.put("assigneeId", "");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("A Translator assignee is required")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-159: PUT /team-workspace/tasks/{taskId} - Unknown task should return 404 Not Found")
    void updateUnknownTask() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Anything\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Task not found")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-160: PUT /team-workspace/tasks/{taskId} - Translator cannot edit tasks and should return 403 Forbidden")
    void updateTaskAsTranslator() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked title\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only this team's Project Leader can edit tasks")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-161: PUT /team-workspace/tasks/{taskId} - Missing token should return 401 Unauthorized")
    void updateTaskWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /tasks/{taskId}/handover ─────────────────

    private ObjectNode handoverBody(UUID newAssigneeId, String factor, String reason, Integer... completedPages) {
        ObjectNode body = json();
        if (newAssigneeId != null) {
            body.put("newAssigneeId", newAssigneeId.toString());
        }
        if (factor != null) {
            body.put("responsibilityFactor", new BigDecimal(factor));
        }
        if (reason != null) {
            body.put("reason", reason);
        }
        var pages = body.putArray("completedPageNumbers");
        for (Integer page : completedPages) {
            pages.add(page);
        }
        return body;
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-162: PUT /team-workspace/tasks/{taskId}/handover - Leader hands the task over and returns 200 OK with the credit split")
    void handoverTask() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "0.50", "Translator went inactive", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handoverId", notNullValue()))
                .andExpect(jsonPath("$.taskId", is(task.getId().toString())))
                .andExpect(jsonPath("$.fromTranslatorId", is(translatorUser.getId().toString())))
                .andExpect(jsonPath("$.toTranslatorId", is(secondTranslatorUser.getId().toString())))
                .andExpect(jsonPath("$.acceptedPageCount", is(1)))
                .andExpect(jsonPath("$.reassignedPageCount", is(1)))
                .andExpect(jsonPath("$.reason", is("Translator went inactive")));

        TeamTaskEntity handed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(handed.getAssigneeId()).isEqualTo(secondTranslatorUser.getId());
        assertThat(handed.getStatus()).isEqualTo("in_progress");

        List<PageTranslationEntity> pages = pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId());
        assertThat(pages.get(0).getStatus()).isEqualTo(PageStatus.DONE);
        assertThat(pages.get(0).getAssignedTranslatorId()).isEqualTo(translatorUser.getId());
        assertThat(pages.get(0).getResponsibilityFactor()).isEqualByComparingTo(new BigDecimal("0.50"));
        assertThat(pages.get(1).getStatus()).isEqualTo(PageStatus.TODO);
        assertThat(pages.get(1).getAssignedTranslatorId()).isEqualTo(secondTranslatorUser.getId());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-163: PUT /team-workspace/tasks/{taskId}/handover - Handover without accepted pages reassigns everything and returns 200 OK")
    void handoverTaskWithoutAcceptedPages() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "1.00", "Restart the chapter"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedPageCount", is(0)))
                .andExpect(jsonPath("$.reassignedPageCount", is(2)))
                .andExpect(jsonPath("$.completedPageNumbers", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-164: PUT /team-workspace/tasks/{taskId}/handover - Handing over to the current assignee should return 400 Bad Request")
    void handoverTaskToSameAssignee() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(translatorUser.getId(), "1.00", "No real change"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("New assignee must be different from the current assignee")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-165: PUT /team-workspace/tasks/{taskId}/handover - New assignee outside the team should return 400 Bad Request")
    void handoverTaskToNonMember() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(applicantUser.getId(), "1.00", "Outsourcing the chapter"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The assignee must be an approved member of this project team")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-166: PUT /team-workspace/tasks/{taskId}/handover - Accepted page outside the task should return 400 Bad Request")
    void handoverTaskWithUnknownPage() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "1.00", "Partial credit", 99))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Page 99 does not exist in this task")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-167: PUT /team-workspace/tasks/{taskId}/handover - Missing reason should return 400 Bad Request from bean validation")
    void handoverTaskWithoutReason() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "1.00", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.errors.reason", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-168: PUT /team-workspace/tasks/{taskId}/handover - Responsibility factor above 1.00 should return 400 Bad Request")
    void handoverTaskWithInvalidFactor() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "1.50", "Too generous", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.errors.responsibilityFactor", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-169: PUT /team-workspace/tasks/{taskId}/handover - Completed task should return 409 Conflict")
    void handoverCompletedTask() throws Exception {
        TeamTaskEntity completedTask = persistTask(backlogChapter, translatorUser.getId(), "completed");

        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", completedTask.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "1.00", "Too late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("A completed task cannot be handed over")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-170: PUT /team-workspace/tasks/{taskId}/handover - Unknown task should return 404 Not Found")
    void handoverUnknownTask() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "1.00", "Missing task"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Task not found")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-171: PUT /team-workspace/tasks/{taskId}/handover - Translator cannot hand over a task and should return 403 Forbidden")
    void handoverTaskAsTranslator() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "1.00", "I am leaving"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only this team's Project Leader can hand over a task")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-172: PUT /team-workspace/tasks/{taskId}/handover - Missing token should return 401 Unauthorized")
    void handoverTaskWithoutToken() throws Exception {
        mockMvc.perform(put(BASE_URL + "/tasks/{taskId}/handover", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                handoverBody(secondTranslatorUser.getId(), "1.00", "No auth"))))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{teamId}/chapters ───────────────────────

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-173: GET /team-workspace/{teamId}/chapters - List published chapters of the team comic should return 200 OK")
    void getTeamChapters() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/chapters", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.chapterId=='" + taskedChapter.getId() + "')].pages", contains(3)))
                .andExpect(jsonPath("$[?(@.chapterId=='" + backlogChapter.getId() + "')].pages", contains(2)))
                .andExpect(jsonPath("$[?(@.chapterId=='" + draftChapter.getId() + "')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.chapterId=='" + taskedChapter.getId() + "')].canCreateTask", contains(false)))
                .andExpect(jsonPath("$[?(@.chapterId=='" + backlogChapter.getId() + "')].canCreateTask", contains(true)))
                .andExpect(jsonPath("$[0].comicId", is(comic.getId().toString())))
                .andExpect(jsonPath("$[0].comicName", is(COMIC_NAME)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-174: GET /team-workspace/{teamId}/chapters - Team without a comic name returns an empty list with 200 OK")
    void getTeamChaptersWithoutComicName() throws Exception {
        ProjectTeamEntity blankTeam = persistTeam("Workspace IT Chapterless Team", "", leaderUser);

        mockMvc.perform(get(BASE_URL + "/{teamId}/chapters", blankTeam.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-175: GET /team-workspace/{teamId}/chapters - Comic name without a matching comic returns an empty list with 200 OK")
    void getTeamChaptersWithoutMatchingComic() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/chapters", otherTeam.getId())
                        .header("Authorization", "Bearer " + otherLeaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-176: GET /team-workspace/{teamId}/chapters - Unknown team should return 404 Not Found")
    void getTeamChaptersForUnknownTeam() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/chapters", UUID.randomUUID())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Project team not found")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-177: GET /team-workspace/{teamId}/chapters - Missing token should return 401 Unauthorized")
    void getTeamChaptersWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{teamId}/chapters", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-178: GET /team-workspace/{teamId}/chapter-backlog - Accepted translation report returns the chapter as a revision while the live translation stays published")
    void getChapterBacklogIncludesUnpublishedTranslationForRevision() throws Exception {
        ChapterEntity revisionChapter = persistChapter("5", "Chapter Five", ChapterStatus.PUBLISHED, 2);
        TeamTaskEntity previousTask = persistTask(revisionChapter, translatorUser.getId(), "completed");
        previousTask.setCompletedAt(Instant.now());
        taskRepository.save(previousTask);
        persistTranslatedPages(previousTask, translatorUser.getId(),
                "[{\"id\":\"b1\",\"text\":\"Xin chao\"}]",
                "[{\"id\":\"b2\",\"text\":\"The gioi\"}]");
        ChapterTranslationEntity translation = persistTranslation(
                revisionChapter,
                ChapterTranslationStatus.PUBLISHED,
                "[{\"pageNumber\":1,\"bubbles\":[{\"id\":\"b1\",\"text\":\"Xin chao\"}]}]"
        );
        persistAcceptedTranslationReport(translation.getId(), "Fix honorifics on page 1");

        mockMvc.perform(get(BASE_URL + "/{teamId}/chapter-backlog", team.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.chapterId=='" + revisionChapter.getId() + "')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.chapterId=='" + revisionChapter.getId() + "')].revision", contains(true)))
                .andExpect(jsonPath("$[?(@.chapterId=='" + revisionChapter.getId() + "')].previousTaskId", contains(previousTask.getId().toString())))
                .andExpect(jsonPath("$[?(@.chapterId=='" + revisionChapter.getId() + "')].resolutionNote", contains("Fix honorifics on page 1")))
                .andExpect(jsonPath("$[?(@.chapterId=='" + backlogChapter.getId() + "')]", hasSize(1)));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-179: POST /team-workspace/{teamId}/tasks - Revision task after an accepted translation report copies previous bubbles")
    void createRevisionTaskCopiesPreviousTranslation() throws Exception {
        ChapterEntity revisionChapter = persistChapter("6", "Chapter Six", ChapterStatus.PUBLISHED, 2);
        TeamTaskEntity previousTask = persistTask(revisionChapter, translatorUser.getId(), "completed");
        previousTask.setCompletedAt(Instant.now());
        taskRepository.save(previousTask);
        String page1Bubbles = "[{\"id\":\"b1\",\"text\":\"Xin chao\"}]";
        String page2Bubbles = "[{\"id\":\"b2\",\"text\":\"The gioi\"}]";
        persistTranslatedPages(previousTask, translatorUser.getId(), page1Bubbles, page2Bubbles);
        ChapterTranslationEntity translation = persistTranslation(
                revisionChapter,
                ChapterTranslationStatus.PUBLISHED,
                "[{\"pageNumber\":1,\"bubbles\":[{\"id\":\"b1\",\"text\":\"Xin chao\"}]},{\"pageNumber\":2,\"bubbles\":[{\"id\":\"b2\",\"text\":\"The gioi\"}]}]"
        );
        persistAcceptedTranslationReport(translation.getId(), "Fix honorifics on page 1");

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(revisionChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType", is("REVISION")))
                .andExpect(jsonPath("$.rejectionReason", is("Fix honorifics on page 1")))
                .andExpect(jsonPath("$.status", is("backlog")));

        TeamTaskEntity created = taskRepository.findByChapter_Id(revisionChapter.getId()).stream()
                .filter(t -> "REVISION".equalsIgnoreCase(t.getTaskType()))
                .findFirst()
                .orElseThrow();
        List<PageTranslationEntity> copiedPages = pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(created.getId());
        assertThat(copiedPages).hasSize(2);
        assertThat(copiedPages.get(0).getBubbles()).isEqualTo(page1Bubbles);
        assertThat(copiedPages.get(1).getBubbles()).isEqualTo(page2Bubbles);
        assertThat(copiedPages.get(0).getStatus()).isEqualTo(PageStatus.TODO);
        assertThat(copiedPages.get(1).getStatus()).isEqualTo(PageStatus.TODO);
        assertThat(taskRepository.findById(previousTask.getId()).orElseThrow().getStatus()).isEqualTo("superseded");
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-182: POST /team-workspace/{teamId}/tasks - Accepted translation report still allows a revision task even if the previous task is active")
    void createRevisionTaskWhenPreviousTaskIsStillActive() throws Exception {
        ChapterEntity revisionChapter = persistChapter("8", "Chapter Eight", ChapterStatus.PUBLISHED, 2);
        TeamTaskEntity previousTask = persistTask(revisionChapter, translatorUser.getId(), "in_progress");
        persistTranslatedPages(previousTask, translatorUser.getId(),
                "[{\"id\":\"b1\",\"text\":\"Old line\"}]",
                "[{\"id\":\"b2\",\"text\":\"Old line 2\"}]");
        ChapterTranslationEntity translation = persistTranslation(
                revisionChapter,
                ChapterTranslationStatus.PUBLISHED,
                "[{\"pageNumber\":1,\"bubbles\":[{\"id\":\"b1\",\"text\":\"Old line\"}]}]"
        );
        persistAcceptedTranslationReport(translation.getId(), "Fix the reported lines");

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(revisionChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType", is("REVISION")));

        assertThat(taskRepository.findById(previousTask.getId()).orElseThrow().getStatus()).isEqualTo("superseded");
        TeamTaskEntity created = taskRepository.findByChapter_Id(revisionChapter.getId()).stream()
                .filter(t -> "REVISION".equalsIgnoreCase(t.getTaskType()))
                .findFirst()
                .orElseThrow();
        assertThat(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(created.getId()).get(0).getBubbles())
                .contains("Old line");
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-183: POST /team-workspace/{teamId}/tasks - Accepted translation report allows a revision task even if the translation is still marked published")
    void createRevisionTaskWhenAcceptedReportExistsButTranslationStillPublished() throws Exception {
        ChapterEntity revisionChapter = persistChapter("9", "Chapter Nine", ChapterStatus.PUBLISHED, 2);
        TeamTaskEntity previousTask = persistTask(revisionChapter, translatorUser.getId(), "completed");
        previousTask.setCompletedAt(Instant.now());
        taskRepository.save(previousTask);
        persistTranslatedPages(previousTask, translatorUser.getId(),
                "[{\"id\":\"b1\",\"text\":\"Published text\"}]",
                "[]");
        ChapterTranslationEntity translation = persistTranslation(
                revisionChapter,
                ChapterTranslationStatus.PUBLISHED,
                "[{\"pageNumber\":1,\"bubbles\":[{\"id\":\"b1\",\"text\":\"Published text\"}]}]"
        );
        persistAcceptedTranslationReport(translation.getId(), "Revise after the accepted report");

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(revisionChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType", is("REVISION")))
                .andExpect(jsonPath("$.rejectionReason", is("Revise after the accepted report")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-180: POST /team-workspace/{teamId}/tasks - Chapter with an active task should return 409 Conflict")
    void createTaskWhenChapterAlreadyHasActiveTask() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(taskedChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("This chapter already has a task in this project")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-184: POST /team-workspace/{teamId}/tasks - Completed task without an accepted report should return 409 Conflict")
    void createTaskWhenChapterAlreadyHasCompletedTask() throws Exception {
        ChapterEntity existingChapter = persistChapter("10", "Chapter Ten", ChapterStatus.PUBLISHED, 2);
        TeamTaskEntity previousTask = persistTask(existingChapter, translatorUser.getId(), "completed");
        previousTask.setCompletedAt(Instant.now());
        taskRepository.save(previousTask);

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(existingChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("This chapter already has a task in this project")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-185: POST /team-workspace/{teamId}/tasks - Unpublished translation without an accepted report should still block a duplicate task")
    void createTaskWhenTranslationUnpublishedWithoutAcceptedReport() throws Exception {
        ChapterEntity existingChapter = persistChapter("11", "Chapter Eleven", ChapterStatus.PUBLISHED, 2);
        TeamTaskEntity previousTask = persistTask(existingChapter, translatorUser.getId(), "completed");
        previousTask.setCompletedAt(Instant.now());
        taskRepository.save(previousTask);
        persistTranslation(
                existingChapter,
                ChapterTranslationStatus.UNPUBLISHED,
                "[{\"pageNumber\":1,\"bubbles\":[{\"id\":\"b1\",\"text\":\"Old\"}]}]"
        );

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(existingChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("This chapter already has a task in this project")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-186: POST /team-workspace/{teamId}/tasks - A second revision task while one is already open should return 409 Conflict")
    void createSecondRevisionTaskShouldBeBlocked() throws Exception {
        ChapterEntity revisionChapter = persistChapter("12", "Chapter Twelve", ChapterStatus.PUBLISHED, 2);
        TeamTaskEntity previousTask = persistTask(revisionChapter, translatorUser.getId(), "completed");
        previousTask.setCompletedAt(Instant.now().minusSeconds(60));
        taskRepository.save(previousTask);
        persistTranslatedPages(previousTask, translatorUser.getId(),
                "[{\"id\":\"b1\",\"text\":\"Old\"}]",
                "[]");
        ChapterTranslationEntity translation = persistTranslation(
                revisionChapter,
                ChapterTranslationStatus.PUBLISHED,
                "[{\"pageNumber\":1,\"bubbles\":[{\"id\":\"b1\",\"text\":\"Old\"}]}]"
        );
        persistAcceptedTranslationReport(translation.getId(), "Fix reported lines");

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(revisionChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(revisionChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("This chapter already has a task in this project")));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-181: POST /team-workspace/{teamId}/tasks - Chapter with a published translation should return 409 Conflict")
    void createTaskWhenTranslationIsStillPublished() throws Exception {
        ChapterEntity publishedChapter = persistChapter("7", "Chapter Seven", ChapterStatus.PUBLISHED, 2);
        persistTranslation(
                publishedChapter,
                ChapterTranslationStatus.PUBLISHED,
                "[{\"pageNumber\":1,\"bubbles\":[{\"id\":\"b1\",\"text\":\"Hello\"}]}]"
        );

        mockMvc.perform(post(BASE_URL + "/{teamId}/tasks", team.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskBody(publishedChapter.getId(), translatorUser.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("This chapter already has a published translation. A revision task can be created after a translation report is accepted")));
    }
}
