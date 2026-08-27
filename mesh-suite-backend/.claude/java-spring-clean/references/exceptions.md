# Exceptions — Hierarquia e Estrutura de Pacotes

## Localização

```
shared/exception/
├── AppException.java              ← base abstrata (nunca instanciar diretamente)
├── ResourceNotFoundException.java ← 404 Not Found
├── BusinessException.java         ← 422 Unprocessable Entity
├── ConflictException.java         ← 409 Conflict
├── UnauthorizedException.java     ← 401 Unauthorized
└── ForbiddenException.java        ← 403 Forbidden

shared/handler/
└── GlobalExceptionHandler.java    ← único ponto de tradução exception → HTTP

auth/exception/                    ← exceções específicas do módulo de auth
└── InvalidTokenException.java     (extends UnauthorizedException)

product/exception/                 ← exceções específicas de domínio (só se necessário)
└── ProductOutOfStockException.java (extends BusinessException)
```

**Regra:** usar `BusinessException` com mensagem descritiva para casos simples.
Criar classe nomeada apenas quando a exceção precisa ser tratada diferente de sua base
ou quando o domínio é complexo o suficiente para justificar o tipo explícito.

---

## Hierarquia

```java
// shared/exception/AppException.java
public abstract class AppException extends RuntimeException {

    private final String errorCode;

    protected AppException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}

// 404
public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resource, UUID id) {
        super("%s não encontrado: %s".formatted(resource, id), "RESOURCE_NOT_FOUND");
    }
    public ResourceNotFoundException(String resource, String field, String value) {
        super("%s não encontrado com %s: %s".formatted(resource, field, value), "RESOURCE_NOT_FOUND");
    }
}

// 422 — regra de negócio violada
public class BusinessException extends AppException {
    public BusinessException(String message) {
        super(message, "BUSINESS_ERROR");
    }
}

// 409 — conflito de estado (ex: e-mail já cadastrado)
public class ConflictException extends AppException {
    public ConflictException(String message) {
        super(message, "CONFLICT");
    }
}

// 401 — não autenticado
public class UnauthorizedException extends AppException {
    public UnauthorizedException(String message) {
        super(message, "UNAUTHORIZED");
    }
}

// 403 — autenticado mas sem permissão
public class ForbiddenException extends AppException {
    public ForbiddenException(String message) {
        super(message, "FORBIDDEN");
    }
}
```

---

## GlobalExceptionHandler

Um único handler cobre toda a hierarquia. Handlers mais específicos sobrescrevem os gerais
(OCP: novos tipos de exceção não exigem modificar o handler).

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        return response(HttpStatus.NOT_FOUND, ex, req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex, HttpServletRequest req) {
        return response(HttpStatus.CONFLICT, ex, req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest req) {
        return response(HttpStatus.UNAUTHORIZED, ex, req);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            ForbiddenException ex, HttpServletRequest req) {
        return response(HttpStatus.FORBIDDEN, ex, req);
    }

    // Captura BusinessException e qualquer outra AppException não mapeada acima
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            AppException ex, HttpServletRequest req) {
        log.warn("Erro de negócio: {}", ex.getMessage());
        return response(HttpStatus.UNPROCESSABLE_ENTITY, ex, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("Erro de validação", req.getRequestURI(), errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Erro interno", "INTERNAL_ERROR",
                        req.getRequestURI(), LocalDateTime.now()));
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> response(HttpStatus status,
                                                    AppException ex,
                                                    HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getMessage(), ex.getErrorCode(),
                        req.getRequestURI(), LocalDateTime.now()));
    }
}
```

---

## DTOs de Erro

```java
// shared/handler/ErrorResponse.java
public record ErrorResponse(
        String message,
        String errorCode,
        String path,
        LocalDateTime timestamp
) {}

// shared/handler/ValidationErrorResponse.java
public record ValidationErrorResponse(
        String message,
        String path,
        List<FieldError> errors
) {}

public record FieldError(String field, String message) {}
```

---

## Exceção de Domínio — Quando Criar

```java
// ✅ Justificado: comportamento diferente da base, domínio complexo
// auth/exception/InvalidTokenException.java
public class InvalidTokenException extends UnauthorizedException {
    public InvalidTokenException() {
        super("Token inválido ou expirado");
    }
}

// ✅ Justificado: clareza semântica em domínio com muitas regras
// order/exception/OrderAlreadyPaidException.java
public class OrderAlreadyPaidException extends ConflictException {
    public OrderAlreadyPaidException(UUID orderId) {
        super("Pedido %s já foi pago".formatted(orderId));
    }
}

// ❌ Não justificado: BusinessException já é suficiente
// NÃO criar: ProductNameTooLongException, InvalidPriceException, etc.
// Usar: throw new BusinessException("Nome do produto excede 200 caracteres")
```

---

## Mapeamento Exception → HTTP

| Exception                  | HTTP Status                  |
|----------------------------|------------------------------|
| `ResourceNotFoundException` | `404 Not Found`              |
| `BusinessException`         | `422 Unprocessable Entity`   |
| `ConflictException`         | `409 Conflict`               |
| `UnauthorizedException`     | `401 Unauthorized`           |
| `ForbiddenException`        | `403 Forbidden`              |
| `AppException` (genérica)   | `422 Unprocessable Entity`   |
| `MethodArgumentNotValidException` | `400 Bad Request`      |
| `Exception` (não mapeada)   | `500 Internal Server Error`  |
