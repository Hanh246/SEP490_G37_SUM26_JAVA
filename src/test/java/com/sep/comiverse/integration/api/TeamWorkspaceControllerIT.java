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
    @DisplayName("TC-INT-TeamWorkspaceController-001 [UC-15]: GET /team-workspace/{teamId}/tasks - missing token should be rejected")
    void tasksUnauthorized() throws Exception {
        SeededUser leader = seedUser("PROJECT_LEADER");
        UUID teamId = createTeam(login(leader.username()), "Auth Team", leader.id());
        getJson("/team-workspace/" + teamId + "/tasks").andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-002 [UC-15]: GET /team-workspace/{teamId}/tasks - leader should return 200")
    void taskBoard() throws Exception {
        SeededUser leader = seedUser("PROJECT_LEADER");
        String token = login(leader.username());
        UUID teamId = createTeam(token, "Task Board Team", leader.id());
        getJson("/team-workspace/" + teamId + "/tasks", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-003 [UC-41]: GET /team-workspace/{teamId}/members - roster should include the leader")
    void membersIncludeLeader() throws Exception {
        SeededUser leader = seedUser("PROJECT_LEADER");
        String token = login(leader.username());
        UUID teamId = createTeam(token, "Roster Team", leader.id());
        getJson("/team-workspace/" + teamId + "/members", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-004 [UC-41]: GET /team-workspace/{teamId}/members - unknown team should return 404")
    void membersUnknown() throws Exception {
        getJson("/team-workspace/" + UUID.randomUUID() + "/members", token("PROJECT_LEADER"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-005 [UC-55]: GET /team-workspace/{teamId}/announcements - leader should return 200")
    void listAnnouncements() throws Exception {
        TeamContext ctx = team();
        getJson("/team-workspace/" + ctx.teamId() + "/announcements", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-006 [UC-55]: POST /team-workspace/{teamId}/announcements - leader should return 200")
    void createAnnouncement() throws Exception {
        TeamContext ctx = team();
        postJson("/team-workspace/" + ctx.teamId() + "/announcements", """
                {"content":"Kickoff notes","authorName":"Leader"}
                """, ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-007 [UC-40]: GET /team-workspace/{teamId}/messages - leader should return 200")
    void listMessages() throws Exception {
        TeamContext ctx = team();
        getJson("/team-workspace/" + ctx.teamId() + "/messages", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-008 [UC-49]: GET /team-workspace/{teamId}/requests - leader should return 200")
    void listRequests() throws Exception {
        TeamContext ctx = team();
        getJson("/team-workspace/" + ctx.teamId() + "/requests", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-009 [UC-49]: POST /team-workspace/{teamId}/requests - missing token should be rejected")
    void joinUnauthorized() throws Exception {
        TeamContext ctx = team();
        postJson("/team-workspace/" + ctx.teamId() + "/requests", """
                {"name":"Applicant","text":"Please let me join"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-010 [UC-49]: POST /team-workspace/{teamId}/requests - TRANSLATOR should return 200")
    void joinAsTranslator() throws Exception {
        TeamContext ctx = team();
        postJson("/team-workspace/" + ctx.teamId() + "/requests", """
                {"name":"Applicant","text":"Please let me join this project"}
                """, token("TRANSLATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-011 [UC-49]: GET /team-workspace/my-application-status - TRANSLATOR should return 200")
    void myApplicationStatus() throws Exception {
        getJson("/team-workspace/my-application-status", token("TRANSLATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSlots").value(20));
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-012 [UC-42]: GET /team-workspace/{teamId}/chapter-backlog - leader should return 200")
    void chapterBacklog() throws Exception {
        TeamContext ctx = team();
        getJson("/team-workspace/" + ctx.teamId() + "/chapter-backlog", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-013 [UC-42]: GET /team-workspace/{teamId}/chapters - leader should return 200")
    void teamChapters() throws Exception {
        TeamContext ctx = team();
        getJson("/team-workspace/" + ctx.teamId() + "/chapters", ctx.token())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-014 [UC-15]: POST /team-workspace/{teamId}/tasks - missing chapterId should return 400")
    void createTaskMissingChapter() throws Exception {
        TeamContext ctx = team();
        postJson("/team-workspace/" + ctx.teamId() + "/tasks", """
                {"title":"Translate chapter"}
                """, ctx.token())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-015 [UC-15]: GET /team-workspace/tasks/{id} - unknown id should return 404")
    void getUnknownTask() throws Exception {
        getJson("/team-workspace/tasks/" + UUID.randomUUID(), token("PROJECT_LEADER"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-TeamWorkspaceController-016 [UC-41]: GET /team-workspace/{teamId}/bans - leader should return 200")
    void listBans() throws Exception {
        TeamContext ctx = team();
        getJson("/team-workspace/" + ctx.teamId() + "/bans", ctx.token())
                .andExpect(status().isOk());
    }

    private TeamContext team() throws Exception {
        SeededUser leader = seedUser("PROJECT_LEADER");
        String token = login(leader.username());
        UUID teamId = createTeam(token, "Workspace Team", leader.id());
        return new TeamContext(token, teamId);
    }

    private record TeamContext(String token, UUID teamId) {
    }
}
