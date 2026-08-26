# Exemplos Completos por Camada

## Estrutura de Pacotes

> **Exemplo de referência** baseado em um sistema de cadastro de pedidos e gestão de vendas.
> Adapte os módulos e entidades ao domínio real do seu projeto.

```
com.empresa.salesmanager/
│
├── auth/                                         ← autenticação e autorização
│   ├── controller/
│   │   └── AuthController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   └── UserDetailsServiceImpl.java
│   ├── provider/
│   │   ├── TokenProvider.java                    ← interface (DIP)
│   │   └── JwtTokenProvider.java
│   ├── filter/
│   │   └── JwtAuthenticationFilter.java
│   ├── domain/
│   │   ├── User.java                             ← extends BaseEntity
│   │   ├── RefreshToken.java                     ← extends BaseEntity
│   │   └── enums/
│   │       └── UserRole.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   └── RefreshTokenRepository.java
│   └── dto/
│       ├── request/
│       │   ├── LoginRequest.java
│       │   └── RefreshTokenRequest.java
│       └── response/
│           └── AuthResponse.java
│
├── access/                                          ← autorização: roles, permissions, menus
│   ├── controller/
│   │   ├── MenuController.java
│   │   ├── RoleController.java
│   │   └── PermissionController.java
│   ├── service/
│   │   ├── MenuService.java
│   │   └── AccessService.java                    ← resolve permissions do usuário atual
│   ├── domain/
│   │   ├── Menu.java                             ← extends BaseEntity (self-referencing)
│   │   ├── Role.java                             ← extends BaseEntity
│   │   └── Permission.java                       ← extends BaseEntity
│   ├── repository/
│   │   ├── MenuRepository.java
│   │   ├── RoleRepository.java
│   │   └── PermissionRepository.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RoleRequest.java
│   │   │   ├── MenuRequest.java
│   │   │   └── RolePermissionsRequest.java
│   │   └── response/
│   │       ├── MenuResponse.java                 ← árvore recursiva com children[]
│   │       ├── RoleResponse.java
│   │       └── PermissionResponse.java
│   └── mapper/
│       ├── MenuMapper.java
│       └── RoleMapper.java
│
├── customer/                                     ← cadastro de clientes
│   ├── controller/
│   │   └── CustomerController.java
│   ├── service/
│   │   └── CustomerService.java
│   ├── domain/
│   │   ├── Customer.java                         ← extends BaseEntity
│   │   └── enums/
│   │       └── CustomerType.java
│   ├── repository/
│   │   ├── CustomerRepository.java
│   │   └── specification/
│   │       └── CustomerSpecification.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── CustomerRequest.java
│   │   │   └── CustomerFilterRequest.java
│   │   └── response/
│   │       ├── CustomerResponse.java
│   │       └── CustomerSummary.java
│   └── mapper/
│       └── CustomerMapper.java
│
├── product/                                      ← produtos e categorias
│   ├── controller/
│   │   └── ProductController.java
│   ├── service/
│   │   └── ProductService.java
│   ├── domain/
│   │   ├── Product.java                          ← extends BaseEntity
│   │   ├── Category.java                         ← extends BaseEntity
│   │   └── enums/
│   │       └── ProductStatus.java
│   ├── repository/
│   │   ├── ProductRepository.java
│   │   ├── ProductSummaryProjection.java          ← interface de projeção nativa
│   │   └── specification/
│   │       └── ProductSpecification.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── ProductRequest.java
│   │   │   └── ProductFilterRequest.java
│   │   └── response/
│   │       ├── ProductResponse.java
│   │       └── ProductSummary.java
│   └── mapper/
│       └── ProductMapper.java
│
├── order/                                        ← pedidos e itens
│   ├── controller/
│   │   └── OrderController.java
│   ├── service/
│   │   └── OrderService.java
│   ├── domain/
│   │   ├── Order.java                            ← extends BaseEntity
│   │   ├── OrderItem.java                        ← extends BaseEntity
│   │   └── enums/
│   │       ├── OrderStatus.java
│   │       └── PaymentMethod.java
│   ├── repository/
│   │   ├── OrderRepository.java
│   │   ├── OrderItemRepository.java
│   │   ├── OrderSummaryProjection.java            ← interface de projeção nativa
│   │   └── specification/
│   │       └── OrderSpecification.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── OrderRequest.java
│   │   │   ├── OrderItemRequest.java
│   │   │   └── OrderFilterRequest.java
│   │   └── response/
│   │       ├── OrderResponse.java
│   │       ├── OrderItemResponse.java
│   │       └── OrderSummary.java
│   └── mapper/
│       └── OrderMapper.java
│
├── sale/                                         ← gestão de vendas
│   ├── controller/
│   │   └── SaleController.java
│   ├── service/
│   │   └── SaleService.java
│   ├── domain/
│   │   └── Sale.java                             ← extends BaseEntity
│   ├── repository/
│   │   ├── SaleRepository.java
│   │   └── SaleSummaryProjection.java
│   ├── dto/
│   │   └── response/
│   │       ├── SaleResponse.java
│   │       └── SaleSummary.java
│   └── mapper/
│       └── SaleMapper.java
│
├── report/                                       ← relatórios e dashboards
│   ├── controller/
│   │   └── ReportController.java
│   ├── service/
│   │   └── ReportService.java
│   ├── repository/
│   │   └── ReportRepository.java                 ← queries nativas, sem entidade JPA
│   └── dto/
│       └── response/
│           ├── SalesReportResponse.java
│           ├── RevenueByPeriodResponse.java
│           └── TopProductsResponse.java
│
│ ─────────────────────────────────────────────── ← separação: negócio | técnico
│
├── shared/                                       ← agnóstico de negócio, sem regras de domínio
│   ├── domain/
│   │   └── BaseEntity.java                       ← @MappedSuperclass (tenantId, active, auditoria)
│   ├── context/
│   │   └── TenantContext.java                    ← ThreadLocal de tenant
│   ├── filter/
│   │   └── TenantFilter.java                     ← popula TenantContext por requisição
│   ├── exception/
│   │   ├── AppException.java                     ← base abstrata
│   │   ├── ResourceNotFoundException.java        ← 404
│   │   ├── BusinessException.java                ← 422
│   │   ├── ConflictException.java                ← 409
│   │   ├── UnauthorizedException.java            ← 401
│   │   └── ForbiddenException.java               ← 403
│   ├── handler/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ErrorResponse.java
│   │   └── ValidationErrorResponse.java
│   ├── dto/
│   │   ├── PagedWithSummaryResponse.java         ← genérico <T, S>
│   │   ├── BatchIdsRequest.java
│   │   ├── BatchResultResponse.java
│   │   └── PaginatedFilter.java                  ← interface para filtros paginados
│   ├── enums/
│   │   └── SortDirection.java
│   ├── validation/
│   │   └── ValidUUID.java
│   └── util/
│       └── PageableBuilder.java
│
└── config/
    ├── SecurityConfig.java                       ← filtro JWT + CORS para Angular
    ├── JpaAuditingConfig.java                    ← AuditorAware<UUID>
    └── OpenApiConfig.java
```

