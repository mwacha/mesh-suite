package com.meshsuite.auth.controller;

import com.meshsuite.auth.dto.ForgotPasswordRequest;
import com.meshsuite.auth.dto.LoginRequest;
import com.meshsuite.auth.dto.LoginResponse;
import com.meshsuite.auth.dto.MeResponse;
import com.meshsuite.auth.dto.ResetPasswordRequest;
import com.meshsuite.auth.dto.SelectAccountRequest;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.exception.RateLimitExceededException;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.auth.service.AuthService;
import com.meshsuite.auth.service.JwtService;
import com.meshsuite.auth.service.PasswordResetService;
import com.meshsuite.auth.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
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

    public AuthController(AuthService authService, JwtService jwtService, RateLimiter rateLimiter,
                           AuthContextService authContextService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.authContextService = authContextService;
        this.passwordResetService = passwordResetService;
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
                        .secure(true)
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
                .secure(true)
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
                .secure(true)
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
