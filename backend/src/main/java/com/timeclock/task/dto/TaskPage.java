package com.timeclock.task.dto;

import java.util.List;

public record TaskPage(List<TaskView> items, int page, int pageSize, long total) {
}
