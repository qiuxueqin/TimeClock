package com.timeclock.item.dto;

import java.util.List;

public record TodayItemsResponse(Object task, int plannedCount, int completedCount, List<TodayItem> items) {
    public record TodayItem(ItemView item, boolean assigned, boolean belongsToToday) {}
}
