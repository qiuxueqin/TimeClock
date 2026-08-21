package com.timeclock.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.SessionService;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.RegisterRequest;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S3ItemsImportApiTests {
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
    void itemCrudIsOwnedAndOtherUserCannotReadOrModify() throws Exception {
        String owner = registerAndLogin("owner");
        String other = registerAndLogin("other");
        String task = createTask(owner, "owned-items");
        String item = createItem(owner, task, "original");

        mockMvc.perform(get("/api/v1/tasks/{task}/items", task).cookie(cookie(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/items/{item}", item).cookie(cookie(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/items/{item}", item).with(csrf()).cookie(cookie(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/items/{item}", item).with(csrf()).cookie(cookie(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"updated\",\"content\":\"body\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("updated"));
        mockMvc.perform(get("/api/v1/items/{item}", item).cookie(cookie(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("body"));
    }

    @Test
    void pastePreviewReportsInvalidLinesAndConfirmDeduplicates() throws Exception {
        String session = registerAndLogin("paste");
        String task = createTask(session, "paste-task");
        String preview = mockMvc.perform(post("/api/v1/tasks/{task}/items/paste-preview", task).with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"First|question|analysis|https://example.test\\n\\n |ignored\\nSecond|body\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalLines").value(4))
                .andExpect(jsonPath("$.data.validLines").value(2)).andExpect(jsonPath("$.data.errors.length()", org.hamcrest.Matchers.is(1))).andReturn()
                .getResponse().getContentAsString();
        assertThat(preview).contains("First");
        mockMvc.perform(post("/api/v1/tasks/{task}/items/paste-confirm", task).with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidates\":[{\"title\":\"First\"},{\"title\":\" first \"},{\"title\":\"Second\"}]}") )
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", org.hamcrest.Matchers.is(2)));
        mockMvc.perform(get("/api/v1/tasks/{task}/items", task).cookie(cookie(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void xlsxPreviewValidatesRowsWithoutWritingAndConfirmIsIdempotent() throws Exception {
        String session = registerAndLogin("xlsx");
        String task = createTask(session, "xlsx-task");
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/tasks/{task}/imports/xlsx/preview", task)
                        .file(file).with(csrf()).cookie(cookie(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.validRows").value(1)).andExpect(jsonPath("$.data.errorRows.length()", org.hamcrest.Matchers.is(2)))
                .andExpect(jsonPath("$.data.candidates[0].title").value("Valid"));
        assertThat(itemCount(task)).isZero();

        String body = "{\"candidates\":[{\"title\":\"Valid\",\"content\":\"c\",\"action\":\"keep_new\"}]}";
        mockMvc.perform(post("/api/v1/tasks/{task}/imports/xlsx/confirm", task).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "xlsx-key").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(1));
        mockMvc.perform(post("/api/v1/tasks/{task}/imports/xlsx/confirm", task).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", "xlsx-key").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(1));
        assertThat(itemCount(task)).isEqualTo(1);
    }

    @Test
    void activationSucceedsAfterItemConfirmationAndTodayReturnsAllocation() throws Exception {
        String session = registerAndLogin("today");
        String task = createTask(session, "today-task");
        createItem(session, task, "one");
        createItem(session, task, "two");
        mockMvc.perform(post("/api/v1/tasks/{task}/activate", task).with(csrf()).cookie(cookie(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("active"));
        mockMvc.perform(get("/api/v1/tasks/{task}/today-items", task).cookie(cookie(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetCount").value(2))
                .andExpect(jsonPath("$.data.items.length()", org.hamcrest.Matchers.is(2)))
                .andExpect(jsonPath("$.data.items[0].title").value("one"));
    }

    private String registerAndLogin(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, "CorrectHorse1!", "CorrectHorse1!"))))
                .andExpect(status().isOk());
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "CorrectHorse1!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SessionService.COOKIE_NAME).getValue();
    }

    private String createTask(String session, String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"type\":\"checklist\",\"startDate\":\"" + LocalDate.now() + "\",\"scheduleType\":\"daily\",\"timezone\":\"Asia/Shanghai\",\"dailyTargetCount\":2}";
        MvcResult result = mockMvc.perform(post("/api/v1/tasks").with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String createItem(String session, String task, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/{task}/items", task).with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private byte[] workbookBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            String[] headers = {"title", "content", "analysis", "link", "order"};
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            var valid = sheet.createRow(1); valid.createCell(0).setCellValue("Valid"); valid.createCell(4).setCellValue(1);
            var badOrder = sheet.createRow(2); badOrder.createCell(0).setCellValue("Bad order"); badOrder.createCell(4).setCellValue(0);
            var blankTitle = sheet.createRow(3); blankTitle.createCell(1).setCellValue("missing title");
            workbook.write(out); return out.toByteArray();
        }
    }

    private long itemCount(String task) { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learning_items WHERE task_id=?", Long.class, task); }
    private jakarta.servlet.http.Cookie cookie(String value) { return new jakarta.servlet.http.Cookie(SessionService.COOKIE_NAME, value); }
    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
}
