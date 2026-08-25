package com.timeclock.checkin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.SessionService;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.RegisterRequest;
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
 * TEST-S6-BE-04-01（补打窗口边界）+ TEST-S6-BE-02-01（月历/统计查询，远程 MySQL 8）。
 *
 * <p>补打：今天/昨天/第 3 天/第 4 天/无计划日/空原因/重复幂等/已补打 409；
 * 月历：跨月、状态筛选、合并视图最差状态、跨用户隔离；统计：连续与条目计数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S6CheckinCalendarApiTests {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CheckinSettlementService settlement;
    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String email : emails) {
            jdbc.update("DELETE FROM tasks WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
            jdbc.update("DELETE FROM user_sessions WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
            jdbc.update("DELETE FROM idempotency_keys WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
            jdbc.update("DELETE FROM users WHERE email=?", email);
        }
        emails.clear();
    }

    /** 补打窗口：昨天可补、第 3 天可补、第 4 天 422、今天 422、非计划日 422。 */
    @Test
    void makeupWindowBoundariesYesterdayThirdAndFourthDay() throws Exception {
        String session = login("win");
        LocalDate today = LocalDate.now(ZONE);
        String taskId = taskWithDates(session, "补打窗口任务", today.minusDays(5), null, 1);
        seedPendingItem(taskId, "窗口题目", today.minusDays(5));
        activate(session, taskId);

        // 结算出 missed/partial 事实（昨日、前日、大前日）。
        settlement.settleTask(taskId);

        // 今天不是过去日期 → 422 MAKEUP_DATE_OUT_OF_WINDOW（且今日无结算事实）。
        makeup(session, taskId, today, "今天不能补", 422, "MAKEUP_DATE_OUT_OF_WINDOW");

        // 第 4 天超出窗口 → 422。
        makeup(session, taskId, today.minusDays(4), "第 4 天", 422, "MAKEUP_DATE_OUT_OF_WINDOW");

        // 昨天与第 3 天在窗口内且为 missed → 成功并返回 makeup 视图。
        for (LocalDate d : new LocalDate[] {today.minusDays(1), today.minusDays(3)}) {
            makeupOk(session, taskId, d, "出差漏做，现补上");
            mockMvc.perform(get("/api/v1/tasks/{t}/checkins/{d}", taskId, d).cookie(cookie(session)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("makeup"))
                    .andExpect(jsonPath("$.data.makeupReason").value("出差漏做，现补上"))
                    .andExpect(jsonPath("$.data.plannedCount").value(1));
        }

        // 已补打的日期再次提交 → 409 CHECKIN_ALREADY_MADE_UP（不可逆 DEC-15）。
        makeup(session, taskId, today.minusDays(3), "再次补打", 409, "CHECKIN_ALREADY_MADE_UP");
    }

    /** 空原因 422；无结算事实的非计划日 404；幂等键重复回放首次响应。 */
    @Test
    void makeupRequiresReasonFactAndReplaysIdempotently() throws Exception {
        String session = login("reason");
        LocalDate today = LocalDate.now(ZONE);
        String taskId = taskWithDates(session, "原因校验任务", today.minusDays(2), null, 2);
        seedPendingItem(taskId, "原因题目A", today.minusDays(1));
        seedPendingItem(taskId, "原因题目B", today.minusDays(1));
        activate(session, taskId);
        settlement.settleTask(taskId); // 昨日 partial（2 条 pending，0 完成）；today-2 计划数为 0 不产生事实

        String key = UUID.randomUUID().toString();
        // 空白原因 → 422 MAKEUP_REASON_REQUIRED（校验失败不占用幂等键）。
        mockMvc.perform(post("/api/v1/tasks/{t}/checkins/{d}/makeup", taskId, today.minusDays(1))
                        .with(csrf()).cookie(cookie(session)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MAKEUP_REASON_REQUIRED"));

        // 有效提交成功：同键同请求后续重放首次响应。
        makeupWithKey(session, taskId, today.minusDays(1), key, "补卡原因");
        MvcResult replay = mockMvc.perform(post("/api/v1/tasks/{t}/checkins/{d}/makeup", taskId, today.minusDays(1))
                        .with(csrf()).cookie(cookie(session)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"补卡原因\"}"))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(
                mapper.readTree(replay.getResponse().getContentAsString()).get("data").get("status").asText(),
                "makeup");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM checkins WHERE task_id=? AND checkin_date=?",
                Integer.class, taskId, today.minusDays(1));
        org.junit.jupiter.api.Assertions.assertEquals(1, count);

        // 同键不同请求体（另一日期）→ 409 IDEMPOTENCY_CONFLICT。
        mockMvc.perform(post("/api/v1/tasks/{t}/checkins/{d}/makeup", taskId, today.minusDays(1))
                        .with(csrf()).cookie(cookie(session)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());

        // 窗口内但无结算事实（计划数为 0 的日期，settle 跳过）→ 404 CHECKIN_NOT_FOUND。
        makeup(session, taskId, today.minusDays(2), "无事实日期", 404, null);
    }

    /** 月历：单任务返回该月各日状态、filter 筛选、跨用户隔离与归属 404。 */
    @Test
    void calendarMonthReturnsFactsFilteredAndIsolated() throws Exception {
        String session = login("cal");
        String other = login("cal-other");
        LocalDate today = LocalDate.now(ZONE);
        String month = today.toString().substring(0, 7);
        // 草稿任务：事实只来自播种，读取结算不会改写（settle 仅作用于 active）。
        String taskId = taskWithDates(session, "月历任务", today.minusDays(3), null, 1);

        seedCompletedDay(taskId, "月历完成题", today.minusDays(3));      // completed
        seedCheckin(taskId, today.minusDays(2), "partial", 2, 1, null);  // partial
        seedCheckin(taskId, today.minusDays(1), "missed", 2, 0, null);   // missed

        // 全量月历按升序返回三个事实日。
        MvcResult all = mockMvc.perform(get("/api/v1/calendar").queryParam("month", month).cookie(cookie(session)))
                .andExpect(status().isOk())
                .andReturn();
        var days = mapper.readTree(all.getResponse().getContentAsString()).get("data").get("days");
        org.junit.jupiter.api.Assertions.assertEquals(3, days.size());
        org.junit.jupiter.api.Assertions.assertEquals(today.minusDays(3).toString(), days.get(0).get("date").asText());
        org.junit.jupiter.api.Assertions.assertEquals("completed", days.get(0).get("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("partial", days.get(1).get("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("missed", days.get(2).get("status").asText());

        // filter=completed 仅保留完成日。
        mockMvc.perform(get("/api/v1/calendar").queryParam("month", month)
                        .queryParam("filter", "completed").cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].status").value("completed"))
                .andExpect(jsonPath("$.data.days[0].completedCount").value(1));

        // 上个月无任何事实。
        mockMvc.perform(get("/api/v1/calendar")
                        .queryParam("month", today.minusMonths(1).toString().substring(0, 7))
                        .cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(0));

        // 其他用户看不到该任务的任何数据。
        mockMvc.perform(get("/api/v1/calendar").queryParam("month", month).cookie(cookie(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(0));

        // 跨用户访问他人 taskId → 404 TASK_NOT_FOUND。
        mockMvc.perform(get("/api/v1/calendar").queryParam("month", month)
                        .queryParam("taskId", taskId).cookie(cookie(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));

        // 非法 month → 422。
        mockMvc.perform(get("/api/v1/calendar").queryParam("month", "2026-13").cookie(cookie(session)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/calendar").queryParam("month", "bad").cookie(cookie(session)))
                .andExpect(status().isUnprocessableEntity());
    }

    /** 合并视图：同日多任务取最差状态（missed 覆盖 completed）。 */
    @Test
    void mergedCalendarTakesWorstStatusPerDay() throws Exception {
        String session = login("merge");
        LocalDate today = LocalDate.now(ZONE);
        String month = today.toString().substring(0, 7);
        // 两个草稿任务：读取结算不作用于 draft，播种事实原样合并。
        String good = taskWithDates(session, "完成任务", today.minusDays(1), null, 1);
        String bad = taskWithDates(session, "漏打任务", today.minusDays(1), null, 1);
        seedCompletedDay(good, "好任务题", today.minusDays(1));
        seedCheckin(bad, today.minusDays(1), "missed", 1, 0, null);

        mockMvc.perform(get("/api/v1/calendar").queryParam("month", month).cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].status").value("missed"))
                .andExpect(jsonPath("$.data.days[0].plannedCount").value(2));

        // 指定完成任务时只看得到 completed。
        mockMvc.perform(get("/api/v1/calendar").queryParam("month", month)
                        .queryParam("taskId", good).cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].status").value("completed"));
    }

    /** 统计：当前/最长连续、条目计数、预计完成日期；未启用任务 estimated 为空。 */
    @Test
    void taskStatsReturnsStreaksCountsAndEstimate() throws Exception {
        String session = login("stats");
        LocalDate today = LocalDate.now(ZONE);
        String taskId = taskWithDates(session, "统计任务", today.minusDays(2), null, 1);
        seedCompletedDay(taskId, "统计历史题", today.minusDays(2));
        seedCheckin(taskId, today.minusDays(1), "completed", 1, 1, null);
        seedPendingItem(taskId, "统计待做题", today);
        activate(session, taskId);

        mockMvc.perform(get("/api/v1/tasks/{t}/stats", taskId).cookie(cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.id").value(taskId))
                // 今天是计划日但未完成：不计数也不断链，当前连续=已完成的过去两日。
                .andExpect(jsonPath("$.data.currentStreak").value(2))
                .andExpect(jsonPath("$.data.longestStreak").value(2))
                .andExpect(jsonPath("$.data.totalItemCount").value(2))
                .andExpect(jsonPath("$.data.completedItemCount").value(1))
                .andExpect(jsonPath("$.data.remainingItemCount").value(1))
                .andExpect(jsonPath("$.data.estimatedCompletionDate").value(today.toString()));

        // 跨用户统一 404；未登录 401。
        String other = login("stats-other");
        mockMvc.perform(get("/api/v1/tasks/{t}/stats", taskId).cookie(cookie(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tasks/{t}/stats", taskId))
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

    private String taskWithDates(String session, String name, LocalDate start, LocalDate end, int target) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks").with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"checklist\",\"startDate\":\"" + start + "\""
                                + (end == null ? "" : ",\"endDate\":\"" + end + "\"")
                                + ",\"scheduleType\":\"daily\",\"timezone\":\"Asia/Shanghai\",\"dailyTargetCount\":" + target + "}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private void seedPendingItem(String taskId, String title, LocalDate createdDate) {
        java.sql.Timestamp at = java.sql.Timestamp.from(createdDate.atTime(9, 0).atZone(ZONE).toInstant());
        jdbc.update("INSERT INTO learning_items (id,task_id,title,status,solution_text,sort_order,created_at,updated_at)"
                        + " VALUES (?,?,?,'pending',NULL,?,?,?)",
                UUID.randomUUID().toString(), taskId, title,
                jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM learning_items WHERE task_id=?", Integer.class, taskId),
                at, at);
    }

    private void seedCompletedDay(String taskId, String title, LocalDate date) {
        java.sql.Timestamp at = java.sql.Timestamp.from(date.atTime(15, 0).atZone(ZONE).toInstant());
        jdbc.update("INSERT INTO learning_items (id,task_id,title,status,solution_text,sort_order,completed_at,created_at,updated_at)"
                        + " VALUES (?,?,?,'completed','历史题解',?,?,?,?)",
                UUID.randomUUID().toString(), taskId, title,
                jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM learning_items WHERE task_id=?", Integer.class, taskId),
                at, at, at);
        seedCheckin(taskId, date, "completed", 1, 1, null);
    }

    private void seedCheckin(String taskId, LocalDate date, String status, int planned, int completed, String reason) {
        jdbc.update("INSERT INTO checkins (id,task_id,checkin_date,status,planned_count,completed_count,makeup_reason,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,NOW(6))",
                UUID.randomUUID().toString(), taskId, date, status, planned, completed, reason,
                java.sql.Timestamp.from(date.atTime(23, 0).atZone(ZONE).toInstant()));
    }

    private void activate(String session, String task) throws Exception {
        mockMvc.perform(post("/api/v1/tasks/{task}/activate", task).with(csrf()).cookie(cookie(session)))
                .andExpect(status().isOk());
    }

    private void makeup(String session, String taskId, LocalDate date, String reason, int expectedStatus, String expectedCode) throws Exception {
        var result = mockMvc.perform(post("/api/v1/tasks/{t}/checkins/{d}/makeup", taskId, date)
                        .with(csrf()).cookie(cookie(session)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().is(expectedStatus));
        if (expectedCode != null) result.andExpect(jsonPath("$.error.code").value(expectedCode));
        result.andReturn();
    }

    private void makeupWithKey(String session, String taskId, LocalDate date, String key, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/tasks/{t}/checkins/{d}/makeup", taskId, date)
                        .with(csrf()).cookie(cookie(session)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("makeup"));
    }

    private void makeupOk(String session, String taskId, LocalDate date, String reason) throws Exception {
        makeupWithKey(session, taskId, date, UUID.randomUUID().toString(), reason);
    }

    private jakarta.servlet.http.Cookie cookie(String value) {
        return new jakarta.servlet.http.Cookie(SessionService.COOKIE_NAME, value);
    }
}
