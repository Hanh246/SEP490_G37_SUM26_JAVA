package com.sep.comiverse.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.ProjectTeamMemberEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.entity.enums.PageStatus;
import com.sep.comiverse.integration.support.AbstractIntegrationTest;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
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

public class PageControllerIT extends AbstractIntegrationTest {

    private static final String TASK_URL = "/translate-workspace/{taskId}";
    private static final String BUBBLES_URL = "/translate-workspace/pages/{pageId}/bubbles";
    private static final String STATUS_URL = "/translate-workspace/pages/{pageId}/status";
    private static final String SAMPLE_BUBBLES = "[{\"id\":1,\"text\":\"Xin chào\"}]";

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
    private ITeamTaskRepository taskRepository;

    @Autowired
    private IPageTranslationRepository pageTranslationRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private UserEntity leaderUser;
    private UserEntity otherLeaderUser;
    private UserEntity translatorUser;
    private UserEntity otherTranslatorUser;
    private UserEntity readerUser;

    private String leaderToken;
    private String otherLeaderToken;
    private String translatorToken;
    private String otherTranslatorToken;
    private String readerToken;

    private ProjectTeamEntity team;
    private ChapterEntity chapter;
    private TeamTaskEntity task;
    private PageTranslationEntity firstPage;
    private PageTranslationEntity secondPage;
    private PageTranslationEntity unassignedPage;

    @BeforeEach
    void setUp() {
        leaderUser = findOrCreateUser("page_leader", "Page Leader", "PROJECT_LEADER");
        otherLeaderUser = findOrCreateUser("page_other_leader", "Page Other Leader", "PROJECT_LEADER");
        translatorUser = findOrCreateUser("page_translator", "Page Translator", "TRANSLATOR");
        otherTranslatorUser = findOrCreateUser("page_translator_two", "Page Translator Two", "TRANSLATOR");
        readerUser = findOrCreateUser("page_reader", "Page Reader", "READER");

        leaderToken = jwtTokenUtil.generateToken(leaderUser);
        otherLeaderToken = jwtTokenUtil.generateToken(otherLeaderUser);
        translatorToken = jwtTokenUtil.generateToken(translatorUser);
        otherTranslatorToken = jwtTokenUtil.generateToken(otherTranslatorUser);
        readerToken = jwtTokenUtil.generateToken(readerUser);

        ComicEntity comicToSave = ComicEntity.builder()
                .title("Page IT Comic")
                .summary("Comic used by translate workspace page tests")
                .language("vi")
                .cover("http://example.com/page_cover.jpg")
                .authorId(UUID.randomUUID())
                .publicationStatus(ComicPublicationStatus.ONGOING)
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build();
        comicToSave.setDeleted(false);
        ComicEntity comic = comicRepository.save(comicToSave);

        team = persistTeam("Page IT Team", leaderUser);
        addMember(team, translatorUser);
        addMember(team, otherTranslatorUser);
        team.setMembersCount(team.getMembers().size());
        team = projectTeamRepository.save(team);

        ChapterEntity chapterToSave = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber("1")
                .title("Chapter One")
                .moderationStatus(ChapterStatus.PUBLISHED)
                .images(new ArrayList<>(List.of(
                        "http://example.com/page1.jpg",
                        "http://example.com/page2.jpg",
                        "http://example.com/page3.jpg")))
                .build();
        chapterToSave.setDeleted(false);
        chapter = chapterRepository.save(chapterToSave);

        task = persistTask("in_progress");
        firstPage = persistPage(task, 1, translatorUser.getId(), PageStatus.TODO);
        secondPage = persistPage(task, 2, translatorUser.getId(), PageStatus.TODO);
        unassignedPage = persistPage(task, 3, null, PageStatus.TODO);
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

    private ProjectTeamEntity persistTeam(String title, UserEntity leader) {
        ProjectTeamEntity entity = ProjectTeamEntity.builder()
                .title(title)
                .comicName("Page IT Comic")
                .status("Active")
                .membersCount(0)
                .leaderId(leader.getId())
                .leaderName(leader.getUsername())
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

    private TeamTaskEntity persistTask(String status) {
        return taskRepository.save(TeamTaskEntity.builder()
                .projectTeamId(team.getId())
                .chapter(chapter)
                .title("Translate chapter one")
                .status(status)
                .assigneeId(translatorUser.getId())
                .taskType("REGULAR")
                .dueDate("2026-12-31")
                .chapterRewardUsd(new BigDecimal("12.00"))
                .build());
    }

    private PageTranslationEntity persistPage(TeamTaskEntity owner, int pageNumber, UUID translatorId, PageStatus status) {
        PageTranslationEntity page = PageTranslationEntity.builder()
                .taskId(owner)
                .pageNumber(pageNumber)
                .imageUrl("http://example.com/page" + pageNumber + ".jpg")
                .assignedTranslatorId(translatorId)
                .responsibilityFactor(BigDecimal.ONE.setScale(2))
                .status(status)
                .bubbles("[]")
                .build();
        page.setDeleted(false);
        page.setCompletedAt(status == PageStatus.DONE ? Instant.now() : null);
        return pageTranslationRepository.save(page);
    }

    private PageTranslationEntity pageOfTaskWithStatus(String taskStatus) {
        TeamTaskEntity lockedTask = persistTask(taskStatus);
        return persistPage(lockedTask, 1, translatorUser.getId(), PageStatus.TODO);
    }

    private String bubblesBody(String bubbles) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (bubbles == null) {
            body.putNull("bubbles");
        } else {
            body.put("bubbles", bubbles);
        }
        return objectMapper.writeValueAsString(body);
    }

    private String statusBody(String status) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (status == null) {
            body.putNull("status");
        } else {
            body.put("status", status);
        }
        return objectMapper.writeValueAsString(body);
    }

