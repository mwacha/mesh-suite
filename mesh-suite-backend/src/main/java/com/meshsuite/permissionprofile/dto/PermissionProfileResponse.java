package com.meshsuite.permissionprofile.dto;

import com.meshsuite.user.dto.PermissionDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PermissionProfileResponse(
        UUID id,
        String name,
        String description,
        Boolean isSystem,
        Instant createdAt,
        List<PermissionDto> grants) {
}
