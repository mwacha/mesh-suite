---
name: java-spring-clean
description: >
  Use this skill for any Java code generation task involving Spring Boot, JPA, Hibernate, or PostgreSQL.
  Triggers when the user asks to create, scaffold, or refactor Java backend code — especially controllers,
  services, repositories, entities, DTOs, or any Spring component. Always use this skill when the user
  mentions "Java", "Spring", "controller", "endpoint", "CRUD", "paginação", "filtro", "JPA", "query nativa",
  "Clean Code", "SOLID", "camada de serviço", "pedido", "venda", "cliente", "produto", "Lombok", "MapStruct",
  "JWT", "autenticação", "CORS", "Angular". Also triggers for refactoring or reviewing existing Java backend
  code for best practices. Context: REST API for an order registration and sales management system consumed
  by an Angular frontend. Covers Spring Boot 3.3+, Java 21, PostgreSQL 16+.
---

# Java Spring Clean — Skill de Geração de Código

Gera código Java production-ready com Spring Boot 3.x, Java 21+, PostgreSQL 16+, seguindo Clean Code e SOLID.

## Contexto do Sistema

REST API consumida por **Angular**. Domínio: cadastro de pedidos e gestão de vendas.
Módulos principais: `customer`, `product`, `order`, `sale`, `report`, `auth`.

## Stack de Referência

| Camada         | Tecnologia                                        |
|----------------|---------------------------------------------------|
| Framework      | Spring Boot 3.3+                                  |
| Linguagem      | Java 21 (records, text blocks, sealed classes)    |
| Persistência   | Spring Data JPA + Hibernate 6.x                   |
| Banco de dados | PostgreSQL 16+                                    |
| Migração       | Flyway                                            |
| Segurança      | Spring Security 6 + JWT (jjwt 0.12.x)            |
| Frontend       | Angular (CORS configurado para `localhost:4200`)   |
| Utilitários    | Lombok + MapStruct 1.5.x                          |
| Validação      | Jakarta Bean Validation 3                         |
| Docs           | springdoc-openapi 2.x (OpenAPI 3 / Swagger UI)    |
| Testes         | JUnit 5 + Mockito + Testcontainers (PostgreSQL)   |

> `pom.xml` completo com todas as dependências e configuração do compiler plugin: `references/dependencies.md`

---

## Princípios Obrigatórios

### Clean Code
- **Identificadores sempre em inglês**: classes, métodos, parâmetros, variáveis locais e
  campos (`create`/`update`/`delete`/`findById`/`list`, não `criar`/`atualizar`/`excluir`/
  `buscarPorId`/`listar`). Mensagens voltadas ao usuário final (exceções de validação,
  respostas de erro da API) continuam em português — o produto é para o mercado
  brasileiro, só o *código* segue o padrão inglês. Nomes de rota (`@RequestMapping`) e de
  colunas/constraints de banco já existentes não precisam ser renomeados retroativamente
  só por causa desta regra — vale para identificadores Java novos ou já sendo tocados.
- Nomes expressivos: sem abreviações (`usr` → `user`, `dto` → sufixo `Request`/`Response`)
- Métodos com responsabilidade única (máx ~20 linhas)
- Sem comentários óbvios; comentar apenas *por quê*, nunca *o quê* -- e o comentário em si também em inglês
- Constantes nomeadas, nunca magic numbers/strings
- Fail fast: validar entradas no topo do método

### SOLID
- **S**: cada classe tem uma única razão para mudar
- **O**: extensível via interfaces, fechado para modificação
- **L**: subtipos substituem o tipo pai sem quebrar comportamento
- **I**: interfaces coesas e pequenas (ex: `Findable<T>`, `Persistable<T>`)
- **D**: depender de abstrações; injetar via construtor (nunca `@Autowired` em field)

---

## Arquitetura de Camadas

