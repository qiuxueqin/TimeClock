package com.timeclock.item.dto;

import java.util.List;

public record PastePreviewResponse(int totalLines, int validLines, List<ErrorLine> errorLines,
                                   List<PasteConfirmRequest.PasteCandidate> candidates) {
    public record ErrorLine(int lineNumber, String reason) {}
}
