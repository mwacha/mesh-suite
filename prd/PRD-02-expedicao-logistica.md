# PRD — Expedição / Logística

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio de **Expedição/Logística** cobre a etapa entre "pedido pronto para envio" e "mercadoria saindo da fábrica": montagem física dos volumes de um pedido de venda (caixas/pacotes), cotação de frete com transportadoras, cadastro de transportadoras, emissão/registro do Conhecimento de Transporte Eletrônico (CT-e) e algumas telas de ajuste manual de estoque que historicamente foram agrupadas sob o mesmo nome "Expedição".

**Importante — este domínio é internamente heterogêneo.** A investigação do sistema legado mostrou que várias telas nomeadas como parte deste domínio não implementam, de fato, lógica de expedição/despacho — são reaproveitamentos (nome copiado, funcionalidade diferente). Isso está documentado explicitamente na seção 8 (Riscos e comportamentos conhecidos a decidir), em vez de ser silenciosamente "arrumado" ou escondido neste PRD.

Objetivo de negócio: garantir que um pedido de venda aprovado seja embalado (com peso e volumes calculados), tenha frete cotado e contratado, e que o transporte tenha o documento fiscal de frete (CT-e) corretamente vinculado à venda ou à compra correspondente.

## 2. Escopo

### Incluído neste PRD
- **Montagem de volumes de expedição do pedido**: associar itens de um pedido de venda a volumes/caixas, calcular peso bruto/líquido, imprimir romaneio, enviar notificação.
- **Embalagem de Expedição**: cadastro de capacidade/peso de embalagem por produto, usado para montar os volumes acima.
- **Cotação de Frete**: reclassificada para este domínio (não é Compras — ver justificativa no risco 1 da seção 8). Selecionar pedidos e transportadoras, enviar cotação, registrar valor/percentual cotado por transportadora/pedido.
- **Cadastro de Transportadora**: não é um cadastro próprio — é um registro de Cliente/Fornecedor marcado com o papel "Transportadora".
- **Conhecimento de Transporte Eletrônico (CT-e)**: cadastro, edição e importação de arquivo do documento, com vínculo a uma Venda (frete de saída) ou a uma Compra (frete de entrada).

### Documentado aqui mas sinalizado como funcionalidade ambígua/mal nomeada (ver seção 8)
- Uma tela nomeada como parte deste domínio, cuja navegação real no sistema legado conduz o usuário para dentro da área de Estoque; funcionalmente é um ajuste manual de estoque (entrada/saída de produto), não uma operação de despacho físico.
- Outra tela nomeada como parte deste domínio, cuja lógica manipula conceitos de reagrupamento de lotes de corte e sequenciamento de produção — conceitos do domínio de Produção/PCP, não de expedição.
- Uma terceira tela que consulta/edita movimentação genérica de estoque (não específica de expedição), com filtro por fornecedor — sugere uma tela de movimentação de estoque com terceiros/terceirizados.

### Fora de escopo (pertence a outro domínio, ou não faz parte do sistema)
- Pedido, item de pedido, status do pedido — domínio **Vendas**. Este PRD apenas consome o pedido como referência.
- Produto — domínio **Cadastro Comercial**.
- Cliente/Fornecedor (de onde vem o papel "Transportadora") — domínio **Cadastro Comercial**.
- Venda, Compra — domínios **Vendas** e **Compras**, respectivamente; o CT-e apenas referencia esses documentos.
- CST do ICMS, situação do documento fiscal, município — domínio **Fiscal/Tributário**; consumidos aqui apenas como referência para o CT-e.
- Cálculo de ICMS/IBS/CBS do CT-e em si — regras fiscais detalhadas pertencem ao domínio Fiscal; aqui documentamos apenas os campos e sua obrigatoriedade.

## 3. Conceitos de dados

### Volume de Expedição (romaneio do pedido)
Representa uma caixa/pacote físico de um pedido de venda. Campos: pedido associado, embalagem utilizada, peso bruto, peso líquido (calculado), peso calculado, número do volume, quantidade de volumes, e os produtos/quantidades incluídos nesse volume.

### Embalagem de Expedição (cadastro mestre)
Associa um produto a: capacidade, peso da embalagem vazia, capacidade mínima. A embalagem é específica de um produto.

### Cotação de Frete
Campos: data de cadastro, data da cotação, e a lista de pedidos incluídos na cotação. Para cada combinação pedido × transportadora dentro da cotação, são registrados: valor cotado, percentual cotado, peso e quantidade de volumes.

