package com.meshsuite.user.dto;

import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotNull Role role,
        Profile profile,
        boolean active,
        String password,
        String confirmPassword,
        List<@Valid PermissionDto> permissions,
        UUID permissionProfileId) {
}
