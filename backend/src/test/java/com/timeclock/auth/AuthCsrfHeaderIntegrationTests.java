package com.timeclock.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AuthCsrfHeaderIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    private String email;

    @AfterEach
    void cleanup() {
        if (email != null) jdbcTemplate.update("DELETE FROM users WHERE email=?", email);
    }

    @Test
    void frontendCsrfHeaderNameIsAcceptedForRegistration() throws Exception {
        email = "csrf-" + UUID.randomUUID() + "@example.com";
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(csrf.getResponse().getContentAsString());
        String token = body.path("data").path("csrfToken").asText();

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-CSRF-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "CorrectHorse1!", "CorrectHorse1!"))))
                .andExpect(status().isOk());
    }
}