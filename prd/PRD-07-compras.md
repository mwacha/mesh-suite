# PRD — Compras

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio de **Compras** cobre dois fluxos distintos:

1. **Ordem de Compra** — um documento interno/administrativo, sem efeito fiscal, para formalizar um pedido de compra a um fornecedor (produtos, prazos de entrega, condição de pagamento), **antes** da nota fiscal chegar.
2. **Compra** — o registro da **nota fiscal de entrada** em si: o documento fiscal que, uma vez lançado, atualiza estoque, gera parcelas financeiras (contas a pagar) e pode registrar o conhecimento de transporte da mercadoria.

**Compra e Venda (domínio Vendas) compartilham a mesma estrutura de dados de "nota fiscal"** no sistema legado: uma nota fiscal, seja de entrada (compra) ou de saída (venda), é modelada com o mesmo conjunto amplo de campos comuns (cerca de 95 campos), com Compra e Venda sendo registros distintos que reaproveitam essa mesma base. **Qualquer alteração nessa estrutura de dados compartilhada afeta os domínios Compras e Vendas simultaneamente** — decisão de arquitetura relevante para o time ao desenhar o sistema novo: manter essa base compartilhada ou modelar Compra e Venda como estruturas independentes é uma escolha explícita a ser feita, não uma imposição deste PRD.

Objetivo de negócio: formalizar pedidos de compra a fornecedores, registrar a entrada fiscal da mercadoria (nota fiscal de entrada) com seus impostos e parcelas financeiras, e opcionalmente registrar o transporte associado.

## 2. Escopo

### Incluído neste PRD
- **Ordem de Compra**: criação de pedido de compra com produtos, cronograma de entrega e condição de pagamento.
- **Compra (Nota Fiscal de Entrada)**: lançamento manual ou por importação de arquivo de nota fiscal eletrônica, cálculo/registro de tributação por item, geração de parcelas financeiras, vínculo opcional com Conhecimento de Transporte.

