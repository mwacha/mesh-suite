package com.meshsuite.permissionprofile.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.permissionprofile.domain.PermissionProfile;
import com.meshsuite.permissionprofile.dto.PermissionProfileRequest;
import com.meshsuite.permissionprofile.dto.PermissionProfileResponse;
import com.meshsuite.permissionprofile.dto.PermissionProfileSummaryResponse;
import com.meshsuite.permissionprofile.exception.DuplicatePermissionProfileNameException;
import com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException;
import com.meshsuite.permissionprofile.exception.PermissionProfileValidationException;
import com.meshsuite.permissionprofile.repository.PermissionProfileRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.dto.PermissionDto;
import com.meshsuite.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionProfileService {

    private final PermissionProfileRepository permissionProfileRepository;
    private final UserRepository userRepository;

    public PermissionProfileService(PermissionProfileRepository permissionProfileRepository, UserRepository userRepository) {
        this.permissionProfileRepository = permissionProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public Page<PermissionProfileSummaryResponse> list(String search, Pageable pageable) {
        ensureDefaultsSeeded();
        Specification<PermissionProfile> spec = search == null || search.isBlank()
                ? null
                : (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
        return permissionProfileRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public PermissionProfileResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.CREATE)
    public PermissionProfileResponse create(UUID tenantId, PermissionProfileRequest request) {
        validateName(request.name(), null);

        PermissionProfile profile = new PermissionProfile();
        profile.setTenantId(tenantId);
        apply(profile, request);
        return toResponse(permissionProfileRepository.saveAndFlush(profile));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.EDIT)
    public PermissionProfileResponse update(UUID id, PermissionProfileRequest request) {
        validateName(request.name(), id);

        PermissionProfile profile = findEntityById(id);
        apply(profile, request);
        return toResponse(permissionProfileRepository.saveAndFlush(profile));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.DELETE)
    public void delete(UUID id) {
        PermissionProfile profile = findEntityById(id);
        if (profile.getIsSystem()) {
            throw new PermissionProfileValidationException(
                    "Não é possível excluir um perfil padrão do sistema");
        }
        long linked = userRepository.countByPermissionProfileId(id);
        if (linked > 0) {
            throw new PermissionProfileValidationException(
                    "Não é possível excluir: " + linked + " usuário(s) usam este perfil");
        }
        permissionProfileRepository.delete(profile);
    }

    private PermissionProfile findEntityById(UUID id) {
        return permissionProfileRepository.findById(id).orElseThrow(PermissionProfileNotFoundException::new);
    }

    private void validateName(String name, UUID currentId) {
        boolean duplicate = currentId == null
                ? permissionProfileRepository.existsByName(name)
                : permissionProfileRepository.existsByNameAndIdNot(name, currentId);
        if (duplicate) {
            throw new DuplicatePermissionProfileNameException();
        }
    }

    private void apply(PermissionProfile profile, PermissionProfileRequest request) {
        profile.setName(request.name());
        profile.setDescription(request.description());
        profile.getGrants().clear();
        for (PermissionDto dto : request.grants()) {
            profile.getGrants().add(new UserPermissionGrant(dto.module(), dto.action()));
        }
    }

    // Display names for the 4 seeded system profiles, keyed by their stable
    // internal code. The code is the profile's real identity for seeding
    // purposes; the name is just a mutable, user-editable label (see below).
    private static final Map<String, String> DEFAULT_NAMES = Map.of(
            "ADMIN", "Admin",
            "MANAGER", "Gerente",
            "SALES", "Vendedor",
            "VIEWER", "Visualizador");

    // Seeds the 4 system defaults the first time a tenant lists its profiles --
    // there is no tenant-registration flow yet to hook this into (see spec §8.1).
    // Seeding is keyed on `code`, a stable internal identity, rather than on the
    // mutable `name` field: PermissionProfileFormView.vue intentionally allows
    // renaming any profile, including isSystem ones, so keying on name would
    // cause a rename to be re-seeded as a brand-new (permanent, undeletable)
    // duplicate on the next list() call.
    // The DB's UNIQUE(tenant_id, code) WHERE code IS NOT NULL makes a concurrent
    // double-seed harmless: the loser's insert throws DataIntegrityViolationException,
    // surfaced as a 409 by the same handler duplicate names already get.
    private void ensureDefaultsSeeded() {
        UUID tenantId = TenantContext.get();
        for (Map.Entry<String, List<UserPermissionGrant>> entry : defaultMatrix().entrySet()) {
            String code = entry.getKey();
            if (!permissionProfileRepository.existsByCode(code)) {
                PermissionProfile profile = new PermissionProfile();
                profile.setTenantId(tenantId);
                profile.setName(DEFAULT_NAMES.get(code));
                profile.setCode(code);
                profile.setIsSystem(true);
                profile.getGrants().addAll(entry.getValue());
                permissionProfileRepository.saveAndFlush(profile);
            }
        }
    }

    // Business-judgment default matrix, ported from the old frontend-only
    // DEFAULT_MATRIX (UserFormView.vue), extended with STOCK -- see spec §3.
    // Keyed by stable code (not display name) -- see ensureDefaultsSeeded().
    private static Map<String, List<UserPermissionGrant>> defaultMatrix() {
        return Map.of(
                "ADMIN", List.of(
                        g(Module.CUSTOMER, Action.VIEW), g(Module.CUSTOMER, Action.CREATE), g(Module.CUSTOMER, Action.EDIT), g(Module.CUSTOMER, Action.DELETE),
                        g(Module.PRODUCT, Action.VIEW), g(Module.PRODUCT, Action.CREATE), g(Module.PRODUCT, Action.EDIT), g(Module.PRODUCT, Action.DELETE),
                        g(Module.ORDER, Action.VIEW), g(Module.ORDER, Action.CREATE), g(Module.ORDER, Action.EDIT), g(Module.ORDER, Action.DELETE),
                        g(Module.USER, Action.VIEW), g(Module.USER, Action.CREATE), g(Module.USER, Action.EDIT),
                        g(Module.PURCHASE, Action.VIEW), g(Module.PURCHASE, Action.CREATE), g(Module.PURCHASE, Action.EDIT), g(Module.PURCHASE, Action.DELETE),
                        g(Module.STOCK, Action.VIEW), g(Module.STOCK, Action.CREATE), g(Module.STOCK, Action.EDIT), g(Module.STOCK, Action.DELETE),
                        g(Module.PAYABLE, Action.VIEW), g(Module.PAYABLE, Action.EDIT),
                        g(Module.SALE, Action.VIEW), g(Module.SALE, Action.CREATE),
                        g(Module.PURCHASE_INVOICE, Action.VIEW), g(Module.PURCHASE_INVOICE, Action.CREATE)),
                "MANAGER", List.of(
                        g(Module.CUSTOMER, Action.VIEW), g(Module.CUSTOMER, Action.CREATE), g(Module.CUSTOMER, Action.EDIT),
                        g(Module.PRODUCT, Action.VIEW), g(Module.PRODUCT, Action.CREATE), g(Module.PRODUCT, Action.EDIT),
                        g(Module.ORDER, Action.VIEW), g(Module.ORDER, Action.CREATE), g(Module.ORDER, Action.EDIT),
                        g(Module.PURCHASE, Action.VIEW), g(Module.PURCHASE, Action.CREATE), g(Module.PURCHASE, Action.EDIT),
                        g(Module.STOCK, Action.VIEW),
                        g(Module.PAYABLE, Action.VIEW), g(Module.PAYABLE, Action.EDIT),
                        g(Module.SALE, Action.VIEW), g(Module.SALE, Action.CREATE),
                        g(Module.PURCHASE_INVOICE, Action.VIEW), g(Module.PURCHASE_INVOICE, Action.CREATE),
                        g(Module.USER, Action.VIEW)),
                "SALES", List.of(
                        g(Module.CUSTOMER, Action.VIEW), g(Module.CUSTOMER, Action.CREATE), g(Module.CUSTOMER, Action.EDIT),
                        g(Module.PRODUCT, Action.VIEW),
                        g(Module.ORDER, Action.VIEW), g(Module.ORDER, Action.CREATE), g(Module.ORDER, Action.EDIT),
                        g(Module.SALE, Action.VIEW), g(Module.SALE, Action.CREATE)),
                "VIEWER", List.of(
                        g(Module.CUSTOMER, Action.VIEW),
                        g(Module.PRODUCT, Action.VIEW),
                        g(Module.ORDER, Action.VIEW),
                        g(Module.PURCHASE, Action.VIEW),
                        g(Module.STOCK, Action.VIEW),
                        g(Module.PAYABLE, Action.VIEW),
                        g(Module.SALE, Action.VIEW),
                        g(Module.PURCHASE_INVOICE, Action.VIEW)));
    }

    private static UserPermissionGrant g(Module module, Action action) {
        return new UserPermissionGrant(module, action);
    }

    private PermissionProfileSummaryResponse toSummary(PermissionProfile p) {
        long moduleCount = p.getGrants().stream().map(UserPermissionGrant::getModule).distinct().count();
        long userCount = userRepository.countByPermissionProfileId(p.getId());
        return new PermissionProfileSummaryResponse(p.getId(), p.getName(), p.getDescription(), p.getIsSystem(),
                (int) moduleCount, userCount);
    }

    private PermissionProfileResponse toResponse(PermissionProfile p) {
        List<PermissionDto> grants = p.getGrants().stream()
                .map(g -> new PermissionDto(g.getModule(), g.getAction()))
                .toList();
        return new PermissionProfileResponse(p.getId(), p.getName(), p.getDescription(), p.getIsSystem(),
                p.getCreatedAt(), grants);
    }
}
