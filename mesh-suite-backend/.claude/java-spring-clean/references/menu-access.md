# Controle de Acesso a Menus

## Decisão de Design

A solução separa dois conceitos distintos em módulos distintos (SRP):

| Módulo | Responsabilidade |
|---|---|
| `auth/` | **Autenticação** — quem o usuário é (login, JWT, refresh token) |
| `access/` | **Autorização** — o que o usuário pode ver e fazer (roles, permissions, menus) |

### Fluxo Completo

```
Login → JWT inclui permissions[] → Angular guarda permissions
                                 ↓
              GET /api/v1/menus/my → backend filtra menus pelas permissions do usuário
                                 ↓
              Angular renderiza navegação dinâmica
              Angular guards protegem rotas com base nas permissions do JWT
              Backend protege endpoints com @PreAuthorize("hasAuthority('orders:read')")
```

### Modelo de Domínio

```
User ──N:N──> Role ──N:N──> Permission <──── Menu
                                              (requiredPermissionKey)
```

- `Role` agrupa permissions (ex: ADMIN, MANAGER, OPERADOR)
- `Permission` é uma ação granular (ex: `orders:read`, `products:write`)
- `Menu` declara qual permission é necessária para ser exibido
- Um menu sem `requiredPermissionKey` é visível para todos os autenticados

---

## Estrutura de Pacotes

```
access/
├── controller/
│   ├── MenuController.java
│   ├── RoleController.java
│   └── PermissionController.java
├── service/
│   ├── MenuService.java
│   └── AccessService.java             ← resolve permissions do usuário atual
├── domain/
│   ├── Menu.java                      ← extends BaseEntity (árvore self-referencing)
│   ├── Role.java                      ← extends BaseEntity
│   └── Permission.java                ← extends BaseEntity
├── repository/
│   ├── MenuRepository.java
│   ├── RoleRepository.java
│   └── PermissionRepository.java
├── dto/
│   ├── request/
│   │   ├── RoleRequest.java
│   │   ├── MenuRequest.java
│   │   └── RolePermissionsRequest.java
│   └── response/
│       ├── MenuResponse.java          ← árvore recursiva com children
│       ├── RoleResponse.java
│       └── PermissionResponse.java
└── mapper/
    ├── MenuMapper.java
    └── RoleMapper.java
```

---

## Entidades

```java
// access/domain/Permission.java
@Entity
@Table(name = "permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Convenção: "recurso:ação"  ex: orders:read, products:write, reports:export
    @Column(nullable = false, unique = true, length = 100)
    private String key;

    @Column(nullable = false, length = 200)
    private String description;

    public static Permission create(String key, String description, Long tenantId) {
        var p = new Permission();
        p.key         = key;
        p.description = description;
        p.setTenantId(tenantId);
        return p;
    }
}
```

```java
// access/domain/Role.java
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public static Role create(String name, String description, Long tenantId) {
        var r = new Role();
        r.name        = name;
        r.description = description;
        r.setTenantId(tenantId);
        return r;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }
}
```

```java
// access/domain/Menu.java
@Entity
@Table(name = "menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    // Rota Angular: "/orders", "/products/list", etc.
    @Column(length = 200)
    private String route;

    // Nome do ícone (Tabler Icons): "ti-shopping-cart", "ti-chart-bar"
    @Column(length = 80)
    private String icon;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    // Null = menu raiz; preenchido = submenu
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Menu parent;

    // Null = visível para qualquer autenticado; preenchido = requer essa permission
    @Column(length = 100)
    private String requiredPermissionKey;

    public static Menu create(String name, String route, String icon,
                              Integer displayOrder, Menu parent,
                              String requiredPermissionKey, Long tenantId) {
        var m = new Menu();
        m.name                  = name;
        m.route                 = route;
        m.icon                  = icon;
        m.displayOrder          = displayOrder;
        m.parent                = parent;
        m.requiredPermissionKey = requiredPermissionKey;
        m.setTenantId(tenantId);
        return m;
    }

    public boolean isAccessibleWith(Set<String> userPermissions) {
        return requiredPermissionKey == null
                || userPermissions.contains(requiredPermissionKey);
    }
}
```

---

## Repository

