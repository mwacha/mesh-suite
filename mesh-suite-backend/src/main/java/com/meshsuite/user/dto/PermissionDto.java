package com.meshsuite.user.dto;

import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import jakarta.validation.constraints.NotNull;

public record PermissionDto(@NotNull Module module, @NotNull Action action) {
}
