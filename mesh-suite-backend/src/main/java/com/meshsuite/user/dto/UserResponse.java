package com.meshsuite.user.dto;

import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        Role role,
        Profile profile,
        boolean active,
        List<PermissionDto> permissions) {
}
