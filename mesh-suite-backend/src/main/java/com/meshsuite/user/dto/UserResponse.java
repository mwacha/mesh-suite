package com.meshsuite.user.dto;

import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;

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
        List<PermissionDto> permissions,
        UUID permissionProfileId,
        String permissionProfileName) {
}
