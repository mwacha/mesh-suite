package com.meshsuite.auth.controller;

import com.meshsuite.auth.dto.ConfirmSignupRequest;
import com.meshsuite.auth.dto.ForgotPasswordRequest;
import com.meshsuite.auth.dto.LoginRequest;
import com.meshsuite.auth.dto.LoginResponse;
import com.meshsuite.auth.dto.MeResponse;
import com.meshsuite.auth.dto.ResetPasswordRequest;
import com.meshsuite.auth.dto.SelectAccountRequest;
import com.meshsuite.auth.dto.SignupRequest;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.exception.RateLimitExceededException;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.auth.service.AuthService;
import com.meshsuite.auth.service.JwtService;
import com.meshsuite.auth.service.PasswordResetService;
import com.meshsuite.auth.service.RateLimiter;
import com.meshsuite.auth.service.TenantSignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Distinct from JwtAuthenticationFilter.COOKIE_NAME (mesh_token, the real
    // session): this one only ever proves "these accounts' passwords were already
    // validated" to POST /select-account. The main auth filter never reads it, so
    // it can't be used to access anything on its own.
    private static final String PENDING_SELECTION_COOKIE_NAME = "mesh_pending_selection";

    private final AuthService authService;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final AuthContextService authContextService;
    private final PasswordResetService passwordResetService;
    private final TenantSignupService tenantSignupService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService, JwtService jwtService, RateLimiter rateLimiter,
                           AuthContextService authContextService, PasswordResetService passwordResetService,
                           TenantSignupService tenantSignupService,
                           @Value("${app.cookie-secure}") boolean cookieSecure) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.authContextService = authContextService;
        this.passwordResetService = passwordResetService;
        this.tenantSignupService = tenantSignupService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        String ip = httpRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(ip, request.email())) {
            throw new RateLimitExceededException();
        }

        try {
            AuthService.AuthOutcome outcome = authService.authenticate(request.email(), request.senha());
            rateLimiter.recordSuccess(ip, request.email());

            if (outcome instanceof AuthService.AuthOutcome.NeedsSelection needsSelection) {
                ResponseCookie cookie = ResponseCookie.from(PENDING_SELECTION_COOKIE_NAME, needsSelection.pendingToken())
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite("Strict")
                        .path("/api/auth")
                        .maxAge(5 * 60)
                        .build();
                httpResponse.addHeader("Set-Cookie", cookie.toString());

                List<LoginResponse.AccountOption> contas = needsSelection.options().stream()
                        .map(o -> new LoginResponse.AccountOption(o.tenantId(), o.companyName()))
                        .toList();
                return ResponseEntity.ok(new LoginResponse(contas));
            }

            AuthService.LoginResult result = ((AuthService.AuthOutcome.LoggedIn) outcome).result();
            issueSessionCookie(httpResponse, result, request.manterConectado());
            return ResponseEntity.ok(new LoginResponse(List.of()));
        } catch (AuthException e) {
            rateLimiter.recordFailure(ip, request.email());
            throw e;
        }
    }

    @PostMapping("/select-account")
    public ResponseEntity<Void> selectAccount(@Valid @RequestBody SelectAccountRequest request,
                                               @CookieValue(name = PENDING_SELECTION_COOKIE_NAME, required = false)
                                               String pendingToken,
                                               HttpServletResponse httpResponse) {
        if (pendingToken == null) {
            throw new AuthException();
        }

        AuthService.LoginResult result = authService.completeSelection(pendingToken, request.tenantId());
        issueSessionCookie(httpResponse, result, request.manterConectado());

        ResponseCookie clearPending = ResponseCookie.from(PENDING_SELECTION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
        httpResponse.addHeader("Set-Cookie", clearPending.toString());

        return ResponseEntity.ok().build();
    }

    private void issueSessionCookie(HttpServletResponse httpResponse, AuthService.LoginResult result,
                                     boolean manterConectado) {
        String token = jwtService.generateToken(
                result.user().getId(), result.tenant().getId(), result.company().getId(),
                result.user().getRole().name(), manterConectado);

        long maxAgeSeconds = manterConectado ? 30L * 24 * 3600 : 8L * 3600;
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        httpResponse.addHeader("Set-Cookie", cookie.toString());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthContextService.Context principal) {
        String nome = authContextService.userName(principal.usuarioId());
        String nomeEmpresa = authContextService.companyName(principal.tenantId());
        return new MeResponse(nome, principal.papel(), nomeEmpresa);
    }

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(ip, request.adminEmail())) {
            throw new RateLimitExceededException();
        }
        // Unlike /login, every attempt counts here, not just failures: signup abuse
        // is about request volume (spinning up junk tenants), not credential
        // guessing, so recordSuccess would let an attacker who varies CNPJ/e-mail on
        // every request wipe their own bucket each time and never trip isBlocked.
        rateLimiter.recordFailure(ip, request.adminEmail());
        tenantSignupService.signup(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirm-signup")
    public ResponseEntity<Void> confirmSignup(@Valid @RequestBody ConfirmSignupRequest request) {
        tenantSignupService.confirmSignup(request.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(ip, request.email())) {
            throw new RateLimitExceededException();
        }

        boolean found = passwordResetService.requestReset(request.email());
        if (found) {
            rateLimiter.recordSuccess(ip, request.email());
        } else {
            rateLimiter.recordFailure(ip, request.email());
        }
        return ResponseEntity.ok().build(); // same 200 regardless of `found` — no account enumeration
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.confirmReset(request.token(), request.novaSenha());
        return ResponseEntity.ok().build();
    }
}
