package com.sep.comiverse.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ChapterTranslationEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ReportCategoryEntity;
import com.sep.comiverse.entity.ReportEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ChapterTranslationStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportTargetType;
import com.sep.comiverse.integration.support.AbstractIntegrationTest;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IReportCategoryRepository;
import com.sep.comiverse.repository.IReportRepository;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ReportControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private IChapterTranslationRepository chapterTranslationRepository;

    @Autowired
    private IReportCategoryRepository reportCategoryRepository;

    @Autowired
    private IReportRepository reportRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private String readerToken;
    private ReportCategoryEntity translationCategory;
    private ChapterEntity chapter;
    private ChapterTranslationEntity translation;

    @BeforeEach
    void setUp() {
        RoleEntity readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        UserEntity readerUser = userRepository.findByEmail("reader_report@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("reader_report")
                        .email("reader_report@example.com")
                        .password("Password123!")
                        .fullName("Reader Report User")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));

        readerToken = jwtTokenUtil.generateToken(readerUser);

        translationCategory = ReportCategoryEntity.builder()
                .name("Translation quality")
                .description("Created by ReportControllerIT")
                .assignedRole(ReportAssignedRole.PROJECT_LEADER)
                .targetTypes(List.of(ReportTargetType.CHAPTER_TRANSLATIONS))
                .isActive(true)
                .build();
        translationCategory.setDeleted(false);
        translationCategory = reportCategoryRepository.save(translationCategory);

        ComicEntity comic = ComicEntity.builder()
                .title("Reader Report Comic")
                .summary("Used by report submit tests")
                .language("en")
                .cover("http://example.com/report_cover.jpg")
                .authorId(UUID.randomUUID())
                .publicationStatus(ComicPublicationStatus.ONGOING)
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build();
        comic.setDeleted(false);
        comic = comicRepository.save(comic);

        chapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber("1")
                .title("Chapter One")
                .moderationStatus(ChapterStatus.PUBLISHED)
                .images(List.of("http://example.com/report/page1.jpg"))
                .build();
        chapter.setDeleted(false);
        chapter = chapterRepository.save(chapter);

        translation = ChapterTranslationEntity.builder()
                .chapter(chapter)
                .languageCode("vi")
                .pagesBubbles("[{\"pageNumber\":1,\"bubbles\":[]}]")
                .status(ChapterTranslationStatus.PUBLISHED)
                .build();
        translation.setDeleted(false);
        translation = chapterTranslationRepository.save(translation);
    }

    @Test
    @DisplayName("TC-INT-ReportController-001: GET /reports/my-reports - Get my submitted reports should return 200 OK")
    void getMyReports() throws Exception {
        mockMvc.perform(get("/reports/my-reports")
                        .header("Authorization", "Bearer " + readerToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("TC-INT-ReportController-002: POST /reports - Reporting a translation by translation id should return 201 Created")
    void createTranslationReportByTranslationId() throws Exception {
        mockMvc.perform(post("/reports")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "target_type", "CHAPTER_TRANSLATIONS",
                                "target_id", translation.getId(),
                                "category_id", translationCategory.getId(),
                                "description_text", "Honorifics are wrong on page 1."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.target_id", is(translation.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-ReportController-003: POST /reports - Reporting a translation by chapter id and language should store the translation id")
    void createTranslationReportByChapterIdAndLanguage() throws Exception {
        mockMvc.perform(post("/reports")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "target_type", "CHAPTER_TRANSLATIONS",
                                "target_id", chapter.getId(),
                                "category_id", translationCategory.getId(),
                                "language_code", "Vietnamese",
                                "description_text", "The translated bubbles are unreadable."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.target_id", is(translation.getId().toString())));

        List<ReportEntity> saved = reportRepository.findAll();
        assertThat(saved).anyMatch(report -> translation.getId().equals(report.getTargetId()));
    }

    @Test
    @DisplayName("TC-INT-ReportController-003b: POST /reports - Locale language tags like vi-VN should resolve the translation")
    void createTranslationReportByChapterIdAndLocaleTag() throws Exception {
        mockMvc.perform(post("/reports")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "target_type", "CHAPTER_TRANSLATIONS",
                                "target_id", chapter.getId(),
                                "category_id", translationCategory.getId(),
                                "language_code", "vi-VN",
                                "description_text", "Honorifics are inconsistent on page 2."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.target_id", is(translation.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-ReportController-004: POST /reports - Unknown translation target should return 404 Not Found")
    void createTranslationReportUnknownTarget() throws Exception {
        mockMvc.perform(post("/reports")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "target_type", "CHAPTER_TRANSLATIONS",
                                "target_id", UUID.randomUUID(),
                                "category_id", translationCategory.getId(),
                                "description_text", "This translation does not exist."
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("translation not found")));
    }
}
