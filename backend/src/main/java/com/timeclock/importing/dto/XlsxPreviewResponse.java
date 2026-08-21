package com.timeclock.importing.dto;

import java.util.List;

public record XlsxPreviewResponse(int totalRows, int validRows, List<XlsxErrorRow> errorRows,
                                   List<XlsxCandidate> candidates) {
    public record XlsxErrorRow(int rowNumber, String reason) {}
}
