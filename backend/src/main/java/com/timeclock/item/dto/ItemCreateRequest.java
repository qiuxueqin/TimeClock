package com.timeclock.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ItemCreateRequest(@NotBlank @Size(max = 255) String title,
                                @Size(max = 10000) String content,
                                @Size(max = 10000) String analysis,
                                String externalUrl) {}
