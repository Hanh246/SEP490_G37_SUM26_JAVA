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

public class SubscriptionControllerIT extends AbstractIntegrationTest {

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

        readerUser = userRepository.findByEmail("reader_sub@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("reader_sub")
                        .email("reader_sub@example.com")
                        .password("Password123!")
                        .fullName("Reader Subscription User")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));

        readerToken = jwtTokenUtil.generateToken(readerUser);
    }

    @Test
    @DisplayName("TC-INT-SubscriptionController-001: GET /subscriptions/me - Get my subscription details as READER should return 200 OK")
    void getMySubscription() throws Exception {
        mockMvc.perform(get("/subscriptions/me")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
