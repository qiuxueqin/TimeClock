package com.timeclock.item.dto;

public record CompletionResponse(
        ItemView item,
        int plannedCount,
        int completedCount,
        int taskCompletedCount,
        String checkinStatus) {}