### Princípios da Estrutura

| Regra | Motivo |
|---|---|
| Organizado por módulo/contexto, não por camada técnica | Tudo que pertence a um contexto fica junto — facilita manutenção e eventual extração |
| Cada módulo é auto-contido | Controller, service, repository, domain e dto dentro do mesmo pacote |
| `shared/` é o único pacote que atravessa módulos | Nada de domínio vaza para outros módulos; só contratos técnicos |
| Separação visual entre negócio e técnico | `auth/` a `report/` = domínio; `shared/` e `config/` = infraestrutura |
| `*Projection` junto ao `*Repository` que a usa | A projeção é contrato da query — mora no mesmo pacote |

---

## Regras de Localização — Enums e Classes Compartilhadas

### Enums

A pergunta que define onde o enum mora:
> *"Esse enum pertence ao vocabulário de um contexto específico?"*

| Situação | Onde colocar |
|---|---|
| Enum de domínio de um único contexto (`OrderStatus`, `PaymentMethod`) | `domain/enums/` |
| Enum técnico usado em múltiplos contextos (`SortDirection`) | `shared/enums/` |
| Enum que pode virar entidade no futuro (ex: `Category`) | Criar entidade desde já |

```java
// ✅ domain/enums/OrderStatus.java — semântica de negócio específica
public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED;
    }
}

// ✅ shared/enums/SortDirection.java — técnico, agnóstico
public enum SortDirection {
    ASC, DESC;

    public Sort.Direction toSpring() {
        return Sort.Direction.fromString(this.name());
    }
}
```

### Classes em `shared/`

Regra de ouro: **se você consegue descrever a classe sem mencionar o domínio da aplicação, ela pertence a `shared/`.**

```
✅ shared/  →  PageResponse, ErrorResponse, AppException, PageableBuilder
❌ shared/  →  OrderService, ProductMapper, CustomerValidator  (têm semântica de negócio)
```

**Nunca colocar em `shared/`:**
- Entidades JPA
- Services com regra de negócio
- Enums com semântica de domínio (`OrderStatus`, `UserRole`)
- Mappers específicos de contexto

---

## Mapper com MapStruct

```java
@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(source = "category.name", target = "categoryName")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponseList(List<Product> products);

    // Sem @Mapping aqui — conversão é feita no service pela entidade
    // (evitar anemia de domínio: Product.create() é o factory)
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>1.5.5.Final</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

---

## Hierarquia de Exceções

```java
// Base
public abstract class AppException extends RuntimeException {
    private final String errorCode;

    protected AppException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}

// Especializações
public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resource, UUID id) {
        super("%s not found: %s".formatted(resource, id), "RESOURCE_NOT_FOUND");
    }
}

public class BusinessException extends AppException {
    public BusinessException(String message) {
        super(message, "BUSINESS_ERROR");
    }
}

public class ConflictException extends AppException {
    public ConflictException(String message) {
        super(message, "CONFLICT");
    }
}
```

---

## GlobalExceptionHandler Completo

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex, request));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex, request));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse(
                        "Validation failed", request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(
                        "Internal server error", "INTERNAL_ERROR",
                        request.getRequestURI(), LocalDateTime.now()));
    }
}

// DTOs de erro
public record ErrorResponse(
        String message,
        String errorCode,
        String path,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(AppException ex, HttpServletRequest req) {
        return new ErrorResponse(ex.getMessage(), ex.getErrorCode(),
                req.getRequestURI(), LocalDateTime.now());
    }
}

public record ValidationErrorResponse(
        String message,
        String path,
        List<FieldError> errors
) {}

public record FieldError(String field, String message) {}
```

---

## application.yml — Configuração Base

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate          # NUNCA create/update em produção
    show-sql: false               # true apenas em dev/debug
    properties:
      hibernate:
        format_sql: true
        default_schema: public
        jdbc:
          time_zone: UTC

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha

logging:
  level:
    org.hibernate.SQL: DEBUG       # remover em produção
    com.empresa.app: INFO
```

---

## Testando com Testcontainers

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void shouldReturnPagedProducts() throws Exception {
        var filter = new ProductFilterRequest(null, null, null, null, true, 0, 10, "name", "ASC");

        mockMvc.perform(post("/api/v1/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }
}
```
