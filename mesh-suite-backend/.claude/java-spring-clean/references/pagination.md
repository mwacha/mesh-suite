# Paginação com Filtros no Body

## Por que filtros no body (POST)?

Filtros complexos com múltiplos campos opcionais são mais limpos via body do que query params.
Usa-se `@PostMapping("/search")` ou `@PostMapping("/filter")` como convenção.

---

## Controller — Endpoint de Busca Paginada

```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    /**
     * Busca paginada com filtros passados no body.
     * POST /api/v1/products/search
     */
    @PostMapping("/search")
    public ResponseEntity<PageResponse<ProductResponse>> search(
            @RequestBody @Valid ProductFilterRequest filter) {
        Page<ProductResponse> page = productService.findAll(filter);
        return ResponseEntity.ok(PageResponse.from(page));
    }
}
```

---

## DTO de Filtro

```java
public record ProductFilterRequest(
        String name,                    // ILIKE %name%
        UUID categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean active,

        @Min(0)
        int page,

        @Min(1) @Max(100)
        int size,

        String sortBy,
        String sortDirection          // ASC | DESC
) {
    // Compact constructor para aplicar defaults
    public ProductFilterRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (sortBy == null || sortBy.isBlank()) sortBy = "createdAt";
        if (sortDirection == null || sortDirection.isBlank()) sortDirection = "DESC";
    }
}
```

---

## PageResponse — Wrapper de Resposta

```java
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
```

---

## Specification — Filtros Dinâmicos (SOLID: OCP)

```java
public final class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> build(ProductFilterRequest filter) {
        return Specification
                .where(hasName(filter.name()))
                .and(hasCategory(filter.categoryId()))
                .and(hasMinPrice(filter.minPrice()))
                .and(hasMaxPrice(filter.maxPrice()))
                .and(isActive(filter.active()));
    }

    private static Specification<Product> hasName(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) return null;
            return cb.like(cb.lower(root.get("name")),
                    "%" + name.toLowerCase() + "%");
        };
    }

    private static Specification<Product> hasCategory(UUID categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null) return null;
            return cb.equal(root.get("category").get("id"), categoryId);
        };
    }

    private static Specification<Product> hasMinPrice(BigDecimal minPrice) {
        return (root, query, cb) -> {
            if (minPrice == null) return null;
            return cb.greaterThanOrEqualTo(root.get("price"), minPrice);
        };
    }

    private static Specification<Product> hasMaxPrice(BigDecimal maxPrice) {
        return (root, query, cb) -> {
            if (maxPrice == null) return null;
            return cb.lessThanOrEqualTo(root.get("price"), maxPrice);
        };
    }

    private static Specification<Product> isActive(Boolean active) {
        return (root, query, cb) -> {
            if (active == null) return null;
            return cb.equal(root.get("active"), active);
        };
    }
}
```

---

## Repository

```java
@Repository
public interface ProductRepository
        extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
}
```

---

## Service — findAll paginado

```java
@Transactional(readOnly = true)
public Page<ProductResponse> findAll(ProductFilterRequest filter) {
    Sort.Direction direction = Sort.Direction.fromString(filter.sortDirection());
    Pageable pageable = PageRequest.of(
            filter.page(),
            filter.size(),
            Sort.by(direction, filter.sortBy())
    );
    Specification<Product> spec = ProductSpecification.build(filter);
    return productRepository.findAll(spec, pageable)
            .map(productMapper::toResponse);
}
```

---

## Exemplo de Request/Response

**Request**
```json
POST /api/v1/products/search
{
  "name": "notebook",
  "categoryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "minPrice": 1000.00,
  "active": true,
  "page": 0,
  "size": 10,
  "sortBy": "price",
  "sortDirection": "ASC"
}
```

**Response**
```json
{
  "content": [...],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "first": true,
  "last": false
}
```

---

## Paginação com Totalizadores (Query Nativa PostgreSQL)

Usado quando o response precisa trazer, além dos itens paginados, um resumo agregado
(ex: total de ativos, inativos, valor médio) — calculado independente da página atual.

### Fluxo

```
Controller → Service → (em paralelo)
                         ├── repository.findAll(spec, pageable)  ← JPA/Specification
                         └── repository.summarize(filter)        ← query nativa PostgreSQL
             Service monta PagedWithSummaryResponse<T, S>
```

As duas queries rodam na mesma transação `readOnly = true`.

---

### DTO de Resposta com Totalizador

```java
// shared/dto/PagedWithSummaryResponse.java
public record PagedWithSummaryResponse<T, S>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        S summary                        // totalizador genérico
) {
    public static <T, S> PagedWithSummaryResponse<T, S> from(Page<T> page, S summary) {
        return new PagedWithSummaryResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                summary
        );
    }
}
```

