package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamWorkspaceControllerIT extends AbstractBlackboxIT {

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-001")
    void tasksUnauthorized() throws Exception {
        UUID teamId = leaderTeam().teamId();
        getJson("/team-workspace/" + teamId + "/tasks").andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-002")
    void taskBoard() throws Exception {
        TeamContext ctx = leaderTeam();
        getJson("/team-workspace/" + ctx.teamId() + "/tasks", ctx.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-003")
    void membersIncludeLeader() throws Exception {
        TeamContext ctx = leaderTeam();
        getJson("/team-workspace/" + ctx.teamId() + "/members", ctx.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-004")
    void membersUnknown() throws Exception {
        getJson("/team-workspace/" + UUID.randomUUID() + "/members", fixedToken(LEADER_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-005")
    void listAnnouncements() throws Exception {
        TeamContext ctx = leaderTeam();
        getJson("/team-workspace/" + ctx.teamId() + "/announcements", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-006")
    void createAnnouncement() throws Exception {
        TeamContext ctx = leaderTeam();
        postJson("/team-workspace/" + ctx.teamId() + "/announcements", """
                {"content":"Kickoff notes","authorName":"Leader"}
                """, ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-007")
    void listMessages() throws Exception {
        TeamContext ctx = leaderTeam();
        getJson("/team-workspace/" + ctx.teamId() + "/messages", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-008")
    void listRequests() throws Exception {
        TeamContext ctx = leaderTeam();
        getJson("/team-workspace/" + ctx.teamId() + "/requests", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-009")
    void joinUnauthorized() throws Exception {
        TeamContext ctx = leaderTeam();
        postJson("/team-workspace/" + ctx.teamId() + "/requests", """
                {"name":"Applicant","text":"Please let me join"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-010")
    void joinAsTranslator() throws Exception {
        TeamContext ctx = leaderTeam();
        postJson("/team-workspace/" + ctx.teamId() + "/requests", """
                {"name":"Applicant","text":"Please let me join this project"}
                """, fixedToken(TRANS_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-011")
    void myApplicationStatus() throws Exception {
        getJson("/team-workspace/my-application-status", fixedToken(TRANS_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSlots").value(5));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-012")
    void chapterBacklog() throws Exception {
        TeamContext ctx = leaderTeam();
        getJson("/team-workspace/" + ctx.teamId() + "/chapter-backlog", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-013")
    void teamChapters() throws Exception {
        TeamContext ctx = leaderTeam();
        getJson("/team-workspace/" + ctx.teamId() + "/chapters", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-014")
    void createTaskMissingChapter() throws Exception {
        TeamContext ctx = leaderTeam();
        postJson("/team-workspace/" + ctx.teamId() + "/tasks", """
                {"title":"Translate chapter"}
                """, ctx.token())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-015")
    void getUnknownTask() throws Exception {
        getJson("/team-workspace/tasks/" + UUID.randomUUID(), fixedToken(LEADER_USER))
                .andExpect(status().isNotFound());
    }


    private TeamContext leaderTeam() throws Exception {
        SeededUser leader = fixedUser(LEADER_USER);
        String token = fixedToken(LEADER_USER);
        UUID teamId = createTeam(token, "Workspace Team", leader.id());
        return new TeamContext(token, teamId);
    }

    private record TeamContext(String token, UUID teamId) {}
}
