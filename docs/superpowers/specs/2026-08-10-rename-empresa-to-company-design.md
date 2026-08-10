# Rename Empresa → Company (Código em Inglês) — Spec de Design

> Sub-projeto 2 da iniciativa "Rename para inglês" (sub-projeto 1, Venda→Sale, concluído e mesclado em `main`, commit `0e354fd`). Escopo completo da iniciativa: renomear todo o codebase mesh-suite de português para inglês, mantendo a exibição pro cliente final (rotas visíveis, textos de UI, labels, mensagens de erro) em português. Módulos ainda pendentes após este: Produto (+Categoria, CorEstampa, TabelaPreco), Parceiro, Município, Pedido.

## 1. Contexto e decisão

O módulo `empresa` é pequeno em arquivos próprios (uma entidade + um repositório, sem controller/service/DTOs), mas é referenciado de forma ampla: 22 arquivos em todo o backend, a maioria testes de controller de outros módulos que criam uma `Empresa` como parte do fixture de login (todo fluxo de autenticação exige uma empresa vinculada ao tenant). O módulo `auth` (já em inglês) também tem métodos e tipos internos nomeados a partir de "Empresa" (`saveEmpresa`, `listEmpresas`, `TenantAndEmpresa`) que precisam ser limpos junto, mesmo sem o módulo `auth` em si ser renomeado.

**Decisões já tomadas com o usuário:**
- `razaoSocial` → `legalName`.
- `cnpj` fica `cnpj` — sigla legal brasileira, mesmo padrão já aplicado a ICMS/IPI/PIS/COFINS no sub-projeto anterior (não traduz siglas legais/fiscais brasileiras).
- Migration `V2__create_empresa.sql` editada diretamente (renomeada para `V2__create_company.sql`), mesmo padrão do sub-projeto Venda→Sale — código greenfield, sem dado de produção, exige resetar o banco local para reaplicar as migrations do zero.

## 2. Escopo

### Incluído
- Pacote Java `com.meshsuite.empresa` → `com.meshsuite.company`: `Empresa`→`Company`, `EmpresaRepository`→`CompanyRepository`.
- Migration `V2` editada para criar a tabela `company` em vez de `empresa`.
- Limpeza das referências dentro de `auth` (não renomeado): `TenantQueryService.saveEmpresa()→saveCompany()`, `.listEmpresas()→listCompanies()`; `AuthService`: `record TenantAndEmpresa→TenantAndCompany`, `loadTenantAndEmpresa()→loadTenantAndCompany()`, `LoginResult(User, Tenant, Empresa)→LoginResult(User, Tenant, Company)`.
- Os 22 arquivos que referenciam `Empresa`/`EmpresaRepository` fora do próprio módulo, atualizados para `Company`/`CompanyRepository` (majoritariamente fixtures de teste idênticos, repetidos em cada `*ControllerTest`).
- `AppTopbar.vue`: classe CSS `.empresa-badge` → `.company-badge` (identificador de código).

### Fora de escopo
- Qualquer texto visível ao usuário final: label "Empresa" no menu lateral, texto "Empresa Principal" no topbar, e qualquer mensagem de erro — tudo continua em português, inalterado.
- Não existe `api/empresa.ts` nem tela própria de empresa no frontend hoje — nada a renomear ali além da classe CSS.
- Os demais módulos ainda em português (Pedido, Parceiro, Produto, Categoria, CorEstampa, TabelaPreco, Município) — ficam para os próximos sub-projetos.

## 3. Mapa de nomes completo

### Backend — classes

| Atual | Novo |
|---|---|
| `Empresa` | `Company` |
| `EmpresaRepository` | `CompanyRepository` |

### Backend — campos (`Company`)

`id`, `tenantId` (sem mudança) · `razaoSocial→legalName` · `cnpj` (sem mudança) · `ativo→active`

### Backend — `EmpresaRepository`

`findByTenantId(UUID tenantId)` — assinatura sem mudança, só o tipo de retorno (`List<Company>` em vez de `List<Empresa>`).

