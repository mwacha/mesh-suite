# BaseEntity — Campos Obrigatórios em Todas as Entidades

## Campos Obrigatórios

| Campo       | Tipo            | Descrição                                          |
|-------------|-----------------|----------------------------------------------------|
| `tenantId`  | `Long`          | Isolamento de dados entre tenants                  |
| `active`    | `Boolean`       | Controle de soft delete (false = deletado)         |
| `deletedAt` | `LocalDateTime` | Momento da deleção lógica (null = não deletado)    |
| `createdBy` | `UUID`          | ID do usuário que criou o registro                 |
| `updatedBy` | `UUID`          | ID do usuário da última atualização                |
| `createdAt` | `LocalDateTime` | Timestamp de criação (imutável)                    |
| `updatedAt` | `LocalDateTime` | Timestamp da última alteração                      |

---

## BaseEntity

```java
// domain/BaseEntity.java
@MappedSuperclass
@Getter
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Boolean active = true;

    @Column
    private LocalDateTime deletedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Deleção lógica — único ponto de entrada para deletar qualquer entidade.
     * Nunca deletar fisicamente registros; usar este método.
     */
    public void softDelete() {
        this.active    = false;
        this.deletedAt = LocalDateTime.now();
    }

    protected void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
```

---

## Entidade Concreta

```java
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    public static Product create(String name, BigDecimal price,
                                 Category category, Long tenantId) {
        var product = new Product();
        product.name     = name;
        product.price    = price;
        product.category = category;
        product.setTenantId(tenantId);   // obrigatório na criação
        return product;
    }

    public void update(String name, BigDecimal price) {
        this.name  = name;
        this.price = price;
        // updatedBy é preenchido automaticamente pelo AuditingEntityListener
    }
}
```

---

## Configuração do Spring Data Auditing

```java
// config/JpaAuditingConfig.java
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    /**
     * Fornece o UUID do usuário autenticado para @CreatedBy e @LastModifiedBy.
     * Adaptar conforme o mecanismo de autenticação do projeto (JWT, session, etc.).
     */
    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .map(auth -> {
                    // Assumindo que o principal carrega o UUID do usuário
                    Object principal = auth.getPrincipal();
                    if (principal instanceof UserDetails ud) {
                        return UUID.fromString(ud.getUsername());
                    }
                    return null;
                });
    }
}
```

---

## TenantId — De Onde Vem

O `tenantId` deve ser resolvido a partir do contexto da requisição (header, JWT claim, etc.)
e nunca recebido diretamente do cliente como campo de request.

```java
// shared/context/TenantContext.java
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static Long get() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("TenantId não definido no contexto");
        }
        return tenantId;
    }

    public static void set(Long tenantId) { CURRENT_TENANT.set(tenantId); }
    public static void clear()            { CURRENT_TENANT.remove(); }
}

// shared/filter/TenantFilter.java
@Component
@Order(1)
public class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        try {
            // Extrair do JWT claim, header X-Tenant-Id, subdomínio, etc.
            String tenantHeader = ((HttpServletRequest) request).getHeader("X-Tenant-Id");
            TenantContext.set(Long.parseLong(tenantHeader));
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();   // evita vazamento entre requisições
        }
    }
}
```

No factory method da entidade:
```java
public static Product create(String name, BigDecimal price, Category category) {
    var product = new Product();
    product.name     = name;
    product.price    = price;
    product.category = category;
    product.setTenantId(TenantContext.get());   // resolvido automaticamente
    return product;
}
```

---

## Migrations — Campos Comuns em Toda Tabela

```sql
-- Todos os campos da BaseEntity devem estar em toda tabela
CREATE TABLE products (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(200)   NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,
    category_id UUID           NOT NULL,

    -- BaseEntity
    tenant_id   BIGINT         NOT NULL,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMP,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_products          PRIMARY KEY (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_products_price   CHECK (price >= 0)
);

-- Índice de tenant é obrigatório em toda tabela (queries sempre filtram por tenant)
CREATE INDEX idx_products_tenant_id ON products (tenant_id);
CREATE INDEX idx_products_active     ON products (active) WHERE active = TRUE;
```

---

## Specification — Filtros Obrigatórios

Todo `Specification.build()` deve incluir os filtros de tenant e active:

```java
public static Specification<Product> build(ProductFilterRequest filter) {
    return Specification
            .where(belongsToTenant())          // SEMPRE — nunca omitir
            .and(isActive())                   // SEMPRE — exclui deletados
            .and(hasName(filter.name()))
            .and(hasCategory(filter.categoryId()))
            .and(hasMinPrice(filter.minPrice()))
            .and(hasMaxPrice(filter.maxPrice()));
}

private static Specification<Product> belongsToTenant() {
    return (root, query, cb) ->
            cb.equal(root.get("tenantId"), TenantContext.get());
}

private static Specification<Product> isActive() {
    return (root, query, cb) ->
            cb.isTrue(root.get("active"));
}
```

---

## Soft Delete em Lote

```java
// Repository — UPDATE em vez de DELETE
@Modifying
@Query(value = """
        UPDATE products
        SET active = FALSE, deleted_at = NOW(), updated_at = NOW()
        WHERE id = ANY(:ids::uuid[])
          AND tenant_id = :tenantId
          AND active = TRUE
        """, nativeQuery = true)
int softDeleteAllByIds(
        @Param("ids")      UUID[] ids,
        @Param("tenantId") Long tenantId
);

// Service
@Transactional
public void deleteBatch(List<UUID> ids) {
    List<UUID> existing = productRepository.findExistingIds(ids, TenantContext.get());
    List<UUID> notFound = ids.stream()
            .filter(id -> !existing.contains(id))
            .toList();

    if (!notFound.isEmpty()) {
        throw new BusinessException("Produtos não encontrados: " + notFound);
    }

    productRepository.softDeleteAllByIds(ids.toArray(UUID[]::new), TenantContext.get());
}
```