```java
// dto/response/ProductSummary.java  ← específico do contexto, não vai para shared/
public record ProductSummary(
        long total,
        long active,
        long inactive,
        BigDecimal averagePrice,
        BigDecimal totalStockValue
) {}
```

---

### Repository — Query Nativa para o Totalizador

A query respeita os **mesmos filtros** da listagem, garantindo consistência entre itens e totais.

```java
@Repository
public interface ProductRepository
        extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @Query(value = """
        SELECT
            COUNT(*)                                        AS total,
            COUNT(*) FILTER (WHERE p.active = true)         AS active,
            COUNT(*) FILTER (WHERE p.active = false)        AS inactive,
            ROUND(AVG(p.price), 2)                          AS averagePrice,
            ROUND(SUM(p.price * p.stock), 2)                AS totalStockValue
        FROM products p
        WHERE (:name IS NULL
                OR p.name ILIKE '%' || :name || '%')
          AND (:categoryId IS NULL
                OR p.category_id = :categoryId::uuid)
          AND (:minPrice IS NULL
                OR p.price >= :minPrice)
          AND (:maxPrice IS NULL
                OR p.price <= :maxPrice)
        """,
        nativeQuery = true)
    ProductSummaryProjection summarize(
            @Param("name")       String name,
            @Param("categoryId") String categoryId,   // UUID como String para query nativa
            @Param("minPrice")   BigDecimal minPrice,
            @Param("maxPrice")   BigDecimal maxPrice
    );
}
```

```java
// Projeção de interface — Spring Data mapeia automaticamente pelo nome da coluna
public interface ProductSummaryProjection {
    Long getTotal();
    Long getActive();
    Long getInactive();
    BigDecimal getAveragePrice();
    BigDecimal getTotalStockValue();
}
```

---

### Service — Execução Paralela das Queries

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper     productMapper;

    public PagedWithSummaryResponse<ProductResponse, ProductSummary> search(
            ProductFilterRequest filter) {

        Pageable pageable = buildPageable(filter);
        Specification<Product> spec = ProductSpecification.build(filter);

        // Executa as duas queries na mesma transação
        Page<ProductResponse> page = productRepository
                .findAll(spec, pageable)
                .map(productMapper::toResponse);

        ProductSummary summary = buildSummary(
                productRepository.summarize(
                        filter.name(),
                        filter.categoryId() != null ? filter.categoryId().toString() : null,
                        filter.minPrice(),
                        filter.maxPrice()
                )
        );

        return PagedWithSummaryResponse.from(page, summary);
    }

    private ProductSummary buildSummary(ProductSummaryProjection projection) {
        return new ProductSummary(
                projection.getTotal(),
                projection.getActive(),
                projection.getInactive(),
                projection.getAveragePrice()   != null ? projection.getAveragePrice()   : BigDecimal.ZERO,
                projection.getTotalStockValue() != null ? projection.getTotalStockValue() : BigDecimal.ZERO
        );
    }

    private Pageable buildPageable(ProductFilterRequest filter) {
        Sort.Direction direction = Sort.Direction.fromString(filter.sortDirection());
        return PageRequest.of(filter.page(), filter.size(), Sort.by(direction, filter.sortBy()));
    }
}
```

---

### Controller

```java
@PostMapping("/search")
public ResponseEntity<PagedWithSummaryResponse<ProductResponse, ProductSummary>> search(
        @RequestBody @Valid ProductFilterRequest filter) {
    return ResponseEntity.ok(productService.search(filter));
}
```

---

### Exemplo de Request/Response

**Request**
```json
POST /api/v1/products/search
{
  "name": "notebook",
  "categoryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "minPrice": 1000.00,
  "page": 0,
  "size": 10,
  "sortBy": "price",
  "sortDirection": "ASC"
}
```

**Response**
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "first": true,
  "last": false,
  "summary": {
    "total": 42,
    "active": 35,
    "inactive": 7,
    "averagePrice": 2340.50,
    "totalStockValue": 187340.00
  }
}
```

---

### Boas Práticas desse Padrão

| Decisão | Motivo |
|---|---|
| Query nativa para o totalizador | `FILTER (WHERE ...)` e `ROUND()` são específicos do PostgreSQL e mais performáticos que JPQL para agregações |
| Mesmos parâmetros de filtro nas duas queries | Garante consistência — totais refletem exatamente o conjunto filtrado |
| `PagedWithSummaryResponse<T, S>` genérico em `shared/` | Reutilizável em qualquer contexto sem duplicação |
| `ProductSummary` e `ProductSummaryProjection` no contexto | São específicos de domínio — não pertencem a `shared/` |
| `buildSummary()` separado no service | Isola a conversão da projeção para o DTO; facilita testes unitários |
| UUID como `String` nos parâmetros nativos | Hibernate não converte `UUID` automaticamente em queries nativas — cast explícito com `::uuid` no SQL |
