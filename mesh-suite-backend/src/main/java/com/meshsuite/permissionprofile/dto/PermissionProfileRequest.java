package com.meshsuite.permissionprofile.dto;

import com.meshsuite.user.dto.PermissionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PermissionProfileRequest(
        @NotBlank String name,
        String description,
        @NotNull List<@Valid PermissionDto> grants) {
}
