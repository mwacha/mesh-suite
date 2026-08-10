package com.meshsuite.auth.controller;

import com.meshsuite.auth.dto.ForgotPasswordRequest;
import com.meshsuite.auth.dto.LoginRequest;
import com.meshsuite.auth.dto.MeResponse;
import com.meshsuite.auth.dto.ResetPasswordRequest;
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
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        String ip = httpRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(ip, request.email())) {
            throw new RateLimitExceededException();
        }

        try {
            AuthService.LoginResult result = authService.authenticate(request.email(), request.senha());
            rateLimiter.recordSuccess(ip, request.email());

            String token = jwtService.generateToken(
                    result.user().getId(), result.tenant().getId(), result.company().getId(),
                    result.user().getRole().name(), request.manterConectado());

            long maxAgeSeconds = request.manterConectado() ? 30L * 24 * 3600 : 8L * 3600;
            ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(maxAgeSeconds)
                    .build();
            httpResponse.addHeader("Set-Cookie", cookie.toString());

            return ResponseEntity.ok().build();
        } catch (AuthException e) {
            rateLimiter.recordFailure(ip, request.email());
            throw e;
        }
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthContextService.Context principal) {
        String nome = authContextService.userName(principal.usuarioId());
        return new MeResponse(nome, principal.papel());
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
