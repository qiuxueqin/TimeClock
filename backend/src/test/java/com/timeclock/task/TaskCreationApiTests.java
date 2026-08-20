package com.timeclock.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.SessionService;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.RegisterRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskCreationApiTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    private final java.util.List<String> emails = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String email : emails) {
            jdbcTemplate.update("DELETE FROM tasks WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
            jdbcTemplate.update("DELETE FROM user_sessions WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
            jdbcTemplate.update("DELETE FROM users WHERE email=?", email);
        }
        emails.clear();
    }

    @Test
    void createsDraftChecklistTaskForAuthenticatedUser() throws Exception {
        String email = registerAndLoginEmail();
        MvcResult result = create(email, validBody("算法题"));
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("status").asText()).isEqualTo("draft");
        assertThat(data.get("type").asText()).isEqualTo("checklist");
        assertThat(data.get("scheduleType").asText()).isEqualTo("daily");
        assertThat(data.get("itemCount").asInt()).isZero();
        assertThat(data.get("completedItemCount").asInt()).isZero();
        assertThat(result.getResponse().getContentAsString()).contains("requestId");
    }

    @Test
    void invalidDailyTargetReturns422WithoutWriting() throws Exception {
        String email = registerAndLoginEmail();
        mockMvc.perform(post("/api/v1/tasks").with(csrf()).cookie(loginCookie(email))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("bad").replace("\"dailyTargetCount\":2", "\"dailyTargetCount\":0")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(taskCount(email)).isZero();
    }

    @Test
    void duplicateNameReturns409() throws Exception {
        String email = registerAndLoginEmail();
        create(email, validBody("重复"));
        mockMvc.perform(post("/api/v1/tasks").with(csrf()).cookie(loginCookie(email))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("重复")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TASK_NAME_ALREADY_EXISTS"));
    }

    @Test
    void missingCsrfAndAnonymousRequestsAreRejected() throws Exception {
        String email = registerAndLoginEmail();
        mockMvc.perform(post("/api/v1/tasks").cookie(loginCookie(email))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("csrf")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tasks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("anonymous")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activationWithoutItemsReturns422AndLeavesDraft() throws Exception {
        String email = registerAndLoginEmail();
        MvcResult created = create(email, validBody("空任务"));
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();
        mockMvc.perform(post("/api/v1/tasks/{id}/activate", id).with(csrf()).cookie(loginCookie(email)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TASK_ACTIVATION_REQUIRES_ITEM"));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tasks WHERE id=?", String.class, id)).isEqualTo("draft");
    }

    @Test
    void anotherUserCannotActivateTask() throws Exception {
        String owner = registerAndLoginEmail();
        String other = registerAndLoginEmail();
        MvcResult created = create(owner, validBody("私有任务"));
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();
        mockMvc.perform(post("/api/v1/tasks/{id}/activate", id).with(csrf()).cookie(loginCookie(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
    }

    private MvcResult create(String email, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/tasks").with(csrf()).cookie(loginCookie(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").isNotEmpty()).andReturn();
    }

    private String registerAndLoginEmail() throws Exception {
        String email = "task-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, "CorrectHorse1!", "CorrectHorse1!"))))
                .andExpect(status().isOk());
        return email;
    }

    private jakarta.servlet.http.Cookie loginCookie(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "CorrectHorse1!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
    }

    private String validBody(String name) {
        return "{\"name\":\"" + name + "\",\"type\":\"checklist\",\"startDate\":\"2026-08-20\","
                + "\"scheduleType\":\"daily\",\"timezone\":\"Asia/Shanghai\",\"dailyTargetCount\":2}";
    }

    private long taskCount(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tasks WHERE user_id IN (SELECT id FROM users WHERE email=?)", Long.class, email);
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
}
