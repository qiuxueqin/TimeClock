package com.timeclock.task.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ScheduleType {
    DAILY("daily");

    private final String value;

    ScheduleType(String value) { this.value = value; }

    @JsonValue
    public String value() { return value; }

    @JsonCreator
    public static ScheduleType fromValue(String value) {
        for (ScheduleType type : values()) if (type.value.equals(value)) return type;
        throw new IllegalArgumentException("不支持的计划频率");
    }
}
