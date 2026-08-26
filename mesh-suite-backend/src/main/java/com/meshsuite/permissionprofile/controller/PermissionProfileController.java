package com.meshsuite.permissionprofile.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.permissionprofile.dto.PermissionProfileRequest;
import com.meshsuite.permissionprofile.dto.PermissionProfileResponse;
import com.meshsuite.permissionprofile.dto.PermissionProfileSummaryResponse;
import com.meshsuite.permissionprofile.service.PermissionProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/permission-profiles")
public class PermissionProfileController {

    private final PermissionProfileService permissionProfileService;

    public PermissionProfileController(PermissionProfileService permissionProfileService) {
        this.permissionProfileService = permissionProfileService;
    }

    @GetMapping
    public Page<PermissionProfileSummaryResponse> list(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return permissionProfileService.list(busca, pageable);
    }

    @GetMapping("/{id}")
    public PermissionProfileResponse findById(@PathVariable UUID id) {
        return permissionProfileService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PermissionProfileResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                              @Valid @RequestBody PermissionProfileRequest request) {
        PermissionProfileResponse response = permissionProfileService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PermissionProfileResponse update(@PathVariable UUID id, @Valid @RequestBody PermissionProfileRequest request) {
        return permissionProfileService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        permissionProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