```
controller/   → recebe HTTP, delega ao service, nunca contém lógica de negócio
service/      → lógica de negócio, orquestração, transações
repository/   → acesso a dados (JPA + queries nativas)
domain/       → entidades JPA
domain/enums/ → enums com semântica de negócio (OrderStatus, PaymentMethod)
dto/          → objetos de transferência (records Java)
mapper/       → conversão domain ↔ dto (MapStruct)
shared/       → classes agnósticas de negócio reutilizadas em múltiplos contextos
config/       → beans de configuração
```

### Regra dos Enums
- **`domain/enums/`** → enum pertence ao vocabulário de um único contexto (`OrderStatus`, `UserRole`)
- **`shared/enums/`** → enum é técnico e agnóstico de negócio (`SortDirection`)
- Enum com muitos dados associados (label, cor, permissão) → considerar entidade JPA

### Regra do `shared/`
> Se consegue descrever a classe **sem mencionar o domínio** da aplicação → `shared/`.

`shared/` contém: `PageResponse`, `ErrorResponse`, exceções base, validações customizadas, helpers técnicos.
`shared/` **nunca** contém: entidades, services, mappers, enums de domínio.

> Leia `references/layers.md` para estrutura de pacotes completa e exemplos.

---

## Controllers

### Regras Gerais
- Anotados com `@RestController` + `@RequestMapping("/api/v1/recurso")`
- Injeção via construtor (`final` + `@RequiredArgsConstructor`)
- Retornam sempre `ResponseEntity<T>`
- Nunca acessam o repositório diretamente
- Validam com `@Valid` nos parâmetros de entrada

### CRUD Padrão

```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    // --- Busca paginada com filtros no body e totalizadores ---

    @PostMapping("/search")
    public ResponseEntity<PagedWithSummaryResponse<ProductResponse, ProductSummary>> search(
            @RequestBody @Valid ProductFilterRequest filter) {
        return ResponseEntity.ok(productService.search(filter));
    }

    // --- CRUD ---

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @RequestBody @Valid ProductRequest request) {
        ProductResponse response = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### Endpoint de Busca — DTOs e Service

```java
// Filtro (body do POST /search)
public record ProductFilterRequest(
        String name, UUID categoryId,
        BigDecimal minPrice, BigDecimal maxPrice, Boolean active,
        @Min(0) int page, @Min(1) @Max(100) int size,
        String sortBy, String sortDirection
) {
    public ProductFilterRequest {
        if (page < 0)                              page = 0;
        if (size <= 0)                             size = 20;
        if (sortBy == null || sortBy.isBlank())    sortBy = "createdAt";
        if (sortDirection == null)                 sortDirection = "DESC";
    }
}

// Totalizador — específico do contexto, não vai para shared/
public record ProductSummary(
        long total, long active, long inactive,
        BigDecimal averagePrice, BigDecimal totalStockValue
) {}
```

```java
// Service — executa paginação (JPA) e totalizadores (query nativa) na mesma transação
@Transactional(readOnly = true)
public PagedWithSummaryResponse<ProductResponse, ProductSummary> search(
        ProductFilterRequest filter) {

    Pageable pageable = PageRequest.of(
            filter.page(), filter.size(),
            Sort.by(Sort.Direction.fromString(filter.sortDirection()), filter.sortBy()));

    Page<ProductResponse> page = productRepository
            .findAll(ProductSpecification.build(filter), pageable)
            .map(productMapper::toResponse);

    ProductSummaryProjection projection = productRepository.summarize(
            filter.name(),
            filter.categoryId() != null ? filter.categoryId().toString() : null,
            filter.minPrice(),
            filter.maxPrice(),
            filter.active()
    );

    return PagedWithSummaryResponse.from(page, toSummary(projection));
}
```

```java
// Repository — query nativa PostgreSQL para os totalizadores
@Query(value = """
        SELECT
            COUNT(*)                                  AS total,
            COUNT(*) FILTER (WHERE p.active = TRUE)   AS active,
            COUNT(*) FILTER (WHERE p.active = FALSE)  AS inactive,
            ROUND(AVG(p.price), 2)                    AS averagePrice,
            ROUND(SUM(p.price * p.stock), 2)          AS totalStockValue
        FROM products p
        WHERE (:name       IS NULL OR p.name        ILIKE '%' || :name || '%')
          AND (:categoryId IS NULL OR p.category_id = :categoryId::uuid)
          AND (:minPrice   IS NULL OR p.price       >= :minPrice)
          AND (:maxPrice   IS NULL OR p.price       <= :maxPrice)
          AND (:active     IS NULL OR p.active       = :active)
        """, nativeQuery = true)
