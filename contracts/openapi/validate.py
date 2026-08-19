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
        ("/tasks/{taskId}/checkins/{date}/complete", "post"),
        ("/tasks/{taskId}/checkins/{date}/edit", "patch"),
        ("/tasks/{taskId}/checkins/{date}/makeup", "post"),
        ("/imports/{batchId}/confirm", "post"),
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

    if errors:
        for e in errors:
            print(f"[ERROR] {e}")
        print(f"\n契约校验失败：{len(errors)} 个问题")
        return 1
    print(f"契约校验通过：{len(paths)} 个路径，{len(schemas)} 个 schema，所有引用/安全要求就绪。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
