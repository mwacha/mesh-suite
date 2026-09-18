package com.meshsuite.auth.service;

import com.meshsuite.auth.domain.TenantSignupToken;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.dto.SignupRequest;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.repository.TenantSignupTokenRepository;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.exception.DuplicateCnpjException;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.mail.service.MailService;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSignupService {

    // Every Module x Action except USER+DELETE, which doesn't exist as an
    // operation (there is no hard delete for User) -- matches the ADMIN grant
    // R__seed_dev_tenant.sql gives its seeded users.
    private static final List<Module> ALL_MODULES = List.of(
            Module.CUSTOMER, Module.PRODUCT, Module.ORDER, Module.USER, Module.PURCHASE,
            Module.STOCK, Module.PAYABLE, Module.SALE, Module.PURCHASE_INVOICE);
    private static final List<Action> ALL_ACTIONS = List.of(Action.VIEW, Action.CREATE, Action.EDIT, Action.DELETE);

    private final TenantRepository tenantRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final TenantSignupTokenRepository tokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final String frontendOrigin;
    private final SecureRandom secureRandom = new SecureRandom();

    // Field injection (not constructor), same reason as PasswordResetService.self:
    // lets TenantSignupServiceTest construct this class directly with mocks and
    // assign `self` manually. In production Spring wires this via @Lazy to avoid a
    // circular-construction failure. Package-private so the test (same package) can
    // assign it directly.
    @Autowired
    @Lazy
    TenantSignupService self;

    public TenantSignupService(TenantRepository tenantRepository, CompanyRepository companyRepository,
                                UserRepository userRepository, TenantSignupTokenRepository tokenRepository,
                                MailService mailService, PasswordEncoder passwordEncoder,
                                EntityManager entityManager,
                                @Value("${app.frontend-origin}") String frontendOrigin) {
        this.tenantRepository = tenantRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.frontendOrigin = frontendOrigin;
    }

    public void signup(SignupRequest request) {
        ExistingSignup existing = self.findExistingSignup(request.cnpj());
        if (existing != null) {
            if (existing.tenantAtivo()) {
                throw new DuplicateCnpjException();
            }
            // Invalidate any prior unused token before issuing a new one -- a resend
            // must supersede the earlier link, not just add a second valid one
            // alongside it for the rest of its 24h life (spec §2 decision 5).
            tokenRepository.invalidateAllForTenant(existing.tenantId());
            issueTokenAndSendEmail(existing.tenantId(), existing.adminEmail());
            return;
        }

        Tenant tenant = new Tenant();
        tenant.setCodigo(generateUniqueCodigo(request.legalName(), request.tradeName()));
        tenant.setNome(request.legalName());
        tenant.setAtivo(false);
        tenantRepository.saveAndFlush(tenant);

        TenantContext.set(tenant.getId());
        try {
            self.createCompanyAndAdmin(tenant.getId(), request);
        } catch (DataIntegrityViolationException e) {
            // Genuine race: two requests for the same CNPJ both passed
            // findExistingSignup's dedup check before either committed. The Tenant
            // row above is already committed (saveAndFlush in its own transaction),
            // so clean it up here rather than leaving an orphaned ativo=false tenant
            // with no Company/User behind it. Tenant has no RLS, so this delete needs
            // no TenantContext/self. dance.
            tenantRepository.deleteById(tenant.getId());
            throw new DuplicateCnpjException();
        } finally {
            TenantContext.clear();
        }

        issueTokenAndSendEmail(tenant.getId(), request.adminEmail());
    }

    public void confirmSignup(String rawToken) {
        TenantSignupToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(AuthException::new);
        if (token.getUsadoEm() != null || Instant.now().isAfter(token.getExpiraEm())) {
            throw new AuthException();
        }
        Tenant tenant = tenantRepository.findById(token.getTenantId()).orElseThrow(AuthException::new);
        tenant.setAtivo(true);
        tenantRepository.save(tenant);

        token.setUsadoEm(Instant.now());
        tokenRepository.save(token);
    }

    private record ExistingSignup(UUID tenantId, boolean tenantAtivo, String adminEmail) {
    }

    // Runs before any tenant is known -- same shape as AuthService.findAllByEmailForLogin:
    // SET LOCAL app.bypass_tenant_check so company_signup_lookup (Task 1) and the
    // existing app_user_login_lookup policy both let this read through, then RESET
    // so the flag doesn't leak into any later query on the same connection.
    @Transactional(readOnly = true)
    ExistingSignup findExistingSignup(String cnpj) {
        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        // try/finally so RESET always runs -- success, early return, or either
        // orElseThrow below -- rather than relying on SET LOCAL's transaction-scoped
        // auto-revert. Don't rely on this method's own @Transactional always opening a
        // fresh physical transaction (true today only when called via `self.`).
        try {
            Optional<Company> company = companyRepository.findByCnpj(cnpj);
            if (company.isEmpty()) {
                return null;
            }
            UUID tenantId = company.get().getTenantId();
            Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(DuplicateCnpjException::new);
            String adminEmail = null;
            if (!tenant.isAtivo()) {
                adminEmail = userRepository.findFirstByTenantIdAndRole(tenantId, Role.ADMIN)
                        .map(User::getEmail)
                        .orElseThrow(DuplicateCnpjException::new);
            }
            return new ExistingSignup(tenantId, tenant.isAtivo(), adminEmail);
        } finally {
            entityManager.createNativeQuery("RESET app.bypass_tenant_check").executeUpdate();
        }
    }

    @Transactional
    void createCompanyAndAdmin(UUID tenantId, SignupRequest request) {
        Company company = new Company();
        company.setTenantId(tenantId);
        company.setLegalName(request.legalName());
        company.setCnpj(request.cnpj());
        company.setTradeName(request.tradeName());
        company.setStateRegistration(request.stateRegistration());
        company.setMunicipalRegistration(request.municipalRegistration());
        company.setPhone(request.phone());
        company.setEmail(request.email());
        company.setWebsite(request.website());
        company.setZipCode(request.zipCode());
        company.setStreet(request.street());
        company.setNumber(request.number());
        company.setComplement(request.complement());
        company.setNeighborhood(request.neighborhood());
        company.setCity(request.city());
        company.setState(request.state());
        companyRepository.save(company);

        User user = new User();
        user.setTenantId(tenantId);
        user.setName(request.adminName());
        user.setEmail(request.adminEmail());
        user.setPasswordHash(passwordEncoder.encode(request.senha()));
        user.setRole(Role.ADMIN);
        for (Module module : ALL_MODULES) {
            for (Action action : ALL_ACTIONS) {
                if (module == Module.USER && action == Action.DELETE) {
                    continue;
                }
                user.getPermissions().add(new UserPermissionGrant(module, action));
            }
        }
        userRepository.save(user);
    }

    private void issueTokenAndSendEmail(UUID tenantId, String adminEmail) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        TenantSignupToken token = new TenantSignupToken();
        token.setTenantId(tenantId);
        token.setTokenHash(sha256(rawToken));
        token.setExpiraEm(Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.save(token);

        String confirmLink = frontendOrigin + "/confirmar-cadastro?token=" + rawToken;
        mailService.sendSignupConfirmationEmail(adminEmail, confirmLink);
    }

    private String generateUniqueCodigo(String legalName, String tradeName) {
        String base = slugify(tradeName != null && !tradeName.isBlank() ? tradeName : legalName);
        String candidate = base;
        int suffix = 2;
        while (tenantRepository.existsByCodigo(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "tenant";
        }
        // Leaves room for a "-NN" collision suffix under the 50-char column limit.
        return slug.length() > 45 ? slug.substring(0, 45) : slug;
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
