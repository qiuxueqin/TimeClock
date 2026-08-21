package com.timeclock.item.dto;

import jakarta.validation.constraints.Size;

public record ItemUpdateRequest(@Size(min = 1, max = 255) String title,
                                @Size(max = 10000) String content,
                                @Size(max = 10000) String analysis,
                                String externalUrl) {}
