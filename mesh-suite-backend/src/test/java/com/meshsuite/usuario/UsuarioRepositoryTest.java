package com.meshsuite.usuario;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    // usuario_tenant_isolation has no explicit WITH CHECK, so its USING expression
    // also gates INSERT: writing a row requires app.tenant_id to already equal that
    // row's tenant_id. usuario_login_lookup (bypass flag) is SELECT-only and doesn't
    // help here.
    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    @Test
    @Transactional
    void savesUsuarioWithPapel() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@confeccaoaurora.com.br");
        usuario.setSenhaHash("bcrypt-hash");
        usuario.setPapel(Papel.ADMINISTRADOR);

        Usuario saved = usuarioRepository.save(usuario);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPapel()).isEqualTo(Papel.ADMINISTRADOR);
        assertThat(saved.isAtivo()).isTrue();
    }

    @Test
    @Transactional
    void rejectsDuplicateEmailAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        Usuario a = new Usuario();
        a.setTenantId(tenantA.getId());
        a.setNome("Marina");
        a.setEmail("marina@confeccaoaurora.com.br");
        a.setSenhaHash("hash");
        a.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(a);

        setTenantContext(tenantB.getId());
        Usuario b = new Usuario();
        b.setTenantId(tenantB.getId());
        b.setNome("Marina Outra");
        b.setEmail("marina@confeccaoaurora.com.br");
        b.setSenhaHash("hash");
        b.setPapel(Papel.ADMINISTRADOR);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> usuarioRepository.saveAndFlush(b));
    }

    @Test
    @Transactional
    void loginBypassPolicyAllowsEmailLookupWithoutTenantContext() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@confeccaoaurora.com.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);
        entityManager.clear();

        // RESET simulates no tenant context: without the bypass flag, RLS hides the row.
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
        Long withoutBypass = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM usuario WHERE email = 'marina@confeccaoaurora.com.br'")
                .getSingleResult()).longValue();
        assertThat(withoutBypass).isZero();

        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        Long withBypass = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM usuario WHERE email = 'marina@confeccaoaurora.com.br'")
                .getSingleResult()).longValue();
        assertThat(withBypass).isEqualTo(1L);
    }
}
