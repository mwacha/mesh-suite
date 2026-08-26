# Operações em Lote (Batch)

## Padrões Cobertos

| Operação              | Método HTTP | Endpoint              | Garantia transacional |
|-----------------------|-------------|----------------------|-----------------------|
| Ativar em lote        | PATCH       | `/batch/activate`    | Tudo ou nada          |
| Desativar em lote     | PATCH       | `/batch/deactivate`  | Tudo ou nada          |
| Excluir em lote       | DELETE      | `/batch`             | Tudo ou nada (soft delete) |

> **Toda deleção é lógica.** Nunca executar `DELETE` SQL. Excluir = setar `active = false` + `deleted_at = NOW()`.

---

## DTOs de Entrada

```java
// Shared — reutilizável para qualquer operação em lote que receba IDs
// shared/dto/BatchIdsRequest.java
public record BatchIdsRequest(

        @NotEmpty(message = "A lista de IDs não pode ser vazia")
        @Size(max = 100, message = "Máximo de 100 itens por operação")
        List<@NotNull UUID> ids
) {}
```

---

## Controller

```java
@PatchMapping("/batch/activate")
public ResponseEntity<BatchResultResponse> activate(
        @RequestBody @Valid BatchIdsRequest request) {
    return ResponseEntity.ok(productService.activate(request.ids()));
}

@PatchMapping("/batch/deactivate")
public ResponseEntity<BatchResultResponse> deactivate(
        @RequestBody @Valid BatchIdsRequest request) {
    return ResponseEntity.ok(productService.deactivate(request.ids()));
}

@DeleteMapping("/batch")
public ResponseEntity<Void> deleteBatch(
        @RequestBody @Valid BatchIdsRequest request) {
    productService.deleteBatch(request.ids());
    return ResponseEntity.noContent().build();
}
```

```java
// Resposta das operações de ativação/desativação
// dto/response/BatchResultResponse.java
public record BatchResultResponse(int affected) {}
```

---

## Repository

```java
@Repository
public interface ProductRepository
        extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    // Ativação em lote — JPQL é suficiente (sem função PostgreSQL específica)
    @Modifying
    @Query("UPDATE Product p SET p.active = true WHERE p.id IN :ids")
    int activateAllByIds(@Param("ids") List<UUID> ids);

    // Desativação em lote
    @Modifying
    @Query("UPDATE Product p SET p.active = false WHERE p.id IN :ids")
    int deactivateAllByIds(@Param("ids") List<UUID> ids);

    /**
     * Exclusão física em lote.
     *
     * Usa = ANY(:ids::uuid[]) — sintaxe PostgreSQL mais eficiente que IN para arrays.
     * O cast ::uuid[] é necessário porque JDBC envia o array como bytea sem ele.
     */
    @Modifying
    @Query(value = "DELETE FROM products WHERE id = ANY(:ids::uuid[])",
           nativeQuery = true)
    int hardDeleteAllByIds(@Param("ids") UUID[] ids);

    // Verifica quais IDs do lote existem de fato na base
    @Query("SELECT p.id FROM Product p WHERE p.id IN :ids")
    List<UUID> findExistingIds(@Param("ids") List<UUID> ids);
}
```

---

## Service — Ativação e Desativação

```java
@Transactional
public BatchResultResponse activate(List<UUID> ids) {
    validateAllExist(ids);
    int affected = productRepository.activateAllByIds(ids);
    return new BatchResultResponse(affected);
}

@Transactional
public BatchResultResponse deactivate(List<UUID> ids) {
    validateAllExist(ids);
    int affected = productRepository.deactivateAllByIds(ids);
    return new BatchResultResponse(affected);
}
```

---

## Service — Exclusão em Lote (Tudo ou Nada)

A garantia "tudo ou nada" vem do `@Transactional`: qualquer exceção lançada dentro do
método faz o Spring reverter toda a transação automaticamente.

A estratégia é **fail fast**: validar que todos os IDs existem *antes* de tocar no banco.
Isso evita exclusões parciais e deixa a mensagem de erro precisa.

```java
@Transactional
public void deleteBatch(List<UUID> ids) {
    Long tenantId = TenantContext.get();

    // 1. Verifica existência já filtrando por tenant e active=true
    List<UUID> existing = productRepository.findExistingIds(ids, tenantId);
    List<UUID> notFound = ids.stream()
            .filter(id -> !existing.contains(id))
            .toList();

    // 2. Qualquer ID ausente aborta tudo — nenhum registro é alterado
    if (!notFound.isEmpty()) {
        throw new BusinessException(
                "Produtos não encontrados para exclusão: " + notFound);
    }

    // 3. Deleção lógica em lote — active=false + deleted_at=now()
    productRepository.softDeleteAllByIds(ids.toArray(UUID[]::new), tenantId);
}
```

---

## Validação Auxiliar (usada por ativar e desativar)

```java
// Reutilizado por activate() e deactivate()
private void validateAllExist(List<UUID> ids) {
    Long tenantId = TenantContext.get();
    List<UUID> existing = productRepository.findExistingIds(ids, tenantId);
    List<UUID> notFound = ids.stream()
            .filter(id -> !existing.contains(id))
            .toList();

    if (!notFound.isEmpty()) {
        throw new BusinessException("Produtos não encontrados: " + notFound);
    }
}
```

---

## Exemplo de Request / Response

**Ativar em lote**
```
PATCH /api/v1/products/batch/activate
{ "ids": ["uuid-1", "uuid-2", "uuid-3"] }

200 OK
{ "affected": 3 }
```

**Excluir em lote (tudo ou nada)**
```
DELETE /api/v1/products/batch
{ "ids": ["uuid-1", "uuid-2", "uuid-invalido"] }

422 Unprocessable Entity
{
  "message": "Produtos não encontrados para exclusão: [uuid-invalido]",
  "errorCode": "BUSINESS_ERROR"
}
```
→ Nenhum dos três produtos foi excluído.

---

## Boas Práticas

| Decisão | Motivo |
|---|---|
| `= ANY(:ids::uuid[])` na exclusão nativa | Mais eficiente que `IN (?, ?, ?)` para listas grandes; evita limite de parâmetros JDBC |
| Validar existência antes de alterar | Fail fast: erro preciso, zero efeito colateral |
| `findExistingIds` retorna só os que existem | Uma query para validar todo o lote, sem N+1 |
| Limite de 100 itens no `@Size` | Protege contra payloads abusivos; ajustar conforme o domínio |
| `BatchIdsRequest` em `shared/` | Agnóstico de negócio — serve para qualquer recurso |
| `BatchResultResponse` no contexto | Poderia ir para `shared/` se outros recursos usarem a mesma estrutura |
