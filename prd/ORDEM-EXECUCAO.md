# Ordem de Execução dos PRDs — Fase definida pelo negócio

> Esta tabela define a ordem de implementação priorizada pelo negócio para este conjunto de funcionalidades, distinta da ordem de dependência técnica listada em `PRD-00-indice.md`. Pressupõe que o cadastro-base de usuário/tenant (`PRD-09-cadastro-seguranca.md`, fatia de login já coberta pelo item 1) já esteja disponível antes do item 3 — Pedidos não funciona sem cliente e produto já cadastráveis, por isso o item 2 foi movido para antes de Pedidos nesta revisão.

| Ordem | Item | PRD relacionado | Observação |
|---|---|---|---|
| 1 | Login | `PRD-14-login-multitenant.md` | Fundação de identidade/multitenant — autenticação e resolução do tenant. Pré-requisito de tudo que vem depois. |
| 2 | Cadastro Comercial (Cliente + Produto) | `PRD-13-cadastro-comercial.md` | **Concluído** (Cliente/Fornecedor e Produto tipo Simples). Subconjunto do PRD-13: só os cadastros-mestre Cliente/Fornecedor e Produto. Pré-requisito de Pedidos — nenhum pedido existe sem cliente e produto para selecionar. Tabela de Preço, Modelo/Ficha Técnica e os cadastros auxiliares de característica (Cor, Tamanho, Estampa, Tecido/Aviamento, Grupo de Produto, Unidade, Origem do Produto) ficam para quando forem necessários, não fazem parte deste recorte inicial. Produto tipo Kit e Com Variação (as outras duas fatias planejadas do cadastro de Produto) também ficam adiadas — não bloqueiam Pedidos, que só depende do cadastro-mestre básico já pronto. |
| 3 | Pedidos | `PRD-12-vendas.md` | Captura do pedido de venda (documento comercial interno, não fiscal) — ponto de entrada do fluxo. |
| 4 | Vendas | `PRD-12-vendas.md` | **Concluído** (faturamento de Pedido em Venda, 1:1, cálculo fiscal simplificado por item). Documento fiscal de saída (Venda), emitido a partir do Pedido faturado. Mesmo PRD do item 3; implementado em sequência dentro dele. Baixa de estoque e título a receber automáticos ficam para quando Estoque/Financeiro tiverem esse gancho desenhado — ver riscos na spec. |
| 5 | Compras | `PRD-07-compras.md` | **Concluído** (Ordem de Compra + Compra/nota fiscal de entrada, 1:1, débito de estoque e parcelas de contas a pagar automáticos, cálculo fiscal simplificado por item). Sem importação de NF-e, sem frete/Conhecimento de Transporte — ver riscos na spec. |
| 6 | Financeiro | `PRD-08-financeiro.md` | Contas a pagar/receber, fluxo de caixa — consome títulos gerados por Vendas e Compras. |
| 7 | Estoque | `PRD-05-estoque.md` | Saldo e movimentação de produto — atualizado por Vendas, Compras e demais domínios operacionais. |
| 8 | Sped Fiscal | `PRD-11-fiscal-tributario.md` | Escrituração fiscal (EFD ICMS/IPI, apuração, Sintegra) — depende dos dados já lançados em Vendas, Compras e Estoque nos itens anteriores. Está descrita dentro do PRD Fiscal/Tributário junto com NF-e/NFC-e, que não fazem parte desta fase. |

## Fora desta fase (não sequenciados aqui)

Os demais domínios (`PRD-01`, `PRD-02`, `PRD-03`, `PRD-04`, `PRD-06`, `PRD-10`) não fazem parte desta ordem específica — seguem a ordem de dependência geral já registrada em `PRD-00-indice.md` quando entrarem em execução.
