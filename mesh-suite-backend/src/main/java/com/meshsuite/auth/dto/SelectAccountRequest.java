package com.meshsuite.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SelectAccountRequest(@NotNull UUID tenantId, boolean manterConectado) {
}
