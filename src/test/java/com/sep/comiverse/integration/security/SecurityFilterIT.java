package com.sep.comiverse.integration.security;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SecurityFilterIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private UserEntity readerUser;

    @BeforeEach
    void setUp() {
        RoleEntity readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        readerUser = userRepository.findByEmail("reader_security@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("reader_security")
                        .email("reader_security@example.com")
                        .password("Password123!")
                        .fullName("Reader Security")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));
    }

    @Test
    @DisplayName("TC-SEC-001: Unauthenticated request to protected admin endpoint should return 401 Unauthorized")
    void tc_sec_001_unauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/admin/settings/premium-plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-002: READER role trying to access ADMIN endpoint should return 403 Forbidden (GBR-02)")
    void tc_sec_002_readerAccessAdminEndpointForbidden() throws Exception {
        String token = jwtTokenUtil.generateToken(readerUser);

        mockMvc.perform(get("/admin/settings/premium-plans")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-003: Request with malformed JWT token should return 401 Unauthorized")
    void tc_sec_003_malformedJwtToken() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer malformed.invalid.token"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("TC-SEC-004: Generic upload endpoint requires authentication")
    void tc_sec_004_unauthenticatedUploadIsRejected() throws Exception {
        mockMvc.perform(multipart("/upload/image")
                        .file("file", "image-bytes".getBytes()))
                .andExpect(status().isUnauthorized());
    }

}
