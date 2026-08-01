package com.sep.comiverse.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.ComiverseApplication;
import com.sep.comiverse.dto.request.AuthRequest;
import com.sep.comiverse.dto.request.RegisterRequest;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.integration.support.ComiverseIntegrationTest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep.comiverse.integration.support.AbstractIntegrationTest;

public class AuthApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private UserEntity testUser;
    private RoleEntity readerRole;

    @BeforeEach
    void setUp() {
        readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        testUser = userRepository.findByEmail("testuser@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("testuser")
                        .email("testuser@example.com")
                        .password(passwordEncoder.encode("Password123!"))
                        .fullName("Test User")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));
    }

    @Test
    @DisplayName("TC-INT-AuthController-001: Login with valid credentials should return 200 OK and JWT tokens")
    void tc_int_authController_001_loginSuccess() throws Exception {
        AuthRequest loginRequest = new AuthRequest();
        loginRequest.setUsername("testuser@example.com");
        loginRequest.setPassword("Password123!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-AuthController-002: Login with invalid password should fail with 400 or 401")
    void tc_int_authController_002_loginInvalidPassword() throws Exception {
        AuthRequest loginRequest = new AuthRequest();
        loginRequest.setUsername("testuser@example.com");
        loginRequest.setPassword("WrongPassword!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AuthController-003: Login with missing fields should trigger Input Validation 400 Bad Request")
    void tc_int_authController_003_loginValidationFail() throws Exception {
        AuthRequest emptyRequest = new AuthRequest();
        emptyRequest.setUsername("");
        emptyRequest.setPassword("");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-004: Register with valid request should return 200 OK")
    void tc_int_authController_004_registerSuccess() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("newuser99");
        registerRequest.setEmail("newuser99@example.com");
        registerRequest.setPassword("SecurePass123!");
        registerRequest.setFullName("New User");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Account created")));
    }

    @Test
    @DisplayName("TC-INT-AuthController-005: Register with existing email should return Error response")
    void tc_int_authController_005_registerDuplicateEmail() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("anotheruser");
        registerRequest.setEmail("testuser@example.com"); // Already exists
        registerRequest.setPassword("SecurePass123!");
        registerRequest.setFullName("Duplicate User");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AuthController-006: GET /auth/me with valid Bearer token should return 200 OK and User Profile (UI Testing)")
    void tc_int_authController_006_getProfileSuccess() throws Exception {
        String token = jwtTokenUtil.generateToken(testUser);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is("testuser@example.com")))
                .andExpect(jsonPath("$.data.username", is("testuser")));
    }

    @Test
    @DisplayName("TC-INT-AuthController-007: GET /auth/me without token should return 401 Unauthorized")
    void tc_int_authController_007_getProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
