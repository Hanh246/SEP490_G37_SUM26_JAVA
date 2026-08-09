package com.sep.comiverse.system.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.integration.support.AbstractIntegrationTest;
import com.sep.comiverse.repository.*;
import com.sep.comiverse.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("L3 System Test — BF-04: Comic & Chapter Moderation Workflow")
public class ModeratorComicWorkflowST extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private ISubmissionRepository submissionRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private INotificationRepository notificationRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private UserEntity authorUser;
    private UserEntity moderatorUserVi;
    private UserEntity readerUser;
    private RoleEntity authorRole;
    private RoleEntity moderatorRole;
    private RoleEntity readerRole;

    @BeforeEach
    void setUp() {
        authorRole = roleRepository.findByRoleName("AUTHOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("AUTHOR").build()));

        moderatorRole = roleRepository.findByRoleName("MODERATOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("MODERATOR").build()));

        readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        authorUser = userRepository.findByEmail("st_author@comiverse.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("st_author")
                        .email("st_author@comiverse.com")
                        .password("Password123!")
                        .fullName("ST Author")
                        .status("ACTIVE")
                        .role(authorRole)
                        .build()));

        moderatorUserVi = userRepository.findByEmail("st_mod_vi@comiverse.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("st_mod_vi")
                        .email("st_mod_vi@comiverse.com")
                        .password("Password123!")
                        .fullName("ST Mod VI")
                        .status("ACTIVE")
                        .assignedLanguages("vi")
                        .role(moderatorRole)
                        .build()));

        readerUser = userRepository.findByEmail("st_reader@comiverse.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("st_reader")
                        .email("st_reader@comiverse.com")
                        .password("Password123!")
                        .fullName("ST Reader")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));

        userRepository.flush();
    }

    @Test
    @DisplayName("TC-SYS-BF04-001: End-to-End Chapter Submission -> Review Queue -> Approve -> Publish & Reading Access")
    void testComicChapterModerationApprovalWorkflow() throws Exception {
        // Step 1: Create Comic & Chapter in PENDING_REVIEW state (Simulating Author Submission)
        ComicEntity comic = comicRepository.save(ComicEntity.builder()
                .title("Solo Leveling Reborn")
                .summary("Epic fantasy journey")
                .language("vi")
                .authorId(authorUser.getId())
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .chapterCount(0)
                .build());

        ChapterEntity chapter = chapterRepository.save(ChapterEntity.builder()
                .comic(comic)
                .title("Chapter 1: The Awakening")
                .chapterNumber("1.0")
                .images(List.of("https://cdn.comiverse.com/page1.jpg", "https://cdn.comiverse.com/page2.jpg"))
                .moderationStatus(ChapterStatus.PENDING_REVIEW)
                .build());

        SubmissionEntity submission = submissionRepository.save(SubmissionEntity.builder()
                .comicId(comic.getId())
                .chapterId(chapter.getId())
                .title(comic.getTitle())
                .chapter("Chapter 1.0")
                .queueType("author")
                .status("pending")
                .submittedBy(authorUser.getFullName())
                .authorId(authorUser.getId())
                .build());

        comicRepository.flush();
        chapterRepository.flush();
        submissionRepository.flush();

        // Step 2: Moderator authenticates and fetches Review Queue
        String modToken = jwtTokenUtil.generateToken(moderatorUserVi);

        mockMvc.perform(get("/submissions/all")
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));

        // Step 3: Moderator approves the submission
        mockMvc.perform(put("/submissions/" + submission.getId() + "/approve")
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("approved")));

        // Step 4: Verify Database State Changes (Chapter & Comic PUBLISHED)
        ChapterEntity updatedChapter = chapterRepository.findById(chapter.getId()).orElseThrow();
        assertEquals(ChapterStatus.PUBLISHED, updatedChapter.getModerationStatus(), "Chapter must be PUBLISHED");
        assertEquals(moderatorUserVi.getId(), updatedChapter.getApprovedById(), "ApprovedBy must match Moderator ID");

        ComicEntity updatedComic = comicRepository.findById(comic.getId()).orElseThrow();
        assertEquals(ComicModerationStatus.PUBLISHED, updatedComic.getModerationStatus(), "Comic must be PUBLISHED");
        assertEquals("1.0", updatedComic.getLatestChapterNumber(), "Latest chapter number must be updated");
        assertTrue(updatedComic.getChapterCount() >= 1, "Chapter count must be incremented");

        // Step 5: Verify Public Discovery by Reader
        mockMvc.perform(get("/comics/" + comic.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("Solo Leveling Reborn")))
                .andExpect(jsonPath("$.data.moderationStatus", is("PUBLISHED")));
    }

    @Test
    @DisplayName("TC-SYS-BF04-002: End-to-End Chapter Submission -> Review Queue -> Reject with Reason & Tombstone Cleanup")
    void testComicChapterModerationRejectWorkflow() throws Exception {
        // Step 1: Prepare Submission for Low Quality Chapter
        ComicEntity comic = comicRepository.save(ComicEntity.builder()
                .title("Low Quality Comic")
                .language("vi")
                .authorId(authorUser.getId())
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .build());

        ChapterEntity chapter = chapterRepository.save(ChapterEntity.builder()
                .comic(comic)
                .title("Chapter 2: Bad Artifacts")
                .chapterNumber("2.0")
                .images(List.of("https://cdn.comiverse.com/bad_page.jpg"))
                .moderationStatus(ChapterStatus.PENDING_REVIEW)
                .build());

        SubmissionEntity submission = submissionRepository.save(SubmissionEntity.builder()
                .comicId(comic.getId())
                .chapterId(chapter.getId())
                .title(comic.getTitle())
                .chapter("Chapter 2.0")
                .queueType("author")
                .status("pending")
                .submittedBy(authorUser.getFullName())
                .authorId(authorUser.getId())
                .build());

        comicRepository.flush();
        chapterRepository.flush();
        submissionRepository.flush();

        // Step 2: Moderator Rejects Submission with Reason
        String modToken = jwtTokenUtil.generateToken(moderatorUserVi);
        Map<String, String> rejectBody = Map.of("reason", "Low image quality and unreadable text artifact");

        mockMvc.perform(put("/submissions/" + submission.getId() + "/reject")
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("rejected")));

        // Step 3: Verify Chapter is REJECTED and images array is Tombstoned
        ChapterEntity rejectedChapter = chapterRepository.findById(chapter.getId()).orElseThrow();
        assertEquals(ChapterStatus.REJECTED, rejectedChapter.getModerationStatus());
        assertEquals("Low image quality and unreadable text artifact", rejectedChapter.getRejectionReason());
        assertTrue(rejectedChapter.getImages() == null || rejectedChapter.getImages().isEmpty(), "Images must be cleared to prevent storage bloat");

        // Step 4: Verify Reader cannot access unapproved chapter (Security Boundary NFR-10)
        String readerToken = jwtTokenUtil.generateToken(readerUser);
        mockMvc.perform(get("/chapters/" + chapter.getId())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-SEC-SPOT-001: Security Spot-Check — Reader attempting approval direct call must return 403 Forbidden")
    void testReaderCannotApproveSubmission() throws Exception {
        SubmissionEntity submission = submissionRepository.save(SubmissionEntity.builder()
                .title("Unauthorized Test")
                .status("pending")
                .queueType("author")
                .build());

        String readerToken = jwtTokenUtil.generateToken(readerUser);

        mockMvc.perform(put("/submissions/" + submission.getId() + "/approve")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-SPOT-005: Security Spot-Check — Moderator Language Scope Isolation")
    void testModeratorLanguageScopeIsolation() throws Exception {
        // Create Japanese comic and submission
        ComicEntity jaComic = comicRepository.save(ComicEntity.builder()
                .title("Japanese Manga")
                .language("ja")
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .build());

        SubmissionEntity jaSubmission = submissionRepository.save(SubmissionEntity.builder()
                .comicId(jaComic.getId())
                .title("Japanese Manga")
                .status("pending")
                .queueType("author")
                .build());

        // Create Vietnamese comic and submission
        ComicEntity viComic = comicRepository.save(ComicEntity.builder()
                .title("Vietnamese Comic")
                .language("vi")
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .build());

        SubmissionEntity viSubmission = submissionRepository.save(SubmissionEntity.builder()
                .comicId(viComic.getId())
                .title("Vietnamese Comic")
                .status("pending")
                .queueType("author")
                .build());

        comicRepository.flush();
        submissionRepository.flush();

        // Moderator with scope="vi" fetches submissions
        String modToken = jwtTokenUtil.generateToken(moderatorUserVi);

        mockMvc.perform(get("/submissions/all")
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("Vietnamese Comic")))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Japanese Manga"))));
    }
}
