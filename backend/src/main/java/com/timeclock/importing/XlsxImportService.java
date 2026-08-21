package com.timeclock.importing;

import com.timeclock.auth.BusinessException;
import com.timeclock.common.IdempotencyService;
import com.timeclock.importing.dto.*;
import com.timeclock.item.dto.ItemView;
import com.timeclock.task.TaskService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.poi.ss.usermodel.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class XlsxImportService {
    private static final List<String> HEADERS = List.of("title", "content", "analysis", "link", "order");
    private final JdbcTemplate jdbc;
    private final TaskService tasks;
    private final IdempotencyService idempotency;

    public XlsxImportService(JdbcTemplate jdbc, TaskService tasks, IdempotencyService idempotency) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.idempotency = idempotency;
    }

    public XlsxPreviewResponse preview(String userId, String taskId, MultipartFile file) {
        tasks.get(userId, taskId);
        validateFile(file);
        Set<String> existing = existingTitles(taskId);
        List<XlsxPreviewResponse.XlsxErrorRow> errors = new ArrayList<>();
        List<XlsxCandidate> candidates = new ArrayList<>();
        int total = 0;
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) throw invalid("xlsx 没有工作表");
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null || !headersMatch(header, formatter)) throw invalid("首行为固定列 title、content、analysis、link、order");
            for (int rn = sheet.getFirstRowNum() + 1; rn <= sheet.getLastRowNum(); rn++) {
                Row row = sheet.getRow(rn);
                if (isBlank(row, formatter)) continue;
                total++;
                try {
                    String title = text(row, 0, formatter);
                    if (title.isBlank() || title.length() > 255) throw new IllegalArgumentException("标题无效");
                    String content = nullableText(row, 1, formatter);
                    String analysis = nullableText(row, 2, formatter);
                    String link = nullableText(row, 3, formatter);
                    Integer order = number(row, 4, formatter);
                    if (order != null && order < 1) throw new IllegalArgumentException("order 必须为正整数");
                    candidates.add(new XlsxCandidate(title.trim(), content, analysis, link, order,
                            existing.contains(normalize(title))));
                } catch (IllegalArgumentException ex) {
                    errors.add(new XlsxPreviewResponse.XlsxErrorRow(rn + 1, ex.getMessage()));
                }
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessException b) throw b;
            throw invalid("xlsx 文件无法解析");
        }
        return new XlsxPreviewResponse(total, candidates.size(), errors, candidates);
    }

    @Transactional
    public XlsxConfirmResponse confirm(String userId, String taskId, String key, XlsxConfirmRequest request) {
        tasks.get(userId, taskId);
        XlsxConfirmResponse prior = idempotency.begin(userId, taskId, "xlsx-confirm", key, request, XlsxConfirmResponse.class);
        if (prior != null) return prior;
        if (request == null || request.candidates() == null || request.candidates().isEmpty()) throw invalid("候选条目不能为空");
        Set<String> seen = existingTitles(taskId);
        int next = nextOrder(taskId), created = 0, skipped = 0;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (XlsxConfirmRequest.XlsxConfirmCandidate candidate : request.candidates()) {
            validateCandidate(candidate);
            String normalized = normalize(candidate.title());
            boolean duplicate = !seen.add(normalized);
            if ("skip".equals(candidate.action()) || duplicate) { skipped++; continue; }
            jdbc.update("INSERT INTO learning_items (id,task_id,title,content,analysis,external_url,sort_order,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'pending',?,?)",
                    UUID.randomUUID().toString(), taskId, candidate.title().trim(), candidate.content(), candidate.analysis(),
                    candidate.link(), next++, now, now);
            created++;
        }
        XlsxConfirmResponse result = new XlsxConfirmResponse(created, skipped);
        idempotency.complete(userId, taskId, "xlsx-confirm", key, result);
        return result;
    }

    private Set<String> existingTitles(String taskId) {
        return new HashSet<>(jdbc.query("SELECT LOWER(TRIM(title)) FROM learning_items WHERE task_id=?",
                (rs, n) -> rs.getString(1), taskId));
    }
    private int nextOrder(String taskId) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM learning_items WHERE task_id=?", Integer.class, taskId);
        return n == null ? 1 : n;
    }
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw invalid("仅支持非空 .xlsx 文件");
    }
    private boolean headersMatch(Row row, DataFormatter f) {
        for (int i = 0; i < HEADERS.size(); i++) if (!HEADERS.get(i).equalsIgnoreCase(text(row, i, f).trim())) return false;
        return true;
    }
    private boolean isBlank(Row row, DataFormatter f) {
        if (row == null) return true;
        for (int i = 0; i < 5; i++) if (!text(row, i, f).isBlank()) return false;
        return true;
    }
    private String text(Row row, int col, DataFormatter f) { Cell c = row == null ? null : row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL); return c == null ? "" : f.formatCellValue(c).trim(); }
    private String nullableText(Row row, int col, DataFormatter f) { String s = text(row, col, f); return s.isBlank() ? null : s; }
    private Integer number(Row row, int col, DataFormatter f) {
        String s = text(row, col, f); if (s.isBlank()) return null;
        try { double d = Double.parseDouble(s); if (d != Math.rint(d)) throw new NumberFormatException(); return (int)d; }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("order 必须为整数"); }
    }
    private void validateCandidate(XlsxConfirmRequest.XlsxConfirmCandidate c) {
        if (c == null || c.title() == null || c.title().isBlank() || c.title().trim().length() > 255) throw invalid("标题无效");
        if (c.action() == null || (!c.action().equals("skip") && !c.action().equals("keep_new"))) throw invalid("action 无效");
        if (c.order() != null && c.order() < 1) throw invalid("order 必须为正整数");
    }
    private String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private BusinessException invalid(String m) { return new BusinessException("VALIDATION_ERROR", m, 422); }
}
