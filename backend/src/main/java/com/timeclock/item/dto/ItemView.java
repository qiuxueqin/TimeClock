package com.timeclock.item.dto;

import java.time.Instant;

public record ItemView(String id, String taskId, String title, String content, String analysis,
                       String externalUrl, int sortOrder, String status, String solutionText,
                       Instant completedAt) {}
