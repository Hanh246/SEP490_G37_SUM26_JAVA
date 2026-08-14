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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SubmissionControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private UserEntity modUser;
    private String modToken;

    @BeforeEach
    void setUp() {
        RoleEntity modRole = roleRepository.findByRoleName("MODERATOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("MODERATOR").build()));

        modUser = userRepository.findByEmail("mod_submission@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("mod_submission")
                        .email("mod_submission@example.com")
                        .password("Password123!")
                        .fullName("Mod Submission User")
                        .status("ACTIVE")
                        .role(modRole)
                        .build()));

        modToken = jwtTokenUtil.generateToken(modUser);
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-001: GET /submissions - List all submissions with pagination as MODERATOR should return 200 OK")
    void findAll() throws Exception {
        mockMvc.perform(get("/submissions")
                        .header("Authorization", "Bearer " + modToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
