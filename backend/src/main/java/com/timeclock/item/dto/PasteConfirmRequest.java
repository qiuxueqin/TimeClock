package com.timeclock.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PasteConfirmRequest(@NotEmpty List<@Valid PasteCandidate> candidates) {
    public record PasteCandidate(String title, String content, String analysis, String externalUrl) {}
}
