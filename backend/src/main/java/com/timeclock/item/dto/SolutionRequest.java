package com.timeclock.item.dto;

import jakarta.validation.constraints.NotNull;

public record SolutionRequest(@NotNull String solutionContent) {}
