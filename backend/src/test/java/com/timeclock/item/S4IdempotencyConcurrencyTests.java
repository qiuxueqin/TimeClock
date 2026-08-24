package com.timeclock.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.SessionService;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.RegisterRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * S4 并发 / 幂等 / 越权专项测试。
 *
 * <p>覆盖：同键并发完成仅一次业务效果、异键并发恰好一个 200、幂等冲突 409、
 * 跨用户 404 且零副作用、缺 Idempotency-Key 400、非今日条目 422、撤销幂等回放不二次扣减。
 *
 * <p>并发用例通过 ExecutorService + CountDownLatch 同时发射，断言只依赖竞态下的稳定结果
 * （唯一业务效果与最终状态），不断言具体响应顺序。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S4IdempotencyConcurrencyTests {
    private static final int THREADS = 10;
    /** 与被测服务一致的任务时区（测试任务统一使用 Asia/Shanghai）。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String email : emails) {
            jdbc.update("DELETE FROM tasks WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
            jdbc.update("DELETE FROM user_sessions WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
            jdbc.update("DELETE FROM users WHERE email=?", email);
        }
        emails.clear();
    }

    // ------------------------------------------------------------------
    // 场景 1：十线程相同 Idempotency-Key 相同请求体 -> 业务效果唯一
    // ------------------------------------------------------------------
    @Test
    void concurrentCompleteWithSameKeyHasSingleBusinessEffect() throws Exception {
        String session = login("s4cc-same");
        String task = task(session, "s4cc-same-" + UUID.randomUUID(), today(), 2);
        String target = item(session, task, "target-item");
        item(session, task, "filler-item");
        activate(session, task);

        String key = "same-key-" + UUID.randomUUID();
        String body = "{\"solutionContent\":\"shared answer\"}";
        List<MvcResult> results = fire(THREADS, () -> complete(session, target, key, body));

        JsonNode firstData = null;
        for (MvcResult result : results) {
            assertEquals(200, result.getResponse().getStatus(), "相同键相同体的并发请求应全部回放首次响应");
            JsonNode data = json(result).get("data");
            assertEquals("completed", data.get("item").get("status").asText());
            assertEquals(target, data.get("item").get("id").asText());
            assertEquals(1, data.get("completedCount").asInt());
            if (firstData == null) firstData = data;
            else assertEquals(firstData, data, "回放响应的 data 必须与首次响应完全一致");
        }

        // 业务效果唯一：条目仅完成一次，无重复行，当日打卡正确。
        assertEquals(1, count(
                "SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed'", task));
        assertEquals(2, count("SELECT COUNT(*) FROM learning_items WHERE task_id=?", task), "不得产生重复条目行");
        assertEquals(1, count(
                "SELECT COUNT(*) FROM learning_items WHERE id=? AND status='completed' AND completed_at IS NOT NULL "
                        + "AND solution_text='shared answer'", target));
        assertEquals(1, count("SELECT COUNT(*) FROM checkins WHERE task_id=?", task), "当日打卡只允许一行");
        LocalDate checkinDate = LocalDate.now(ZONE);
        assertEquals(1, count(
                "SELECT completed_count FROM checkins WHERE task_id=? AND checkin_date=?", task, checkinDate),
                "completed_count 必须为 1");
        assertEquals(2, count(
                "SELECT planned_count FROM checkins WHERE task_id=? AND checkin_date=?", task, checkinDate));
        assertEquals("partial", scalar(
                "SELECT status FROM checkins WHERE task_id=? AND checkin_date=?", task, checkinDate));
        assertEquals(1, count(
                "SELECT COUNT(*) FROM idempotency_keys WHERE task_id=? AND operation='complete'", task),
                "同一幂等键只能落一行");
    }

    // ------------------------------------------------------------------
    // 场景 2：十线程十个不同键并发完成同一条目 -> 恰好一个 200，其余 409
    // ------------------------------------------------------------------
    @Test
    void concurrentCompleteWithDistinctKeysCompletesExactlyOnce() throws Exception {
        String session = login("s4cc-distinct");
        String task = task(session, "s4cc-distinct-" + UUID.randomUUID(), today(), 2);
        String target = item(session, task, "contested-item");
        item(session, task, "filler-item");
        activate(session, task);

        final String itemId = target;
        List<MvcResult> results = fire(THREADS, () ->
                complete(session, itemId, "distinct-key-" + UUID.randomUUID(),
                        "{\"solutionContent\":\"race answer\"}"));

        int okCount = 0;
        int conflictCount = 0;
        for (MvcResult result : results) {
            int status = result.getResponse().getStatus();
            if (status == 200) {
                okCount++;
                assertEquals(1, json(result).get("data").get("completedCount").asInt());
            } else {
                assertEquals(409, status);
                assertEquals("ITEM_ALREADY_COMPLETED", json(result).get("error").get("code").asText());
                conflictCount++;
            }
        }
        assertEquals(1, okCount, "恰好一个请求成功完成");
        assertEquals(THREADS - 1, conflictCount, "其余请求必须以 ITEM_ALREADY_COMPLETED 失败");

        // 最终状态稳定：单次完成效果 + 打卡计数正确。
        assertEquals(1, count(
                "SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed'", task));
        assertEquals(1, count("SELECT completed_count FROM checkins WHERE task_id=? AND checkin_date=?",
                task, LocalDate.now(ZONE)));
        assertEquals(1, count("SELECT COUNT(*) FROM idempotency_keys WHERE task_id=? AND operation='complete'", task),
                "失败事务的幂等键必须随事务回滚");
    }

    // ------------------------------------------------------------------
    // 场景 3：同键不同请求体 -> 409 IDEMPOTENCY_CONFLICT
    // ------------------------------------------------------------------
    @Test
    void sameKeyWithDifferentBodyIsRejectedAsConflict() throws Exception {
        String session = login("s4cc-conflict");
        String task = task(session, "s4cc-conflict-" + UUID.randomUUID(), today(), 2);
        String target = item(session, task, "conflict-item");
        item(session, task, "filler-item");
        activate(session, task);

        String key = "conflict-key";
        mockMvc.perform(complete(session, target, key, "{\"solutionContent\":\"first body\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(complete(session, target, key, "{\"solutionContent\":\"different body\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

        // 首次结果不被第二次请求改写。
        assertEquals("first body", scalar(
                "SELECT solution_text FROM learning_items WHERE id=?", target));
    }

    // ------------------------------------------------------------------
    // 场景 4：跨用户访问一律 404，且属主数据零变化
    // ------------------------------------------------------------------
    @Test
    void crossUserAccessReturns404AndLeavesOwnerDataUntouched() throws Exception {
        String owner = login("s4cc-owner");
        String task = task(owner, "s4cc-owner-" + UUID.randomUUID(), today(), 2);
        String target = item(owner, task, "owner-item");
        item(owner, task, "filler-item");
        activate(owner, task);

        String intruder = login("s4cc-intruder");
        mockMvc.perform(complete(intruder, target, "intruder-complete", "{\"solutionContent\":\"stolen\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
        mockMvc.perform(reopen(intruder, target, "intruder-reopen"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));

        // 属主数据零变化：条目未动、无打卡事实、无幂等键残留。
        assertEquals(0, count(
                "SELECT COUNT(*) FROM learning_items WHERE id=? AND (status<>'pending' OR solution_text IS NOT NULL "
                        + "OR completed_at IS NOT NULL)", target),
                "属主条目必须保持 pending 且无题解");
        assertEquals(0, count("SELECT COUNT(*) FROM checkins WHERE task_id=?", task));
        assertEquals(0, count("SELECT COUNT(*) FROM idempotency_keys WHERE task_id=?", task));
    }

    // ------------------------------------------------------------------
    // 场景 5：缺失 Idempotency-Key -> 400 VALIDATION_ERROR
    // ------------------------------------------------------------------
    @Test
    void missingIdempotencyKeyHeaderIsRejectedWith400() throws Exception {
        String session = login("s4cc-nokey");
        String task = task(session, "s4cc-nokey-" + UUID.randomUUID(), today(), 2);
        String target = item(session, task, "nokey-item");
        item(session, task, "filler-item");
        activate(session, task);

        mockMvc.perform(post("/api/v1/items/{id}/complete", target).with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"solutionContent\":\"answer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/items/{id}/reopen", target).with(csrf()).cookie(cookie(session)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertEquals(0, count("SELECT COUNT(*) FROM idempotency_keys WHERE task_id=?", task));
        assertEquals("pending", scalar("SELECT status FROM learning_items WHERE id=?", target));
    }

    // ------------------------------------------------------------------
    // 场景 6：非今日条目（startDate=明天）-> 422 ITEM_NOT_TODAY
    // ------------------------------------------------------------------
    @Test
    void completingNonTodayItemIsRejected() throws Exception {
        String session = login("s4cc-future");
        String task = task(session, "s4cc-future-" + UUID.randomUUID(), today().plusDays(1), 2);
        String target = item(session, task, "future-item");
        activate(session, task);

        mockMvc.perform(complete(session, target, "future-key", "{\"solutionContent\":\"early attempt\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_TODAY"));

        assertEquals("pending", scalar("SELECT status FROM learning_items WHERE id=?", target));
        assertNull(scalarNullable("SELECT solution_text FROM learning_items WHERE id=?", target),
                "失败的提前完成不得写入题解");
        assertEquals(0, count("SELECT COUNT(*) FROM checkins WHERE task_id=?", task));
    }

    // ------------------------------------------------------------------
    // 场景 7：撤销幂等回放 —— 同键重复 reopen 返回首次响应且不二次扣减
    // ------------------------------------------------------------------
    @Test
    void reopenReplayReturnsFirstResponseWithoutDoubleDeduction() throws Exception {
        String session = login("s4cc-reopen");
        String task = task(session, "s4cc-reopen-" + UUID.randomUUID(), today(), 1);
        String target = item(session, task, "reopened-item");
        activate(session, task);

        mockMvc.perform(complete(session, target, "reopen-precursor", "{\"solutionContent\":\"kept solution\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkinStatus").value("completed"));

        MvcResult first = mockMvc.perform(reopen(session, target, "replay-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.status").value("pending"))
                .andExpect(jsonPath("$.data.item.solutionText").value("kept solution"))
                .andExpect(jsonPath("$.data.completedCount").value(0))
                .andExpect(jsonPath("$.data.checkinStatus").value("partial"))
                .andReturn();

        // 同键重复 reopen：回放首次响应，而非再次执行撤销逻辑。
        MvcResult second = mockMvc.perform(reopen(session, target, "replay-key"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondData = json(second).get("data");
        assertEquals(json(first).get("data"), secondData, "重复 reopen 的 data 必须与首次响应一致");
        assertEquals("pending", secondData.get("item").get("status").asText());

        // 计数不被二次扣减：completed_count 保持 0，条目保持 pending。
        assertEquals("pending", scalar("SELECT status FROM learning_items WHERE id=?", target));
        assertEquals(1, count("SELECT COUNT(*) FROM idempotency_keys WHERE task_id=? AND operation='reopen'", task));
        LocalDate checkinDate = LocalDate.now(ZONE);
        assertEquals(0, count("SELECT completed_count FROM checkins WHERE task_id=? AND checkin_date=?",
                task, checkinDate));
        assertEquals("partial", scalar("SELECT status FROM checkins WHERE task_id=? AND checkin_date=?",
                task, checkinDate));
    }

    // ==================================================================
    // 并发发射器与辅助方法
    // ==================================================================

    /** n 个工作线程经 CountDownLatch 同时发射各自构建的请求；返回全部结果。 */
    private List<MvcResult> fire(int n, RequestBuilderSupplier supplier) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("启动闩锁超时");
                    return mockMvc.perform(supplier.get()).andReturn();
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "工作线程未能全部就绪");
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(120, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface RequestBuilderSupplier {
        MockHttpServletRequestBuilder get();
    }

    private MockHttpServletRequestBuilder complete(String session, String itemId, String key, String body) {
        return post("/api/v1/items/{id}/complete", itemId).with(csrf()).cookie(cookie(session))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder reopen(String session, String itemId, String key) {
        return post("/api/v1/items/{id}/reopen", itemId).with(csrf()).cookie(cookie(session))
                .header("Idempotency-Key", key);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String scalar(String sql, Object... args) {
        String value = scalarNullable(sql, args);
        assertNotNull(value, "查询应返回非空标量: " + sql);
        return value;
    }

    private String scalarNullable(String sql, Object... args) {
        return jdbc.query(sql, rs -> rs.next() ? rs.getString(1) : null, args);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 与 ItemService 一致的“任务时区今天”。 */
    private LocalDate today() {
        return LocalDate.now(ZONE);
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

    private String task(String session, String name, LocalDate startDate, int target) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks").with(csrf()).cookie(cookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"checklist\",\"startDate\":\"" + startDate
                                + "\",\"scheduleType\":\"daily\",\"timezone\":\"Asia/Shanghai\",\"dailyTargetCount\":"
                                + target + "}"))
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

    private jakarta.servlet.http.Cookie cookie(String value) {
        return new jakarta.servlet.http.Cookie(SessionService.COOKIE_NAME, value);
    }
}
