package com.timeclock.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PastePreviewResponse(int totalLines, int validLines, List<ErrorLine> errorLines,
                                   List<PasteConfirmRequest.PasteCandidate> candidates) {
    @JsonProperty("errors") public List<ErrorLine> errors() { return errorLines; }
    public record ErrorLine(int lineNumber, String reason) {}
}
