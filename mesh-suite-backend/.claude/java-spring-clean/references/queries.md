# Queries: JPA Declarativo, JPQL, Nativas, Projeções

## 1. Spring Data JPA — Métodos Declarativos

Para queries simples. O Spring gera o SQL automaticamente pelo nome do método.

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // SELECT * FROM products WHERE active = true
    List<Product> findByActiveTrue();

    // SELECT * FROM products WHERE category_id = ? AND active = true
    List<Product> findByCategoryIdAndActiveTrue(UUID categoryId);

    // SELECT * FROM products WHERE name ILIKE '%?%'
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // Existe produto com esse nome na categoria?
    boolean existsByNameAndCategoryId(String name, UUID categoryId);

    // Contar ativos
    long countByActiveTrue();
}
```

---

## 2. JPQL com @Query

Quando o nome de método fica longo ou precisa de joins explícitos.

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Join explícito com fetch (evita N+1)
    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.customer c
        JOIN FETCH o.items i
        WHERE o.status = :status
        AND o.createdAt >= :since
        """)
    List<Order> findWithDetailsByStatusSince(
            @Param("status") OrderStatus status,
            @Param("since") LocalDateTime since);

    // Paginação com countQuery separado (performance)
    @Query(
        value = """
            SELECT o FROM Order o
            JOIN FETCH o.customer
            WHERE o.customer.id = :customerId
            """,
        countQuery = "SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId"
    )
    Page<Order> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);
}
```

---

## 3. Query Nativa — Funções PostgreSQL Específicas

Use quando precisar de: `JSONB`, `array_agg`, `date_trunc`, `ILIKE`, `unnest`, full-text search, window functions.

```java
@Repository
public interface ReportRepository extends JpaRepository<Order, UUID> {

    // Agregação por mês — date_trunc é específica do PostgreSQL
    @Query(value = """
        SELECT
            date_trunc('month', o.created_at) AS month,
            COUNT(o.id)                        AS total_orders,
            SUM(o.total_amount)                AS total_revenue,
            AVG(o.total_amount)                AS avg_ticket
        FROM orders o
        WHERE o.created_at BETWEEN :start AND :end
          AND (:customerId IS NULL OR o.customer_id = :customerId)
        GROUP BY 1
        ORDER BY 1
        """,
        nativeQuery = true)
    List<Object[]> findMonthlyRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("customerId") UUID customerId);
}
```

> **Prefira projeções** (interface ou record) a `Object[]` — mais seguro e legível.

---

## 4. Projeções — Interface ou Record

### Interface Projection (Spring Data nativo)

```java
public interface OrderSummary {
    UUID getId();
    String getCustomerName();
    BigDecimal getTotalAmount();
    OrderStatus getStatus();
    LocalDateTime getCreatedAt();
}

// No repository:
@Query("""
    SELECT o.id AS id,
           c.name AS customerName,
           o.totalAmount AS totalAmount,
           o.status AS status,
           o.createdAt AS createdAt
    FROM Order o
    JOIN o.customer c
    WHERE o.status = :status
    """)
List<OrderSummary> findSummaryByStatus(@Param("status") OrderStatus status);
```

### Record Projection com Query Nativa

```java
public record MonthlyRevenueProjection(
        LocalDateTime month,
        Long totalOrders,
        BigDecimal totalRevenue,
        BigDecimal avgTicket
) {}

// No repository (Spring Data 3.x suporta records como projeção via @SqlResultSetMapping ou constructor expression)
@Query(value = """
    SELECT
        date_trunc('month', created_at)::timestamp AS month,
        COUNT(id)::bigint                          AS totalOrders,
        SUM(total_amount)                          AS totalRevenue,
        AVG(total_amount)                          AS avgTicket
    FROM orders
    WHERE created_at BETWEEN :start AND :end
    GROUP BY 1
    ORDER BY 1
    """,
    nativeQuery = true)
List<MonthlyRevenueProjection> findMonthlyRevenue(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end);
```

---

## 5. Bulk Update / Delete com @Modifying

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Bulk soft delete
    @Modifying
    @Query("UPDATE Product p SET p.active = false WHERE p.category.id = :categoryId")
    int deactivateByCategory(@Param("categoryId") UUID categoryId);

    // Bulk update nativo (útil com arrays/jsonb do PostgreSQL)
    @Modifying
    @Query(value = """
        UPDATE products
        SET price = price * :multiplier,
            updated_at = now()
        WHERE category_id = :categoryId
          AND active = true
        """,
        nativeQuery = true)
    int applyPriceMultiplier(
            @Param("categoryId") UUID categoryId,
            @Param("multiplier") BigDecimal multiplier);
}
```

> **Sempre** anotar com `@Transactional` no service ao chamar métodos `@Modifying`.

---

## 6. Evitar N+1 — Estratégias

```java
// ❌ N+1: Hibernate faz 1 query pra orders + N queries pra cada customer
List<Order> orders = orderRepository.findAll();
orders.forEach(o -> System.out.println(o.getCustomer().getName())); // N queries!

// ✅ JOIN FETCH na query
@Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.status = :status")
List<Order> findWithCustomer(@Param("status") OrderStatus status);

// ✅ EntityGraph (alternativa declarativa)
@EntityGraph(attributePaths = {"customer", "items"})
List<Order> findByStatus(OrderStatus status);

// ✅ @BatchSize para coleções (ajuste no hibernate)
@BatchSize(size = 30)
@OneToMany(mappedBy = "order")
private List<OrderItem> items;
```

---

## 7. Queries com JSONB (PostgreSQL)

```java
// Entidade com campo JSONB
@Entity
@Table(name = "products")
public class Product {

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}

// Query nativa buscando dentro do JSONB
@Query(value = """
    SELECT * FROM products
    WHERE metadata @> :filter::jsonb
    """,
    nativeQuery = true)
List<Product> findByMetadata(@Param("filter") String jsonFilter);
// Chamada: repository.findByMetadata("{\"brand\": \"Apple\"}")
```

Dependência necessária:
```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```
