package com.meshsuite.auth.service;

import com.meshsuite.auth.aspect.TenantContextAspect;
import com.meshsuite.auth.controller.AuthController;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final AuthService self;

    // Self-injection: `authenticate()` is called externally (from AuthController,
    // a different bean) so it goes through this class's real Spring proxy -- but
    // calling `this.findByEmailForLogin(...)` etc. from inside it would be a plain
    // Java self-invocation, which bypasses that proxy entirely. @Transactional (and
    // TenantContextAspect, which relies on @Transactional's own proxying) would
    // have no effect on such a call outside of an already-active transaction --
    // which is exactly the case for a real login request (AuthController isn't
    // @Transactional, and application.yml disables open-in-view, so nothing
    // pre-opens one). A @Lazy self-reference lets internal calls go through
    // `self.` instead, routing them through the real proxy so @Transactional
    // actually applies. @Lazy avoids a circular-construction failure (Spring can't
    // otherwise build a bean that depends on itself).
    public AuthService(UserRepository userRepository, TenantRepository tenantRepository,
                        CompanyRepository companyRepository, PasswordEncoder passwordEncoder,
                        EntityManager entityManager, @Lazy AuthService self) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.self = self;
    }

    public record LoginResult(User user, Tenant tenant, Company company) {
    }

    private record TenantAndCompany(Tenant tenant, Company company) {
    }

    // Runs before the caller's tenant is known -- needs the app_user_login_lookup
    // RLS policy (SET LOCAL app.bypass_tenant_check below).
    @Transactional(readOnly = true)
    public User findByEmailForLogin(String email) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        User user = userRepository.findByEmail(email).orElse(null);
        // In production this method's @Transactional always starts a fresh physical
        // transaction (see the self-injection note above), so SET LOCAL naturally
        // expires at commit and this RESET is a no-op. It matters when this method
        // runs inside an already-active, longer-lived transaction -- e.g. a
        // @Transactional integration test that shares one physical transaction across
        // several "requests" -- where, without this, the bypass flag would otherwise
        // leak into every later query on app_user for the rest of that transaction
        // and silently defeat its tenant-isolation RLS policy.
        entityManager.createNativeQuery("RESET app.bypass_tenant_check").executeUpdate();
        return user;
    }

    // Used by PasswordResetService.confirmReset: a reset token identifies a user id
    // but not a tenant, so this lookup is also pre-tenant-context and needs the same
    // bypass. Reuses app_user_login_lookup -- that policy is unconditional on the
    // flag, not scoped to email lookups specifically.
    @Transactional(readOnly = true)
    public User findUserByIdBypassingTenant(UUID userId) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        User user = userRepository.findById(userId).orElse(null);
        // See the matching comment in findByEmailForLogin above.
        entityManager.createNativeQuery("RESET app.bypass_tenant_check").executeUpdate();
        return user;
    }

    public LoginResult authenticate(String email, String senha) {
        User user = self.findByEmailForLogin(email);
        if (user == null || !passwordEncoder.matches(senha, user.getPasswordHash()) || !user.isActive()) {
            throw new AuthException();
        }

        TenantContext.set(user.getTenantId());
        try {
            TenantAndCompany loaded = self.loadTenantAndCompany(user.getTenantId());
            if (loaded == null || !loaded.tenant().isAtivo() || loaded.company() == null) {
                throw new AuthException();
            }

            self.registerAcesso(user.getId());
            return new LoginResult(user, loaded.tenant(), loaded.company());
        } finally {
            TenantContext.clear();
        }
    }

    // Consolidates the tenant+company lookups into one plain, hand-written
    // @Transactional method, proven to work with TenantContextAspect.
    @Transactional(readOnly = true)
    public TenantAndCompany loadTenantAndCompany(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return null;
        }
        List<Company> companies = companyRepository.findByTenantId(tenantId);
        Company company = companies.isEmpty() ? null : companies.get(0);
        return new TenantAndCompany(tenant, company);
    }

    @Transactional
    public void registerAcesso(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setLastAccessAt(Instant.now());
        userRepository.save(user);
    }
}
