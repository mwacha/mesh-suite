# PRD — Financeiro

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio **Financeiro** é o núcleo de contas a pagar e a receber, fluxo de caixa, controle de cheques, cartão de crédito e desconto de duplicatas. Seu conceito central, o **título financeiro**, é **um único conceito de dado usado tanto para títulos a pagar quanto a receber**, diferenciados por um indicador de natureza (pagar/receber).

Objetivo de negócio: registrar tudo que a empresa deve pagar (fornecedores, despesas) e tudo que tem a receber (clientes), controlar a baixa (quitação) desses títulos com reflexo automático no saldo da conta bancária e no livro-caixa, e apoiar operações bancárias de antecipação de recebíveis (desconto de duplicata) e meios de pagamento (cheque, cartão).

## 2. Escopo

### Incluído neste PRD
- **Contas a Pagar / Contas a Receber**: lançamento, baixa (quitação) e estorno de títulos.
- **Fluxo de Caixa / Movimentação de Caixa**: livro-razão de todas as movimentações financeiras (créditos/débitos por conta bancária e plano de contas).
- **Controle de Cheque**.
- **Cartão de Crédito** (cadastro de bandeiras/taxas, não o lançamento de venda em si).
- **Desconto de Duplicata**: antecipação de recebíveis junto ao banco.
- **Programação de Pagamento**: agendamento de pagamentos futuros.
- **Forma de Recebimento**: condições de pagamento (à vista, parcelado em N vezes com prazos), usadas como referência por Vendas, Compras e Ordem de Compra.

### Fora de escopo (pertence a outro domínio)
- Conta bancária (cadastro, saldo) — domínio **Financeiro** por natureza, mas já documentado como consumido no PRD de **Cobrança Bancária**; aqui tratado apenas como referência (leitura/atualização de saldo).
- Plano de Contas, Centro de Custo — domínio **Contábil/Patrimonial**; consumidos aqui como classificação de cada lançamento.
- Remessa de títulos / arquivo bancário / boleto — domínio **Cobrança Bancária**; este PRD apenas fornece os títulos que são agrupados em uma remessa.
- Cliente/Fornecedor — domínio **Cadastro Comercial**.
- Geração automática do título a partir de Venda/Compra — documentada nos respectivos domínios; aqui documentamos apenas o título e seu ciclo de vida próprio (baixa/estorno).

## 3. Conceitos de dados

### Título Financeiro (Pagar/Receber)
Um único conceito de dado para títulos a pagar e a receber. Campos: número do documento, número da parcela (obrigatórios), data de emissão, data de cadastro (obrigatórias), cliente/fornecedor (obrigatório), conta bancária (preenchida na baixa), plano de contas (classificação contábil), valor do lançamento (obrigatório, valor original do título), vencimento (obrigatório), valor efetivamente pago/recebido, data da baixa (vazia = título em aberto), indicador e valores de juros/desconto/multa aplicados na baixa, tipo de documento, referência bancária (nosso número, borderô — usada em cobrança/desconto), indicador de natureza (a pagar ou a receber), venda de origem (opcional, quando o título foi gerado por uma venda), compra de origem (opcional, quando gerado por uma compra), empresa, situação financeira, desconto de duplicata associado (preenchido quando o título foi antecipado), forma de pagamento usada na baixa, indicador de visibilidade pública, indicador de quitação, dados de sincronização/validação de cobrança (mecanismo não detalhado nesta investigação), e a movimentação de caixa gerada pela baixa deste título.

### Movimentação de Caixa (livro-caixa)
Campos: descrição (obrigatória), número do documento, conta bancária, forma de pagamento, plano de contas, natureza (débito/crédito), data de lançamento, valor de crédito, valor de débito, indicador de exibição no balancete (usado para "esconder" lançamentos estornados sem apagá-los, ver seção 4).

### Cheque
Campos: valor, número do cheque, banco, agência, número da conta, data de vencimento, titular, data de baixa, número de documento de origem, nome de origem (há campos adicionais de status/movimentação não detalhados nesta investigação).

### Cartão de Crédito
Cadastro de referência (bandeira/operadora com suas taxas), não o lançamento de uma transação de cartão. Campos: nome (obrigatório), percentual para venda a prazo, percentual para venda à vista, indicador de ativo.

