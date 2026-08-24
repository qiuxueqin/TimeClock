package com.timeclock.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TodayItemsResponse(Object task, int plannedCount, int completedCount, List<TodayItem> items) {
    @JsonProperty("targetCount") public int targetCount() { return plannedCount; }
    public record TodayItem(ItemView item, boolean assigned, boolean belongsToToday) {}
}
