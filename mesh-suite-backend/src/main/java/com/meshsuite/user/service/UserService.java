package com.meshsuite.user.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.permissionprofile.domain.PermissionProfile;
import com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException;
import com.meshsuite.permissionprofile.repository.PermissionProfileRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.dto.*;
import com.meshsuite.user.exception.EmailAlreadyExistsException;
import com.meshsuite.user.exception.UserNotFoundException;
import com.meshsuite.user.exception.UserValidationException;
import com.meshsuite.user.repository.UserRepository;
import com.meshsuite.user.repository.specification.UserSpecifications;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionProfileRepository permissionProfileRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        PermissionProfileRepository permissionProfileRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionProfileRepository = permissionProfileRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public Page<UserListItemResponse> list(String search, Profile profile, Boolean active, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.withSearch(search),
                UserSpecifications.withProfile(profile),
                UserSpecifications.withActive(active));
        return userRepository.findAll(spec, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public UserCountsResponse counts() {
        long active = userRepository.countByActive(true);
        long inactive = userRepository.countByActive(false);
        return new UserCountsResponse(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public UserResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.CREATE)
    public UserResponse create(UUID tenantId, UserRequest request) {
        validate(request, null);

        User user = new User();
        user.setTenantId(tenantId);
        applyRequest(user, request);
        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.EDIT)
    public UserResponse update(UUID id, UserRequest request) {
        validate(request, id);

        User user = findEntityById(id);
        applyRequest(user, request);
        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.EDIT)
    public UserResponse updateStatus(UUID id, boolean active) {
        User user = findEntityById(id);
        user.setActive(active);
        return toResponse(userRepository.saveAndFlush(user));
    }

    private User findEntityById(UUID id) {
        return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
    }

    private void validate(UserRequest request, UUID currentId) {
        boolean duplicate = userRepository.findByEmail(request.email())
                .filter(u -> currentId == null || !u.getId().equals(currentId))
                .isPresent();
        if (duplicate) {
            throw new EmailAlreadyExistsException();
        }

        boolean creating = currentId == null;
        String password = request.password();
        if (creating && (password == null || password.isBlank())) {
            throw new UserValidationException("Senha é obrigatória");
        }
        if (password != null && !password.isBlank()) {
            if (!password.equals(request.confirmPassword())) {
                throw new UserValidationException("As senhas não coincidem");
            }
            if (!PASSWORD_PATTERN.matcher(password).matches()) {
                throw new UserValidationException("A senha deve ter no mínimo 8 caracteres, com letras e números");
            }
        }
    }

    private void applyRequest(User user, UserRequest request) {
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        if (request.profile() != null) {
            user.setProfile(request.profile());
        }
        user.setActive(request.active());
        if (request.permissionProfileId() != null) {
            PermissionProfile permissionProfile = permissionProfileRepository.findById(request.permissionProfileId())
                    .orElseThrow(PermissionProfileNotFoundException::new);
            user.setPermissionProfile(permissionProfile);
        } else {
            user.setPermissionProfile(null);
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        user.getPermissions().clear();
        List<PermissionDto> permissions = request.permissions() == null ? List.of() : request.permissions();
        for (PermissionDto dto : permissions) {
            user.getPermissions().add(new UserPermissionGrant(dto.module(), dto.action()));
        }
    }

    private UserListItemResponse toListItem(User u) {
        PermissionProfile pp = u.getPermissionProfile();
        return new UserListItemResponse(u.getId(), u.getName(), u.getEmail(), u.getProfile(), u.isActive(),
                pp == null ? null : pp.getId(), pp == null ? null : pp.getName());
    }

    private UserResponse toResponse(User u) {
        List<PermissionDto> permissions = u.getPermissions().stream()
                .map(p -> new PermissionDto(p.getModule(), p.getAction()))
                .toList();
        PermissionProfile pp = u.getPermissionProfile();
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getProfile(),
                u.isActive(), permissions, pp == null ? null : pp.getId(), pp == null ? null : pp.getName());
    }
}
