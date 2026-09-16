package com.meshsuite.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmSignupRequest(@NotBlank String token) {
}
