package com.meshsuite.user;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Page<UserListItemResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Profile profile,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return userService.list(search, profile, active, pageable);
    }

    @GetMapping("/counts")
    public UserCountsResponse counts() {
        return userService.counts();
    }

    // Deliberately bypasses UserService/@RequiresPermission -- support lookup for
    // the Pedido form's vendor picker, not "viewing the Users module". See the
    // Global Constraints note.
    //
    // Still needs its own @Transactional: TenantContextAspect only fires around
    // methods carrying that annotation directly (see its class comment), and
    // findByRoleOrderByName's RLS-scoped query is invisible to every tenant
    // without app.tenant_id set. Without this, the endpoint silently returns an
    // empty list instead of erroring -- caught live, not by the existing test,
    // which only asserted 200 + array shape and never a specific result.
    @Transactional(readOnly = true)
    @GetMapping("/sales-reps")
    public List<SalesRepResponse> salesReps() {
        return userRepository.findByRoleOrderByName(Role.SALES_REP).stream()
                .map(u -> new SalesRepResponse(u.getId(), u.getName()))
                .toList();
    }

    // Deliberately bypasses UserService/@RequiresPermission -- support lookup
    // for the Ordem de Compra form's buyer picker, not "viewing the Users
    // module". Needs its own @Transactional for the same reason /sales-reps
    // does (see that method's comment above): TenantContextAspect only fires
    // around methods carrying that annotation directly, and
    // findByRoleOrderByName's RLS-scoped query is invisible to every tenant
    // without app.tenant_id set.
    @Transactional(readOnly = true)
    @GetMapping("/buyers")
    public List<BuyerResponse> buyers() {
        return userRepository.findByRoleOrderByName(Role.ADMINISTRATIVE).stream()
                .map(u -> new BuyerResponse(u.getId(), u.getName()))
                .toList();
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                @Valid @RequestBody UserRequest request) {
        UserResponse response = userService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request) {
        return userService.updateStatus(id, request.active());
    }
}
