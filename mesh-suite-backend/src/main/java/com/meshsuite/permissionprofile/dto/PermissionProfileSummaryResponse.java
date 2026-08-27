package com.meshsuite.permissionprofile.dto;

import java.util.UUID;

public record PermissionProfileSummaryResponse(
        UUID id,
        String name,
        String description,
        Boolean isSystem,
        Integer moduleCount,
        Long userCount) {
}