### Desconto de Duplicata
Campos: número do borderô, conta bancária, data do desconto, tipo de documento, forma de pagamento, valor do desconto, quantidade de títulos, e a lista de títulos incluídos.

### Forma de Recebimento
Representa uma "condição de pagamento" nomeada (ex. "30/60/90 dias"), reutilizada por Vendas, Compras e Ordem de Compra. Campos: descrição (obrigatória), a lista de prazos de cada parcela, percentual, indicador de ativo, data de cadastro, indicador de visibilidade pública.

## 4. Fluxos funcionais

### Fluxo principal — Baixar (quitar) um título
1. Usuário informa a data de pagamento/recebimento e a conta bancária em um título existente.
2. Ao salvar, se a data de pagamento estiver preenchida (ou seja, está sendo baixado), o título é marcado como quitado e o processo de baixa é executado antes de persistir o título.
3. O processo de baixa:
   - Lê o saldo atual da conta bancária; se o título for a pagar, subtrai o valor pago do saldo; se for a receber, soma.
   - Cria uma nova Movimentação de Caixa com descrição composta pelo nome do cliente/fornecedor (truncado) e o número do documento/parcela, vinculada à mesma conta e plano de contas do título; natureza **débito** se o título for a pagar, **crédito** se for a receber.
   - Persiste a movimentação de caixa e atualiza o saldo da conta bancária.
   - Associa a movimentação de caixa criada ao título.
4. O título é então persistido com os dados de baixa.

### Fluxo — Estornar uma baixa
1. Reverte o efeito no saldo da conta (soma de volta se era a pagar, subtrai se era a receber).
2. Cria uma **nova** Movimentação de Caixa de estorno (não edita nem apaga o lançamento original), classificada em um plano de contas fixo e específico para estorno — um para reverter título a pagar, outro para reverter título a receber (ver risco 1 na seção 8) — com natureza invertida em relação ao lançamento original (crédito para reverter um título a pagar, débito para reverter um título a receber).
3. A Movimentação de Caixa original (a que foi gerada pela baixa) é marcada para **não aparecer** em relatórios de balancete — **não é excluída**, apenas deixa de ser exibida, preservando o rastro completo da operação original e do estorno.
4. O título volta ao estado "em aberto": data de pagamento, valor pago, forma de pagamento, data de desconto, borderô e desconto de duplicata associado são todos limpos; o indicador de quitação volta a falso; dados de sincronização de cobrança são limpos; se o título era a pagar, a conta bancária associada também é limpa.

### Fluxo — Desconto de Duplicata (antecipação de recebíveis)
1. Usuário seleciona títulos a receber para antecipar junto ao banco, informa conta, data de desconto, tipo de documento/pagamento.
2. Ao salvar, o sistema grava o cabeçalho do desconto, associa os títulos selecionados a ele e aplica o efeito financeiro correspondente — o comportamento detalhado não foi confirmado literalmente nesta investigação, mas o padrão espelha o fluxo de baixa de título descrito acima.
3. Estornar a operação desfaz o vínculo e reverte o efeito financeiro, também espelhando o padrão de estorno descrito acima.

### Fluxo — Controle de Cheque
Cadastro, edição, exclusão e consulta de cheques, com uma operação que permite tratar vários cheques de uma vez (ex. depósito em lote ou mudança de status/custódia) — o detalhe exato dessa operação não foi confirmado nesta investigação.

### Fluxo — Programação de Pagamento
Permite gravar múltiplos agendamentos de pagamento futuro de uma vez — o detalhe exato desse fluxo não foi confirmado nesta investigação.

## 5. Regras de negócio

1. **O título financeiro é compartilhado entre Pagar e Receber**: toda lógica de negócio deve sempre checar o indicador de natureza antes de decidir o sinal do efeito no saldo/movimentação de caixa.
2. **Baixa gera automaticamente uma Movimentação de Caixa**; não é possível baixar um título sem que isso reflita no livro-caixa e no saldo da conta bancária vinculada.
3. **Estorno nunca apaga o lançamento de caixa original**; apenas o oculta de relatórios, preservando auditoria completa (padrão que deve ser preservado em qualquer reescrita).
4. **As contas contábeis usadas para estorno são fixas e específicas** — uma para estorno de título a receber, outra para estorno de título a pagar (ver risco 1 na seção 8).
5. **Forma de Recebimento é compartilhada entre múltiplos domínios** (Vendas, Compras, Ordem de Compra) como "condição de pagamento" — qualquer alteração em sua estrutura deve considerar todos os consumidores.