### Transportadora
Não é um cadastro próprio: reaproveita o cadastro de Cliente/Fornecedor (domínio Cadastro Comercial), identificado pelo papel "Transportadora".

### Conhecimento de Transporte Eletrônico (CT-e)
Documento fiscal de frete, vinculado a uma Venda (frete de saída) ou a uma Compra (frete de entrada), conforme o tipo do documento. Campos principais: natureza da operação, série, subsérie, número, chave de acesso, data de emissão, data de entrada, tipo de CT-e, valor do documento, valor de desconto, valor total, base de cálculo do ICMS, alíquota e valor do ICMS, valor não tributado, CST do ICMS, município de origem, indicador de tipo de frete, situação do documento fiscal, peso aferido, indicador de registro ativo. Possui também um conjunto de dados de tributação da reforma tributária (CST e classificação IBS/CBS, base de cálculo, alíquotas e reduções) associado de forma exclusiva a cada CT-e.

### Tipo de CT-e
Cadastro de referência simples (descrição do tipo de CT-e).

### Relacionamentos
Um Volume de Expedição pertence a um Pedido e usa uma Embalagem; contém um ou mais itens de produto/quantidade. Uma Cotação de Frete contém um ou mais Pedidos e, para cada Pedido, uma ou mais Transportadoras cotadas. Um Conhecimento de Transporte se relaciona, de forma exclusiva, a uma Venda **ou** a uma Compra (nunca as duas), conforme seu tipo.

## 4. Fluxos funcionais

### Fluxo principal — Montar volumes de expedição de um pedido
1. Usuário acessa a área de expedição, que carrega as embalagens cadastradas disponíveis.
2. Usuário pesquisa pedidos com status "Importado" (regra do domínio Vendas) e seleciona um.
3. Os itens do pedido ainda não associados a nenhum volume ficam destacados como pendentes. Usuário cria um volume, escolhe a embalagem e associa produtos/quantidades a esse volume.
4. O sistema soma o peso bruto de todos os volumes e calcula o peso líquido subtraindo o peso da embalagem vazia; a quantidade de volumes e o peso bruto do pedido são atualizados.
5. Ao salvar, cada volume deve ter ao menos um produto e peso bruto maior que zero; o pedido é marcado como tendo volume cadastrado.
6. Usuário pode emitir o romaneio de expedição para impressão e/ou enviar uma notificação.

### Fluxo — Embalagem de Expedição (dado mestre)
Cadastro simples (criar, listar, pesquisar, editar, excluir) associando um produto a capacidade, peso e capacidade mínima. Usado como origem das opções de embalagem no fluxo principal.

### Fluxo — Cotação de Frete
1. Usuário cria uma cotação de frete, adiciona um ou mais pedidos e uma ou mais transportadoras.
2. Validação exige data de cadastro e ao menos um pedido na lista.
3. Usuário envia a cotação às transportadoras selecionadas (o canal de envio observado no sistema legado sugere notificação por e-mail — o canal exato a preservar ou substituir **requer validação com o time**).
4. O retorno da cotação (valor e/ou percentual por transportadora e por pedido) é registrado.
5. Usuário salva a cotação; erros de validação são exibidos como mensagem.
6. Usuário pode emitir relatório da cotação.

### Fluxo — Cadastro de Transportadora
Ao salvar uma nova transportadora, o sistema automaticamente marca o registro com o papel "Transportadora" e como ativo, reaproveitando o cadastro de Cliente/Fornecedor. Validações: documento (CNPJ) deve estar cadastrado; município é obrigatório.

### Fluxo — Conhecimento de Transporte (CT-e)
1. Usuário acessa a área de Conhecimento de Transporte e cria um novo registro.
2. Conforme o tipo do documento (saída ou entrada), o sistema direciona o vínculo para uma Venda nova ou uma Compra nova.
3. Alternativamente, o usuário pode importar os dados a partir do arquivo do CT-e, que preenche automaticamente o registro (mesmo padrão usado para importação de nota fiscal eletrônica no domínio Fiscal).
4. Ao salvar, validações obrigatórias são aplicadas (seção 5); a situação do documento fiscal é sempre definida como "normal" (ver risco na seção 8); para a maioria dos tipos de documento, um indicador de operação é definido automaticamente.

### Exceções
Exclusão do CT-e é uma operação própria e independente, feita a partir do registro específico. Falha de validação em qualquer um dos fluxos acima exibe mensagem de erro junto à ação que falhou; no sistema legado, boa parte dos caminhos de erro mostra apenas mensagem genérica em vez de uma mensagem específica do problema — **o sistema novo deve tratar isso melhor**, não replicar a mensagem genérica.

