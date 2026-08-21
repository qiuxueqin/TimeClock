package com.timeclock.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.timeclock.auth.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Durable request idempotency for transactional write confirmations. */
@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public <T> java.util.List<T> beginList(String userId, String taskId, String operation, String key,
                                            Object request, Class<T> elementType) {
        return begin(userId, taskId, operation, key, request,
                mapper.getTypeFactory().constructCollectionType(java.util.List.class, elementType));
    }

    /** Returns the saved response, or null when this transaction owns a new key. */
    public <T> T begin(String userId, String taskId, String operation, String key,
                       Object request, Class<T> responseType) {
        return begin(userId, taskId, operation, key, request, mapper.constructType(responseType));
    }

    public <T> T begin(String userId, String taskId, String operation, String key,
                       Object request, JavaType responseType) {
        if (key == null || key.isBlank() || key.length() > 128)
            throw new BusinessException("VALIDATION_ERROR", "Idempotency-Key 无效", 422);
        String hash = hash(request);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            jdbc.update("INSERT INTO idempotency_keys (id,user_id,task_id,operation,request_key,request_hash,response_json,created_at,expires_at) VALUES (?,?,?,?,?,?,NULL,?,?)",
                    UUID.randomUUID().toString(), userId, taskId, operation, key, hash, now, now.plus(30, ChronoUnit.DAYS));
            return null;
        } catch (DuplicateKeyException ignored) {
            Record row = jdbc.queryForObject("SELECT request_hash,response_json FROM idempotency_keys WHERE user_id=? AND task_id=? AND operation=? AND request_key=? FOR UPDATE",
                    (rs, n) -> new Record(rs.getString(1), rs.getString(2)), userId, taskId, operation, key);
            if (!hash.equals(row.hash()))
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "Idempotency-Key 已用于不同请求", 409);
            if (row.response() == null) return null;
            try { return mapper.readValue(row.response(), responseType); }
            catch (JsonProcessingException e) { throw new IllegalStateException("幂等响应快照损坏", e); }
        }
    }

    public void complete(String userId, String taskId, String operation, String key, Object response) {
        try {
            jdbc.update("UPDATE idempotency_keys SET response_json=? WHERE user_id=? AND task_id=? AND operation=? AND request_key=?",
                    mapper.writeValueAsString(response), userId, taskId, operation, key);
        } catch (JsonProcessingException e) { throw new IllegalStateException("无法保存幂等响应", e); }
    }

    private String hash(Object request) {
        try {
            byte[] bytes = mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) { throw new IllegalStateException("无法计算请求哈希", e); }
    }
    private record Record(String hash, String response) {}
}