    // ── GET /translate-workspace/{taskId} ────────────

    @Test
    @DisplayName("TC-INT-PageController-001: GET /translate-workspace/{taskId} - List task pages ordered by page number should return 200 OK")
    void getPagesForTask() throws Exception {
        mockMvc.perform(get(TASK_URL, task.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].pageId", is(firstPage.getId().toString())))
                .andExpect(jsonPath("$[0].pageNumber", is(1)))
                .andExpect(jsonPath("$[0].imageUrl", is("http://example.com/page1.jpg")))
                .andExpect(jsonPath("$[0].status", is("TODO")))
                .andExpect(jsonPath("$[0].bubbles", is("[]")))
                .andExpect(jsonPath("$[0].assignedTranslatorId", is(translatorUser.getId().toString())))
                .andExpect(jsonPath("$[0].responsibilityFactor", is(1.00)))
                .andExpect(jsonPath("$[0].completedAt", nullValue()))
                .andExpect(jsonPath("$[1].pageNumber", is(2)))
                .andExpect(jsonPath("$[2].pageNumber", is(3)))
                .andExpect(jsonPath("$[2].assignedTranslatorId", nullValue()));
    }

    @Test
    @DisplayName("TC-INT-PageController-002: GET /translate-workspace/{taskId} - Completed page exposes its status and completion time with 200 OK")
    void getPagesForTaskWithCompletedPage() throws Exception {
        TeamTaskEntity doneTask = persistTask("in_progress");
        persistPage(doneTask, 1, translatorUser.getId(), PageStatus.DONE);

        mockMvc.perform(get(TASK_URL, doneTask.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("DONE")))
                .andExpect(jsonPath("$[0].completedAt", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-PageController-003: GET /translate-workspace/{taskId} - Task without pages returns an empty list with 200 OK")
    void getPagesForTaskWithoutPages() throws Exception {
        TeamTaskEntity emptyTask = persistTask("todo");

        mockMvc.perform(get(TASK_URL, emptyTask.getId())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-PageController-004: GET /translate-workspace/{taskId} - Unknown task returns an empty list with 200 OK")
    void getPagesForUnknownTask() throws Exception {
        mockMvc.perform(get(TASK_URL, UUID.randomUUID())
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TC-INT-PageController-005: GET /translate-workspace/{taskId} - Project Leader can read the pages and gets 200 OK")
    void getPagesForTaskAsLeader() throws Exception {
        mockMvc.perform(get(TASK_URL, task.getId())
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("TC-INT-PageController-006: GET /translate-workspace/{taskId} - Endpoint is open to any authenticated role and returns 200 OK")
    void getPagesForTaskAsReader() throws Exception {
        mockMvc.perform(get(TASK_URL, task.getId())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("TC-INT-PageController-007: GET /translate-workspace/{taskId} - Malformed task id should return 400 Bad Request")
    void getPagesForTaskWithMalformedId() throws Exception {
        mockMvc.perform(get(TASK_URL, "not-a-uuid")
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid taskId format")));
    }

    @Test
    @DisplayName("TC-INT-PageController-008: GET /translate-workspace/{taskId} - Missing token should return 401 Unauthorized")
    void getPagesForTaskWithoutToken() throws Exception {
        mockMvc.perform(get(TASK_URL, task.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /translate-workspace/pages/{pageId}/bubbles ──

    @Test
    @DisplayName("TC-INT-PageController-009: PUT /translate-workspace/pages/{pageId}/bubbles - Assigned translator saves bubbles should return 200 OK")
    void saveBubblesAsAssignedTranslator() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageId", is(firstPage.getId().toString())))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.bubbles", is(SAMPLE_BUBBLES)))
                .andExpect(jsonPath("$.status", is("TODO")));

        assertThat(pageTranslationRepository.findById(firstPage.getId()).orElseThrow().getBubbles())
                .isEqualTo(SAMPLE_BUBBLES);
    }

    @Test
    @DisplayName("TC-INT-PageController-010: PUT /translate-workspace/pages/{pageId}/bubbles - Team Project Leader saves bubbles should return 200 OK")
    void saveBubblesAsTeamLeader() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bubbles", is(SAMPLE_BUBBLES)));
    }

    @Test
    @DisplayName("TC-INT-PageController-011: PUT /translate-workspace/pages/{pageId}/bubbles - Leader can edit a page nobody is assigned to and gets 200 OK")
    void saveBubblesOnUnassignedPageAsLeader() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, unassignedPage.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTranslatorId", nullValue()))
                .andExpect(jsonPath("$.bubbles", is(SAMPLE_BUBBLES)));
    }

    @Test
    @DisplayName("TC-INT-PageController-012: PUT /translate-workspace/pages/{pageId}/bubbles - Null bubbles are stored as an empty array and return 200 OK")
    void saveNullBubbles() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bubbles", is("[]")));

        assertThat(pageTranslationRepository.findById(firstPage.getId()).orElseThrow().getBubbles())
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("TC-INT-PageController-013: PUT /translate-workspace/pages/{pageId}/bubbles - Saving twice overwrites the previous bubbles and returns 200 OK")
    void saveBubblesTwice() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, secondPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isOk());

        String updatedBubbles = "[{\"id\":1,\"text\":\"Tạm biệt\"}]";
        mockMvc.perform(put(BUBBLES_URL, secondPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(updatedBubbles)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bubbles", is(updatedBubbles)));
    }

    @Test
    @DisplayName("TC-INT-PageController-014: PUT /translate-workspace/pages/{pageId}/bubbles - Unknown page should return 404 Not Found")
    void saveBubblesForUnknownPage() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, UUID.randomUUID())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-PageController-015: PUT /translate-workspace/pages/{pageId}/bubbles - Translator who is not assigned should return 403 Forbidden")
    void saveBubblesAsOtherTranslator() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + otherTranslatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("You are not assigned to this page")));
    }

    @Test
    @DisplayName("TC-INT-PageController-016: PUT /translate-workspace/pages/{pageId}/bubbles - Reader role should return 403 Forbidden")
    void saveBubblesAsReader() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You are not assigned to this page")));
    }

    @Test
    @DisplayName("TC-INT-PageController-017: PUT /translate-workspace/pages/{pageId}/bubbles - Leader of another team should return 403 Forbidden")
    void saveBubblesAsForeignLeader() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + otherLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You are not assigned to this page")));
    }

    @Test
    @DisplayName("TC-INT-PageController-018: PUT /translate-workspace/pages/{pageId}/bubbles - Unassigned translator on an unassigned page should return 403 Forbidden")
    void saveBubblesOnUnassignedPageAsTranslator() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, unassignedPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You are not assigned to this page")));
    }

    @Test
    @DisplayName("TC-INT-PageController-019: PUT /translate-workspace/pages/{pageId}/bubbles - Task under review locks editing and returns 409 Conflict")
    void saveBubblesWhileUnderReview() throws Exception {
        PageTranslationEntity lockedPage = pageOfTaskWithStatus("under_review");

        mockMvc.perform(put(BUBBLES_URL, lockedPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Pages cannot be edited while the task is under review or completed")));
    }

    @Test
    @DisplayName("TC-INT-PageController-020: PUT /translate-workspace/pages/{pageId}/bubbles - Completed task locks editing and returns 409 Conflict")
    void saveBubblesWhenTaskCompleted() throws Exception {
        PageTranslationEntity lockedPage = pageOfTaskWithStatus("completed");

        mockMvc.perform(put(BUBBLES_URL, lockedPage.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Pages cannot be edited while the task is under review or completed")));
    }

    @Test
    @DisplayName("TC-INT-PageController-021: PUT /translate-workspace/pages/{pageId}/bubbles - Published task locks editing and returns 409 Conflict")
    void saveBubblesWhenTaskPublished() throws Exception {
        PageTranslationEntity lockedPage = pageOfTaskWithStatus("published");

        mockMvc.perform(put(BUBBLES_URL, lockedPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-INT-PageController-022: PUT /translate-workspace/pages/{pageId}/bubbles - Task status written with a hyphen is still recognised as locked and returns 409 Conflict")
    void saveBubblesWhenTaskStatusUsesHyphen() throws Exception {
        PageTranslationEntity lockedPage = pageOfTaskWithStatus("Under-Review");

        mockMvc.perform(put(BUBBLES_URL, lockedPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-INT-PageController-023: PUT /translate-workspace/pages/{pageId}/bubbles - Page without a task is editable by its translator and returns 200 OK")
    void saveBubblesOnPageWithoutTask() throws Exception {
        PageTranslationEntity orphanPage = persistPage(null, 1, translatorUser.getId(), PageStatus.TODO);

        mockMvc.perform(put(BUBBLES_URL, orphanPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bubbles", is(SAMPLE_BUBBLES)));
    }

    @Test
    @DisplayName("TC-INT-PageController-024: PUT /translate-workspace/pages/{pageId}/bubbles - Malformed page id should return 400 Bad Request")
    void saveBubblesWithMalformedPageId() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, "not-a-uuid")
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid pageId format")));
    }

    @Test
    @DisplayName("TC-INT-PageController-025: PUT /translate-workspace/pages/{pageId}/bubbles - Missing token should return 401 Unauthorized")
    void saveBubblesWithoutToken() throws Exception {
        mockMvc.perform(put(BUBBLES_URL, firstPage.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bubblesBody(SAMPLE_BUBBLES)))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /translate-workspace/pages/{pageId}/status ──

    @Test
    @DisplayName("TC-INT-PageController-026: PUT /translate-workspace/pages/{pageId}/status - Assigned translator marks a page DONE should return 200 OK")
    void markPageDone() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageId", is(firstPage.getId().toString())))
                .andExpect(jsonPath("$.status", is("DONE")))
                .andExpect(jsonPath("$.completedAt", notNullValue()));

        PageTranslationEntity updated = pageTranslationRepository.findById(firstPage.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PageStatus.DONE);
        assertThat(updated.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-INT-PageController-027: PUT /translate-workspace/pages/{pageId}/status - Reopening a page clears the completion time and returns 200 OK")
    void reopenPage() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isOk());

        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("TODO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("TODO")))
                .andExpect(jsonPath("$.completedAt", nullValue()));

        assertThat(pageTranslationRepository.findById(firstPage.getId()).orElseThrow().getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("TC-INT-PageController-028: PUT /translate-workspace/pages/{pageId}/status - Status value is trimmed and case insensitive and returns 200 OK")
    void updatePageStatusIsCaseInsensitive() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("  done  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DONE")));
    }

    @Test
    @DisplayName("TC-INT-PageController-029: PUT /translate-workspace/pages/{pageId}/status - Team Project Leader can change the status and gets 200 OK")
    void updatePageStatusAsLeader() throws Exception {
        mockMvc.perform(put(STATUS_URL, unassignedPage.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DONE")));
    }

    @Test
    @DisplayName("TC-INT-PageController-030: PUT /translate-workspace/pages/{pageId}/status - Unsupported status value should return 400 Bad Request")
    void updatePageStatusWithInvalidValue() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("IN_PROGRESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Status must be TODO or DONE")));

        assertThat(pageTranslationRepository.findById(firstPage.getId()).orElseThrow().getStatus())
                .isEqualTo(PageStatus.TODO);
    }

    @Test
    @DisplayName("TC-INT-PageController-031: PUT /translate-workspace/pages/{pageId}/status - Null status should return 400 Bad Request")
    void updatePageStatusWithNullValue() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Status must be TODO or DONE")));
    }

    @Test
    @DisplayName("TC-INT-PageController-032: PUT /translate-workspace/pages/{pageId}/status - Missing status field should return 400 Bad Request")
    void updatePageStatusWithoutStatusField() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Status must be TODO or DONE")));
    }

    @Test
    @DisplayName("TC-INT-PageController-033: PUT /translate-workspace/pages/{pageId}/status - Unknown page should return 404 Not Found")
    void updateStatusForUnknownPage() throws Exception {
        mockMvc.perform(put(STATUS_URL, UUID.randomUUID())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-PageController-034: PUT /translate-workspace/pages/{pageId}/status - Translator who is not assigned should return 403 Forbidden")
    void updatePageStatusAsOtherTranslator() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + otherTranslatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You are not assigned to this page")));
    }

    @Test
    @DisplayName("TC-INT-PageController-035: PUT /translate-workspace/pages/{pageId}/status - Leader of another team should return 403 Forbidden")
    void updatePageStatusAsForeignLeader() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + otherLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-PageController-036: PUT /translate-workspace/pages/{pageId}/status - Reader role should return 403 Forbidden")
    void updatePageStatusAsReader() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-PageController-037: PUT /translate-workspace/pages/{pageId}/status - Task under review locks the status and returns 409 Conflict")
    void updatePageStatusWhileUnderReview() throws Exception {
        PageTranslationEntity lockedPage = pageOfTaskWithStatus("under_review");

        mockMvc.perform(put(STATUS_URL, lockedPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("TODO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Page status cannot be changed while the task is under review or completed")));
    }

    @Test
    @DisplayName("TC-INT-PageController-038: PUT /translate-workspace/pages/{pageId}/status - Completed task locks the status and returns 409 Conflict")
    void updatePageStatusWhenTaskCompleted() throws Exception {
        PageTranslationEntity lockedPage = pageOfTaskWithStatus("completed");

        mockMvc.perform(put(STATUS_URL, lockedPage.getId())
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("TODO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Page status cannot be changed while the task is under review or completed")));
    }

    @Test
    @DisplayName("TC-INT-PageController-039: PUT /translate-workspace/pages/{pageId}/status - Published task locks the status and returns 409 Conflict")
    void updatePageStatusWhenTaskPublished() throws Exception {
        PageTranslationEntity lockedPage = pageOfTaskWithStatus("published");

        mockMvc.perform(put(STATUS_URL, lockedPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-INT-PageController-040: PUT /translate-workspace/pages/{pageId}/status - Locked task is checked before the status value and returns 409 Conflict")
    void updatePageStatusLockTakesPrecedenceOverValidation() throws Exception {
        PageTranslationEntity lockedPage = pageOfTaskWithStatus("under_review");

        mockMvc.perform(put(STATUS_URL, lockedPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("WHATEVER")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-INT-PageController-041: PUT /translate-workspace/pages/{pageId}/status - Page without a task can be updated by its translator and returns 200 OK")
    void updatePageStatusOnPageWithoutTask() throws Exception {
        PageTranslationEntity orphanPage = persistPage(null, 1, translatorUser.getId(), PageStatus.TODO);

        mockMvc.perform(put(STATUS_URL, orphanPage.getId())
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DONE")));
    }

    @Test
    @DisplayName("TC-INT-PageController-042: PUT /translate-workspace/pages/{pageId}/status - Malformed page id should return 400 Bad Request")
    void updatePageStatusWithMalformedPageId() throws Exception {
        mockMvc.perform(put(STATUS_URL, "not-a-uuid")
                        .header("Authorization", "Bearer " + translatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid pageId format")));
    }

    @Test
    @DisplayName("TC-INT-PageController-043: PUT /translate-workspace/pages/{pageId}/status - Missing token should return 401 Unauthorized")
    void updatePageStatusWithoutToken() throws Exception {
        mockMvc.perform(put(STATUS_URL, firstPage.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DONE")))
                .andExpect(status().isUnauthorized());
    }
}
