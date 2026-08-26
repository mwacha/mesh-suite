# Autenticação — Estrutura e Implementação

## Estrutura de Pacotes

```
auth/                                      ← módulo isolado, não acessa outros contextos
├── controller/
│   └── AuthController.java
├── service/
│   ├── AuthService.java                   ← orquestra login, refresh, logout
│   └── UserDetailsServiceImpl.java        ← contrato Spring Security
├── dto/
│   ├── request/
│   │   ├── LoginRequest.java
│   │   └── RefreshTokenRequest.java
│   └── response/
│       └── AuthResponse.java              ← accessToken + refreshToken + expiresIn
├── domain/
│   ├── User.java                          ← extends BaseEntity
│   ├── RefreshToken.java                  ← extends BaseEntity
│   └── enums/
│       └── UserRole.java
├── repository/
│   ├── UserRepository.java
│   └── RefreshTokenRepository.java
├── filter/
│   └── JwtAuthenticationFilter.java       ← OncePerRequestFilter
└── provider/
    ├── TokenProvider.java                  ← interface (DIP)
    └── JwtTokenProvider.java              ← implementação JWT

config/
└── SecurityConfig.java                    ← SecurityFilterChain + CORS + rotas públicas
```

---

## Domínio

```java
// auth/domain/User.java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    public static User create(String email, String encodedPassword,
                              UserRole role, Long tenantId) {
        var user = new User();
        user.email    = email;
        user.password = encodedPassword;
        user.role     = role;
        user.setTenantId(tenantId);
        return user;
    }

    // UserDetails — Spring Security
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getUsername()            { return email; }
    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isAccountNonLocked()   { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()            { return getActive(); }
}
```

```java
// auth/domain/enums/UserRole.java
public enum UserRole { ADMIN, MANAGER, USER }
```

```java
// auth/domain/RefreshToken.java
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public static RefreshToken create(String token, User user,
                                      LocalDateTime expiresAt) {
        var rt = new RefreshToken();
        rt.token     = token;
        rt.user      = user;
        rt.expiresAt = expiresAt;
        rt.setTenantId(user.getTenantId());
        return rt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
```

---

## TokenProvider — Interface (DIP)

```java
// auth/provider/TokenProvider.java
public interface TokenProvider {
    String generateAccessToken(User user);
    String generateRefreshToken();
    UUID   extractUserId(String token);
    Long   extractTenantId(String token);
    boolean isValid(String token);
}
```

```java
// auth/provider/JwtTokenProvider.java
@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements TokenProvider {

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-ms:900000}")   // 15 min
    private long expirationMs;

    @Override
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role",     user.getRole().name())
                .claim("tenantId", user.getTenantId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getKey())
                .compact();
    }

    @Override
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    @Override
    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    @Override
    public Long extractTenantId(String token) {
        return parseClaims(token).get("tenantId", Long.class);
    }

    @Override
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(getKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
```

---

## JwtAuthenticationFilter

```java
// auth/filter/JwtAuthenticationFilter.java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenProvider          tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && tokenProvider.isValid(token)) {
            UUID userId   = tokenProvider.extractUserId(token);
            Long tenantId = tokenProvider.extractTenantId(token);

            UserDetails userDetails = userDetailsService.loadUserById(userId);
            var auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(tenantId);          // popula o contexto de tenant
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();                // evita vazamento entre requisições
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

---

## AuthService

```java
// auth/service/AuthService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final long REFRESH_TOKEN_DAYS = 30;

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenProvider          tokenProvider;
    private final PasswordEncoder        passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(u -> u.getActive())
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Credenciais inválidas");
            // Mensagem igual para não revelar se o e-mail existe
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenAndActiveTrue(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido"));

        if (refreshToken.isExpired()) {
            refreshToken.softDelete();
            throw new UnauthorizedException("Refresh token expirado");
        }

        refreshToken.softDelete();    // invalida o token atual (rotação)
        return buildAuthResponse(refreshToken.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenAndActiveTrue(rawRefreshToken)
                .ifPresent(RefreshToken::softDelete);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken();

        RefreshToken rt = RefreshToken.create(
                refreshToken, user,
                LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));
        refreshTokenRepository.save(rt);

        return new AuthResponse(accessToken, refreshToken, 900L);
    }
}
```

---

## AuthController

```java
// auth/controller/AuthController.java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody @Valid RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
```

---

## DTOs

```java
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank         String password
) {}

public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {}

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long   expiresIn      // segundos
) {}
```

---

## SecurityConfig

```java
// config/SecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api-docs/**",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (req, res, e) -> res.sendError(401, "Não autenticado"))
                        .accessDeniedHandler(
                                (req, res, e) -> res.sendError(403, "Acesso negado"))
                )
                .build();
    }

    /**
     * CORS configurado para aceitar requisições do Angular.
     * Em desenvolvimento: http://localhost:4200
     * Em produção: URL real do frontend (via env CORS_ORIGINS)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Id"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);   // preflight cache: 1 hora

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

**application.yml:**
```yaml
cors:
  allowed-origins: ${CORS_ORIGINS:http://localhost:4200}
  # Múltiplas origens (homologação + produção): http://app.hml.com,https://app.com
```

---

## application.yml — Configuração de Segurança

```yaml
security:
  jwt:
    secret: ${JWT_SECRET}          # mínimo 256 bits em base64
    expiration-ms: 900000          # 15 minutos (access token)
```

---

## Migration — Tabelas de Auth

```sql
-- V4__create_table_users.sql
CREATE TABLE users (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    email      VARCHAR(150) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL,

    -- BaseEntity
    tenant_id  BIGINT       NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users       PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email     ON users (email);
CREATE INDEX idx_users_tenant_id ON users (tenant_id);

-- V5__create_table_refresh_tokens.sql
CREATE TABLE refresh_tokens (
    id         UUID      NOT NULL DEFAULT gen_random_uuid(),
    token      TEXT      NOT NULL,
    user_id    UUID      NOT NULL,
    expires_at TIMESTAMP NOT NULL,

    -- BaseEntity
    tenant_id  BIGINT    NOT NULL,
    active     BOOLEAN   NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_tokens      PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_token     ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_tenant_id ON refresh_tokens (tenant_id);
```

---

## Autorização por Role — @PreAuthorize

```java
// Proteger endpoints por role sem lógica no controller
@GetMapping("/admin/report")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ReportResponse> adminReport() { ... }

@DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public ResponseEntity<Void> delete(@PathVariable UUID id) { ... }
```
