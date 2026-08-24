package com.timeclock.importing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record XlsxConfirmResponse(int createdCount, int skippedCount) {
    @JsonProperty("created") public int created() { return createdCount; }
    @JsonProperty("skipped") public int skipped() { return skippedCount; }
}