```java
@Repository
public interface MenuRepository extends JpaRepository<Menu, UUID> {

    // Carrega todos os menus ativos do tenant em uma única query (sem N+1)
    @Query("""
        SELECT m FROM Menu m
        LEFT JOIN FETCH m.parent
        WHERE m.tenantId = :tenantId
          AND m.active = TRUE
        ORDER BY m.displayOrder ASC
        """)
    List<Menu> findAllActiveByTenant(@Param("tenantId") Long tenantId);
}

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    // Carrega roles com permissions já em memória para montar o JWT
    @Query("""
        SELECT DISTINCT r FROM Role r
        JOIN FETCH r.permissions
        WHERE r.id IN :roleIds
          AND r.active = TRUE
        """)
    List<Role> findWithPermissionsByIds(@Param("roleIds") Set<UUID> roleIds);
}
```

---

## DTOs

```java
// Resposta recursiva — children são submenus acessíveis
public record MenuResponse(
        UUID   id,
        String name,
        String route,
        String icon,
        int    displayOrder,
        List<MenuResponse> children    // submenus filtrados pelas permissions
) {}

public record RoleRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255)          String description
) {}

public record RolePermissionsRequest(
        @NotEmpty Set<@NotNull UUID> permissionIds
) {}
```

---

## AccessService — Resolve Permissions do Usuário

```java
// access/service/AccessService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessService {

    private final RoleRepository roleRepository;

    /**
     * Retorna as chaves de permission do usuário.
     * Chamado na geração do JWT e no carregamento do UserDetails.
     */
    public Set<String> resolvePermissionKeys(Set<UUID> roleIds) {
        if (roleIds.isEmpty()) return Set.of();

        return roleRepository.findWithPermissionsByIds(roleIds)
                .stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getKey)
                .collect(Collectors.toSet());
    }
}
```

---

## MenuService — Árvore Filtrada por Permission

```java
// access/service/MenuService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuMapper     menuMapper;

    /**
     * Retorna a árvore de menus que o usuário autenticado pode ver.
     * Carrega todos os menus do tenant em uma query e monta a árvore em memória
     * para evitar N+1 e múltiplas queries recursivas.
     */
    public List<MenuResponse> getMyMenus() {
        Long          tenantId        = TenantContext.get();
        Set<String>   userPermissions = resolveCurrentUserPermissions();
        List<Menu>    allMenus        = menuRepository.findAllActiveByTenant(tenantId);

        return buildTree(allMenus, userPermissions, null);
    }

    // -------------------------------------------------------------------------

    private List<MenuResponse> buildTree(List<Menu> allMenus,
                                         Set<String> permissions,
                                         UUID parentId) {
        return allMenus.stream()
                .filter(m -> Objects.equals(parentId(m), parentId))
                .filter(m -> m.isAccessibleWith(permissions))
                .sorted(Comparator.comparing(Menu::getDisplayOrder))
                .map(m -> new MenuResponse(
                        m.getId(), m.getName(), m.getRoute(), m.getIcon(),
                        m.getDisplayOrder(),
                        buildTree(allMenus, permissions, m.getId())  // submenus
                ))
                // Remove grupos de menus que ficaram sem filhos acessíveis
                .filter(m -> m.route() != null || !m.children().isEmpty())
                .toList();
    }

    private UUID parentId(Menu menu) {
        return menu.getParent() != null ? menu.getParent().getId() : null;
    }

    private Set<String> resolveCurrentUserPermissions() {
        // Permissions já estão no SecurityContext (carregadas no UserDetails)
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
```

---

## Controller

```java
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Tag(name = "Menus")
public class MenuController {

    private final MenuService menuService;

    // Endpoint principal — chamado pelo Angular após o login
    @GetMapping("/my")
    public ResponseEntity<List<MenuResponse>> getMyMenus() {
        return ResponseEntity.ok(menuService.getMyMenus());
    }

    // CRUD de menus (apenas ADMIN)
    @GetMapping
    @PreAuthorize("hasAuthority('menus:read')")
    public ResponseEntity<List<MenuResponse>> findAll() {
        return ResponseEntity.ok(menuService.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('menus:write')")
    public ResponseEntity<MenuResponse> create(@RequestBody @Valid MenuRequest request) {
        MenuResponse response = menuService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
}

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles")
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @PreAuthorize("hasAuthority('roles:write')")
    public ResponseEntity<RoleResponse> create(@RequestBody @Valid RoleRequest request) {
        RoleResponse response = roleService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    // Atribuir permissions a um role
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('roles:write')")
    public ResponseEntity<RoleResponse> setPermissions(
            @PathVariable UUID id,
            @RequestBody @Valid RolePermissionsRequest request) {
        return ResponseEntity.ok(roleService.setPermissions(id, request));
    }
}
```

