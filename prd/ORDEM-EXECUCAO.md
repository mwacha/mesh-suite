# Ordem de Execução dos PRDs — Fase definida pelo negócio

> Esta tabela define a ordem de implementação priorizada pelo negócio para este conjunto de funcionalidades, distinta da ordem de dependência técnica listada em `PRD-00-indice.md`. Pressupõe que os cadastros-base (`PRD-09-cadastro-seguranca.md`, `PRD-13-cadastro-comercial.md`) já estejam disponíveis antes do item 2 — nenhum dos itens abaixo funciona sem cliente, produto, usuário e tenant já cadastráveis.

| Ordem | Item | PRD relacionado | Observação |
|---|---|---|---|
| 1 | Login | `PRD-14-login-multitenant.md` | Fundação de identidade/multitenant — autenticação e resolução do tenant. Pré-requisito de tudo que vem depois. |
| 2 | Pedidos | `PRD-12-vendas.md` | Captura do pedido de venda (documento comercial interno, não fiscal) — ponto de entrada do fluxo. |
| 3 | Vendas | `PRD-12-vendas.md` | Documento fiscal de saída (Venda), emitido a partir do Pedido faturado. Mesmo PRD do item 2; implementado em sequência dentro dele. |
| 4 | Compras | `PRD-07-compras.md` | Ordem de Compra e Compra (nota fiscal de entrada). |
| 5 | Financeiro | `PRD-08-financeiro.md` | Contas a pagar/receber, fluxo de caixa — consome títulos gerados por Vendas e Compras. |
| 6 | Estoque | `PRD-05-estoque.md` | Saldo e movimentação de produto — atualizado por Vendas, Compras e demais domínios operacionais. |
| 7 | Sped Fiscal | `PRD-11-fiscal-tributario.md` | Escrituração fiscal (EFD ICMS/IPI, apuração, Sintegra) — depende dos dados já lançados em Vendas, Compras e Estoque nos itens anteriores. Está descrita dentro do PRD Fiscal/Tributário junto com NF-e/NFC-e, que não fazem parte desta fase. |

## Fora desta fase (não sequenciados aqui)

Os demais domínios (`PRD-01`, `PRD-02`, `PRD-03`, `PRD-04`, `PRD-06`, `PRD-10`) não fazem parte desta ordem específica — seguem a ordem de dependência geral já registrada em `PRD-00-indice.md` quando entrarem em execução.