ProductSummaryProjection summarize(
        @Param("name") String name,
        @Param("categoryId") String categoryId,   // UUID como String — cast ::uuid no SQL
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        @Param("active") Boolean active
);
```

**Response esperado:**
```json
{
  "content": [ { "id": "...", "name": "Notebook Pro", "price": 4500.00, "active": true } ],
  "page": 0, "size": 10, "totalElements": 42, "totalPages": 5,
  "first": true, "last": false,
  "summary": {
    "total": 42, "active": 35, "inactive": 7,
    "averagePrice": 2340.50, "totalStockValue": 187340.00
  }
}
```

> Detalhes completos (Specification, PagedWithSummaryResponse, ProjectionInterface): `references/pagination.md`

---

## DTOs com Records Java

```java
// Request
public record ProductRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 200)
    String name,

    @NotNull
    @Positive
    BigDecimal price,

    @NotNull
    UUID categoryId
) {}

// Response
public record ProductResponse(
    UUID id,
    String name,
    BigDecimal price,
    String categoryName,
    LocalDateTime createdAt
) {}

// Filtro para paginação (body da requisição)
public record ProductFilterRequest(
    String name,
    UUID categoryId,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Boolean active,

    @Min(0) int page,
    @Min(1) @Max(100) int size,
    String sortBy,
    String sortDirection
) {
    public ProductFilterRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (sortBy == null || sortBy.isBlank()) sortBy = "createdAt";
        if (sortDirection == null) sortDirection = "DESC";
    }
}
```

---

## Entidades JPA

### BaseEntity — Obrigatória em todas as entidades

Toda entidade herda de `BaseEntity`, que centraliza os campos de auditoria e soft delete.

```java
// domain/BaseEntity.java
@MappedSuperclass
@Getter
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;        // isolamento multi-tenant

    @Column(nullable = false)
    private Boolean active = true;

    @Column
    private LocalDateTime deletedAt;   // null = ativo; preenchido no softDelete()

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;       // preenchido automaticamente pelo AuditingEntityListener

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;       // preenchido automaticamente pelo AuditingEntityListener

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void softDelete() {
        this.active    = false;
        this.deletedAt = LocalDateTime.now();
        // updatedBy é preenchido automaticamente pelo listener
    }

    protected void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
```

### Entidade Concreta

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

    public static Product create(String name, BigDecimal price, Category category) {
        var product = new Product();
        product.name     = name;
        product.price    = price;
        product.category = category;
        product.setTenantId(TenantContext.get());  // resolvido do contexto da requisição
        return product;
    }

    public void update(String name, BigDecimal price) {
        this.name  = name;
        this.price = price;
    }
}
```

### Specification — Filtros Obrigatórios

Todo `Specification.build()` deve sempre incluir `belongsToTenant()` e `isActive()`:

```java
public static Specification<Product> build(ProductFilterRequest filter) {
    return Specification
            .where(belongsToTenant())          // NUNCA omitir — isola dados por tenant
            .and(isActive())                   // NUNCA omitir — exclui registros deletados
            .and(hasName(filter.name()))
            .and(hasCategory(filter.categoryId()));
}

private static Specification<Product> belongsToTenant() {
    return (root, query, cb) -> cb.equal(root.get("tenantId"), TenantContext.get());
}

private static Specification<Product> isActive() {
    return (root, query, cb) -> cb.isTrue(root.get("active"));
}
```

