package com.timeclock.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record LoginResponse(UserView user, @JsonIgnore String rawToken) {
}
