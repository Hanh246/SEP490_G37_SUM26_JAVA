package com.sep.comiverse.integration.api;

import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.integration.support.AbstractIntegrationTest;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ReviewControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private UserEntity readerUser;
    private String readerToken;

    @BeforeEach
    void setUp() {
        RoleEntity readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        readerUser = userRepository.findByEmail("reader_review_ctrl@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("reader_review_ctrl")
                        .email("reader_review_ctrl@example.com")
                        .password("Password123!")
                        .fullName("Reader Review Control")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));

        readerToken = jwtTokenUtil.generateToken(readerUser);
    }

    @Test
    @DisplayName("TC-INT-ReviewController-001: GET /review-workspace/{taskId} - Get pages for review workspace as authenticated user should return 200 OK")
    void getPagesForReview() throws Exception {
        mockMvc.perform(get("/review-workspace/{taskId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk());
    }
}
