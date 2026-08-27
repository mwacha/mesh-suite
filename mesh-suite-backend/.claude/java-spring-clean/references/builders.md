# Builders — Padrões e Localização

## Quando Usar Cada Abordagem

| Situação | Abordagem |
|---|---|
| DTO / record simples | Construtor direto — records já são builders naturais |
| Entidade com muitos campos opcionais | Factory method na entidade + Lombok `@Builder` interno |
| Montagem de `Pageable` | `PageableBuilder` em `shared/util/` |
| Dados de teste (fixtures) | Test Data Builder em `src/test/.../fixture/` |
| Query dinâmica | `Specification<T>` (não builder — veja `references/pagination.md`) |

---

## Localização

```
shared/util/
└── PageableBuilder.java        ← builder técnico, agnóstico de negócio

src/test/java/com/empresa/app/
└── fixture/
    ├── ProductFixture.java     ← Test Data Builder
    └── CategoryFixture.java    ← Test Data Builder
```

> **Nunca criar builders no `src/main/` para classes que já são records.**
> Records têm construtor canônico — builder seria redundância.

---

## PageableBuilder — Shared/Util

Centraliza a lógica de conversão `FilterRequest → Pageable`, evitando duplicação em cada service.

```java
// shared/util/PageableBuilder.java
public final class PageableBuilder {

    private PageableBuilder() {}

    public static Pageable from(int page, int size, String sortBy, String sortDirection) {
        Sort.Direction direction = parseDirection(sortDirection);
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    public static Pageable fromFilter(PaginatedFilter filter) {
        Sort.Direction direction = parseDirection(filter.sortDirection());
        return PageRequest.of(filter.page(), filter.size(),
                Sort.by(direction, filter.sortBy()));
    }

    private static Sort.Direction parseDirection(String direction) {
        try {
            return Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException e) {
            return Sort.Direction.DESC;  // default seguro
        }
    }
}
```

```java
// Interface marcadora para filtros paginados — opcional, mas melhora reutilização
// shared/dto/PaginatedFilter.java
public interface PaginatedFilter {
    int page();
    int size();
    String sortBy();
    String sortDirection();
}

// Uso: ProductFilterRequest implementa PaginatedFilter
public record ProductFilterRequest(
        String name, UUID categoryId,
        // ...
        int page, int size, String sortBy, String sortDirection
) implements PaginatedFilter { ... }
```

---

## Test Data Builder — Fixtures

Mantidos em `src/test/`, nunca em `src/main/`. Cada fixture cobre um contexto de domínio.
O padrão "builder de teste" usa métodos estáticos que retornam objetos prontos para uso,
com possibilidade de customização pontual.

```java
// src/test/java/com/empresa/app/fixture/CategoryFixture.java
public final class CategoryFixture {

    public static final Long   DEFAULT_TENANT = 1L;
    public static final String DEFAULT_NAME   = "Eletrônicos";

    private CategoryFixture() {}

    // Objeto pronto para salvar no banco (estado válido)
    public static Category valid() {
        return Category.create(DEFAULT_NAME, "Descrição padrão", DEFAULT_TENANT);
    }

    // Variações nomeadas para cenários específicos
    public static Category inactive() {
        Category c = valid();
        c.softDelete();
        return c;
    }

    public static Category withName(String name) {
        return Category.create(name, "Descrição padrão", DEFAULT_TENANT);
    }
}
```

```java
// src/test/java/com/empresa/app/fixture/ProductFixture.java
public final class ProductFixture {

    private ProductFixture() {}

    public static Product valid(Category category) {
        return Product.create("Notebook Pro", new BigDecimal("4500.00"),
                0, category, CategoryFixture.DEFAULT_TENANT);
    }

    public static Product withPrice(BigDecimal price, Category category) {
        return Product.create("Produto", price, 0, category, CategoryFixture.DEFAULT_TENANT);
    }

    public static Product inactive(Category category) {
        Product p = valid(category);
        p.softDelete();
        return p;
    }

    // Request DTO para testes de controller
    public static ProductRequest validRequest(UUID categoryId) {
        return new ProductRequest("Notebook Pro", "Descrição", new BigDecimal("4500.00"), 10, categoryId);
    }

    public static ProductFilterRequest defaultFilter() {
        return new ProductFilterRequest(null, null, null, null, null, 0, 20, "createdAt", "DESC");
    }
}
```

**Uso nos testes:**

```java
@Test
void create_comDadosValidos_deveRetornar201() throws Exception {
    Category category = categoryRepository.save(CategoryFixture.valid());
    ProductRequest request = ProductFixture.validRequest(category.getId());

    mockMvc.perform(post("/api/v1/products")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(request.name()));
}
```

---

## Lombok @Builder em Entidades — Quando e Como

Preferir factory methods. Usar `@Builder` apenas quando a entidade tem muitos campos
opcionais e o factory method ficaria longo demais.

```java
// ❌ Evitar: builder público expõe estado incompleto
@Builder
public class Product extends BaseEntity { ... }
Product.builder().name("x").build(); // active? tenantId? categoria?

// ✅ Preferir: factory method controla invariantes
public static Product create(String name, BigDecimal price,
                              Category category, Long tenantId) {
    // garante estado consistente na criação
}

// ✅ Se precisar de @Builder: escopo privado + factory method público
@Builder(access = AccessLevel.PRIVATE)
private Product(String name, BigDecimal price, Category category) { ... }

public static Product create(String name, BigDecimal price, Category category) {
    return Product.builder()
            .name(name).price(price).category(category)
            .build();
}
```
