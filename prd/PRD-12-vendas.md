# PRD — Vendas

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio **Vendas** cobre dois documentos distintos, análogos ao par Ordem de Compra/Compra do domínio Compras:

1. **Pedido** — o documento comercial interno (não fiscal): o que o cliente/representante pediu, com fluxo de status próprio (digitação → exportação/importação → validação/autorização → faturamento).
2. **Venda** — o documento fiscal de saída, que **compartilha a mesma estrutura de dados de "nota fiscal" usada por Compra** (documentado no PRD de Compras). É a partir da Venda que a NF-e é emitida (domínio Fiscal/Tributário).

Além disso, o fluxo de **Gerenciar Venda** parece ser uma tela consolidada de atendimento (dados de cliente + pedido + produtos em um único fluxo), possivelmente o ponto de entrada usado pela equipe comercial/representantes.

Objetivo de negócio: capturar o pedido de venda de um cliente, processá-lo por um fluxo de aprovação/validação, e convertê-lo no documento fiscal de venda que dispara faturamento, baixa de estoque, título financeiro a receber e emissão de NF-e.

## 2. Escopo

### Incluído neste PRD
- **Pedido**: captura do pedido de venda, com status, cliente, tabela de preço, condição de pagamento, representante, comissão.
- **Venda**: documento fiscal de saída com cálculo detalhado de ICMS/ICMS-ST/IPI/PIS/COFINS por item.
- **Serviços de cálculo e validação** da venda (cálculo de tributos por item, validações de negócio, população de dados do item, geração financeira).
- **Gerenciar Venda**: fluxo consolidado de atendimento (cliente + pedido + produtos).
- **Agrupamento de Pedido**: consolidação de múltiplos pedidos.
- **Tabela de Preço**: preços de venda por produto, referenciada por Pedido/Venda.

### Fora de escopo (pertence a outro domínio)
- A estrutura de dados de "nota fiscal" compartilhada com Compras — estrutura documentada no PRD de **Compras**; mudanças nela afetam ambos os domínios.
- Cliente/Fornecedor — domínio **Cadastro Comercial**.
- Produto — domínio **Cadastro Comercial**.
- Título a receber gerado pela venda — domínio **Financeiro**; este PRD apenas dispara sua criação.
- Baixa de estoque pela venda — domínio **Estoque**; disparada com as origens "pedido faturado"/"pedido cancelado" (já documentado no PRD de Estoque).
- Emissão de NF-e a partir da Venda — domínio **Fiscal/Tributário**.
- Montagem de volumes de expedição do pedido, cotação de frete — domínio **Expedição/Logística**.
- Produção do pedido (corte, roteiro) — domínio **Produção/PCP**.

## 3. Conceitos de dados

### Pedido
Campos: número do pedido, data de cadastro, data do pedido, data de entrega (obrigatórias), quantidade de peças, e a composição do valor do pedido (subtotal, desconto, percentual de desconto total, percentual de desconto sobre valor, percentual de desconto sobre condição de pagamento, valor de despesa, valor total). Relacionamentos: cliente, empresa, representante, condição de pagamento, tabela de preço, itens do pedido, status (ver valores abaixo), usuário responsável, percentual de comissão. Também possui os campos preenchidos pelo domínio Expedição (quantidade de volumes, peso bruto, indicador de volume cadastrado), conforme documentado naquele PRD.

Valores de status do pedido: Digitado, Exportado, Importado, Cancelado, Validado, Faturado, Aguardando Autorização, Rejeitado, Autorizado. A existência de Exportado/Importado junto com um par Aguardando Autorização/Autorizado/Rejeitado sugere: (a) pedidos podem ser capturados fora do sistema principal (ex. por representante externo) e depois "importados", e (b) existe um fluxo de aprovação (provavelmente de desconto ou limite de crédito) antes do faturamento — **o mecanismo exato de exportação/importação e de autorização não foi confirmado nesta investigação; requer investigação dedicada antes de qualquer alteração nesse fluxo.**

