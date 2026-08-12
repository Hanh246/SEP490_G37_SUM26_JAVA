package com.sep.comiverse.system.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.TranslationRequestDTO;
import com.sep.comiverse.dto.request.TranslatorRegistrationRequest;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.integration.support.AbstractIntegrationTest;
import com.sep.comiverse.repository.*;
import com.sep.comiverse.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("L3 System Test — BF-02: Translator Onboarding & Translation Team Management")
public class ModeratorTeamWorkflowST extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IProjectTeamRepository projectTeamRepository;

    @Autowired
    private ITranslatorRepository translatorRepository;

    @Autowired
    private INotificationRepository notificationRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    private UserEntity readerUser;
    private UserEntity moderatorUser;
    private UserEntity translatorUser;
    private RoleEntity readerRole;
    private RoleEntity moderatorRole;
    private RoleEntity translatorRole;

    @BeforeEach
    void setUp() {
        readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        moderatorRole = roleRepository.findByRoleName("MODERATOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("MODERATOR").build()));

        translatorRole = roleRepository.findByRoleName("TRANSLATOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("TRANSLATOR").build()));

        readerUser = userRepository.findByEmail("st_team_reader@comiverse.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("st_team_reader")
                        .email("st_team_reader@comiverse.com")
                        .password("Password123!")
                        .fullName("ST Team Reader")
                        .status("ACTIVE")
                        .role(readerRole)
                        .build()));

        moderatorUser = userRepository.findByEmail("st_team_mod@comiverse.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("st_team_mod")
                        .email("st_team_mod@comiverse.com")
                        .password("Password123!")
                        .fullName("ST Team Mod")
                        .status("ACTIVE")
                        .role(moderatorRole)
                        .build()));

        translatorUser = userRepository.findByEmail("st_pro_translator@comiverse.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .username("st_pro_translator")
                        .email("st_pro_translator@comiverse.com")
                        .password("Password123!")
                        .fullName("ST Pro Translator")
                        .status("ACTIVE")
                        .role(translatorRole)
                        .build()));

        userRepository.flush();
    }

    @Test
    @DisplayName("TC-SYS-BF02-Flow1: Translator Registration -> Role Elevation -> Database Persistence")
    void testTranslatorRegistrationWorkflow() throws Exception {
        TranslatorRegistrationRequest request = new TranslatorRegistrationRequest();
        request.setSpecializations(List.of("EN-VI Manga Translation"));
        request.setExperiencedYears(3);
        request.setPhone("0987654321");
        request.setBio("Experienced translator passionate about comics");

        String readerToken = jwtTokenUtil.generateToken(readerUser);

        mockMvc.perform(post("/translator-registration")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify User Role is elevated to TRANSLATOR in Database
        UserEntity updatedUser = userRepository.findById(readerUser.getId()).orElseThrow();
        assertEquals("TRANSLATOR", updatedUser.getRole().getRoleName().toUpperCase(), "Role must be elevated to TRANSLATOR");
    }

    @Test
    @DisplayName("TC-SYS-BF02-Flow2: Moderator Creates Translation Pool Request -> Unclaimed Project Team Initialized")
    void testModeratorCreateTranslationPoolRequest() throws Exception {
        // Step 1: Create a Published Comic with source language "vi"
        ComicEntity comic = comicRepository.save(ComicEntity.builder()
                .title("Legendary Moon Sculptor")
                .summary("VRMMO comic")
                .language("vi")
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build());
        comicRepository.flush();

        // Step 2: Moderator requests translation into English and Japanese
        TranslationRequestDTO req = new TranslationRequestDTO();
        req.setComicId(comic.getId());
        req.setTargetLanguages(List.of("en", "ja"));

        String modToken = jwtTokenUtil.generateToken(moderatorUser);

        mockMvc.perform(post("/translation-pool/request")
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Step 3: Verify UNCLAIMED ProjectTeamEntity records created in DB
        List<ProjectTeamEntity> createdTeams = projectTeamRepository.findAll().stream()
                .filter(pt -> "Legendary Moon Sculptor".equalsIgnoreCase(pt.getComicName()))
                .toList();

        assertEquals(2, createdTeams.size(), "Should create 2 unclaimed project teams for 2 target languages");
        assertTrue(createdTeams.stream().allMatch(t -> "UNCLAIMED".equalsIgnoreCase(t.getStatus())), "All created projects must be UNCLAIMED");
    }

    @Test
    @DisplayName("TC-SYS-BF02-Flow3: Translator Discovers Unclaimed Projects -> Claims Project Team -> Workspace Activated")
    void testTranslatorClaimsProjectTeam() throws Exception {
        // Step 1: Create an UNCLAIMED Project Team
        ProjectTeamEntity unclaimedProject = projectTeamRepository.save(ProjectTeamEntity.builder()
                .title("Solo Leveling - (English)")
                .comicName("Solo Leveling")
                .sourceLang("vi")
                .targetLang("en")
                .status("UNCLAIMED")
                .build());
        projectTeamRepository.flush();

        // Step 2: Translator claims project
        String translatorToken = jwtTokenUtil.generateToken(translatorUser);

        mockMvc.perform(put("/translation-pool/" + unclaimedProject.getId() + "/claim")
                        .header("Authorization", "Bearer " + translatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Step 3: Verify Project Team is ACTIVE with Translator as Leader
        ProjectTeamEntity claimed = projectTeamRepository.findById(unclaimedProject.getId()).orElseThrow();
        assertEquals("active", claimed.getStatus().toLowerCase(), "Project status must transition to ACTIVE");
        assertEquals(translatorUser.getId(), claimed.getLeaderId(), "LeaderId must match claiming Translator");
    }

    @Test
    @DisplayName("TC-SEC-SPOT-002: Security Spot-Check — Reader attempting translation request direct call must return 403 Forbidden")
    void testReaderCannotCreateTranslationRequest() throws Exception {
        TranslationRequestDTO req = new TranslationRequestDTO();
        req.setTargetLanguages(List.of("en"));

        String readerToken = jwtTokenUtil.generateToken(readerUser);

        mockMvc.perform(post("/translation-pool/request")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
