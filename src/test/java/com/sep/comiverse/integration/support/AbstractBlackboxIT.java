package com.sep.comiverse.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.ComiverseApplication;
import com.sep.comiverse.entity.AuthorEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.AuthorLicenseStatus;
import com.sep.comiverse.entity.enums.AuthorType;
import com.sep.comiverse.repository.IAuthorRepository;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.util.EmailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ComiverseApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestPropertySource("classpath:integration/application-integration.properties")
@Transactional
public abstract class AbstractBlackboxIT {

    protected static final String PASSWORD = "Password123!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected IUserRepository userRepository;

    @Autowired
    protected IRoleRepository roleRepository;

    @Autowired
    protected IAuthorRepository authorRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @MockBean
    protected EmailUtil emailUtil;

    @MockBean
    protected RedisTemplate<String, Object> redisTemplate;

    @MockBean
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpBlackboxInfrastructure() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        SetOperations<String, Object> setOps = mock(SetOperations.class);
        ZSetOperations<String, Object> zsetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.keys(any())).thenReturn(Collections.emptySet());
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.executePipelined(any(org.springframework.data.redis.core.SessionCallback.class)))
                .thenReturn(List.of());

        ValueOperations<String, String> strValueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(strValueOps);

        for (String role : List.of("ADMIN", "MODERATOR", "AUTHOR", "READER", "TRANSLATOR", "PROJECT_LEADER")) {
            roleOf(role);
        }
    }

    protected RoleEntity roleOf(String roleName) {
        return roleRepository.findByRoleName(roleName).orElseGet(() -> {
            RoleEntity role = RoleEntity.builder().roleName(roleName).build();
            return roleRepository.save(role);
        });
    }

    protected SeededUser seedUser(String roleName) {
        return seedUser(roleName, "ACTIVE");
    }

    protected SeededUser seedUser(String roleName, String status) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "u" + token;
        String email = username + "@example.com";
        UserEntity user = UserEntity.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName("Black Box User")
                .email(email)
                .role(roleOf(roleName))
                .status(status)
                .provider("LOCAL")
                .build();
        user = userRepository.save(user);
        if ("AUTHOR".equals(roleName)) {
            authorRepository.save(AuthorEntity.builder()
                    .user(user)
                    .authorType(AuthorType.INDIVIDUAL)
                    .displayName(user.getFullName())
                    .contactEmail(user.getEmail())
                    .licenseStatus(AuthorLicenseStatus.ACTIVE)
                    .build());
        }
        return new SeededUser(user.getId(), username, email, roleName);
    }

    protected String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return readTree(result).path("token").asText();
    }

    protected String token(String roleName) throws Exception {
        return login(seedUser(roleName).username());
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, String bearerToken) throws Exception {
        if (bearerToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions getJson(String url) throws Exception {
        return perform(get(url).accept(MediaType.APPLICATION_JSON));
    }

    protected ResultActions getJson(String url, String token) throws Exception {
        return perform(get(url).accept(MediaType.APPLICATION_JSON), token);
    }

    protected ResultActions postJson(String url, String body) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions postJson(String url, String body, String token) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    protected ResultActions putJson(String url, String body, String token) throws Exception {
        return perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : body), token);
    }

    protected ResultActions deleteJson(String url, String token) throws Exception {
        return perform(delete(url).accept(MediaType.APPLICATION_JSON), token);
    }

    protected String json(String raw) {
        return raw;
    }

    protected JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected UUID readDataId(MvcResult result) throws Exception {
        return UUID.fromString(readTree(result).path("data").path("id").asText());
    }

    protected UUID createComicAsAdmin(String adminToken, UUID authorId, String title) throws Exception {
        MvcResult result = postJson("/comics", """
                {
                  "title": "%s",
                  "language": "en",
                  "cover": "https://cdn.example.com/cover.png",
                  "summary": "Blackbox comic",
                  "authorId": "%s",
                  "chapterCount": 0,
                  "viewCount": 0,
                  "likeCount": 0,
                  "saveCount": 0,
                  "ratingAverage": 0,
                  "ratingCount": 0,
                  "moderationStatus": "DRAFT",
                  "publicationStatus": "ONGOING",
                  "isAppealed": false,
                  "isModEdited": false
                }
                """.formatted(title, authorId), adminToken)
                .andExpect(status().isCreated())
                .andReturn();
        return readDataId(result);
    }

    protected UUID createChapterAsAdmin(String adminToken, UUID comicId, String number) throws Exception {
        MvcResult result = postJson("/chapters", """
                {
                  "comicId": "%s",
                  "chapterNumber": "%s",
                  "title": "Chapter %s",
                  "images": ["https://cdn.example.com/p1.png"],
                  "moderationStatus": "PREVIEW_READY",
                  "viewCount": 0,
                  "isPremium": false,
                  "pageCount": 1
                }
                """.formatted(comicId, number, number), adminToken)
                .andExpect(status().isCreated())
                .andReturn();
        return readDataId(result);
    }

    protected UUID createAuthorComic(String authorToken, String title) throws Exception {
        MvcResult result = postJson("/author/comics", """
                {
                  "title": "%s",
                  "language": "en",
                  "cover": "https://cdn.example.com/cover.png",
                  "summary": "Author draft"
                }
                """.formatted(title), authorToken)
                .andExpect(status().isCreated())
                .andReturn();
        return readDataId(result);
    }

    protected UUID createTeam(String token, String title, UUID leaderId) throws Exception {
        MvcResult result = postJson("/project-teams", """
                {
                  "title": "%s",
                  "comicName": "%s",
                  "status": "ongoing",
                  "leaderId": "%s",
                  "leaderName": "Team Leader",
                  "isRecruiting": true,
                  "maxMembers": 5
                }
                """.formatted(title, title, leaderId), token)
                .andExpect(status().isCreated())
                .andReturn();
        return readDataId(result);
    }

    protected UUID createSubmission(String token, UUID comicId, UUID chapterId, UUID authorId) throws Exception {
        MvcResult result = postJson("/submissions", """
                {
                  "title": "Blackbox submission",
                  "status": "pending",
                  "queueType": "author",
                  "comicId": "%s",
                  "chapterId": "%s",
                  "authorId": "%s",
                  "submittedBy": "author"
                }
                """.formatted(comicId, chapterId, authorId), token)
                .andExpect(status().isCreated())
                .andReturn();
        return readDataId(result);
    }

    public record SeededUser(UUID id, String username, String email, String role) {
    }
}
