package com.sep.comiverse.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AdminControllerIT extends AbstractIntegrationTest {

    private static final String BASE_URL = "/admin/users";
    private static final String DEFAULT_RESET_PASSWORD = "abcd1234";

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

    private UserEntity adminUser;
    private UserEntity moderatorUser;
    private UserEntity readerUser;
    private UserEntity targetUser;
    private UserEntity bannedUser;
    private UserEntity deletedUser;

    private String adminToken;
    private String readerToken;

    @BeforeEach
    void setUp() {
        findOrCreateRole("PROJECT_LEADER");
        findOrCreateRole("TRANSLATOR");
        Instant now = Instant.now();

        adminUser = persistUser("admin_users_it", "Admin Users Person", "ADMIN",
                "ACTIVE", false, now.minus(5, ChronoUnit.HOURS));
        moderatorUser = persistUser("moderator_users_it", "Moderator Users Person", "MODERATOR",
                "ACTIVE", false, now.minus(4, ChronoUnit.HOURS));
        readerUser = persistUser("reader_users_it", "Reader Users Person", "READER",
                "ACTIVE", false, now.minus(3, ChronoUnit.HOURS));

        targetUser = persistUser("target_users_it", "Target Users Person", "READER",
                "ACTIVE", false, now.minus(2, ChronoUnit.HOURS));
        targetUser.setPhone("0900000001");
        targetUser.setAvatarUrl("http://example.com/avatar.png");
        targetUser.setBackgroundImageUrl("http://example.com/background.png");
        targetUser.setDateOfBirth(LocalDate.of(1995, 5, 20));
        targetUser.setAssignedLanguages("vi,en");
        targetUser = userRepository.save(targetUser);

        bannedUser = persistUser("banned_users_it", "Banned Users Person", "READER",
                "INACTIVE", false, now.minus(1, ChronoUnit.HOURS));
        deletedUser = persistUser("deleted_users_it", "Deleted Users Person", "READER",
                "ACTIVE", true, now);

        adminToken = jwtTokenUtil.generateToken(adminUser);
        readerToken = jwtTokenUtil.generateToken(readerUser);
    }

    private RoleEntity findOrCreateRole(String roleName) {
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName(roleName).build()));
    }

    private UserEntity persistUser(
            String username,
            String fullName,
            String roleName,
            String status,
            boolean deleted,
            Instant createdAt
    ) {
        UserEntity user = UserEntity.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .fullName(fullName)
                .status(status)
                .provider("LOCAL")
                .role(findOrCreateRole(roleName))
                .build();
        user.setDeleted(deleted);
        user.setCreatedAt(createdAt);
        return userRepository.save(user);
    }

    private String expectedDisplayId(UserEntity user) {
        return "USR-" + user.getId().toString().substring(0, 8).toUpperCase();
    }

    private String updateBody(String fullName, String role) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("fullName", fullName);
        node.put("role", role);
        return node.toString();
    }

    // ---------------------------------------------------------------------
    // GET /admin/users
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("TC-INT-AdminController-001: GET /admin/users - Without params should return the first page with default metadata")
    void getAllUsers_defaultPaging_shouldReturnFirstPage() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.metadata.page", is(1)))
                .andExpect(jsonPath("$.metadata.size", is(10)))
                .andExpect(jsonPath("$.metadata.totalElements", is(5)))
                .andExpect(jsonPath("$.metadata.totalPages", is(1)));
    }

    @Test
    @DisplayName("TC-INT-AdminController-002: GET /admin/users - Should expose the full admin user payload")
    void getAllUsers_shouldExposeFullPayload() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "target_users_it"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(targetUser.getId().toString())))
                .andExpect(jsonPath("$.data[0].userId", is(expectedDisplayId(targetUser))))
                .andExpect(jsonPath("$.data[0].username", is("target_users_it")))
                .andExpect(jsonPath("$.data[0].fullName", is("Target Users Person")))
                .andExpect(jsonPath("$.data[0].email", is("target_users_it@example.com")))
                .andExpect(jsonPath("$.data[0].phone", is("0900000001")))
                .andExpect(jsonPath("$.data[0].role", is("Reader")))
                .andExpect(jsonPath("$.data[0].status", is("Active")))
                .andExpect(jsonPath("$.data[0].provider", is("LOCAL")))
                .andExpect(jsonPath("$.data[0].avatarUrl", is("http://example.com/avatar.png")))
                .andExpect(jsonPath("$.data[0].backgroundImageUrl", is("http://example.com/background.png")))
                .andExpect(jsonPath("$.data[0].assignedLanguages", contains("vi", "en")))
                .andExpect(jsonPath("$.data[0].dateOfBirth", notNullValue()))
                .andExpect(jsonPath("$.data[0].createdDate", notNullValue()))
                .andExpect(jsonPath("$.data[0].updatedDate", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-AdminController-003: GET /admin/users - Soft-deleted users should be excluded")
    void getAllUsers_shouldExcludeSoftDeletedUsers() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(deletedUser.getId().toString()))))
                .andExpect(jsonPath("$.metadata.totalElements", is(5)));
    }

    @Test
    @DisplayName("TC-INT-AdminController-004: GET /admin/users - An inactive account should be reported as Banned")
    void getAllUsers_inactiveUser_shouldBeReportedAsBanned() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "banned_users_it"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status", is("Banned")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-005: GET /admin/users - Role filter should only return users with that role")
    void getAllUsers_roleFilter_shouldFilterByRole() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "READER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].role", everyItem(is("Reader"))))
                .andExpect(jsonPath("$.metadata.totalElements", is(3)));
    }

    @Test
    @DisplayName("TC-INT-AdminController-006: GET /admin/users - Role filter should be case-insensitive")
    void getAllUsers_roleFilter_shouldBeCaseInsensitive() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "Moderator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(moderatorUser.getId().toString())))
                .andExpect(jsonPath("$.data[0].role", is("Moderator")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-007: GET /admin/users - Status filter should only return matching accounts")
    void getAllUsers_statusFilter_shouldFilterByStatus() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(bannedUser.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-AdminController-008: GET /admin/users - Status filter should be case-insensitive")
    void getAllUsers_statusFilter_shouldBeCaseInsensitive() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[*].status", everyItem(is("Active"))));
    }

    @Test
    @DisplayName("TC-INT-AdminController-009: GET /admin/users - Search should match the email regardless of case")
    void getAllUsers_search_shouldMatchEmail() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "TARGET_USERS_IT@EXAMPLE.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(targetUser.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-AdminController-010: GET /admin/users - Search should match the full name")
    void getAllUsers_search_shouldMatchFullName() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "Moderator Users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(moderatorUser.getId().toString())));
    }

    @Test
    @DisplayName("TC-INT-AdminController-011: GET /admin/users - Search combined with a role filter should apply both")
    void getAllUsers_searchWithRoleFilter_shouldApplyBoth() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "users_it")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(adminUser.getId().toString())))
                .andExpect(jsonPath("$.data[0].role", is("Admin")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-012: GET /admin/users - A search without matches should return an empty page")
    void getAllUsers_searchWithoutMatches_shouldReturnEmptyPage() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "no_such_account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()))
                .andExpect(jsonPath("$.metadata.totalElements", is(0)))
                .andExpect(jsonPath("$.metadata.totalPages", is(0)));
    }

    @Test
    @DisplayName("TC-INT-AdminController-013: GET /admin/users - Should return the newest account first")
    void getAllUsers_shouldReturnNewestAccountFirst() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains(
                        "banned_users_it",
                        "target_users_it",
                        "reader_users_it",
                        "moderator_users_it",
                        "admin_users_it")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-014: GET /admin/users - Pagination should return the requested one-based page")
    void getAllUsers_pagination_shouldReturnRequestedPage() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].username", contains("reader_users_it", "moderator_users_it")))
                .andExpect(jsonPath("$.metadata.page", is(2)))
                .andExpect(jsonPath("$.metadata.size", is(2)))
                .andExpect(jsonPath("$.metadata.totalElements", is(5)))
                .andExpect(jsonPath("$.metadata.totalPages", is(3)));
    }

    @Test
    @DisplayName("TC-INT-AdminController-015: GET /admin/users - The last page should contain the remaining account")
    void getAllUsers_lastPage_shouldContainRemainingAccount() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "3")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].username", is("admin_users_it")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-016: GET /admin/users - Page index below 1 should return 400 Bad Request")
    void getAllUsers_pageBelowOne_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errors.page", is("Page index must be greater than or equal to 1")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-017: GET /admin/users - Page size above 100 should return 400 Bad Request")
    void getAllUsers_sizeAboveMaximum_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size", is("Page size must not exceed 100")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-018: GET /admin/users - Without token should return 401 Unauthorized")
    void getAllUsers_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AdminController-019: GET /admin/users - As READER should return 403 Forbidden")
    void getAllUsers_asReader_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // GET /admin/users/{id}
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("TC-INT-AdminController-020: GET /admin/users/{id} - Should return the requested account details")
    void getUserById_shouldReturnDetails() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(targetUser.getId().toString())))
                .andExpect(jsonPath("$.data.userId", is(expectedDisplayId(targetUser))))
                .andExpect(jsonPath("$.data.email", is("target_users_it@example.com")))
                .andExpect(jsonPath("$.data.fullName", is("Target Users Person")))
                .andExpect(jsonPath("$.data.role", is("Reader")))
                .andExpect(jsonPath("$.data.status", is("Active")))
                .andExpect(jsonPath("$.data.assignedLanguages", contains("vi", "en")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-021: GET /admin/users/{id} - A banned account should report the Banned status")
    void getUserById_bannedUser_shouldReportBannedStatus() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", bannedUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("Banned")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-022: GET /admin/users/{id} - An unknown account should return 404 Not Found")
    void getUserById_unknownUser_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-023: GET /admin/users/{id} - A soft-deleted account should return 404 Not Found")
    void getUserById_softDeletedUser_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", deletedUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-024: GET /admin/users/{id} - A malformed id should return 400 Bad Request")
    void getUserById_malformedId_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", "not-a-uuid")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid id format")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-025: GET /admin/users/{id} - Without token should return 401 Unauthorized")
    void getUserById_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", targetUser.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AdminController-026: GET /admin/users/{id} - As READER should return 403 Forbidden")
    void getUserById_asReader_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // PUT /admin/users/{id}/ban
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("TC-INT-AdminController-027: PUT /admin/users/{id}/ban - An active account should be banned")
    void banUser_activeUser_shouldBeBanned() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/ban", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("User has been banned successfully.")))
                .andExpect(jsonPath("$.data.id", is(targetUser.getId().toString())))
                .andExpect(jsonPath("$.data.status", is("Banned")));

        assertThat(userRepository.findById(targetUser.getId()).orElseThrow().getStatus())
                .isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("TC-INT-AdminController-028: PUT /admin/users/{id}/ban - An already banned account should return 400 Bad Request")
    void banUser_alreadyBanned_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/ban", bannedUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("User is already banned")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-029: PUT /admin/users/{id}/ban - An admin account cannot be banned")
    void banUser_adminAccount_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/ban", adminUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Cannot ban an Admin account")));

        assertThat(userRepository.findById(adminUser.getId()).orElseThrow().getStatus())
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("TC-INT-AdminController-030: PUT /admin/users/{id}/ban - An unknown account should return 404 Not Found")
    void banUser_unknownUser_shouldReturnNotFound() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/ban", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-031: PUT /admin/users/{id}/ban - Without token should return 401 Unauthorized")
    void banUser_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/ban", targetUser.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AdminController-032: PUT /admin/users/{id}/ban - As READER should return 403 Forbidden")
    void banUser_asReader_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/ban", targetUser.getId())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // PUT /admin/users/{id}/unban
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("TC-INT-AdminController-033: PUT /admin/users/{id}/unban - A banned account should be restored")
    void unbanUser_bannedUser_shouldBeRestored() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/unban", bannedUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("User has been unbanned successfully.")))
                .andExpect(jsonPath("$.data.id", is(bannedUser.getId().toString())))
                .andExpect(jsonPath("$.data.status", is("Active")));

        assertThat(userRepository.findById(bannedUser.getId()).orElseThrow().getStatus())
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("TC-INT-AdminController-034: PUT /admin/users/{id}/unban - An already active account should return 400 Bad Request")
    void unbanUser_alreadyActive_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/unban", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("User is already active")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-035: PUT /admin/users/{id}/unban - An unknown account should return 404 Not Found")
    void unbanUser_unknownUser_shouldReturnNotFound() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/unban", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-036: PUT /admin/users/{id}/unban - Without token should return 401 Unauthorized")
    void unbanUser_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/unban", bannedUser.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AdminController-037: PUT /admin/users/{id}/unban - As READER should return 403 Forbidden")
    void unbanUser_asReader_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}/unban", bannedUser.getId())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // POST /admin/users/{id}/reset-password
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("TC-INT-AdminController-038: POST /admin/users/{id}/reset-password - Should reset the password to the default value")
    void resetPassword_shouldResetToDefaultPassword() throws Exception {
        targetUser.setResetToken("stale-reset-token");
        targetUser.setResetTokenExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(targetUser);

        mockMvc.perform(post(BASE_URL + "/{id}/reset-password", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("User password has been reset to the default password.")))
                .andExpect(jsonPath("$.data", nullValue()));

        UserEntity stored = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(DEFAULT_RESET_PASSWORD, stored.getPassword())).isTrue();
        assertThat(stored.getResetToken()).isNull();
        assertThat(stored.getResetTokenExpiresAt()).isNull();
    }

    @Test
    @DisplayName("TC-INT-AdminController-039: POST /admin/users/{id}/reset-password - An unknown account should return 404 Not Found")
    void resetPassword_unknownUser_shouldReturnNotFound() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{id}/reset-password", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-040: POST /admin/users/{id}/reset-password - A malformed id should return 400 Bad Request")
    void resetPassword_malformedId_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{id}/reset-password", "not-a-uuid")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid id format")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-041: POST /admin/users/{id}/reset-password - Without token should return 401 Unauthorized")
    void resetPassword_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{id}/reset-password", targetUser.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AdminController-042: POST /admin/users/{id}/reset-password - As READER should return 403 Forbidden")
    void resetPassword_asReader_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{id}/reset-password", targetUser.getId())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // PUT /admin/users/{id}
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("TC-INT-AdminController-043: PUT /admin/users/{id} - Should update the full name and role")
    void updateUser_shouldUpdateNameAndRole() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("  Promoted Moderator  ", "MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("User details updated successfully.")))
                .andExpect(jsonPath("$.data.fullName", is("Promoted Moderator")))
                .andExpect(jsonPath("$.data.role", is("Moderator")));

        UserEntity stored = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(stored.getFullName()).isEqualTo("Promoted Moderator");
        assertThat(stored.getRole().getRoleName()).isEqualTo("MODERATOR");
    }

    @Test
    @DisplayName("TC-INT-AdminController-044: PUT /admin/users/{id} - A lowercase role should be normalized and formatted")
    void updateUser_lowercaseRole_shouldBeNormalized() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Target Users Person", "project_leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("Project Leader")));

        assertThat(userRepository.findById(targetUser.getId()).orElseThrow().getRole().getRoleName())
                .isEqualTo("PROJECT_LEADER");
    }

    @Test
    @DisplayName("TC-INT-AdminController-045: PUT /admin/users/{id} - Assigned languages should be stored as a comma separated list")
    void updateUser_assignedLanguages_shouldBeStoredAsCsv() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("fullName", "Translator Users Person");
        body.put("role", "TRANSLATOR");
        body.putArray("assignedLanguages").add("ja").add("ko");

        mockMvc.perform(put(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedLanguages", contains("ja", "ko")));

        assertThat(userRepository.findById(targetUser.getId()).orElseThrow().getAssignedLanguages())
                .isEqualTo("ja,ko");
    }

    @Test
    @DisplayName("TC-INT-AdminController-046: PUT /admin/users/{id} - An unknown role should return 400 Bad Request")
    void updateUser_unknownRole_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Target Users Person", "ghost_role")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Role not found: GHOST_ROLE")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-047: PUT /admin/users/{id} - A blank full name should return 400 Bad Request")
    void updateUser_blankFullName_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("   ", "READER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName", is("Full name cannot be blank")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-048: PUT /admin/users/{id} - An empty payload should report both required fields")
    void updateUser_emptyPayload_shouldReportRequiredFields() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.errors.fullName", is("Full name cannot be blank")))
                .andExpect(jsonPath("$.errors.role", is("Role cannot be blank")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-049: PUT /admin/users/{id} - An unknown account should return 404 Not Found")
    void updateUser_unknownUser_shouldReturnNotFound() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Ghost User", "READER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-050: PUT /admin/users/{id} - A malformed id should return 400 Bad Request")
    void updateUser_malformedId_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", "not-a-uuid")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Target Users Person", "READER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid id format")));
    }

    @Test
    @DisplayName("TC-INT-AdminController-051: PUT /admin/users/{id} - Without token should return 401 Unauthorized")
    void updateUser_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", targetUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Target Users Person", "READER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AdminController-052: PUT /admin/users/{id} - As READER should return 403 Forbidden")
    void updateUser_asReader_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", targetUser.getId())
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Target Users Person", "READER")))
                .andExpect(status().isForbidden());
    }
}