## 5. Regras de negócio

1. **Peso líquido do volume**: peso líquido = peso bruto − peso da embalagem vazia, calculado e somado a cada volume; o total de volumes do pedido é a quantidade de volumes cadastrados.
2. **Validação de volume**: todo volume deve ter ao menos um produto associado, peso bruto diferente de zero, e uma embalagem selecionada.
3. **Status mínimo do pedido para expedir**: somente pedidos com status "Importado" (regra do domínio Vendas) ficam disponíveis para montagem de volume.
4. **Transportadora é um papel do cadastro de Cliente/Fornecedor, não um cadastro próprio**: qualquer operação sobre "transportadora" filtra o cadastro geral por esse papel. Ao criar uma nova transportadora, o sistema força esse papel e o status "ativo".
5. **Validação de Cotação de Frete**: data de cadastro obrigatória; lista de pedidos não pode estar vazia. Envio de cotação exige ao menos uma transportadora selecionada.
6. **Validação do CT-e**:
   - Se o documento for de saída (vinculado a Venda): número da nota fiscal e valor da nota fiscal (diferente de zero) são obrigatórios.
   - Se for de entrada (vinculado a Compra): mesma exigência para número e valor da nota fiscal da compra.
   - CST do ICMS, valor do documento, valor total e peso aferido são sempre obrigatórios.
   - Ao salvar, o sistema sempre define a situação do documento fiscal como "normal", independentemente do que foi importado ou digitado — **não presumir** que isso reflita o status real de um CT-e cancelado importado via arquivo; **requer validação com o time**.
   - Existe uma categoria especial de documento tratada de forma diferenciada (o indicador de operação não é definido automaticamente nesse caso), cujo significado de negócio não pôde ser confirmado pela investigação — **requer validação com o time**.
7. **Ajuste de estoque a partir de uma tela historicamente rotulada como "Expedição"**: para cada item lançado, se o tipo for "Entrada", a quantidade é somada ao estoque do produto; se for "Saída", é subtraída. Este ajuste é direto e não tem qualquer vínculo com pedido ou venda — funciona como um ajuste manual de estoque (ver risco na seção 8 sobre a real localização funcional desta tela).

## 6. Integrações e dependências

- **Depende de Vendas**: pedido, itens do pedido, status do pedido (para o status "Importado") e a venda associada ao CT-e de saída. Este domínio lê pedidos e seus itens, e escreve de volta no pedido a quantidade de volumes, o peso bruto e a marcação de "volume cadastrado".
- **Depende de Compras**: a compra associada ao CT-e de entrada.
- **Depende de Cadastro Comercial**: produto (para os itens de volume e para a embalagem) e cliente/fornecedor (para transportadora).
- **Depende de Fiscal/Tributário**: CST do ICMS, situação do documento fiscal, município, tipo de frete — usados como referência pelo CT-e.
- **Depende de Estoque**: a tela de ajuste mencionada na regra de negócio 7 escreve diretamente no saldo de estoque do produto, fora do fluxo normal de entrada/saída do domínio Estoque — ver risco 2 na seção 8.
- **Depende de capacidade de importação de arquivo de documento fiscal**, compartilhada com o domínio Fiscal (usada também para nota fiscal eletrônica).
- **Depende de capacidade de geração de relatório/documento**, compartilhada com o restante do sistema (romaneio de expedição e relatório de cotação de frete).
- **Depende de capacidade de envio de mensagem/e-mail**, compartilhada com o restante do sistema (envio da cotação de frete às transportadoras).
- Nenhuma integração eletrônica direta com a autoridade fiscal para emissão do CT-e: o documento é apenas **registrado/importado** no sistema, não emitido eletronicamente por ele — diferente do fluxo de nota fiscal eletrônica do domínio Fiscal.

## 7. Requisitos não-funcionais relevantes