## 6. Integrações e dependências

- **Depende de Contábil/Patrimonial**: Plano de Contas (classificação de todo lançamento), incluindo as duas contas fixas de estorno mencionadas na regra 4 — **este domínio depende implicitamente de que essas duas contas específicas sempre existam no Plano de Contas**.
- **Depende de Cadastro Comercial**: cliente/fornecedor (sacado/beneficiário do título).
- **É consumido por Vendas e Compras**: ambos geram um título financeiro (respectivamente a receber e a pagar) a partir de suas notas fiscais, vinculando-o à venda ou compra de origem.
- **É consumido por Cobrança Bancária**: os títulos a receber são agrupados em uma remessa para gerar o arquivo bancário, o que atualiza o vínculo do título com a remessa (fora deste PRD, mas afeta esta entidade).
- Nenhuma dependência direta encontrada com Estoque ou Fiscal.

## 7. Requisitos não-funcionais relevantes

- Toda operação financeira (baixa, estorno, desconto) deve manter título, movimentação de caixa e saldo da conta sempre atualizados de forma atômica — **crítico**, dado o risco de inconsistência financeira caso apenas parte da operação seja concluída.
- O padrão de "nunca apagar, sempre marcar como não-visível" no estorno de caixa é uma boa prática de auditoria contábil e deve ser mantido no sistema novo.

## 8. Riscos e comportamentos conhecidos a decidir

1. **As contas contábeis de estorno são identificadas por um valor fixo no sistema legado**, não por uma referência configurável. Se esses registros específicos forem excluídos, renumerados, ou não existirem em um ambiente novo (ex. banco de homologação recém-criado), a função de estorno falha sem nenhuma mensagem de erro amigável. Recomenda-se que o sistema novo identifique essas contas por uma referência configurável (ex. uma marcação explícita de "conta de estorno" no cadastro do Plano de Contas), não por um valor fixo.
2. **A referência bancária (nosso número/borderô) está acoplada diretamente ao título financeiro**, reforçando o acoplamento já documentado no PRD de Cobrança Bancária entre os dois domínios.
3. **O comportamento detalhado do Controle de Cheque, do Desconto de Duplicata e da Programação de Pagamento não foi confirmado em profundidade nesta investigação** — este PRD documenta apenas o padrão inferido por analogia com o fluxo de baixa de título; qualquer decisão de implementação para esses três fluxos deve validar o comportamento exato com o time antes de assumir que segue exatamente o mesmo padrão.
4. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado — neste domínio o risco é agravado por lidar diretamente com saldo de contas bancárias e valores financeiros; o sistema novo não deve reproduzir esse padrão.

## 9. Critérios de aceite / Definition of Done

- [ ] Baixa de título (pagar e receber) continua atualizando corretamente saldo da conta bancária e gerando o lançamento de caixa com a natureza (débito/crédito) correta.
- [ ] Estorno de baixa continua revertendo o saldo, ocultando (não apagando) o lançamento de caixa original, e voltando o título ao estado "em aberto" com todos os campos limpos conforme documentado.
- [ ] Estorno continua utilizando as contas contábeis corretas de estorno — ou, se corrigido para referência configurável (risco 1), a migração preserva o comportamento correto.
- [ ] Desconto de Duplicata continua vinculando/desvinculando corretamente os títulos ao cabeçalho de desconto.
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de contas a pagar/receber, fluxo de caixa, controle de cheque, cartão de crédito, programação de pagamento, desconto de duplicata e forma de recebimento.
- [ ] Decisão registrada sobre a fragilidade da identificação fixa das contas de estorno (risco 1).
- [ ] Comportamento não confirmado nesta investigação (risco 3) validado com o time antes de qualquer decisão de implementação.
