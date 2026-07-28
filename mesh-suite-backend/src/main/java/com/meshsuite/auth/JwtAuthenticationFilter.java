package com.meshsuite.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "mesh_token";

    private final JwtService jwtService;
    private final AuthContextService authContextService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthContextService authContextService) {
        this.jwtService = jwtService;
        this.authContextService = authContextService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // The whole body -- not just chain.doFilter -- is wrapped in this one
        // try/finally. usuarioETenantAtivos() below can throw an unexpected runtime
        // exception (e.g. a DB connectivity issue), not just the JwtException/
        // IllegalArgumentException the inner catch handles; if that exception
        // escaped before TenantContext.clear() ran, this pooled servlet thread would
        // carry this request's tenant into whatever request reuses it next --
        // exactly the kind of leak a tenant-isolation boundary can't afford.
        try {
            String token = extractCookie(request, COOKIE_NAME);

            if (token != null) {
                try {
                    Claims claims = jwtService.parseClaims(token);
                    UUID usuarioId = UUID.fromString(claims.getSubject());
                    UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
                    String papel = claims.get("papel", String.class);

                    // Set before calling the transactional check so TenantContextAspect
                    // can scope that query to this tenant.
                    TenantContext.set(tenantId);

                    if (!authContextService.usuarioETenantAtivos(tenantId, usuarioId)) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }

                    var principal = new AuthContextService.Context(usuarioId, tenantId, papel);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + papel)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (JwtException | IllegalArgumentException e) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            }

            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
