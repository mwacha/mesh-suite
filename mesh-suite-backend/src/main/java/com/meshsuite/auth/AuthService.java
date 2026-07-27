package com.meshsuite.auth;

import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final EmpresaRepository empresaRepository;
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
    public AuthService(UsuarioRepository usuarioRepository, TenantRepository tenantRepository,
                        EmpresaRepository empresaRepository, PasswordEncoder passwordEncoder,
                        EntityManager entityManager, @Lazy AuthService self) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.empresaRepository = empresaRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.self = self;
    }

    public record LoginResult(Usuario usuario, Tenant tenant, Empresa empresa) {
    }

    private record TenantAndEmpresa(Tenant tenant, Empresa empresa) {
    }

    // Runs before the caller's tenant is known. See plan §"Design decision beyond
    // the spec" for why this needs the usuario_login_lookup RLS policy.
    @Transactional(readOnly = true)
    public Usuario findByEmailForLogin(String email) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        return usuarioRepository.findByEmail(email).orElse(null);
    }

    // Used by PasswordResetService.confirmReset (Task 11): a reset token identifies
    // a usuario_id but not a tenant, so this lookup is also pre-tenant-context and
    // needs the same bypass. Reuses usuario_login_lookup -- that policy is
    // unconditional on the flag, not scoped to email lookups specifically.
    @Transactional(readOnly = true)
    public Usuario findUsuarioByIdBypassingTenant(UUID usuarioId) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        return usuarioRepository.findById(usuarioId).orElse(null);
    }

    public LoginResult authenticate(String email, String senha) {
        Usuario usuario = self.findByEmailForLogin(email);
        if (usuario == null || !passwordEncoder.matches(senha, usuario.getSenhaHash()) || !usuario.isAtivo()) {
            throw new AuthException();
        }

        TenantContext.set(usuario.getTenantId());
        try {
            TenantAndEmpresa loaded = self.loadTenantAndEmpresa(usuario.getTenantId());
            if (loaded == null || !loaded.tenant().isAtivo() || loaded.empresa() == null) {
                throw new AuthException();
            }

            self.registerAcesso(usuario.getId());
            return new LoginResult(usuario, loaded.tenant(), loaded.empresa());
        } finally {
            TenantContext.clear();
        }
    }

    // Consolidates the tenant+empresa lookups into one plain, hand-written
    // @Transactional method -- the same pattern TenantQueryService (Task 7)
    // already uses and is proven to work with TenantContextAspect. Calling
    // tenantRepository/empresaRepository methods directly from authenticate()
    // would rely on Spring Data's dynamically-generated repository proxy methods
    // exposing @Transactional in a way a custom @annotation(...) pointcut reliably
    // matches, which is less certain than a plain, explicitly-annotated method.
    @Transactional(readOnly = true)
    public TenantAndEmpresa loadTenantAndEmpresa(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return null;
        }
        List<Empresa> empresas = empresaRepository.findByTenantId(tenantId);
        Empresa empresa = empresas.isEmpty() ? null : empresas.get(0);
        return new TenantAndEmpresa(tenant, empresa);
    }

    @Transactional
    public void registerAcesso(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setUltimoAcesso(Instant.now());
        usuarioRepository.save(usuario);
    }
}
