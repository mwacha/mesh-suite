# Dependências — Sistema de Pedidos e Gestão de Vendas

## Contexto do Sistema

Backend REST consumido por frontend **Angular**. Funcionalidades centrais:
- Cadastro de clientes, produtos e categorias
- Registro e gestão de pedidos
- Controle de vendas e relatórios

---

## pom.xml Completo

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
    </parent>

    <groupId>com.empresa</groupId>
    <artifactId>sales-manager</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>sales-manager</name>
    <description>Sistema de cadastro de pedidos e gestão de vendas</description>

    <properties>
        <java.version>21</java.version>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
        <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
        <jjwt.version>0.12.6</jjwt.version>
        <springdoc.version>2.5.0</springdoc.version>
        <testcontainers.version>1.19.8</testcontainers.version>
    </properties>

    <dependencies>

        <!-- ================================================================ -->
        <!-- Spring Boot Starters                                             -->
        <!-- ================================================================ -->

        <!-- API REST -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- JPA / Hibernate -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Validação (Jakarta Bean Validation 3) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring Security (JWT + CORS) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- ================================================================ -->
        <!-- Banco de Dados                                                   -->
        <!-- ================================================================ -->

        <!-- Driver PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Flyway para PostgreSQL (inclui flyway-core) -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- ================================================================ -->
        <!-- Autenticação JWT                                                 -->
        <!-- ================================================================ -->

        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- ================================================================ -->
        <!-- Utilitários                                                      -->
        <!-- ================================================================ -->

        <!-- Lombok — @Getter, @RequiredArgsConstructor, @Slf4j, etc. -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
            <!-- optional=true: não vaza como dependência transitiva -->
        </dependency>

        <!-- MapStruct — geração de código para mapeamento domain ↔ DTO -->
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${mapstruct.version}</version>
        </dependency>

        <!-- OpenAPI 3 / Swagger UI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <!-- ================================================================ -->
        <!-- Testes                                                           -->
        <!-- ================================================================ -->

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
            <!-- inclui: JUnit 5, Mockito, AssertJ, MockMvc -->
        </dependency>

        <!-- Spring Security Test (@WithMockUser, SecurityMockMvcRequestPostProcessors) -->
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Testcontainers — PostgreSQL real nos testes de integração -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <!--
                        Ordem dos annotation processors é obrigatória:
                        1. Lombok processa primeiro (gera getters/setters)
                        2. MapStruct usa os métodos gerados pelo Lombok
                        3. lombok-mapstruct-binding garante essa ordem
                    -->
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok-mapstruct-binding</artifactId>
                            <version>${lombok-mapstruct-binding.version}</version>
                        </path>
                    </annotationProcessorPaths>
                    <compilerArgs>
                        <!-- MapStruct: usa injeção por construtor (padrão Spring) -->
                        <arg>-Amapstruct.defaultComponentModel=spring</arg>
                        <arg>-Amapstruct.defaultInjectionStrategy=constructor</arg>
                    </compilerArgs>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <!-- Exclui Lombok do jar final (só necessário em compile time) -->
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

---

## Lombok — Anotações Usadas no Projeto

| Anotação | Uso |
|---|---|
| `@Getter` | Gera getters para todos os campos — usado em entidades |
| `@NoArgsConstructor(access = PROTECTED)` | Construtor sem args para JPA, acesso restrito |
| `@RequiredArgsConstructor` | Construtor com todos os campos `final` — injeção via construtor |
| `@Slf4j` | Gera `log` como campo estático do Logger |
| `@Builder(access = PRIVATE)` | Builder privado quando necessário — acesso apenas via factory method |

```java
// ✅ Uso correto em entidade
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity { ... }

// ✅ Uso correto em service
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository; // final → injetado pelo construtor gerado
    ...
    log.info("Pedido {} criado para cliente {}", order.getId(), order.getCustomerId());
}

// ❌ Nunca usar @Data em entidades JPA
// @Data gera equals/hashCode baseado em todos os campos → problema com lazy loading
// @Data gera setter para todos os campos → viola o encapsulamento da entidade
```

---

## application.yml Completo

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:sales_db}
    username: ${DB_USERNAME:sales}
    password: ${DB_PASSWORD:sales}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        default_schema: public
        jdbc:
          time_zone: UTC
        order_inserts: true     # batch insert otimizado
        order_updates: true     # batch update otimizado
        jdbc.batch_size: 25

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

security:
  jwt:
    secret: ${JWT_SECRET}       # mínimo 256 bits em Base64
    expiration-ms: 900000       # 15 min (access token)

cors:
  allowed-origins: ${CORS_ORIGINS:http://localhost:4200}  # Angular dev server

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
  # Expõe Swagger apenas em dev/homologação — desabilitar em produção
  # packages-to-scan: com.empresa.sales

logging:
  level:
    com.empresa.sales: INFO
    org.hibernate.SQL: DEBUG      # remover em produção
```

---

## Contexto de Domínio — Pedidos e Vendas

Módulos esperados e seus pacotes:

```
customer/     → cadastro de clientes (PF e PJ)
product/      → produtos e categorias
order/        → pedidos (Order, OrderItem, OrderStatus)
sale/         → vendas consolidadas / faturamento
report/       → relatórios e dashboards (queries nativas)
auth/         → autenticação e autorização
```

Entidades centrais e relações:

```
Customer  1 ──── N  Order
Order     1 ──── N  OrderItem
OrderItem N ──── 1  Product
Product   N ──── 1  Category
Order     1 ──── 1  Sale       (quando o pedido é faturado)
```
