package com.timeclock.task;

import com.timeclock.auth.BusinessException;
import com.timeclock.task.dto.ScheduleType;
import com.timeclock.task.dto.TaskCreateRequest;
import com.timeclock.task.dto.TaskPage;
import com.timeclock.task.dto.TaskType;
import com.timeclock.task.dto.TaskUpdateRequest;
import com.timeclock.task.dto.TaskView;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public TaskService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    TaskService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public TaskView create(String userId, TaskCreateRequest request) {
        validateConfig(request);
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        try {
            jdbcTemplate.update("INSERT INTO tasks (id,user_id,name,description,start_date,end_date,"
                            + "task_type,schedule_type,timezone,daily_target_count,status,created_at,updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?, 'draft', ?, ?)",
                    id, userId, request.name(), request.description(), request.startDate(), request.endDate(),
                    "checklist", "daily", request.timezone(), request.dailyTargetCount(), now, now);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("TASK_NAME_ALREADY_EXISTS", "同一用户下任务名称已存在", 409);
        }
        return findOwned(id, userId);
    }

    public TaskView get(String userId, String taskId) {
        TaskView task = findOwned(taskId, userId);
        if (task == null) throw notFound();
        return task;
    }

    public TaskPage list(String userId, String status, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw validation("分页参数无效");
        }
        if (status != null && !status.equals("draft") && !status.equals("active")) {
            throw validation("任务状态无效");
        }
        int offset = (page - 1) * pageSize;
        String filter = status == null ? "" : " AND status = ?";
        Object[] args = status == null
                ? new Object[] {userId, pageSize, offset}
                : new Object[] {userId, status, pageSize, offset};
        String sql = "SELECT id,name,description,task_type,status,start_date,end_date,schedule_type,"
                + "timezone,daily_target_count FROM tasks WHERE user_id=?" + filter
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        java.util.List<TaskView> items = jdbcTemplate.query(sql, (rs, rowNum) -> toView(row(rs)), args);
        String countSql = "SELECT COUNT(*) FROM tasks WHERE user_id=?" + filter;
        Object[] countArgs = status == null ? new Object[] {userId} : new Object[] {userId, status};
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countArgs);
        return new TaskPage(items, page, pageSize, total == null ? 0 : total);
    }

    @Transactional
    public TaskView update(String userId, String taskId, TaskUpdateRequest patch) {
        TaskView current = get(userId, taskId);
        if (patch == null || (patch.name() == null && patch.description() == null && patch.startDate() == null
                && patch.endDate() == null && patch.scheduleType() == null && patch.timezone() == null
                && patch.dailyTargetCount() == null)) {
            throw validation("至少需要一个更新字段");
        }
        String name = patch.name() == null ? current.name() : patch.name();
        String description = patch.description() == null ? current.description() : patch.description();
        LocalDate start = patch.startDate() == null ? current.startDate() : patch.startDate();
        LocalDate end = patch.endDate() == null ? current.endDate() : patch.endDate();
        ScheduleType schedule = patch.scheduleType() == null ? current.scheduleType() : patch.scheduleType();
        String timezone = patch.timezone() == null ? current.timezone() : patch.timezone();
        int target = patch.dailyTargetCount() == null ? current.dailyTargetCount() : patch.dailyTargetCount();
        validateUpdate(name, description, start, end, schedule, timezone, target);
        try {
            jdbcTemplate.update("UPDATE tasks SET name=?,description=?,start_date=?,end_date=?,schedule_type=?,"
                            + "timezone=?,daily_target_count=?,updated_at=? WHERE id=? AND user_id=?",
                    name, description, start, end, "daily", timezone, target,
                    Instant.now(clock).truncatedTo(ChronoUnit.MICROS), taskId, userId);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("TASK_NAME_ALREADY_EXISTS", "同一用户下任务名称已存在", 409);
        }
        return get(userId, taskId);
    }

    @Transactional
    public void delete(String userId, String taskId) {
        int rows = jdbcTemplate.update("DELETE FROM tasks WHERE id=? AND user_id=?", taskId, userId);
        if (rows != 1) throw notFound();
    }

    private TaskRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskRow(rs.getString("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("task_type"), rs.getString("status"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getString("schedule_type"),
                rs.getString("timezone"), rs.getInt("daily_target_count"));
    }

    private void validateUpdate(String name, String description, LocalDate start, LocalDate end,
                                 ScheduleType schedule, String timezone, int target) {
        if (name == null || name.isBlank() || name.length() > 50) throw validation("任务名称长度必须为 1-50");
        if (description != null && description.length() > 500) throw validation("任务描述最多 500 个字符");
        if (start == null || (end != null && end.isBefore(start))) throw validation("日期范围无效");
        if (schedule != ScheduleType.DAILY || target < 1) throw validation("任务配置无效");
        try { ZoneId.of(timezone); } catch (DateTimeException ex) { throw validation("timezone 必须是有效 IANA 时区"); }
    }

    @Transactional
    public TaskView activate(String userId, String taskId) {
        TaskRow task = jdbcTemplate.query("SELECT id,name,description,task_type,status,start_date,end_date,"
                        + "schedule_type,timezone,daily_target_count FROM tasks WHERE id=? AND user_id=? FOR UPDATE",
                rs -> rs.next() ? row(rs) : null, taskId, userId);
        if (task == null) throw notFound();
        if (!"draft".equals(task.status())) {
            throw new BusinessException("TASK_STATE_CONFLICT", "任务已经启用，不能重复启用", 409);
        }
        throw new BusinessException("TASK_ACTIVATION_REQUIRES_ITEM", "任务至少需要一个已确认条目才能启用", 422);
    }


    private TaskView findOwned(String id, String userId) {
        return jdbcTemplate.query("SELECT id,name,description,task_type,status,start_date,end_date,schedule_type,"
                        + "timezone,daily_target_count FROM tasks WHERE id=? AND user_id=?",
                rs -> rs.next() ? toView(new TaskRow(rs.getString("id"), rs.getString("name"),
                        rs.getString("description"), rs.getString("task_type"), rs.getString("status"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                        rs.getString("schedule_type"), rs.getString("timezone"), rs.getInt("daily_target_count"))) : null,
                id, userId);
    }

    private TaskView toView(TaskRow row) {
        TaskScheduleCalculator calculator = new TaskScheduleCalculator(clock);
        TaskScheduleCalculator.Task scheduleTask = new TaskScheduleCalculator.Task(row.status(), row.startDate(),
                row.endDate(), row.scheduleType(), ZoneId.of(row.timezone()), row.dailyTargetCount());
        boolean ended = "active".equals(row.status()) && row.endDate() != null
                && row.endDate().isBefore(calculator.today(scheduleTask));
        return new TaskView(row.id(), row.name(), row.description(), TaskType.CHECKLIST, row.status(), row.startDate(),
                row.endDate(), ScheduleType.DAILY, row.timezone(), row.dailyTargetCount(), ended, 0, 0);
    }

    private void validateConfig(TaskCreateRequest request) {
        if (request.type() != TaskType.CHECKLIST) throw validation("任务类型必须为 checklist");
        if (request.scheduleType() != ScheduleType.DAILY) throw validation("计划频率必须为 daily");
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw validation("结束日期不能早于开始日期");
        }
        try {
            ZoneId.of(request.timezone());
        } catch (DateTimeException ex) {
            throw validation("timezone 必须是有效 IANA 时区");
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_ERROR", message, 422);
    }

    private BusinessException notFound() {
        return new BusinessException("TASK_NOT_FOUND", "任务不存在", 404);
    }

    private record TaskRow(String id, String name, String description, String type, String status,
                           LocalDate startDate, LocalDate endDate, String scheduleType,
                           String timezone, int dailyTargetCount) {}
}