- Listagens de pedidos e transportadoras devem usar busca paginada (não carregar todos os registros de uma vez).
- Upload do arquivo do CT-e deve ter um limite de tamanho compatível com o volume esperado desses documentos; o sistema legado usava um limite geral de aproximadamente 3MB para todo o sistema — o limite exato para o sistema novo é uma decisão de implementação.
- Falha ao salvar ou excluir deve reverter a operação por completo e informar o usuário.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Cotação de Frete estava classificada, na investigação inicial, como pertencente ao domínio Compras.** A leitura do comportamento do sistema confirmou que se trata de frete de **saída** (envio ao cliente, vinculado a pedido de venda), não de frete de compra. Foi reclassificada para este domínio; documentado aqui para rastreabilidade da decisão.
2. **Uma tela historicamente nomeada como parte deste domínio não é, na prática, uma tela de expedição.** A navegação real do sistema legado conduz para dentro da área de Estoque, e a lógica por trás dela apenas soma/subtrai quantidade do estoque de um produto (regra de negócio 7), sem vínculo com pedido, nota fiscal ou transporte físico. Há também um bug de usabilidade conhecido a não replicar: a mensagem de sucesso é exibida com estilo visual de erro. **Decisão necessária do time**: esse recurso deve nascer no sistema novo como parte do domínio Estoque, ou permanecer aqui com outro nome?
3. **Outra tela historicamente nomeada como parte deste domínio implementa, na prática, lógica que pertence ao domínio de Produção/PCP** (reagrupamento de lotes de corte/facção, sequenciamento de produção), não expedição. **Requer validação com o time** se isso é: (a) uma tela de produção historicamente pendurada no menu de expedição, (b) um erro de escopo a corrigir, ou (c) uma integração intencional entre o fim da produção e a entrada de produto na área de expedição — o nome da tela sugere a terceira hipótese, mas não há confirmação.
4. **Risco de integridade de dado identificado no sistema legado** na geração do identificador dos dados de tributação padrão associados ao CT-e (indício de possível colisão de identificador com outro tipo de registro). Não é uma regra de negócio a replicar — é um defeito técnico do sistema legado; o sistema novo deve garantir uma estratégia de identificador única e sem colisão para este tipo de dado, cabendo à implementação decidir o mecanismo.
5. **Uma terceira tela historicamente nomeada como parte deste domínio opera, na prática, sobre movimentação genérica de estoque** (não específica de expedição) e tem lógica de busca por fornecedor — sugere gestão de estoque com terceiros/terceirizados, possivelmente ligada à produção terceirizada (facção, hoje classificada no domínio Produção/PCP). **Requer validação com o time** sobre a que fluxo de negócio esta tela efetivamente atende hoje.
6. **Os tipos de referência deste domínio** (tipo de expedição, tipo de CT-e, tipo de cliente/fornecedor) têm valores fixos definidos internamente pelo sistema legado, não editáveis pelo usuário final através de uma tela de configuração — mesmo padrão observado em outros domínios; qualquer novo valor exige alteração de sistema. **Requer validação com o time** se o sistema novo deve tornar esses valores configuráveis.
7. **A situação do documento fiscal do CT-e é sempre forçada para "normal" ao salvar**, inclusive quando o registro veio de importação de arquivo — pode mascarar o status fiscal real de um CT-e cancelado ou complementar importado. **Requer validação com o time.**
8. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado (ver `PRD-00`) afetam o acesso aos dados deste domínio também; o sistema novo não deve reproduzir esse padrão.

## 9. Critérios de aceite / Definition of Done

- [ ] Montagem de volumes de um pedido (criar volume, associar produtos, calcular peso bruto/líquido, salvar) funciona com as mesmas validações (produto obrigatório por volume, peso > 0, embalagem obrigatória).
- [ ] Apenas pedidos com status "Importado" aparecem disponíveis para expedição.
- [ ] Emissão de romaneio de expedição e envio de mensagem funcionam a partir dos mesmos dados.
- [ ] Cadastro de Embalagem de Expedição (capacidade/peso por produto) preservado.
- [ ] Fluxo de Cotação de Frete (criar cotação, adicionar pedidos e transportadoras, enviar cotação, registrar valor/percentual, salvar) preservado com as mesmas validações obrigatórias.
- [ ] Cadastro de Transportadora continua criando/editando um registro de Cliente/Fornecedor do tipo Transportadora, com as mesmas validações (documento cadastrado, município obrigatório).
- [ ] CT-e: cadastro manual e importação de arquivo continuam funcionando; validações obrigatórias (CST do ICMS, valor do documento, valor total, peso aferido, e dados de Venda/Compra conforme o tipo) preservadas.
- [ ] Risco de integridade de identificador nos dados de tributação padrão do CT-e (risco 4) resolvido na nova implementação.
- [ ] Decisão registrada sobre o destino da tela de ajuste manual de estoque hoje nomeada como expedição (permanecer aqui vs. mover para Estoque) e sobre a real natureza das duas outras telas ambíguas descritas nos riscos 3 e 5.
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de montagem de volumes, embalagem de expedição, conhecimento de transporte e cotação de frete.
