package com.meshsuite.user.dto;

import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotNull Role role,
        @NotNull Profile profile,
        boolean active,
        String password,
        String confirmPassword,
        List<@Valid PermissionDto> permissions) {
}
