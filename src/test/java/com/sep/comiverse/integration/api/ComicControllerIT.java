package com.sep.comiverse.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.AuthorEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.integration.support.AbstractIntegrationTest;
import com.sep.comiverse.repository.IAuthorRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ComicControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IAuthorRepository authorRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private ComicEntity publishedComic;
    private UserEntity readerUser;

    @BeforeEach
    void setUp() {
        RoleEntity readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        readerUser = userRepository.findByEmail("reader_comic_ctrl@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("reader_comic_ctrl")
                        .email("reader_comic_ctrl@example.com")
                        .password("Password123!")
                        .fullName("Reader Comic Control")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));

        AuthorEntity author = authorRepository.findByUserIdAndDeletedFalse(readerUser.getId())
                .orElseGet(() -> authorRepository.save(AuthorEntity.builder()
                        .user(readerUser)
                        .displayName("Public Pen Name")
                        .build()));
        author.setDisplayName("Public Pen Name");
        authorRepository.save(author);

        publishedComic = comicRepository.save(ComicEntity.builder()
                .title("Sample Test Comic Control")
                .summary("Sample comic summary control")
                .authorId(readerUser.getId())
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build());
    }

    @Test
    @DisplayName("TC-INT-ComicController-001: GET /comics - List published comics with pagination should return 200 OK")
    void findPublishedComics() throws Exception {
        mockMvc.perform(get("/comics")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-ComicController-002: GET /comics/leaderboard - Retrieve top ranked comics by timeframe should return 200 OK")
    void getLeaderboard() throws Exception {
        mockMvc.perform(get("/comics/leaderboard")
                        .param("timeframe", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-ComicController-003: GET /comics/{id} - Retrieve public detail for published comic should return 200 OK")
    void getComicDetailSuccess() throws Exception {
        mockMvc.perform(get("/comics/" + publishedComic.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.title", is("Sample Test Comic Control")))
                .andExpect(jsonPath("$.data.authorName", is("Public Pen Name")));
    }

    @Test
    @DisplayName("TC-INT-ComicController-004: GET /comics/{id} - Non-existent comic ID should return 4xx Client Error")
    void getComicDetailNotFound() throws Exception {
        mockMvc.perform(get("/comics/" + UUID.randomUUID()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-ComicController-005: POST /comics - Unprivileged role (READER) attempting mutation should return 403 Forbidden")
    void createComicAsReaderForbidden() throws Exception {
        String token = jwtTokenUtil.generateToken(readerUser);

        mockMvc.perform(post("/comics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Unauthorized Comic Control\"}"))
                .andExpect(status().isForbidden());
    }
}
