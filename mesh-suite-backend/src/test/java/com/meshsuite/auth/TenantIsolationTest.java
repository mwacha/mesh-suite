package com.meshsuite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.aspect.TenantContextAspect;
import com.meshsuite.auth.service.TenantQueryService;
import com.meshsuite.empresa.domain.Empresa;
import com.meshsuite.empresa.repository.EmpresaRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// @Transactional here (Spring test rollback) keeps this test's "aurora"/"boreal"
// fixture rows from persisting after the test -- every other test class in this
// suite reuses those same literal codes and relies on them being rolled back.
// Nested @Transactional calls on TenantQueryService still join this outer
// transaction (default REQUIRED propagation) and TenantContextAspect still fires
// on each one, issuing its own SET LOCAL before that call's queries run -- so this
// still genuinely proves that switching app.tenant_id mid-transaction changes what
// RLS allows to be seen, not just that separate transactions are isolated.
@Transactional
class TenantIsolationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantQueryService tenantQueryService;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void tenantACannotSeeTenantBData() {
        Tenant tenantA = new Tenant();
        tenantA.setCodigo("aurora");
        tenantA.setNome("Confecção Aurora");
        tenantRepository.saveAndFlush(tenantA);

        Tenant tenantB = new Tenant();
        tenantB.setCodigo("boreal");
        tenantB.setNome("Confecção Boreal");
        tenantRepository.saveAndFlush(tenantB);

        TenantContext.set(tenantA.getId());
        tenantQueryService.saveEmpresa(tenantA.getId(), "Aurora Ltda", "11222333000144");
        tenantQueryService.saveUser(tenantA.getId(), "Marina", "marina@aurora.com.br", Role.ADMIN);
        TenantContext.clear();

        TenantContext.set(tenantB.getId());
        tenantQueryService.saveEmpresa(tenantB.getId(), "Boreal Ltda", "55666777000188");
        tenantQueryService.saveUser(tenantB.getId(), "Carlos", "carlos@boreal.com.br", Role.ADMIN);
        TenantContext.clear();

        TenantContext.set(tenantA.getId());
        assertThat(tenantQueryService.listEmpresas()).extracting(Empresa::getCnpj).containsExactly("11222333000144");
        assertThat(tenantQueryService.listUsers()).extracting(User::getEmail).containsExactly("marina@aurora.com.br");
        TenantContext.clear();

        TenantContext.set(tenantB.getId());
        assertThat(tenantQueryService.listEmpresas()).extracting(Empresa::getCnpj).containsExactly("55666777000188");
        assertThat(tenantQueryService.listUsers()).extracting(User::getEmail).containsExactly("carlos@boreal.com.br");
    }
}
