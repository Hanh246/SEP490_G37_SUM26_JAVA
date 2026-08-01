package com.sep.comiverse.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.ComiverseApplication;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.integration.support.ComiverseIntegrationTest;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep.comiverse.integration.support.AbstractIntegrationTest;

public class ComicApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private ComicEntity publishedComic;
    private UserEntity adminUser;
    private UserEntity readerUser;

    @BeforeEach
    void setUp() {
        RoleEntity adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("ADMIN").build()));

        RoleEntity readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        adminUser = userRepository.findByEmail("admin_comic@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("admin_comic")
                        .email("admin_comic@example.com")
                        .password("Password123!")
                        .fullName("Admin Comic")
                        .status("ACTIVE")
                        .role(adminRole)
                        .build()));

        readerUser = userRepository.findByEmail("reader_comic@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("reader_comic")
                        .email("reader_comic@example.com")
                        .password("Password123!")
                        .fullName("Reader Comic")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));

        publishedComic = comicRepository.save(ComicEntity.builder()
                .title("Sample Test Comic")
                .summary("A great sample comic for integration tests")
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build());
    }

    @Test
    @DisplayName("TC-INT-ComicController-001: GET /comics should return paginated published comics list (200 OK)")
    void tc_int_comicController_001_findPublishedComics() throws Exception {
        mockMvc.perform(get("/comics")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-ComicController-002: GET /comics/leaderboard should return 200 OK")
    void tc_int_comicController_002_getLeaderboard() throws Exception {
        mockMvc.perform(get("/comics/leaderboard")
                        .param("timeframe", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-ComicController-003: GET /comics/{id} should return public detail for published comic (UI Testing)")
    void tc_int_comicController_003_getComicDetailSuccess() throws Exception {
        mockMvc.perform(get("/comics/" + publishedComic.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.title", is("Sample Test Comic")));
    }

    @Test
    @DisplayName("TC-INT-ComicController-004: GET /comics/{id} for non-existent comic ID should return 404 or 500 error")
    void tc_int_comicController_004_getComicDetailNotFound() throws Exception {
        mockMvc.perform(get("/comics/" + UUID.randomUUID()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-ComicController-005: POST /comics as READER should fail with 403 Forbidden")
    void tc_int_comicController_005_createComicAsReaderForbidden() throws Exception {
        String token = jwtTokenUtil.generateToken(readerUser);

        mockMvc.perform(post("/comics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Unauthorized Comic\"}"))
                .andExpect(status().isForbidden());
    }
}
