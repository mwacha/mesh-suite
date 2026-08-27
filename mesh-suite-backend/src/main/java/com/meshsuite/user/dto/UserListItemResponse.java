package com.meshsuite.user.dto;

import com.meshsuite.user.domain.enums.Profile;

import java.util.UUID;

public record UserListItemResponse(
        UUID id,
        String name,
        String email,
        Profile profile,
        boolean active,
        UUID permissionProfileId,
        String permissionProfileName) {
}
