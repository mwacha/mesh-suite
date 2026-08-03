package com.meshsuite.user;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @GetMapping("/sales-reps")
    public List<SalesRepResponse> salesReps() {
        return userRepository.findByRoleOrderByName(Role.SALES_REP).stream()
                .map(u -> new SalesRepResponse(u.getId(), u.getName()))
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
