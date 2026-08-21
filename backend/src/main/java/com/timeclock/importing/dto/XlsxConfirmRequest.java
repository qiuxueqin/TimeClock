package com.timeclock.importing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record XlsxConfirmRequest(@NotEmpty List<@Valid XlsxConfirmCandidate> candidates) {
    public record XlsxConfirmCandidate(String title, String content, String analysis,
                                       String link, Integer order, String action) {}
}
