package com.meshsuite.user.controller;

import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.dto.*;
import com.meshsuite.user.repository.UserRepository;
import com.meshsuite.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
    // findByRoleInAndPermission's RLS-scoped query is invisible to every
    // tenant without app.tenant_id set.
    //
    // Role alone isn't enough here: a user can be ADMIN/ADMINISTRATIVE by
    // role but have no PURCHASE+CREATE grant in user_permission (e.g. a
    // seeded/legacy user predating the permission system), in which case
    // picking them as buyer would let a purchase order reference someone who
    // can't actually create one.
    @Transactional(readOnly = true)
    @GetMapping("/buyers")
    public List<BuyerResponse> buyers() {
        return userRepository.findByRoleInAndPermission(
                        List.of(Role.ADMIN, Role.ADMINISTRATIVE), Module.PURCHASE, Action.CREATE).stream()
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