---

## Integração com JWT

As permissions são incluídas no JWT para que o Angular possa proteger as rotas sem
uma chamada extra ao backend.

```java
// auth/provider/JwtTokenProvider.java
@Override
public String generateAccessToken(User user, Set<String> permissions) {
    return Jwts.builder()
            .subject(user.getId().toString())
            .claim("tenantId",   user.getTenantId())
            .claim("permissions", permissions)          // ← lista de chaves
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getKey())
            .compact();
}
```

```java
// auth/service/UserDetailsServiceImpl.java
@Override
public UserDetails loadUserById(UUID userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    Set<String> permissions = accessService.resolvePermissionKeys(
            user.getRoles().stream().map(Role::getId).collect(Collectors.toSet()));

    // Permissions viram GrantedAuthority — habilitam @PreAuthorize
    List<GrantedAuthority> authorities = permissions.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

    return new org.springframework.security.core.userdetails.User(
            user.getId().toString(), user.getPassword(), authorities);
}
```

---

## Migrations

```sql
-- V6__create_table_permissions.sql
CREATE TABLE permissions (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    key         VARCHAR(100) NOT NULL,
    description VARCHAR(200) NOT NULL,
    tenant_id   BIGINT       NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMP,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_permissions     PRIMARY KEY (id),
    CONSTRAINT uq_permissions_key UNIQUE (key)
);

-- V7__create_table_roles.sql
CREATE TABLE roles (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    tenant_id   BIGINT       NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMP,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_roles PRIMARY KEY (id)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,
    CONSTRAINT pk_role_permissions  PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role           FOREIGN KEY (role_id)       REFERENCES roles(id),
    CONSTRAINT fk_rp_permission     FOREIGN KEY (permission_id) REFERENCES permissions(id)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user   FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ur_role   FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- V8__create_table_menus.sql
CREATE TABLE menus (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                    VARCHAR(100) NOT NULL,
    route                   VARCHAR(200),
    icon                    VARCHAR(80),
    display_order           INTEGER      NOT NULL DEFAULT 0,
    parent_id               UUID,
    required_permission_key VARCHAR(100),
    tenant_id               BIGINT       NOT NULL,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at              TIMESTAMP,
    created_by              UUID,
    updated_by              UUID,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_menus         PRIMARY KEY (id),
    CONSTRAINT fk_menus_parent  FOREIGN KEY (parent_id) REFERENCES menus(id)
);

CREATE INDEX idx_menus_tenant_id ON menus (tenant_id);
CREATE INDEX idx_menus_parent_id ON menus (parent_id);
```

---

## Seed de Dados Iniciais (Flyway)

```sql
-- V9__seed_permissions_and_roles.sql

-- Permissions por recurso (convenção: recurso:ação)
INSERT INTO permissions (key, description, tenant_id) VALUES
  ('orders:read',      'Visualizar pedidos',          1),
  ('orders:write',     'Criar e editar pedidos',       1),
  ('orders:delete',    'Deletar pedidos',              1),
  ('products:read',    'Visualizar produtos',          1),
  ('products:write',   'Criar e editar produtos',      1),
  ('customers:read',   'Visualizar clientes',          1),
  ('customers:write',  'Criar e editar clientes',      1),
  ('reports:read',     'Acessar relatórios',           1),
  ('roles:write',      'Gerenciar perfis de acesso',   1),
  ('menus:read',       'Visualizar menus',             1),
  ('menus:write',      'Gerenciar menus',              1);

-- Roles
INSERT INTO roles (id, name, description, tenant_id) VALUES
  ('role-admin-uuid',    'Administrador', 'Acesso total',                 1),
  ('role-manager-uuid',  'Gerente',       'Acesso operacional completo',   1),
  ('role-operator-uuid', 'Operador',      'Acesso somente leitura/vendas', 1);

-- Menus (árvore de dois níveis)
INSERT INTO menus (name, route, icon, display_order, parent_id, required_permission_key, tenant_id) VALUES
  ('Vendas',     NULL,              'ti-chart-bar',      1, NULL,            NULL,             1),
  ('Pedidos',    '/orders',         'ti-shopping-cart',  1, <vendas-id>,     'orders:read',    1),
  ('Clientes',   '/customers',      'ti-users',          2, <vendas-id>,     'customers:read', 1),
  ('Produtos',   '/products',       'ti-package',        3, NULL,            'products:read',  1),
  ('Relatórios', '/reports',        'ti-report',         4, NULL,            'reports:read',   1),
  ('Admin',      NULL,              'ti-settings',       5, NULL,            'roles:write',    1),
  ('Perfis',     '/admin/roles',    'ti-shield',         1, <admin-id>,      'roles:write',    1),
  ('Menus',      '/admin/menus',    'ti-layout',         2, <admin-id>,      'menus:write',    1);
```