> Configuração do `AuditorAware`, `TenantContext`, `TenantFilter` e DDL completo: `references/base-entity.md`

---

## JPA vs Query Nativa — Quando Usar Cada Uma

| Cenário                                      | Abordagem                   |
|----------------------------------------------|-----------------------------|
| CRUD simples, filtros básicos                | Spring Data JPA (método declarativo) |
| Filtros dinâmicos (nullable)                 | `JpaSpecificationExecutor` + `Specification<T>` |
| Joins complexos, agregações, performance     | JPQL com `@Query`            |
| Funções específicas do PostgreSQL            | Query nativa `nativeQuery = true` |
| Relatórios, projeções complexas              | Query nativa + projeção (interface ou record) |
| Bulk updates/deletes                         | `@Modifying` + JPQL ou nativa |

> Leia `references/queries.md` para exemplos completos de cada padrão.

---

## Services

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public Page<ProductResponse> findAll(ProductFilterRequest filter) {
        Pageable pageable = buildPageable(filter);
        Specification<Product> spec = ProductSpecification.build(filter);
        return productRepository.findAll(spec, pageable)
                .map(productMapper::toResponse);
    }

    public ProductResponse findById(UUID id) {
        return productRepository.findById(id)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        Product product = Product.create(request.name(), request.price(), category);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.update(request.name(), request.price());
        return productMapper.toResponse(product); // dirty checking, sem save explícito
    }

    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.softDelete(); // deleção sempre lógica — nunca excluir fisicamente
    }

    private Pageable buildPageable(ProductFilterRequest filter) {
        Sort.Direction direction = Sort.Direction.fromString(filter.sortDirection());
        return PageRequest.of(filter.page(), filter.size(), Sort.by(direction, filter.sortBy()));
    }
}
```

---

## Operações em Lote

Todos os endpoints em lote seguem a garantia **tudo ou nada**: a anotação `@Transactional`
garante que qualquer exceção reverte a transação inteira — nenhuma alteração parcial persiste.

```java
// Controller
@PatchMapping("/batch/activate")
public ResponseEntity<BatchResultResponse> activate(@RequestBody @Valid BatchIdsRequest request) {
    return ResponseEntity.ok(productService.activate(request.ids()));
}

@PatchMapping("/batch/deactivate")
public ResponseEntity<BatchResultResponse> deactivate(@RequestBody @Valid BatchIdsRequest request) {
    return ResponseEntity.ok(productService.deactivate(request.ids()));
}

@DeleteMapping("/batch")
public ResponseEntity<Void> deleteBatch(@RequestBody @Valid BatchIdsRequest request) {
    productService.deleteBatch(request.ids());
    return ResponseEntity.noContent().build();
}
```

```java
// shared/dto/BatchIdsRequest.java
public record BatchIdsRequest(
        @NotEmpty(message = "A lista de IDs não pode ser vazia")
        @Size(max = 100, message = "Máximo de 100 itens por operação")
        List<@NotNull UUID> ids
) {}
```

```java
// Service — exclusão lógica em lote com fail fast (tudo ou nada)
@Transactional
public void deleteBatch(List<UUID> ids) {
    List<UUID> existing = productRepository.findExistingIds(ids, TenantContext.get());
    List<UUID> notFound = ids.stream()
            .filter(id -> !existing.contains(id))
            .toList();

    if (!notFound.isEmpty()) {
        throw new BusinessException("Produtos não encontrados: " + notFound);
        // @Transactional garante rollback — nenhum item é alterado
    }

    // Deleção sempre lógica: active=false + deletedAt=now()
    productRepository.softDeleteAllByIds(ids.toArray(UUID[]::new), TenantContext.get());
}

