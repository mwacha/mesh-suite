package com.meshsuite.company.dto;

import jakarta.validation.constraints.NotNull;

public record CompanyStatusRequest(@NotNull Boolean active) {
}
