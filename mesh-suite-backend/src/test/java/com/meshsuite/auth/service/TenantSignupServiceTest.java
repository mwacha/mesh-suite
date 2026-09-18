package com.meshsuite.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.meshsuite.auth.domain.TenantSignupToken;
import com.meshsuite.auth.dto.SignupRequest;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.repository.TenantSignupTokenRepository;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.exception.DuplicateCnpjException;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.mail.service.MailService;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TenantSignupServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock CompanyRepository companyRepository;
    @Mock UserRepository userRepository;
    @Mock TenantSignupTokenRepository tokenRepository;
    @Mock MailService mailService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EntityManager entityManager;
    @Mock Query query;

    // Not in the plan's brief verbatim: EntityManager.createNativeQuery(...) is an
    // unstubbed Mockito mock call, which returns null by default (Mockito doesn't
    // auto-deep-stub), so findExistingSignup's unconditional
    // "SET LOCAL app.bypass_tenant_check" .executeUpdate() call NPEs before any
    // business logic runs. lenient() because the confirmSignup tests never touch
    // entityManager and would otherwise trip strict-stubbing's unnecessary-stub check.
    @BeforeEach
    void stubEntityManagerNativeQuery() {
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    private TenantSignupService service() {
        TenantSignupService svc = new TenantSignupService(tenantRepository, companyRepository, userRepository,
                tokenRepository, mailService, passwordEncoder, entityManager, "http://localhost:5173");
        // Same rationale as PasswordResetServiceTest: plain Mockito test, no Spring
        // proxy, so `self` is pointed back at the same instance to simulate it.
        svc.self = svc;
        return svc;
    }

    private SignupRequest request(String cnpj) {
        return new SignupRequest("Confecção Aurora Ltda", cnpj, "Aurora", null, null, null, null, null,
                null, null, null, null, null, null, null,
                "Marina", "marina@aurora.com.br", "senha1234");
    }

    @Test
    void signupCreatesTenantCompanyAdminAndSendsConfirmation() {
        when(companyRepository.findByCnpj("11222333000144")).thenReturn(Optional.empty());
        when(tenantRepository.existsByCodigo(any())).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("hashed");
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        service().signup(request("11222333000144"));

        verify(companyRepository).save(argThat(c -> c.getCnpj().equals("11222333000144")
                && c.getLegalName().equals("Confecção Aurora Ltda")));
        verify(userRepository).save(argThat(u -> u.getEmail().equals("marina@aurora.com.br")
                && u.getRole() == Role.ADMIN
                && u.getPasswordHash().equals("hashed")
                // USER+DELETE is deliberately excluded -- see the seed's own comment
                // (there is no hard delete for User) -- 35 grants, not 36.
                && u.getPermissions().size() == 35));
        verify(tokenRepository).save(any(TenantSignupToken.class));
        verify(mailService).sendSignupConfirmationEmail(eq("marina@aurora.com.br"), any());
        // A brand-new tenant has no prior token to invalidate -- invalidateAllForTenant
        // is scoped to the resend branch only.
        verify(tokenRepository, never()).invalidateAllForTenant(any());
    }

    @Test
    void signupDeletesOrphanedTenantAndThrowsDuplicateCnpjOnRaceDuringCompanyCreation() {
        when(companyRepository.findByCnpj("11222333000144")).thenReturn(Optional.empty());
        when(tenantRepository.existsByCodigo(any())).thenReturn(false);
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(tenantId);
            return t;
        });
        // Simulates the genuine race described in the finding: another concurrent
        // signup for the same CNPJ committed its Company row first, so this
        // companyRepository.save(...) trips the DB's unique constraint.
        when(companyRepository.save(any(Company.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThrows(DuplicateCnpjException.class, () -> service().signup(request("11222333000144")));

        verify(tenantRepository).deleteById(tenantId);
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
        verify(mailService, never()).sendSignupConfirmationEmail(any(), any());
    }

    @Test
    void signupThrowsDuplicateCnpjWhenExistingTenantIsAlreadyConfirmed() {
        UUID tenantId = UUID.randomUUID();
        Company existing = new Company();
        existing.setTenantId(tenantId);
        existing.setCnpj("11222333000144");
        when(companyRepository.findByCnpj("11222333000144")).thenReturn(Optional.of(existing));
        Tenant confirmedTenant = new Tenant();
        confirmedTenant.setId(tenantId);
        confirmedTenant.setAtivo(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(confirmedTenant));

        assertThrows(DuplicateCnpjException.class, () -> service().signup(request("11222333000144")));
        verify(companyRepository, never()).save(any());
        verify(mailService, never()).sendSignupConfirmationEmail(any(), any());
    }

    @Test
    void signupResendsConfirmationWithoutDuplicatingWhenExistingTenantIsNotYetConfirmed() {
        UUID tenantId = UUID.randomUUID();
        Company existing = new Company();
        existing.setTenantId(tenantId);
        existing.setCnpj("11222333000144");
        when(companyRepository.findByCnpj("11222333000144")).thenReturn(Optional.of(existing));
        Tenant unconfirmedTenant = new Tenant();
        unconfirmedTenant.setId(tenantId);
        unconfirmedTenant.setAtivo(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(unconfirmedTenant));
        User existingAdmin = new User();
        existingAdmin.setEmail("marina@aurora.com.br");
        when(userRepository.findFirstByTenantIdAndRole(tenantId, Role.ADMIN)).thenReturn(Optional.of(existingAdmin));

        service().signup(request("11222333000144"));

        verify(companyRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(tokenRepository).invalidateAllForTenant(tenantId);
        verify(tokenRepository).save(any(TenantSignupToken.class));
        verify(mailService).sendSignupConfirmationEmail(eq("marina@aurora.com.br"), any());
    }

    @Test
    void signupGeneratesSuffixedCodigoOnCollision() {
        when(companyRepository.findByCnpj(any())).thenReturn(Optional.empty());
        when(tenantRepository.existsByCodigo("confeccao-aurora-ltda")).thenReturn(true);
        when(tenantRepository.existsByCodigo("confeccao-aurora-ltda-2")).thenReturn(false);
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        SignupRequest req = new SignupRequest("Confecção Aurora Ltda", "11222333000144", null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                "Marina", "marina@aurora.com.br", "senha1234");
        service().signup(req);

        verify(tenantRepository).saveAndFlush(argThat(t -> t.getCodigo().equals("confeccao-aurora-ltda-2")));
    }

    @Test
    void confirmSignupActivatesTenantAndMarksTokenUsed() {
        UUID tenantId = UUID.randomUUID();
        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(tenantId);
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setAtivo(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        service().confirmSignup("raw-token");

        verify(tenantRepository).save(argThat(Tenant::isAtivo));
        verify(tokenRepository).save(argThat(t -> t.getUsadoEm() != null));
    }

    @Test
    void confirmSignupThrowsAuthExceptionWhenTokenUnknown() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(AuthException.class, () -> service().confirmSignup("bogus"));
    }

    @Test
    void confirmSignupThrowsAuthExceptionWhenTokenExpired() {
        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(UUID.randomUUID());
        token.setExpiraEm(Instant.now().minus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(AuthException.class, () -> service().confirmSignup("expired"));
    }

    @Test
    void confirmSignupThrowsAuthExceptionWhenTokenAlreadyUsed() {
        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(UUID.randomUUID());
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        token.setUsadoEm(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(AuthException.class, () -> service().confirmSignup("used"));
    }
}