### Backend — dentro de `auth` (módulo não renomeado, só limpeza de referências)

- `TenantQueryService.java`: campo `empresaRepository→companyRepository`; método `saveEmpresa(UUID tenantId, String razaoSocial, String cnpj)→saveCompany(UUID tenantId, String legalName, String cnpj)`; método `listEmpresas()→listCompanies()`.
- `AuthService.java`: campo `empresaRepository→companyRepository`; `record LoginResult(User user, Tenant tenant, Empresa empresa)→LoginResult(User user, Tenant tenant, Company company)`; `private record TenantAndEmpresa(Tenant tenant, Empresa empresa)→TenantAndCompany(Tenant tenant, Company company)`; método `loadTenantAndEmpresa(UUID tenantId)→loadTenantAndCompany(UUID tenantId)`; variável local `loaded.empresa()→loaded.company()`.

### Banco de dados

Tabela `empresa→company`. Colunas: `razao_social→legal_name`, `cnpj`/`tenant_id`/`ativo→active` (id sem mudança, `ativo` vira `active`). Índice `idx_empresa_tenant_id→idx_company_tenant_id`. Política RLS `empresa_tenant_isolation→company_tenant_isolation`.

### Os 22 arquivos que referenciam Empresa fora do módulo

Todos seguem o mesmo padrão mecânico — trocar `Empresa`/`EmpresaRepository`/`empresaRepository` por `Company`/`CompanyRepository`/`companyRepository`, e os setters `setRazaoSocial(...)→setLegalName(...)`. Lista completa (verificada via `grep -rl "com\.meshsuite\.empresa\|\bEmpresa\b" mesh-suite-backend/src --include="*.java"`):

- `auth/service/AuthService.java`, `auth/service/TenantQueryService.java` (já detalhados acima)
- `auth/controller/AuthControllerNoAmbientTransactionTest.java`, `AuthControllerTest.java`, `PasswordResetControllerNoAmbientTransactionTest.java`
- `auth/TenantIsolationTest.java`
- `municipio/controller/MunicipioControllerTest.java`
- `parceiro/controller/ParceiroControllerTest.java`
- `payable/controller/AccountsPayableControllerTest.java`
- `pedido/controller/PedidoControllerTest.java`
- `produto/controller/CategoriaControllerTest.java`, `CorEstampaControllerTest.java`, `ProdutoControllerTest.java`, `TabelaPrecoControllerTest.java`
- `purchaseorder/controller/PurchaseOrderControllerTest.java`
- `sale/controller/SaleControllerTest.java`
- `stock/controller/StockMovementControllerTest.java`
- `tenant/repository/TenantRepositoryTest.java`
- `user/controller/UserControllerTest.java`

### Frontend

- `mesh-suite-frontend/src/components/AppTopbar.vue`: classe CSS `.empresa-badge` → `.company-badge` (usada tanto no template quanto no bloco `<style>`). Texto visível `"Empresa Principal"` inalterado.

## 4. Testes

Mesma cobertura de hoje, renomeada: `CompanyRepositoryTest` (mirror de `EmpresaRepositoryTest`, mesmos casos). Os 17 arquivos de teste de outros módulos mantêm exatamente os mesmos casos de teste, só com os identificadores renomeados nos seus fixtures — nenhuma lógica de teste muda.

## 5. Riscos e notas abertas

1. **Reset do banco local necessário** — mesma situação do sub-projeto Venda→Sale: como a migration `V2` é editada em vez de gerar uma migration de rename, qualquer banco local que já tenha aplicado a versão antiga precisa ser recriado (`docker-compose down -v` + subir de novo).
2. **Blast radius amplo mas raso**: 22 arquivos tocados, mas a mudança em cada um é mecânica e pequena (renomear identificadores num fixture de teste) — não há lógica de negócio nova a validar, diferente do sub-projeto Venda.
3. **Ordem dos próximos sub-projetos** ainda não decidida — será definida quando este for concluído.
