package com.timeclock.item;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.SessionService;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.RegisterRequest;
import java.time.LocalDate;
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
class S4CompletionApiTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String email : emails) {
            jdbc.update("DELETE FROM tasks WHERE user_id IN (SELECT id FROM users WHERE email=? )", email);
            jdbc.update("DELETE FROM user_sessions WHERE user_id IN (SELECT id FROM users WHERE email=? )", email);
            jdbc.update("DELETE FROM users WHERE email=?", email);
        }
        emails.clear();
    }

    @Test
    void blankSolutionIsRejectedAndValidSolutionCompletesAndIsIdempotent() throws Exception {
        String session = login("complete");
        String task = task(session, "complete-task", 2);
        String first = item(session, task, "first");
        String second = item(session, task, "second");
        activate(session, task);

        mockMvc.perform(post("/api/v1/items/{id}/complete", first).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "blank-key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solutionContent\":\" \\n\\t\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SOLUTION_REQUIRED"));

        mockMvc.perform(post("/api/v1/items/{id}/complete", first).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "complete-key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solutionContent\":\" answer \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.status").value("completed"))
                .andExpect(jsonPath("$.data.completedCount").value(1))
                .andExpect(jsonPath("$.data.checkinStatus").value("partial"));

        mockMvc.perform(post("/api/v1/items/{id}/complete", first).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "complete-key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solutionContent\":\" answer \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.id").value(first))
                .andExpect(jsonPath("$.data.completedCount").value(1));

        mockMvc.perform(post("/api/v1/items/{id}/complete", first).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "complete-key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solutionContent\":\"different\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(post("/api/v1/items/{id}/complete", second).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "second-key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solutionContent\":\"second answer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedCount").value(2))
                .andExpect(jsonPath("$.data.checkinStatus").value("completed"));
    }

    @Test
    void reopenRollsBackCheckinAndKeepsSolution() throws Exception {
        String session = login("reopen");
        String task = task(session, "reopen-task", 1);
        String item = item(session, task, "reopen-item");
        activate(session, task);
        mockMvc.perform(post("/api/v1/items/{id}/complete", item).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "complete-reopen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solutionContent\":\"kept solution\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.checkinStatus").value("completed"));
        mockMvc.perform(post("/api/v1/items/{id}/reopen", item).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "reopen-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.status").value("pending"))
                .andExpect(jsonPath("$.data.item.solutionText").value("kept solution"))
                .andExpect(jsonPath("$.data.checkinStatus").value("partial"));
        assert jdbc.queryForObject("SELECT status FROM learning_items WHERE id=?", String.class, item).equals("pending");
        assert jdbc.queryForObject("SELECT status FROM checkins WHERE task_id=? AND checkin_date=?", String.class, task, LocalDate.now()).equals("partial");
    }

    private String login(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RegisterRequest(email, "CorrectHorse1!", "CorrectHorse1!"))))
                .andExpect(status().isOk());
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, "CorrectHorse1!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SessionService.COOKIE_NAME).getValue();
    }

    private String task(String session, String name, int target) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks").with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"checklist\",\"startDate\":\"" + LocalDate.now() + "\",\"scheduleType\":\"daily\",\"timezone\":\"Asia/Shanghai\",\"dailyTargetCount\":" + target + "}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String item(String session, String task, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/{task}/items", task).with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private void activate(String session, String task) throws Exception {
        mockMvc.perform(post("/api/v1/tasks/{task}/activate", task).with(csrf()).cookie(cookie(session)))
                .andExpect(status().isOk());
    }

    private jakarta.servlet.http.Cookie cookie(String value) { return new jakarta.servlet.http.Cookie(SessionService.COOKIE_NAME, value); }
}