### Venda
Além dos campos comuns a qualquer nota fiscal (documentados no PRD de Compras): referência ao número do pedido de origem (aparentemente uma referência solta, não um vínculo estrutural formal — ver risco 2 na seção 8), condição de pagamento, tabela de preço, representante, valor da cotação de frete, referência ao cartão de crédito usado, tipo e espécie de documento, plano de contas, conta bancária, campos de valor incidente federal/estadual/municipal/próprio, campos de IBS/CBS (diferimento, devolução tributária, estorno — mesmo padrão de Compra), a lista de itens da venda, origem da nota, e o cadastro fiscal aplicado (domínio Fiscal).

### Item de Venda
Campos próprios: venda associada, valores incidentes federal/estadual/municipal/próprio, indicador de se a venda deste item resultou em estoque negativo, e a tributação padrão aplicada ao item. Campos de cálculo fiscal por item: quantidade, valor unitário/desconto/total, alíquotas e valores de ICMS (normal, Simples Nacional, substituição tributária com margem de valor agregado), IPI (com redução), PIS/COFINS (com tipo de redução e alíquota diferenciada) — estrutura equivalente em granularidade à do Item de Compra.

## 4. Fluxos funcionais

### Fluxo principal — Capturar e processar um Pedido
1. Pedido é criado com status inicial "Digitado", associando cliente, itens, tabela de preço e condição de pagamento.
2. Conforme o fluxo (não confirmado em detalhe): pedido pode ser exportado/importado, validado, e passar por um estágio de autorização (Aguardando Autorização → Autorizado/Rejeitado) antes de estar apto a faturar.
3. Apenas pedidos com status "Importado" aparecem disponíveis para montagem de volumes de expedição (regra já documentada no PRD de Expedição/Logística, confirmando que esse é o status mínimo operacional para prosseguir ao envio físico).
4. Ao faturar, o pedido é convertido/vinculado a uma Venda (documento fiscal) — o mecanismo exato de conversão (criação automática dos itens de venda a partir dos itens de pedido) não foi confirmado em detalhe nesta investigação.
5. Status muda para "Faturado".

### Fluxo — Emitir/gravar a Venda (documento fiscal)
1. A partir dos dados do pedido (ou diretamente), os itens de Venda são populados, presumivelmente por um mecanismo equivalente ao usado em Compras — não confirmado; **requer investigação dedicada antes de qualquer alteração**.
2. O sistema calcula, por item:
   - IPI, PIS, COFINS e ICMS, considerando o tipo de frete — o tipo de frete sugere que o valor do frete pode compor a base de cálculo de alguns tributos conforme a modalidade (CIF/FOB).
   - Totaliza a nota a partir dos itens.
   - Rateia proporcionalmente desconto/frete/despesas entre os itens.
3. O sistema valida:
   - Coerência entre natureza da operação e o par cliente/empresa (ex. operação interestadual vs. UF do cliente).
   - Condições antes de gerar o título financeiro.
   - Regras gerais da venda e de cada item.
4. Ao salvar, a Venda dispara: baixa de estoque, geração de título financeiro a receber, e fica disponível para emissão de NF-e (domínio Fiscal).

### Fluxo — Gerenciar Venda (atendimento consolidado)
Um fluxo consolidado onde o atendente busca/cadastra o cliente, monta o pedido e os produtos em uma única tela — provavelmente a interface principal usada pela equipe comercial no dia a dia, em vez do cadastro de Pedido "cru". **Fluxo interno não confirmado em detalhe nesta investigação.**

### Fluxo — Agrupamento de Pedido
Permite consolidar múltiplos pedidos (do mesmo cliente? para expedição conjunta?) — não confirmado em detalhe; **requer investigação adicional antes de qualquer trabalho nessa área**.

## 5. Regras de negócio