### Fora de escopo (pertence a outro domínio)
- Venda (a contraparte de saída da mesma base de dados de nota fiscal) — domínio **Vendas**. Este PRD documenta a base compartilhada apenas como dependência estrutural, não os fluxos de venda.
- Cliente/Fornecedor (fornecedor da compra/ordem) — domínio **Cadastro Comercial**.
- Produto e sua tributação padrão — domínio **Cadastro Comercial**/**Fiscal**.
- Contas a pagar (parcelas financeiras geradas pela compra) — domínio **Financeiro**; este PRD apenas dispara a criação dessas parcelas.
- Conhecimento de Transporte — domínio **Expedição/Logística**; este PRD apenas cria o vínculo quando o frete é por conta do destinatário.
- CST do ICMS, natureza da operação, cálculo detalhado de ICMS/IPI/PIS/COFINS/IBS/CBS — domínio **Fiscal/Tributário**; aqui documentamos apenas onde a tributação é aplicada ao item de compra.
- Atualização de estoque em si — domínio **Estoque**; aqui documentamos apenas que a Compra dispara essa atualização.
- Importação/leitura do arquivo de nota fiscal eletrônica — capacidade compartilhada do domínio **Fiscal/Tributário**; aqui apenas documentamos que a Compra é o registro populado por essa capacidade.

## 3. Conceitos de dados

### Base comum de "nota fiscal" (compartilhada entre Compra e Venda)
Cerca de 95 campos comuns a qualquer nota fiscal (entrada ou saída), incluindo: número da nota, data de cadastro, data de emissão, data de saída/entrada, hora de saída, dados de contingência (data e justificativa, quando a nota foi emitida em contingência), natureza da operação, dados do cliente/fornecedor (nome, endereço replicados na própria nota — um retrato fiscal, não uma referência dinâmica), além de valores totais, campos de tributação agregada e chave de acesso da nota fiscal eletrônica.

### Compra
Além dos campos comuns acima: valores agregados da reforma tributária (IBS estadual com diferimento, devolução de tributo, valor líquido; IBS municipal com as mesmas variações; CBS com diferimento, devolução de tributo, valor líquido; estornos de IBS e CBS), a lista de itens da compra, e um indicador de observação fiscal.

### Item de Compra
Campos próprios além dos comuns a qualquer item de nota: compra associada, indicador de redução de base de cálculo do ICMS-ST, nome do produto conforme cadastro do fornecedor (pode divergir do nome interno do produto), indicador de se os dados do fornecedor devem atualizar o vínculo produto×fornecedor, indicadores de crédito de ICMS e de crédito de Simples Nacional, e a tributação padrão aplicada ao item.

### Tributação Padrão do Item de Compra
Tributação (incluindo campos de IBS/CBS) aplicada ao item, copiada a partir do cadastro de tributação padrão do produto **no momento do lançamento** — ou seja, a tributação do item de compra é um retrato fixado naquele momento, não recalculada dinamicamente depois.

### Ordem de Compra
Campos: fornecedor, data da ordem, valor de produtos, valor total, valor de desconto, telefone, condição de pagamento, comprador (texto livre), a lista de produtos da ordem, e a lista de entregas programadas. **Não é um documento fiscal.**

### Produto da Ordem de Compra / Entrega Programada
Itens de produto da ordem e cronograma de entregas (uma ordem pode ter múltiplas entregas parciais programadas, cada uma com seus próprios produtos/quantidades).

## 4. Fluxos funcionais

### Fluxo — Ordem de Compra
1. Usuário cria uma ordem, seleciona o fornecedor, adiciona produtos e programa entregas, e define a condição de pagamento.
2. Existe mais de um "tipo" de ordem de compra que altera o comportamento da tela — o detalhe exato de cada tipo **requer validação com o time**.
3. Usuário salva a ordem.
4. A Ordem de Compra **não gera nota fiscal nem atualiza estoque/financeiro por si só** — ela é apenas um documento de intenção/planejamento; a entrada real acontece quando a nota fiscal de Compra correspondente é lançada. Não foi encontrada, nesta investigação, uma vinculação automática/obrigatória entre a Ordem de Compra e a Compra — **requer validação com o time** se essa vinculação deve existir no sistema novo (ver risco 3 na seção 8).

### Fluxo principal — Lançar Compra (Nota Fiscal de Entrada)
1. Usuário cria uma nova Compra manualmente ou importa o arquivo da nota fiscal eletrônica, que aciona a leitura automática do documento para popular cabeçalho, itens e totais.
2. Usuário preenche/confirma dados da nota (número, série, modelo, chave de acesso, datas de emissão/saída-entrada, natureza da operação) e adiciona itens, com a tributação populada automaticamente a partir do cadastro do produto.
3. O sistema recalcula os totais da nota a partir dos itens.
4. Usuário lança as parcelas financeiras, que devem, juntas, somar o mesmo valor total da nota (ver regra de negócio).
5. Se o frete for por conta do destinatário (comprador), dados de transportadora/placa/tipo de frete são exigidos (ver seção 5) e o sistema cria automaticamente um registro de Conhecimento de Transporte associado.
6. Ao salvar:
   - As movimentações de estoque pré-existentes da compra são removidas e recriadas — **mesmo em uma edição, o registro de movimentação de estoque é apagado e recriado do zero**, mesmo padrão "regenerar tudo" observado no domínio Contábil/Patrimonial (Ativo Imobilizado);
   - um código de indicador de pagamento é sempre definido com um valor fixo — **significado exato requer validação com o time**, plausivelmente um código de leiaute fiscal/SPED;
   - o cabeçalho da compra é gravado;
   - os itens são removidos e regravados por completo a cada salvamento (tributação padrão e itens antigos apagados, cada item reconstruído do zero) — novamente o padrão "regenerar tudo";
   - as parcelas financeiras são gravadas;
   - se aplicável, o Conhecimento de Transporte é gravado.
7. Excluir uma compra remove o registro — o comportamento exato sobre reversão de estoque/financeiro não foi confirmado em detalhe nesta investigação; **requer validação com o time antes de qualquer alteração nesse comportamento**.

## 5. Regras de negócio

1. **Validação de nota obrigatória**: número da nota, modelo, série, data de emissão e data de saída/entrada são obrigatórios; a nota deve ter ao menos um item.
2. **Nota duplicada bloqueada**: o sistema verifica se já existe uma nota com os mesmos dados (número/fornecedor, presumivelmente) antes de permitir salvar, informando que a nota já está cadastrada para aquele fornecedor.
3. **Código de fornecedor não pode estar associado a mais de um produto**: valida unicidade do código do produto no cadastro do fornecedor durante a inclusão de item.
4. **Frete por conta do destinatário exige dados de transporte**: se o tipo de frete não for informado, é erro; se a transportadora não for selecionada, é erro; se a placa do veículo não for informada, é erro. Quando esses dados estão completos e o frete é por conta do destinatário, a compra gera automaticamente um Conhecimento de Transporte.
5. **Soma das parcelas deve bater com o valor da nota**: validação cruzada entre o total das parcelas financeiras lançadas e o valor total da nota.
6. **Desconto não pode exceder o valor dos produtos**.
7. **Data de saída não pode ser anterior à data de emissão**.
8. **Regeneração destrutiva ao salvar**: tanto a movimentação de estoque quanto os itens/tributação da compra são apagados e recriados a cada salvamento, mesmo em edições — mesma observação de risco já registrada no domínio Contábil/Patrimonial para Ativo Imobilizado (perda de histórico intermediário se houver auditoria fina por movimentação).

## 6. Integrações e dependências

- **Compartilha estrutura de dados com Vendas** (ver seção 1) — mudanças estruturais em qualquer um dos dois domínios devem considerar o impacto no outro.
- **Depende de Cadastro Comercial**: cliente/fornecedor, produto, tributação padrão do produto, vínculo produto × fornecedor (atualizado quando indicado pelo usuário).
- **Depende de Fiscal/Tributário**: importação de arquivo de nota fiscal eletrônica, CST do ICMS/natureza da operação/campos IBS-CBS.
- **Alimenta Estoque**: toda compra lançada dispara atualização de movimentação/saldo do produto (mecanismo documentado no PRD de Estoque).
- **Alimenta Financeiro**: gera parcelas de contas a pagar — o vínculo exato (se a criação é automática ou manual pelo usuário na tela) não foi confirmado em detalhe nesta investigação.
- **Alimenta Expedição/Logística**: cria Conhecimento de Transporte quando o frete é por conta do destinatário, conforme documentado no PRD de Expedição.

## 7. Requisitos não-funcionais relevantes

- O salvamento de uma Compra deve ser protegido contra execução concorrente — o sistema legado trata esse salvamento como uma operação exclusiva (indicando preocupação prévia com concorrência, provavelmente relacionada à numeração ou ao padrão de regeneração destrutiva descrito acima). **Preservar essa proteção** em qualquer implementação nova, para não introduzir condição de corrida.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Regeneração destrutiva de itens e movimentações de estoque a cada salvamento** (ver regra de negócio 8) — mesmo risco já sinalizado no domínio Contábil/Patrimonial: pode complicar auditoria e rastreabilidade histórica se períodos já fechados forem editados.
2. **Um código de indicador de pagamento é sempre gravado com um valor fixo, sem explicação registrada no sistema legado do que ele significa** — **requer validação com o time** antes de qualquer decisão sobre como esse código deve funcionar no sistema novo.
3. **Relação entre Ordem de Compra e Compra não confirmada nesta investigação**: não está claro se o sistema legado oferece algum mecanismo de conversão automática de uma Ordem de Compra aprovada em uma nota fiscal de Compra, ou se são fluxos totalmente desconectados operacionalmente (o usuário lança a Compra manualmente ao receber a mercadoria, sem referência à ordem original) — **requer validação com o time**.
4. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado — o sistema novo não deve reproduzir esse padrão.

## 9. Critérios de aceite / Definition of Done

- [ ] Criação, edição e exclusão de Ordem de Compra (produtos, entregas programadas, condição de pagamento) preservadas.
- [ ] Lançamento manual de Compra e importação via arquivo de nota fiscal eletrônica continuam populando corretamente cabeçalho, itens e tributação.
- [ ] Todas as validações obrigatórias da nota (número, modelo, série, datas, itens, frete/transportadora quando aplicável) preservadas.
- [ ] Bloqueio de nota duplicada por fornecedor preservado.
- [ ] Validação de soma de parcelas × valor da nota preservada.
- [ ] Ao salvar uma Compra, estoque, tributação de itens e (quando aplicável) Conhecimento de Transporte continuam sendo gerados/atualizados corretamente, incluindo em edições (comportamento de regeneração).
- [ ] Esclarecido com o time o significado do código de indicador de pagamento (risco 2) antes de qualquer decisão sobre seu comportamento no sistema novo.
- [ ] Esclarecida a relação (ou ausência dela) entre Ordem de Compra e Compra (risco 3).
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de ordem de compra e lançamento de compra.
