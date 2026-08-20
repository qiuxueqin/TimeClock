package com.timeclock.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.SessionService;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.RegisterRequest;
import java.util.ArrayList;
import java.util.List;
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
class TaskLifecycleApiTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    private final List<String> emails = new ArrayList<>();

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
    void listAndDetailAreUserScopedAndStatusFilterWorks() throws Exception {
        String owner = register();
        String other = register();
        String ownerCookie = login(owner);
        String otherCookie = login(other);
        String first = create(ownerCookie, "owner-draft");
        String second = create(ownerCookie, "owner-second");
        String otherTask = create(otherCookie, "private-task");
        jdbcTemplate.update("UPDATE tasks SET status='active' WHERE id=?", second);

        mockMvc.perform(get("/api/v1/tasks?page=1&pageSize=1").cookie(cookie(ownerCookie)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(get("/api/v1/tasks?status=active").cookie(cookie(ownerCookie)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("active"));
        mockMvc.perform(get("/api/v1/tasks/" + first).cookie(cookie(otherCookie)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
        assertThat(otherTask).isNotEqualTo(first);
    }

    @Test
    void ownerCanUpdateAndDeleteTaskAndDeletedTaskIsInaccessible() throws Exception {
        String owner = register();
        String session = login(owner);
        String id = create(session, "before-update");
        mockMvc.perform(patch("/api/v1/tasks/" + id).with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"after-update\",\"dailyTargetCount\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("after-update"))
                .andExpect(jsonPath("$.data.dailyTargetCount").value(3));
        mockMvc.perform(delete("/api/v1/tasks/" + id).with(csrf()).cookie(cookie(session)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tasks/" + id).cookie(cookie(session)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void invalidPagingAndMissingCsrfAreRejected() throws Exception {
        String owner = register();
        String session = login(owner);
        mockMvc.perform(get("/api/v1/tasks?page=0").cookie(cookie(session)))
                .andExpect(status().isUnprocessableEntity());
        String id = create(session, "csrf-check");
        mockMvc.perform(delete("/api/v1/tasks/" + id).cookie(cookie(session)))
                .andExpect(status().isForbidden());
    }

    private String create(String session, String name) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/tasks")
                        .with(csrf()).cookie(cookie(session)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"checklist\",\"startDate\":\"2026-08-20\",\"scheduleType\":\"daily\",\"timezone\":\"Asia/Shanghai\",\"dailyTargetCount\":2}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String register() throws Exception {
        String email = "lifecycle-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "CorrectHorse1!", "CorrectHorse1!"))))
                .andExpect(status().isOk());
        return email;
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "CorrectHorse1!"))))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie(SessionService.COOKIE_NAME).getValue();
    }

    private jakarta.servlet.http.Cookie cookie(String value) {
        return new jakarta.servlet.http.Cookie(SessionService.COOKIE_NAME, value);
    }
}