// Service — ativação/desativação em lote
@Transactional
public BatchResultResponse activate(List<UUID> ids) {
    validateAllExist(ids);
    return new BatchResultResponse(productRepository.activateAllByIds(ids));
}
```

```java
// Repository
@Modifying
@Query("UPDATE Product p SET p.active = true WHERE p.id IN :ids AND p.tenantId = :tenantId")
int activateAllByIds(@Param("ids") List<UUID> ids, @Param("tenantId") Long tenantId);

@Modifying
@Query("UPDATE Product p SET p.active = false WHERE p.id IN :ids AND p.tenantId = :tenantId")
int deactivateAllByIds(@Param("ids") List<UUID> ids, @Param("tenantId") Long tenantId);

// Deleção lógica em lote — ANY(:ids::uuid[]) mais eficiente que IN para arrays no PostgreSQL
@Modifying
@Query(value = """
        UPDATE products
        SET active = FALSE, deleted_at = NOW(), updated_at = NOW()
        WHERE id = ANY(:ids::uuid[]) AND tenant_id = :tenantId AND active = TRUE
        """, nativeQuery = true)
int softDeleteAllByIds(@Param("ids") UUID[] ids, @Param("tenantId") Long tenantId);

@Query("SELECT p.id FROM Product p WHERE p.id IN :ids AND p.tenantId = :tenantId AND p.active = TRUE")
List<UUID> findExistingIds(@Param("ids") List<UUID> ids, @Param("tenantId") Long tenantId);
```

> Detalhes completos (DTOs de resposta, validação auxiliar, exemplos de request/response): `references/batch.md`

---

## Tratamento de Exceções

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage(), "NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("Validation failed", errors));
    }
}

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, UUID id) {
        super("%s not found with id: %s".formatted(resource, id));
    }
}
```

---

## Referências Detalhadas

| Arquivo                      | Conteúdo                                                   |
|------------------------------|------------------------------------------------------------|
| `references/pagination.md`   | Controller paginado com filtros no body, Specification, resposta paginada, paginação com totalizadores via query nativa |
| `references/queries.md`      | JPA declarativo, JPQL, queries nativas, projeções, bulk ops |
| `references/layers.md`       | Exemplos completos das camadas controller/service/repository |
| `references/flyway.md`       | Convenção de migrations, exemplos de DDL PostgreSQL         |
| `references/batch.md`        | Ativação, desativação e exclusão lógica em lote (tudo ou nada) |
| `references/base-entity.md`  | BaseEntity, AuditorAware, TenantContext, TenantFilter, DDL  |
| `references/exceptions.md`   | Hierarquia de exceções, GlobalExceptionHandler, quando criar exceção de domínio |
| `references/builders.md`     | PageableBuilder, Test Data Builders (fixtures), Lombok @Builder em entidades |
| `references/auth.md`         | Spring Security 6, JWT, TokenProvider, AuthService, refresh token, roles, CORS Angular |
| `references/dependencies.md`  | pom.xml completo, Lombok annotations, application.yml, contexto de domínio |
| `references/menu-access.md`   | Controle de acesso a menus: Role, Permission, Menu, árvore filtrada, JWT, @PreAuthorize |

---

## Checklist de Geração

Antes de entregar o código, verificar:

