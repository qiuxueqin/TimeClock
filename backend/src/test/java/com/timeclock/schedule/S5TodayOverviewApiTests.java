package com.timeclock.schedule;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.SessionService;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.RegisterRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/**
 * TEST-S5-BE-01-01（聚合，远程 MySQL）：今日总览混合状态、连续摘要事实、跨用户隔离与认证边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S5TodayOverviewApiTests {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
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

    /** 混合未开始 / 进行中 / 已完成 / 无计划日（草稿、未开始日期、已结束）的任务聚合。 */
    @Test
    void aggregatesMixedTaskStatuses() throws Exception {
        String session = login("mix");
        LocalDate today = LocalDate.now(ZONE);

        String inProgress = taskFrom(session, "进行中任务", today, 2);
        item(session, inProgress, "题目A1");
        item(session, inProgress, "题目A2");
        activate(session, inProgress);
        complete(session, firstItem(inProgress), "题解 A1");

        String notStarted = taskFrom(session, "未开始任务", today, 1);
        item(session, notStarted, "题目B1");
        activate(session, notStarted);

        String done = taskFrom(session, "已完成任务", today, 1);
        item(session, done, "题目C1");
        activate(session, done);
        complete(session, firstItem(done), "题解 C1");

        String draft = taskFrom(session, "草稿任务", today, 3);
        item(session, draft, "题目D1");

        String notYetStarted = taskWithDates(session, "未来任务", tomorrow(today), null, 1);
        item(session, notYetStarted, "题目E1");
        activate(session, notYetStarted);

        String ended = taskWithDates(session, "已结束任务", today.minusDays(2), today.minusDays(1), 1);
        item(session, ended, "题目F1");
        activate(session, ended);

        mockMvc.perform(get("/api/v1/dashboard/today").cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(today.toString()))
                .andExpect(jsonPath("$.data.todayCount").value(3))
                .andExpect(jsonPath("$.data.completedCount").value(1))
                .andExpect(jsonPath("$.data.pendingCount").value(2))
                .andExpect(jsonPath("$.data.completionRate").value(closeTo(1.0 / 3, 0.0001)))
                .andExpect(jsonPath("$.data.tasks", hasSize(6)))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='进行中任务')].status").value(hasItem("inProgress")))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='进行中任务')].completedCount").value(hasItem(1)))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='进行中任务')].plannedCount").value(hasItem(2)))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='未开始任务')].status").value(hasItem("notStarted")))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='未开始任务')].currentStreak").value(hasItem(0)))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='已完成任务')].status").value(hasItem("completed")))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='已完成任务')].currentStreak").value(hasItem(1)))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='草稿任务')].status").value(hasItem("noPlan")))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='草稿任务')].plannedCount").value(hasItem(0)))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='未来任务')].status").value(hasItem("noPlan")))
                .andExpect(jsonPath("$.data.tasks[?(@.task.name=='已结束任务')].status").value(hasItem("noPlan")));
    }

    /** 连续摘要：今天经真实完成链路打卡 + 两个历史 completed 打卡事实 → 当前连续 3。 */
    @Test
    void streakSummaryCountsConsecutiveCompletedDays() throws Exception {
        String session = login("streak");
        LocalDate today = LocalDate.now(ZONE);
        String taskId = taskFrom(session, "连续任务", today.minusDays(2), 1);

        // 前两天：直接播种历史完成条目与 completed 打卡事实（API 无法回填历史）。
        seedCompletedDay(taskId, "历史题目-1", today.minusDays(2));
        seedCompletedDay(taskId, "历史题目-2", today.minusDays(1));

        // 今天走真实完成闭环：目标 1 达成自动生成当日 completed 打卡。
        String itemId = item(session, taskId, "今日题目");
        activate(session, taskId);
        complete(session, itemId, "今日题解");

        mockMvc.perform(get("/api/v1/dashboard/today").cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreak").value(3))
                .andExpect(jsonPath("$.data.longestStreak").value(3))
                .andExpect(jsonPath("$.data.tasks[?(@.task.id=='" + taskId + "')].currentStreak").value(hasItem(3)))
                .andExpect(jsonPath("$.data.tasks[?(@.task.id=='" + taskId + "')].status").value(hasItem("completed")));
    }

    /** 今天是计划日但尚未完成：不计数也不断链（昨日链保持为 1）。 */
    @Test
    void todayPendingDoesNotBreakPriorStreak() throws Exception {
        String session = login("pending");
        LocalDate today = LocalDate.now(ZONE);
        String taskId = taskFrom(session, "待完成任务", today.minusDays(1), 1);
        seedCompletedDay(taskId, "昨日题目", today.minusDays(1));
        jdbc.update("INSERT INTO learning_items (id,task_id,title,status,solution_text,sort_order,created_at,updated_at)"
                        + " VALUES (?,?,?,'pending',NULL,?,NOW(6),NOW(6))",
                UUID.randomUUID().toString(), taskId, "今日待做题目", 2);
        activate(session, taskId);

        mockMvc.perform(get("/api/v1/dashboard/today").cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[?(@.task.id=='" + taskId + "')].status").value(hasItem("notStarted")))
                .andExpect(jsonPath("$.data.tasks[?(@.task.id=='" + taskId + "')].currentStreak").value(hasItem(1)));
    }

    /** makeup 不计入且不修复连续链；其前的 completed 链仍构成最长连续。 */
    @Test
    void makeupDoesNotCountAndDoesNotRepairChain() throws Exception {
        String session = login("makeup");
        LocalDate today = LocalDate.now(ZONE);
        String taskId = taskFrom(session, "补打任务", today.minusDays(3), 1);
        seedCheckin(taskId, today.minusDays(3), "completed");
        seedCheckin(taskId, today.minusDays(2), "completed");
        seedCheckin(taskId, today.minusDays(1), "makeup");
        jdbc.update("INSERT INTO learning_items (id,task_id,title,status,solution_text,sort_order,created_at,updated_at)"
                        + " VALUES (?,?,'待做','pending',NULL,1,NOW(6),NOW(6))",
                UUID.randomUUID().toString(), taskId);

        mockMvc.perform(get("/api/v1/dashboard/today").cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[?(@.task.id=='" + taskId + "')].currentStreak").value(hasItem(0)))
                .andExpect(jsonPath("$.data.longestStreak").value(2))
                .andExpect(jsonPath("$.data.currentStreak").value(0));
    }

    /** 条目全部完成后剩余为零：尾部不再生成计划，最终连续保留且状态为 noPlan。 */
    @Test
    void exhaustedTailKeepsFinalStreakWithNoPlanStatus() throws Exception {
        String session = login("done");
        LocalDate today = LocalDate.now(ZONE);
        String taskId = taskFrom(session, "完结任务", today.minusDays(7), 1);
        seedCompletedDay(taskId, "完结题目-1", today.minusDays(7));
        seedCompletedDay(taskId, "完结题目-2", today.minusDays(6));

        mockMvc.perform(get("/api/v1/dashboard/today").cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[?(@.task.id=='" + taskId + "')].status").value(hasItem("noPlan")))
                .andExpect(jsonPath("$.data.tasks[?(@.task.id=='" + taskId + "')].currentStreak").value(hasItem(2)))
                .andExpect(jsonPath("$.data.todayCount").value(0));
    }

    /** 用户隔离：B 的今日总览不包含 A 的任何任务或计数。 */
    @Test
    void overviewIsIsolatedPerUser() throws Exception {
        String sessionA = login("iso-a");
        String sessionB = login("iso-b");
        LocalDate today = LocalDate.now(ZONE);
        String taskA = taskFrom(sessionA, "甲的独占任务", today, 1);
        item(sessionA, taskA, "甲的题目");
        activate(sessionA, taskA);
        complete(sessionA, firstItem(taskA), "甲的题解");

        MvcResult resultB = mockMvc.perform(get("/api/v1/dashboard/today").cookie(cookie(sessionB)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode dataB = mapper.readTree(resultB.getResponse().getContentAsString()).get("data");
        org.junit.jupiter.api.Assertions.assertEquals(0, dataB.get("todayCount").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(0, dataB.get("tasks").size());
        org.junit.jupiter.api.Assertions.assertFalse(dataB.toString().contains("甲的独占任务"));

        MvcResult resultA = mockMvc.perform(get("/api/v1/dashboard/today").cookie(cookie(sessionA)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode dataA = mapper.readTree(resultA.getResponse().getContentAsString()).get("data");
        org.junit.jupiter.api.Assertions.assertEquals(1, dataA.get("todayCount").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(1, dataA.get("completedCount").asInt());
    }

    /** 未登录访问返回 401。 */
    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/today"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 夹具 ----------

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

    private String taskFrom(String session, String name, LocalDate start, int target) throws Exception {
        return taskWithDates(session, name, start, null, target);
    }

    private String taskWithDates(String session, String name, LocalDate start, LocalDate end, int target) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks").with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"checklist\",\"startDate\":\"" + start + "\""
                                + (end == null ? "" : ",\"endDate\":\"" + end + "\"")
                                + ",\"scheduleType\":\"daily\",\"timezone\":\"Asia/Shanghai\",\"dailyTargetCount\":" + target + "}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String item(String session, String task, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/{task}/items", task).with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String firstItem(String task) {
        return jdbc.queryForObject(
                "SELECT id FROM learning_items WHERE task_id=? ORDER BY sort_order LIMIT 1", String.class, task);
    }

    private void activate(String session, String task) throws Exception {
        mockMvc.perform(post("/api/v1/tasks/{task}/activate", task).with(csrf()).cookie(cookie(session)))
                .andExpect(status().isOk());
    }

    private void complete(String session, String itemId, String solution) throws Exception {
        mockMvc.perform(post("/api/v1/items/{id}/complete", itemId).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solutionContent\":\"" + solution + "\"}"))
                .andExpect(status().isOk());
    }

    /** 播种一个历史完成日：完成条目（completed_at 落在任务时区当天下午）+ completed 打卡事实。 */
    private void seedCompletedDay(String taskId, String title, LocalDate date) {
        Timestamp at = Timestamp.from(date.atTime(15, 0).atZone(ZONE).toInstant());
        jdbc.update("INSERT INTO learning_items (id,task_id,title,status,solution_text,sort_order,completed_at,created_at,updated_at)"
                        + " VALUES (?,?,?,'completed','历史题解',?,?,?,?)",
                UUID.randomUUID().toString(), taskId, title,
                jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM learning_items WHERE task_id=?", Integer.class, taskId),
                at, at, at);
        seedCheckin(taskId, date, "completed");
    }

    private void seedCheckin(String taskId, LocalDate date, String status) {
        jdbc.update("INSERT INTO checkins (id,task_id,checkin_date,status,planned_count,completed_count,created_at,updated_at)"
                        + " VALUES (?,?,?,?,1,1,NOW(6),NOW(6))",
                UUID.randomUUID().toString(), taskId, date, status);
    }

    private LocalDate tomorrow(LocalDate today) { return today.plusDays(1); }

    private jakarta.servlet.http.Cookie cookie(String value) { return new jakarta.servlet.http.Cookie(SessionService.COOKIE_NAME, value); }
}