---

## Exemplo de Response — GET /api/v1/menus/my

Usuário com role `Operador` (permissions: `orders:read`, `customers:read`):

```json
[
  {
    "id": "...",
    "name": "Vendas",
    "route": null,
    "icon": "ti-chart-bar",
    "displayOrder": 1,
    "children": [
      {
        "id": "...",
        "name": "Pedidos",
        "route": "/orders",
        "icon": "ti-shopping-cart",
        "displayOrder": 1,
        "children": []
      },
      {
        "id": "...",
        "name": "Clientes",
        "route": "/customers",
        "icon": "ti-users",
        "displayOrder": 2,
        "children": []
      }
    ]
  }
]
```

> Menus `Produtos`, `Relatórios` e `Admin` não aparecem — usuário não tem as permissions.
> Menu `Vendas` aparece mesmo sem `requiredPermissionKey` porque tem filhos acessíveis.

---

## Convenção de Chaves de Permission

```
{recurso}:{ação}

orders:read      → listar e visualizar pedidos
orders:write     → criar e editar pedidos
orders:delete    → deletar pedidos (soft delete)
products:read
products:write
customers:read
customers:write
reports:read
reports:export   → exportar relatórios (ação mais restrita)
roles:write      → gerenciar perfis
menus:write      → gerenciar menus
```

---

## Proteção de Endpoints com @PreAuthorize

```java
// Granular — endpoint exige permission específica
@GetMapping
@PreAuthorize("hasAuthority('orders:read')")
public ResponseEntity<PagedWithSummaryResponse<OrderResponse, OrderSummary>> search(...) { }

@PostMapping
@PreAuthorize("hasAuthority('orders:write')")
public ResponseEntity<OrderResponse> create(...) { }

@DeleteMapping("/batch")
@PreAuthorize("hasAuthority('orders:delete')")
public ResponseEntity<Void> deleteBatch(...) { }

// Múltiplas permissions aceitas
@GetMapping("/reports/revenue")
@PreAuthorize("hasAnyAuthority('reports:read', 'reports:export')")
public ResponseEntity<RevenueByPeriodResponse> revenueReport(...) { }
```

---

## ⚠️ Riscos de Segurança e Mitigações

### Risco 1 — `GET /permissions` expõe o mapa do sistema

Listar todas as chaves de permission revela ao atacante exatamente quais recursos e
operações existem, facilitando o mapeamento de superfície de ataque.

**Mitigação:**

```java
// ❌ Endpoint aberto ou com autenticação apenas
@GetMapping
@PreAuthorize("isAuthenticated()")
public ResponseEntity<List<PermissionResponse>> findAll() { ... }

// ✅ Restrito a quem gerencia permissões — somente ADMIN
@GetMapping
@PreAuthorize("hasAuthority('permissions:read')")   // role ADMIN exclusivo
public ResponseEntity<List<PermissionResponse>> findAll() { ... }
```

```
Regra: nenhum usuário comum deve ver a lista de permissions.
O endpoint de listagem deve ser acessível apenas por quem administra o sistema.
```

---

### Risco 2 — Permissions no JWT são legíveis

JWT usa Base64, **não criptografia**. Qualquer pessoa com o token pode decodificar
o payload e ver todas as permissions do usuário.

```
eyJhbGciOiJIUzI1NiJ9.eyJwZXJtaXNzaW9ucyI6WyJvcmRlcnM6cmVhZCIsInByb2R1Y3RzOndyaXRlIl19
                     ↑ decodificável por qualquer pessoa em jwt.io
```

**Não é um problema de segurança real** — o Angular precisa das permissions para guards
de rota, e a segurança verdadeira acontece no servidor via `@PreAuthorize`. Mas exige
atenção em dois pontos:

