package com.timeclock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.RegisterRequest;
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
class AuthSessionApiTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    private String email;

    @AfterEach
    void cleanup() {
        if (email != null) {
            jdbcTemplate.update("DELETE FROM user_sessions WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
            jdbcTemplate.update("DELETE FROM users WHERE email=?", email);
        }
    }

    @Test
    void loginCreatesHashedSessionAndMeRestoresUser() throws Exception {
        email = uniqueEmail();
        register(email, "CorrectHorse1!");
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "CorrectHorse1!"))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("SESSION_ID"))
                .andExpect(cookie().httpOnly("SESSION_ID", true))
                .andExpect(cookie().secure("SESSION_ID", true))
                .andExpect(cookie().sameSite("SESSION_ID", "Lax"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andReturn();
        String raw = login.getResponse().getCookie("SESSION_ID").getValue();
        assertThat(raw).hasSize(64);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE token_hash=?", Long.class,
                SessionService.hash(raw))).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE token_hash=?", Long.class, raw)).isZero();

        mockMvc.perform(get("/api/v1/auth/me").cookie(login.getResponse().getCookie("SESSION_ID")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void wrongAndUnknownCredentialsHaveSameUnauthorizedResponse() throws Exception {
        email = uniqueEmail();
        register(email, "CorrectHorse1!");
        String wrong = mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "WrongHorse1!"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("unknown-" + email, "WrongHorse1!"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        JsonNode a = objectMapper.readTree(wrong).get("error");
        JsonNode b = objectMapper.readTree(unknown).get("error");
        assertThat(a.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(a.get("message").asText()).isEqualTo(b.get("message").asText());
    }

    @Test
    void logoutRevokesOnlyCurrentSession() throws Exception {
        email = uniqueEmail();
        register(email, "CorrectHorse1!");
        MvcResult first = login(email);
        MvcResult second = login(email);
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(first.getResponse().getCookie("SESSION_ID")))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("SESSION_ID", 0));
        mockMvc.perform(get("/api/v1/auth/me").cookie(first.getResponse().getCookie("SESSION_ID")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").cookie(second.getResponse().getCookie("SESSION_ID")))
                .andExpect(status().isOk());
    }

    @Test
    void missingCsrfRejectsWriteRequest() throws Exception {
        email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, "CorrectHorse1!", "CorrectHorse1!"))))
                .andExpect(status().isForbidden());
    }

    private MvcResult login(String address) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(address, "CorrectHorse1!"))))
                .andExpect(status().isOk()).andReturn();
    }

    private void register(String address, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(address, password, password))))
                .andExpect(status().isOk());
    }

    private String uniqueEmail() { return "auth-" + UUID.randomUUID() + "@example.com"; }
    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
}
