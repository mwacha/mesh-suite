package com.meshsuite.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.TenantSignupToken;
import com.meshsuite.auth.repository.TenantSignupTokenRepository;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TenantSignupControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired TenantSignupTokenRepository tokenRepository;
    @Autowired EntityManager entityManager;
    @MockBean com.meshsuite.mail.service.MailService mailService;

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void signupCreatesInactiveTenantAndSendsConfirmationEmail() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddr("10.0.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Confecção Aurora Ltda","cnpj":"11222333000144",
                                 "adminName":"Marina","adminEmail":"marina@aurora.com.br","senha":"senha1234"}"""))
                .andExpect(status().isAccepted());

        List<Company> companies = companyRepository.findAll();
        assertThat(companies).anyMatch(c -> c.getCnpj().equals("11222333000144"));
        Tenant tenant = tenantRepository.findById(
                companies.stream().filter(c -> c.getCnpj().equals("11222333000144")).findFirst().get().getTenantId())
                .orElseThrow();
        assertThat(tenant.isAtivo()).isFalse();

        verify(mailService).sendSignupConfirmationEmail(eq("marina@aurora.com.br"), any());
    }

    @Test
    void confirmSignupActivatesTheTenant() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora-confirm");
        tenant.setNome("Aurora Confirm");
        tenant.setAtivo(false);
        tenantRepository.saveAndFlush(tenant);

        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(tenant.getId());
        token.setTokenHash(sha256("raw-confirm-token"));
        token.setExpiraEm(Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.saveAndFlush(token);

        mockMvc.perform(post("/api/auth/confirm-signup")
                        .with(remoteAddr("10.0.0.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"raw-confirm-token"}"""))
                .andExpect(status().isOk());

        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void confirmSignupWithUnknownTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/confirm-signup")
                        .with(remoteAddr("10.0.0.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"nao-existe"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupWithCnpjOfAlreadyConfirmedTenantReturns409() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora-confirmed");
        tenant.setNome("Aurora Confirmed");
        tenant.setAtivo(true);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Aurora Ltda");
        company.setCnpj("22333444000155");
        companyRepository.saveAndFlush(company);
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddr("10.0.0.4"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Outra Ltda","cnpj":"22333444000155",
                                 "adminName":"Carlos","adminEmail":"carlos@outra.com.br","senha":"senha1234"}"""))
                .andExpect(status().isConflict());
    }

    private static String tokenFromConfirmLink(String confirmLink) {
        return confirmLink.split("\\?token=")[1];
    }

    @Test
    void signupThenConfirmSignupWithTheRealEmailedTokenActivatesTheTenant() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddr("10.0.0.5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Confecção Boreal Ltda","cnpj":"33444555000166",
                                 "adminName":"Paula","adminEmail":"paula@boreal.com.br","senha":"senha1234"}"""))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendSignupConfirmationEmail(eq("paula@boreal.com.br"), linkCaptor.capture());
        String rawToken = tokenFromConfirmLink(linkCaptor.getValue());

        mockMvc.perform(post("/api/auth/confirm-signup")
                        .with(remoteAddr("10.0.0.5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        Company company = companyRepository.findAll().stream()
                .filter(c -> c.getCnpj().equals("33444555000166"))
                .findFirst().orElseThrow();
        Tenant tenant = tenantRepository.findById(company.getTenantId()).orElseThrow();
        assertThat(tenant.isAtivo()).isTrue();
    }

    @Test
    void resendingSignupInvalidatesThePreviousTokenAndDoesNotDuplicateTheTenant() throws Exception {
        String body = """
                {"legalName":"Confecção Linda Ltda","cnpj":"44555666000177",
                 "adminName":"Renata","adminEmail":"renata@linda.com.br","senha":"senha1234"}""";

        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddr("10.0.0.6"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddr("10.0.0.7"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService, times(2)).sendSignupConfirmationEmail(eq("renata@linda.com.br"), linkCaptor.capture());
        List<String> links = linkCaptor.getAllValues();
        String firstToken = tokenFromConfirmLink(links.get(0));
        String secondToken = tokenFromConfirmLink(links.get(1));

        mockMvc.perform(post("/api/auth/confirm-signup")
                        .with(remoteAddr("10.0.0.6"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + firstToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/confirm-signup")
                        .with(remoteAddr("10.0.0.7"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + secondToken + "\"}"))
                .andExpect(status().isOk());

        List<Company> companies = companyRepository.findAll().stream()
                .filter(c -> c.getCnpj().equals("44555666000177"))
                .toList();
        assertThat(companies).hasSize(1);
        Tenant tenant = tenantRepository.findById(companies.get(0).getTenantId()).orElseThrow();
        assertThat(tenant.isAtivo()).isTrue();
    }

    @Test
    void sixthSignupAttemptFromSameIpAndEmailIsRateLimited() throws Exception {
        String ip = "10.0.0.8";
        String adminEmail = "rate-limit-signup@teste.com.br";
        String[] cnpjs = {
                "90000000000101", "90000000000102", "90000000000103",
                "90000000000104", "90000000000105", "90000000000106"
        };

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signup")
                            .with(remoteAddr(ip))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"legalName":"Empresa Rate Limit %d Ltda","cnpj":"%s",
                                     "adminName":"Teste","adminEmail":"%s","senha":"senha1234"}"""
                                    .formatted(i, cnpjs[i], adminEmail)))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/auth/signup")
                        .with(remoteAddr(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Empresa Rate Limit 6 Ltda","cnpj":"%s",
                                 "adminName":"Teste","adminEmail":"%s","senha":"senha1234"}"""
                                .formatted(cnpjs[5], adminEmail)))
                .andExpect(status().isTooManyRequests());
    }
}
