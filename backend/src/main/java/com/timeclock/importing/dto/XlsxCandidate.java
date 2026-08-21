package com.timeclock.importing.dto;

public record XlsxCandidate(String title, String content, String analysis, String link,
                             Integer order, boolean duplicate) {}