- [ ] Identificadores (classes/métodos/variáveis/comentários) em inglês; mensagens de exceção/erro voltadas ao usuário em português
- [ ] Injeção de dependência via construtor
- [ ] DTOs como records Java
- [ ] `@Transactional(readOnly = true)` no service como padrão
- [ ] Paginação retorna `Page<T>` no service, `ResponseEntity<PageResponse<T>>` no controller
- [ ] Entidade com factory method, construtor `protected`
- [ ] Exceções com mensagens descritivas
- [ ] Validações com mensagens em português (ou inglês, conforme o projeto)
- [ ] Migrations Flyway com padrão `V{versão}__{descricao}.sql`
- [ ] Sem lógica de negócio no controller
- [ ] Sem acesso ao repositório fora do service
- [ ] Enums de domínio em `domain/enums/`, enums técnicos em `shared/enums/`
- [ ] Classes reutilizáveis agnósticas de negócio em `shared/` (não em `exception/` ou `dto/` soltos)
- [ ] `PageResponse`, `ErrorResponse` e exceções base sempre em `shared/`
- [ ] Endpoint com totalizador: `PagedWithSummaryResponse<T,S>` em `shared/`, `*Summary` e `*SummaryProjection` no contexto
- [ ] Query nativa do totalizador usa os mesmos filtros da listagem (consistência)
- [ ] UUID convertido para String ao passar para query nativa (`::uuid` no SQL)
- [ ] Toda entidade herda de `BaseEntity` (tenantId, active, deletedAt, createdBy, updatedBy)
- [ ] `tenantId` resolvido via `TenantContext.get()` no factory method — nunca vindo do request
- [ ] `Specification.build()` sempre inclui `belongsToTenant()` e `isActive()` como primeiros filtros
- [ ] Toda deleção usa `softDelete()` — nunca deletar fisicamente registros
- [ ] Operações em lote com `@Transactional` — garantia de tudo ou nada
- [ ] Validar existência de todos os IDs antes de qualquer alteração (fail fast)
- [ ] Batch delete usa UPDATE com `active=false, deleted_at=now()` — nunca DELETE SQL
- [ ] Queries em lote filtram por `tenantId` além dos IDs
- [ ] `BatchIdsRequest` em `shared/dto/` com `@Size(max=100)` para proteger contra abuso
- [ ] Exceções de domínio só criadas quando tratamento difere da base — preferir `BusinessException` com mensagem
- [ ] `GlobalExceptionHandler` em `shared/handler/` como único ponto de tradução exception → HTTP
- [ ] Auth isolado no pacote `auth/` — nunca misturar com outros contextos de domínio
- [ ] `TokenProvider` como interface — `JwtTokenProvider` como implementação (DIP)
- [ ] `JwtAuthenticationFilter` popula `TenantContext` e limpa no `finally`
- [ ] Fixtures de teste em `src/test/.../fixture/` — nunca em `src/main/`
- [ ] `PageableBuilder` em `shared/util/` — não duplicar lógica de `Pageable` em cada service
- [ ] Estrutura de pacotes por módulo/contexto (product/, auth/) — não por camada técnica
- [ ] `@Data` nunca usado em entidades JPA — apenas `@Getter` + `@NoArgsConstructor(PROTECTED)`
- [ ] Lombok e MapStruct com `annotationProcessorPaths` na ordem correta no `maven-compiler-plugin`
- [ ] CORS configurado via `CorsConfigurationSource` no `SecurityConfig` (não via `@CrossOrigin`)
- [ ] Controle de menus: módulo `access/` com `Role`, `Permission` e `Menu` — separado de `auth/`
- [ ] `Menu.requiredPermissionKey` referencia a chave da `Permission` necessária para exibição
- [ ] Árvore de menus construída em memória (uma query + filtro) — sem N+1 recursivo
- [ ] Permissions incluídas no JWT como `GrantedAuthority` para habilitar `@PreAuthorize`
- [ ] Convenção de chaves: `{recurso}:{ação}` (ex: `orders:read`, `products:write`)
- [ ] `GET /permissions` restrito a ADMIN — nunca aberto a usuários comuns
- [ ] Permissions no JWT: apenas chaves, nunca dados pessoais ou flags internas
- [ ] `PUT /roles/{id}/permissions` com auditoria obrigatória (quem, quando, o que mudou)
- [ ] Guard Angular é UX — `@PreAuthorize` no backend é a segurança real, sempre obrigatório
- [ ] Access token curto (15 min) para minimizar janela de permissions obsoletas
- [ ] `cors.allowed-origins` externalizado em `application.yml` via variável de ambiente
