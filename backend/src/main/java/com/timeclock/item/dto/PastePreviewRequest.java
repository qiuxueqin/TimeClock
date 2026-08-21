package com.timeclock.item.dto;

import jakarta.validation.constraints.NotBlank;

public record PastePreviewRequest(@NotBlank String text) {}
