package com.timeclock.task.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskType {
    CHECKLIST("checklist");

    private final String value;

    TaskType(String value) { this.value = value; }

    @JsonValue
    public String value() { return value; }

    @JsonCreator
    public static TaskType fromValue(String value) {
        for (TaskType type : values()) if (type.value.equals(value)) return type;
        throw new IllegalArgumentException("不支持的任务类型");
    }
}
