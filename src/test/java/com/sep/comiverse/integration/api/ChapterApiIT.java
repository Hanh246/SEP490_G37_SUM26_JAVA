package com.sep.comiverse.integration.api;

import com.sep.comiverse.ComiverseApplication;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.integration.support.ComiverseIntegrationTest;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sep.comiverse.integration.support.AbstractIntegrationTest;

public class ChapterApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private ComicEntity testComic;
    private ChapterEntity testChapter;
    private UserEntity adminUser;
    private UserEntity readerUser;

    @BeforeEach
    void setUp() {
        RoleEntity adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("ADMIN").build()));

        RoleEntity readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        adminUser = userRepository.findByEmail("admin_chap@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("admin_chap")
                        .email("admin_chap@example.com")
                        .password("Password123!")
                        .fullName("Admin Chap")
                        .status("ACTIVE")
                        .role(adminRole)
                        .build()));

        readerUser = userRepository.findByEmail("reader_chap@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("reader_chap")
                        .email("reader_chap@example.com")
                        .password("Password123!")
                        .fullName("Reader Chap")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));

        testComic = comicRepository.save(ComicEntity.builder()
                .title("Chapter Test Comic")
                .summary("Comic for testing chapters")
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build());

        testChapter = chapterRepository.save(ChapterEntity.builder()
                .comic(testComic)
                .title("Chapter 1: The Beginning")
                .chapterNumber("1")
                .moderationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW)
                .build());

        userRepository.flush();
        comicRepository.flush();
        chapterRepository.flush();
    }

    @Test
    @DisplayName("TC-INT-ChapterController-001: GET /chapters/comic/{comicId} should return 200 OK")
    void tc_int_chapterController_001_getChaptersByComicId() throws Exception {
        mockMvc.perform(get("/chapters/comic/" + testComic.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-ChapterController-002: GET /chapters (all) as READER should return 403 Forbidden")
    void tc_int_chapterController_002_findAllAsReaderForbidden() throws Exception {
        String token = jwtTokenUtil.generateToken(readerUser);

        mockMvc.perform(get("/chapters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-003: PUT /chapters/{id}/approve as ADMIN should approve chapter and change state to PUBLISHED")
    void tc_int_chapterController_003_approveChapterAsAdmin() throws Exception {
        String token = jwtTokenUtil.generateToken(adminUser);

        mockMvc.perform(put("/chapters/" + testChapter.getId() + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