1. **Venda e Compra compartilham a mesma estrutura de dados de "nota fiscal"** — qualquer regra de cálculo fiscal genérica deve ser considerada para os dois fluxos.
2. **Cálculo de tributos depende do tipo de frete** — o frete pode entrar na base de cálculo conforme a modalidade (CIF/FOB).
3. **Apenas pedidos com status "Importado" são elegíveis para expedição** (regra já confirmada no PRD de Expedição/Logística).
4. **Existe validação cruzada entre natureza da operação, cliente e empresa** antes de prosseguir com a venda — provavelmente relacionada à determinação correta de CFOP/tributação interestadual vs. intraestadual.

## 6. Integrações e dependências

- **Compartilha estrutura de dados com Compras** (ver seção 1).
- **Depende de Cadastro Comercial**: cliente/fornecedor, produto, tabela de preço.
- **Depende de Financeiro**: condição de pagamento, geração de título financeiro a receber.
- **Depende de Fiscal/Tributário**: cadastro fiscal, cálculo de tributos, e é a fonte de dados para emissão de NF-e (já documentado).
- **Alimenta Estoque**: baixa de estoque no faturamento (origens "pedido faturado"/"pedido cancelado").
- **Alimenta Expedição/Logística**: pedidos com status "Importado" tornam-se elegíveis para montagem de volumes; cotação de frete referencia o pedido.
- **Alimenta Produção/PCP**: pedido é a origem da necessidade de produção (corte).
- **Alimenta Administração do Sistema**: os eventos "nota fiscal faturada na expedição" e "pedido liberado para faturar" do mecanismo de notificação (já documentado no PRD de Administração) presumivelmente são disparados a partir de transições de status neste domínio — **o gatilho exato não foi localizado nesta investigação**.

## 7. Requisitos não-funcionais relevantes

- Cálculo fiscal por item é granular (múltiplas alíquotas, bases, reduções por tributo) — qualquer alteração deve ser validada contra casos de teste reais de tributação (Simples Nacional, Regime Normal, ICMS-ST, diferentes UFs).

## 8. Riscos e comportamentos conhecidos a decidir

1. **Fluxo de status do Pedido não totalmente mapeado nesta investigação**: a relação exata entre Exportado/Importado (sugerindo integração com processo externo, possivelmente representantes com captura offline) e Aguardando Autorização/Autorizado/Rejeitado (sugerindo aprovação de desconto/crédito) não foi confirmada nesta investigação. **Antes de qualquer decisão sobre o fluxo de pedidos no sistema novo, é necessário mapear esse fluxo com investigação dedicada.**
2. **A referência da Venda ao Pedido de origem parece ser solta**, não um vínculo estrutural formal — risco de inconsistência referencial (o número pode não corresponder a nenhum pedido real, ou a relação pode quebrar silenciosamente). **Requer validação com o time**; o sistema novo deve usar um vínculo estrutural formal entre os dois documentos.
3. **O comportamento detalhado da população de itens de venda, da geração financeira, do fluxo de Gerenciar Venda e do mecanismo de Agrupamento de Pedido não foi confirmado em profundidade nesta investigação** — qualquer especificação de execução para esses fluxos deve começar por uma investigação dedicada.

## 9. Critérios de aceite / Definition of Done

- [ ] Captura de Pedido (cliente, itens, tabela de preço, condição de pagamento) preservada.
- [ ] Transições de status do Pedido preservadas exatamente como hoje (mesmo sem o fluxo completo mapeado neste PRD — qualquer decisão de reimplementação deve primeiro documentar o comportamento atual observado em ambiente de teste).
- [ ] Cálculo de ICMS/ICMS-ST/IPI/PIS/COFINS por item de Venda continua produzindo os mesmos valores para os mesmos cenários de entrada (regime tributário, UF, tipo de frete).
- [ ] Geração de título financeiro e baixa de estoque no faturamento preservadas.
- [ ] Fluxo de Gerenciar Venda (atendimento consolidado) preservado.
- [ ] Mapeamento completo do fluxo de status do Pedido (risco 1) realizado antes de qualquer decisão de reimplementação nesse fluxo.
- [ ] Esclarecida a natureza da referência entre Venda e Pedido (risco 2) — vínculo formal ou referência solta — antes de qualquer decisão de modelagem.
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de pedido, venda e gerenciar venda.
