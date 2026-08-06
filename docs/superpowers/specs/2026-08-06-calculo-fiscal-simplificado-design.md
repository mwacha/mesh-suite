# Cálculo Fiscal Simplificado — Spec de Design

> Sub-projeto 4 de 5 da iniciativa "Compras completa". Ver `docs/superpowers/specs/2026-08-04-ordem-compra-design.md` seção 1 para a decomposição inteira.

## 1. Contexto e decisão

`PRD-11-fiscal-tributario.md` documenta o domínio Fiscal/Tributário do sisconf legado como um núcleo amplo: Cadastro Fiscal (regra de tributação por natureza de operação), emissão eletrônica de NF-e/NFC-e (assinatura digital, envio à autoridade fiscal estadual, DANFE), e escrituração fiscal (SPED Fiscal, apuração de ICMS, Sintegra). É o único domínio do sistema com integração eletrônica real com um órgão externo.

A decomposição do Compras já limitou esta fatia a "só o essencial de ICMS/IPI/PIS/COFINS por item necessário pra Compra funcionar, não o `PRD-11` inteiro" — o `PRD-07-compras.md` confirma que a Compra só precisa de tributação por item calculada e registrada como um retrato fixo no momento do lançamento, não recalculada depois. Emissão de NF-e, SPED, certificado digital, ICMS-ST, Simples Nacional/CSON e os campos de IBS/CBS da reforma tributária pertencem ao `PRD-11` completo e não são necessários para a Compra funcionar.

Esta fatia entrega só a capacidade de cálculo — sem tela, mesmo padrão do Estoque mínimo — para o sub-projeto 5 (Compra/nota fiscal de entrada) consumir.

## 2. Escopo

### Incluído
- `FiscalRegistration`: cadastro de regra de tributação (descrição/natureza da operação, CFOP, CST do ICMS, alíquotas de ICMS/IPI/PIS/COFINS).
- `Produto` ganha referência opcional a um `FiscalRegistration`.
- `FiscalCalculationService.calculate(...)`: calcula os 4 valores de imposto de um item a partir da alíquota do `FiscalRegistration` e da base (quantidade × valor unitário) — método interno, sem persistência própria.

### Fora de escopo
- **Emissão de NF-e/NFC-e** (montagem, assinatura digital, envio à autoridade fiscal, DANFE) — domínio Fiscal/Tributário completo, não construído.
- **SPED Fiscal, apuração de ICMS, Sintegra** — mesma razão.
- **CFOP e CST do ICMS validados/usados de verdade** — guardados como campos no `FiscalRegistration` (pra não precisar migrar depois), mas sem validação de tabela nem uso no cálculo nesta fatia; não têm consumidor até a emissão de NF-e existir.
- **ICMS-ST, modalidade de cálculo, redução de base de cálculo, indicadores de crédito, Simples Nacional/CSON, campos de IBS/CBS** — regras reais de tributação brasileira, mas fora do que a Compra precisa (um valor por imposto por item). Simplificação documentada: cada imposto é um percentual simples sobre a mesma base, sem cascata entre impostos.
- **Tela de gestão do `FiscalRegistration`** — cadastro é uma pré-condição real pra Compra funcionar (sem ele, não dá pra vincular a um Produto), mas populá-lo via UI fica pra quando houver uma tela de verdade — adiado pro sub-projeto 5 ou além, quando houver demanda concreta de uso diário.
- **Tela em `Produto` pra vincular um `FiscalRegistration`** — mesma razão; atribuição nesta fatia é só via API/dados de teste.
- **Endpoint HTTP / `Module` de permissão novo** — não há tela nem consumidor externo nesta fatia, então não há API a proteger.

## 3. Modelo de dados

### `FiscalRegistration` (tabela `fiscal_registration` — RLS por tenant direto, mesmo padrão de `purchase_order`/`stock_movement`/`accounts_payable`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | RLS |
| `description` | String | natureza da operação, texto livre |
| `cfop` | String | código, guardado sem validação de tabela |
| `icmsCst` | String | código, guardado sem validação de tabela |
| `icmsRate` | BigDecimal(5,2) | percentual, ex. `18.00` = 18% |
| `ipiRate` | BigDecimal(5,2) | percentual |
| `pisRate` | BigDecimal(5,2) | percentual |
| `cofinsRate` | BigDecimal(5,2) | percentual |
| `createdAt` | Instant | `updatable = false` |

### `Produto` (modificação)

Novo campo `fiscalRegistration` — FK opcional (nullable) para `FiscalRegistration`. Sem tela pra atribuir nesta fatia.

### `FiscalCalculationService`

```
calculate(FiscalRegistration registration, BigDecimal quantity, BigDecimal unitPrice): FiscalCalculationResult
```

Método puro, sem persistência própria — a Compra (sub-projeto 5) é quem vai persistir o retrato calculado no seu próprio item de nota. `FiscalCalculationResult` contém `icmsValue`, `ipiValue`, `pisValue`, `cofinsValue`.

## 4. Regras de negócio

1. Base de cálculo = `quantity × unitPrice`. Cada um dos 4 impostos é calculado independentemente sobre essa mesma base: `valor = base × (alíquota / 100)`, arredondado para 2 casas decimais (`HALF_UP`, mesmo padrão monetário já usado no resto do sistema). Sem cascata entre impostos, sem base reduzida, sem ICMS-ST — simplificação explícita.
2. `FiscalRegistration` não tem conceito de ativo/inativo nesta fatia — se referenciado, é utilizável.
3. `Produto.fiscalRegistration` é opcional — um produto sem `FiscalRegistration` vinculado simplesmente não pode ser usado ainda em cálculo fiscal; a decisão de bloquear ou não uma Compra por falta de vínculo fica para o sub-projeto 5.
4. Sem exclusão física ou lógica do `FiscalRegistration` nesta fatia — só criação e consulta.

## 5. Testes

- `FiscalRegistrationRepositoryTest`: isolamento RLS entre tenants (mesmo padrão dos outros repositórios standalone).
- `FiscalCalculationServiceTest`: **teste unitário puro, sem Testcontainers** — diferente do padrão usual dos outros Services deste projeto, porque a função não toca banco, só recebe um `FiscalRegistration` já em memória e calcula. Cobre: cálculo dos 4 impostos com alíquotas distintas, alíquota zero (imposto isento), arredondamento (`HALF_UP`).

## 6. Riscos e notas abertas

1. **Sem tela, cadastro só populável via API**: como o Estoque mínimo, esta fatia fica sem uso real no dia a dia até o sub-projeto 5 (Compra) existir e trazer as telas que efetivamente conectam `FiscalRegistration` → `Produto` → item de Compra.
2. **CFOP e CST guardados sem validação**: são campos de texto livre nesta fatia — se a validação de tabela (valores oficiais de CFOP/CST) se mostrar necessária mais cedo, precisa de uma migração de validação depois, não coberta aqui.
3. **Simplificação da tributação real**: a fórmula desta fatia (percentual simples sobre a mesma base, sem cascata) não reflete a legislação tributária brasileira em sua totalidade (haveria composição de bases, ICMS-ST, diferimento, etc. em um cálculo fiscal real). Documentado como simplificação deliberada, já sinalizada como risco por PRD-11 seção 8 item 2 ("qualquer alteração no cálculo de impostos... exige investigação direta e cuidadosa, dado o risco fiscal") — esta fatia não pretende ser válida para uso fiscal real, só desbloquear o fluxo de Compra internamente.
