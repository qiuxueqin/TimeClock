#!/usr/bin/env python3
"""S0-API-01 / S0-OPS-02 OpenAPI 契约校验脚本。

TEST-S0-API-01-01：检查语法、引用、必填字段、重复 operation；
写接口声明 CSRF；防重复接口声明 Idempotency-Key。
供 CI（contract job）与本地复现使用。退出码 0=通过，1=失败。
"""
import sys

import yaml


def collect_refs(obj, out):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "$ref" and isinstance(v, str):
                out.add(v)
            else:
                collect_refs(v, out)
    elif isinstance(obj, list):
        for x in obj:
            collect_refs(x, out)


def main() -> int:
    path = "contracts/openapi/openapi-v1.0.yaml"
    doc = yaml.safe_load(open(path, encoding="utf-8"))
    errors = []

    schemas = doc["components"].get("schemas", {})
    responses = doc["components"].get("responses", {})
    paths = doc.get("paths", {})

    # 1. 所有 $ref 可解析（无孤立模型）
    refs = set()
    collect_refs(doc, refs)
    for r in sorted(refs):
        if not r.startswith("#/"):
            errors.append(f"非本地引用: {r}")
            continue
        node = doc
        for p in r.lstrip("#/").split("/"):
            if isinstance(node, dict) and p in node:
                node = node[p]
            else:
                errors.append(f"未解析引用: {r}")
                break

    # 2. 写接口（post/put/patch/delete）必须声明 CSRF
    wm = {"post", "put", "patch", "delete"}
    for p, ops in paths.items():
        for m, op in ops.items():
            if m not in wm:
                continue
            sec = op.get("security", [])
            if not any("csrf" in s for s in sec):
                errors.append(f"写接口缺 CSRF: {m.upper()} {p}")

    # 3. 防重复接口必须声明 Idempotency-Key
    idem = [
        ("/items/{itemId}/complete", "post"),
        ("/items/{itemId}/reopen", "post"),
        ("/tasks/{taskId}/checkins/{date}/makeup", "post"),
        ("/tasks/{taskId}/imports/xlsx/confirm", "post"),
        ("/tasks/{taskId}/items/paste-confirm", "post"),
    ]
    for p, m in idem:
        op = paths.get(p, {}).get(m)
        if not op:
            errors.append(f"幂等操作不存在: {m.upper()} {p}")
            continue
        names = [prm.get("name") for prm in op.get("parameters", [])]
        if "Idempotency-Key" not in names:
            errors.append(f"幂等操作缺 Idempotency-Key: {m.upper()} {p}")

    # 4. required 字段必须存在于 properties
    for name, sch in schemas.items():
        if isinstance(sch, dict) and sch.get("type") == "object" and "required" in sch:
            props = sch.get("properties", {})
            for req in sch["required"]:
                if req not in props:
                    errors.append(f"必填字段缺属性 {name}.{req}")

    # 5. 响应 $ref 可解析
    resp_refs = set()
    for p, ops in paths.items():
        for m, op in ops.items():
            for _c, r in op.get("responses", {}).items():
                if isinstance(r, dict) and r.get("$ref"):
                    resp_refs.add(r["$ref"])
    for r in resp_refs:
        if r.split("/")[-1] not in responses:
            errors.append(f"未解析响应引用: {r}")

    # 6. S2 任务契约冻结断言（TEST-S2-API-01-01）
    task_paths = {p: paths.get(p) for p in (
        "/tasks", "/tasks/{taskId}", "/tasks/{taskId}/activate")}
    for p, op in task_paths.items():
        if not op:
            errors.append(f"S2 任务路径缺失: {p}")
    for forbidden in ("/tasks/{taskId}/enable", "/tasks/{taskId}/pause",
                      "/tasks/{taskId}/resume", "/tasks/{taskId}/archive"):
        if forbidden in paths:
            errors.append(f"S2 任务路径超出精简范围: {forbidden}")

    task_type = schemas.get("TaskType", {}).get("enum")
    if task_type != ["checklist"]:
        errors.append(f"TaskType 必须仅为 checklist，实际为 {task_type}")
    task_status = schemas.get("TaskStatus", {}).get("enum")
    if task_status != ["draft", "active"]:
        errors.append(f"TaskStatus 必须为 draft/active，实际为 {task_status}")
    schedule_type = schemas.get("ScheduleType", {}).get("enum")
    if schedule_type != ["daily"]:
        errors.append(f"ScheduleType 必须仅为 daily，实际为 {schedule_type}")

    create = schemas.get("TaskCreateRequest", {})
    create_props = create.get("properties", {})
    for field in ("name", "type", "scheduleType", "startDate", "timezone", "dailyTargetCount"):
        if field not in create.get("required", []) or field not in create_props:
            errors.append(f"TaskCreateRequest 缺少必填字段: {field}")
    if create_props.get("name", {}).get("minLength") != 1 or create_props.get("name", {}).get("maxLength") != 50:
        errors.append("TaskCreateRequest.name 必须限制为 1-50")
    if create_props.get("description", {}).get("maxLength") != 500:
        errors.append("TaskCreateRequest.description 必须限制为最多 500")
    if create_props.get("dailyTargetCount", {}).get("minimum") != 1:
        errors.append("TaskCreateRequest.dailyTargetCount 必须至少为 1")

    update = schemas.get("TaskUpdateRequest", {})
    if "version" in update.get("properties", {}) or "If-Match-Version" in str(task_paths.get("/tasks/{taskId}", {}).get("patch", {})):
        errors.append("S2 任务 PATCH 不得声明 version/If-Match-Version")
    activate = paths.get("/tasks/{taskId}/activate", {}).get("post", {})
    if "csrf" not in {k for sec in activate.get("security", []) for k in sec}:
        errors.append("任务 activate 缺 CSRF")
    delete = paths.get("/tasks/{taskId}", {}).get("delete", {})
    if "物理删除" not in delete.get("description", ""):
        errors.append("任务 DELETE 必须明确物理删除")

    # 7. S3 精简条目与同步 xlsx 导入契约断言
    item_status = schemas.get("ItemStatus", {}).get("enum")
    if item_status != ["pending", "completed"]:
        errors.append(f"S3 ItemStatus 必须为 pending/completed，实际为 {item_status}")
    item_path = paths.get("/items/{itemId}", {})
    if "delete" in item_path:
        errors.append("S3 条目不得声明软归档 DELETE")
    item_patch = item_path.get("patch", {})
    if any(p.get("name") == "If-Match-Version" for p in item_patch.get("parameters", [])):
        errors.append("S3 条目 PATCH 不得声明 If-Match-Version")
    if "version" in schemas.get("ItemUpdateRequest", {}).get("properties", {}):
        errors.append("S3 ItemUpdateRequest 不得声明 version")

    preview_path = paths.get("/tasks/{taskId}/imports/xlsx/preview", {})
    confirm_path = paths.get("/tasks/{taskId}/imports/xlsx/confirm", {})
    for path, method in (("/tasks/{taskId}/imports/xlsx/preview", "post"),
                         ("/tasks/{taskId}/imports/xlsx/confirm", "post")):
        if not paths.get(path, {}).get(method):
            errors.append(f"S3 xlsx 路径缺失: {method.upper()} {path}")
    preview_content = preview_path.get("post", {}).get("requestBody", {}).get("content", {})
    if "multipart/form-data" not in preview_content:
        errors.append("xlsx preview 必须使用 multipart/form-data")
    if "file" not in schemas.get("XlsxPreviewRequest", {}).get("required", []):
        errors.append("xlsx preview 必须要求 file")
    confirm_op = confirm_path.get("post", {})
    confirm_names = [p.get("name") for p in confirm_op.get("parameters", [])]
    if "Idempotency-Key" not in confirm_names:
        errors.append("xlsx confirm 缺 Idempotency-Key")
    for forbidden in ("/imports/{batchId}", "/imports/{batchId}/candidates",
                      "/imports/{batchId}/candidates/stats",
                      "/imports/{batchId}/candidates/{candidateId}",
                      "/imports/{batchId}/candidates/{candidateId}/split",
                      "/imports/{batchId}/candidates/{candidateId}/merge",
                      "/imports/{batchId}/candidates/{candidateId}/action",
                      "/imports/{batchId}/confirm"):
        if forbidden in paths:
            errors.append(f"S3 不得保留异步导入路径: {forbidden}")
    for forbidden_schema in ("ImportBatchStatus", "ImportBatchView", "ImportCandidateView",
                             "ImportCandidatePage", "ConfirmImportRequest", "ConfirmImportResponse"):
        if forbidden_schema in schemas:
            errors.append(f"S3 不得保留异步导入 schema: {forbidden_schema}")

    # 8. S5 今日总览契约断言（TEST-S5-API-01-01）
    dash = paths.get("/dashboard/today", {})
    if not dash.get("get"):
        errors.append("S5 /dashboard/today 缺失 GET")
    else:
        sec = {k for s in dash["get"].get("security", []) for k in s}
        if "sessionCookie" not in sec:
            errors.append("S5 /dashboard/today 必须声明 sessionCookie")
        if "csrf" in sec:
            errors.append("S5 /dashboard/today 为只读接口不得要求 CSRF")
    dash_status = schemas.get("DashboardStatus", {}).get("enum")
    if dash_status != ["notStarted", "inProgress", "completed", "noPlan"]:
        errors.append(f"S5 DashboardStatus 必须为 notStarted/inProgress/completed/noPlan，实际为 {dash_status}")
    today_task = schemas.get("TodayTask", {})
    tt_props = today_task.get("properties", {})
    for field in ("task", "status", "completedCount", "plannedCount", "currentStreak"):
        if field not in today_task.get("required", []) or field not in tt_props:
            errors.append(f"S5 TodayTask 缺少必填字段: {field}")
    for forbidden in ("actualValue", "reminderText"):
        if forbidden in tt_props:
            errors.append(f"S5 TodayTask 不得保留精简范围外字段: {forbidden}")
    dash_resp = schemas.get("DashboardTodayResponse", {})
    for field in ("date", "todayCount", "completedCount", "pendingCount",
                  "completionRate", "tasks", "currentStreak", "longestStreak"):
        if field not in dash_resp.get("required", []):
            errors.append(f"S5 DashboardTodayResponse 缺少必填字段: {field}")

    # 9. S6 日历/统计/补打契约断言（TEST-S6-API-01-01）
    calendar_op = paths.get("/calendar", {}).get("get", {})
    if not calendar_op:
        errors.append("S6 /calendar 缺失 GET")
    else:
        sec = {k for s in calendar_op.get("security", []) for k in s}
        if "sessionCookie" not in sec or "csrf" in sec:
            errors.append("S6 /calendar 必须为只读 sessionCookie 接口")
        month_param = [p for p in calendar_op.get("parameters", []) if p.get("name") == "month"]
        if not month_param or not month_param[0].get("required"):
            errors.append("S6 /calendar 必须要求 month 查询参数")

    makeup_op = paths.get("/tasks/{taskId}/checkins/{date}/makeup", {}).get("post", {})
    if not makeup_op:
        errors.append("S6 makeup 路径缺失")
    else:
        names = [p.get("name") for p in makeup_op.get("parameters", [])]
        if "Idempotency-Key" not in names:
            errors.append("S6 makeup 缺 Idempotency-Key")
        if "不可编辑" not in makeup_op.get("description", "") and "不可逆" not in makeup_op.get("description", ""):
            errors.append("S6 makeup 描述必须声明不可逆（DEC-15）")

    checkin_get = paths.get("/tasks/{taskId}/checkins/{date}", {}).get("get", {})
    if not checkin_get:
        errors.append("S6 日期详情 GET 缺失")
    stats_path = paths.get("/tasks/{taskId}/stats", {})
    if not stats_path.get("get"):
        errors.append("S6 /tasks/{{taskId}}/stats 缺失 GET")
    elif stats_path["get"].get("responses", {}).get("404") is None:
        errors.append("S6 stats 必须声明 404 越权响应")

    checkin_view = schemas.get("CheckinView", {})
    cv_props = checkin_view.get("properties", {})
    if set(cv_props) & {"actualValue", "note", "version"}:
        errors.append(f"S6 CheckinView 不得保留习惯型字段: {sorted(set(cv_props) & {'actualValue', 'note', 'version'})}")
    cv_status = cv_props.get("status", {}).get("enum")
    if cv_status != ["completed", "partial", "missed", "makeup", "noPlan"]:
        errors.append(f"S6 CheckinView.status 枚举错误: {cv_status}")

    makeup_req = schemas.get("MakeupRequest", {})
    mr_props = makeup_req.get("properties", {})
    if "reason" not in makeup_req.get("required", []):
        errors.append("S6 MakeupRequest.reason 必填")
    if set(mr_props) & {"actualValue", "note", "idempotencyKey"}:
        errors.append("S6 MakeupRequest 不得保留习惯型/idempotencyKey 体字段（幂等键在请求头）")
    if mr_props.get("reason", {}).get("maxLength") != 500:
        errors.append("S6 MakeupRequest.reason 必须限制最多 500 字符")

    day_status = schemas.get("CalendarDay", {}).get("properties", {}).get("status", {}).get("enum")
    if day_status != ["completed", "partial", "missed", "makeup", "noPlan"]:
        errors.append(f"S6 CalendarDay.status 枚举错误: {day_status}")

    task_stats = schemas.get("TaskStats", {})
    ts_props = task_stats.get("properties", {})
    if set(ts_props) & {"habitTotalCheckinDays", "habitMonthCompletedDays", "habitMonthCompletionRate"}:
        errors.append("S6 TaskStats 不得保留习惯型统计字段")
    for field in ("task", "currentStreak", "longestStreak", "completedItemCount",
                  "totalItemCount", "remainingItemCount"):
        if field not in task_stats.get("required", []):
            errors.append(f"S6 TaskStats 缺少必填字段: {field}")

    for forbidden_schema in ("HabitCheckinRequest", "HabitCheckinEditRequest",
                             "SubmissionStatus", "ExportFormat", "FilePurpose"):
        if forbidden_schema in schemas:
            errors.append(f"S6 精简清理后不得残留 schema: {forbidden_schema}")
    if "/export" in paths:
        errors.append("S6 精简范围外路径 /export 不得存在")

    if errors:
        for e in errors:
            print(f"[ERROR] {e}")
        print(f"\n契约校验失败：{len(errors)} 个问题")
        return 1
    print(f"契约校验通过：{len(paths)} 个路径，{len(schemas)} 个 schema，所有引用/安全要求就绪。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
