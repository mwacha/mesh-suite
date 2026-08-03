package com.meshsuite.user.dto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import jakarta.validation.constraints.NotNull;

public record PermissionDto(@NotNull Module module, @NotNull Action action) {
}