```java
// ✅ Nunca incluir no JWT informações sensíveis além de permissions
// ❌ Não colocar: dados pessoais, segredos, flags de feature interna
.claim("permissions", permissions)   // OK — apenas chaves como "orders:read"
.claim("cpf", user.getCpf())         // NUNCA
.claim("internalFlag", "bypass_audit") // NUNCA — revela operações internas

// ✅ Se a lista de permissions for muito grande, incluir só os roles
//    e resolver permissions no servidor por request (com cache)
.claim("roles", user.getRoles().stream().map(Role::getName).toList())
```

---

### Risco 3 — Permissions obsoletas no JWT (stale permissions)

Se um admin revoga uma permission de um usuário, o JWT antigo ainda carrega essa
permission até expirar. O usuário continua com acesso pelo tempo restante do token.

```
Revogação às 14h00 → JWT expira às 14h15 → usuário ainda acessa por 15 min
```

**Estratégia recomendada (escolher uma):**

```java
// Opção A — Access token curto (recomendado para sistemas sensíveis)
// Mantém a janela de permission obsoleta pequena
@Value("${security.jwt.expiration-ms:900000}")  // 15 minutos
private long expirationMs;

// Opção B — Blacklist de tokens revogados (cache Redis)
// Permite invalidação imediata, mas exige infraestrutura de cache
@Service
public class TokenBlacklistService {
    private final RedisTemplate<String, String> redis;

    public void revoke(String jti, long ttlSeconds) {
        redis.opsForValue().set("blacklist:" + jti, "1",
                Duration.ofSeconds(ttlSeconds));
    }

    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey("blacklist:" + jti));
    }
}
// No JwtAuthenticationFilter: verificar blacklist antes de autenticar

// Opção C — Não incluir permissions no JWT; resolver do banco por request
//           Com @Cacheable, o custo por request é mínimo
@Cacheable(value = "user-permissions", key = "#userId")
public Set<String> resolvePermissionKeys(UUID userId) { ... }

@CacheEvict(value = "user-permissions", key = "#userId")
public void revokeUserPermissions(UUID userId) { ... }
```

---

### Risco 4 — Endpoint de atribuição de roles é crítico

`PUT /roles/{id}/permissions` é a operação mais sensível do sistema — quem executa
essa chamada controla o que todos os outros usuários podem fazer.

```java
// ✅ Dupla proteção: permission + log de auditoria
@PutMapping("/{id}/permissions")
@PreAuthorize("hasAuthority('roles:write')")
public ResponseEntity<RoleResponse> setPermissions(
        @PathVariable UUID id,
        @RequestBody @Valid RolePermissionsRequest request) {

    RoleResponse response = roleService.setPermissions(id, request);

    // Registrar quem alterou, quando, e o que mudou
    auditLogService.log(
            AuditEvent.ROLE_PERMISSIONS_CHANGED,
            "Role %s permissions updated by %s".formatted(id, currentUserId())
    );

    return ResponseEntity.ok(response);
}
```

```
Regra: toda operação que altera roles ou permissions deve ser auditada com:
- quem executou (userId)
- quando (timestamp)
- o que mudou (diff de permissions: adicionadas e removidas)
```

---

### Risco 5 — Guards Angular são UX, não segurança

O guard Angular que usa as permissions do JWT para esconder rotas pode ser contornado
diretamente no browser — basta manipular o estado ou acessar a URL diretamente.

```typescript
// Angular guard — impede navegação acidental, não é barreira de segurança
canActivate(): boolean {
  return this.authService.hasPermission('orders:read');
}
```

```
Regra absoluta: TODA proteção real acontece no servidor.
@PreAuthorize no backend é obrigatório — nunca confiar só no guard do frontend.
O guard Angular é apenas UX para esconder opções que o usuário não pode usar.
```

---

### Resumo — O que é seguro e o que não é

| Endpoint / Comportamento | Seguro? | Ação |
|---|---|---|
| `GET /menus/my` — retorna só o que o usuário pode ver | ✅ | Sem restrição adicional |
| `GET /permissions` — lista todas as permissions | ⚠️ | Restringir a `permissions:read` (só ADMIN) |
| `GET /roles/{id}` — detalhes de um role | ⚠️ | Restringir a `roles:read` (só ADMIN) |
| `PUT /roles/{id}/permissions` — altera permissions | 🔴 | `roles:write` + auditoria obrigatória |
| Permissions no JWT — visíveis via Base64 | ⚠️ | Nunca incluir dados sensíveis; só chaves |
| Guard Angular protegendo rota | ⚠️ | Complementar ao `@PreAuthorize` — nunca substituto |
| `@PreAuthorize` no backend | ✅ | Sempre obrigatório para endpoints protegidos |
