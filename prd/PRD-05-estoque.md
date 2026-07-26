# PRD — Estoque

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio de **Estoque** é o **livro-razão central de quantidade de produto** e do histórico de movimentações que sustentam esse saldo. Praticamente todos os outros domínios que movem mercadoria física (Vendas, Compras, Produção/PCP, Expedição, Fiscal/nota fiscal eletrônica) escrevem, direta ou indiretamente, neste mesmo registro central — a lista de origens possíveis de uma movimentação inclui explicitamente "pedido faturado", "apontamento de produção", "expedição", "nota fiscal de entrada", "nota fiscal eletrônica" (ver seção 3).

Além do saldo corrente, o domínio inclui:
- **Baixa manual de estoque** (ajuste de saída sem vínculo com pedido/nota).
- **Movimentação manual de estoque** (ajuste livre, com origem/destino, unidade, conversão).
- **Inventário físico anual/periódico** (declaração de quantidade e valor de produtos em estoque, com dados fiscais da empresa — CNPJ, inscrição estadual, UF — sugerindo alimentar o Bloco H do SPED Fiscal, embora essa integração não tenha sido confirmada na investigação).
- **Inventário/estoque em poder de terceiros** — controle de produto enviado para terceirização (relacionado ao subsistema de facção do domínio Produção/PCP).

Objetivo de negócio: manter o saldo de estoque de produtos confiável e rastreável, com histórico de todas as movimentações e apoio a inventários físicos periódicos.

## 2. Escopo

### Incluído neste PRD
- **Saldo de estoque do produto** — mecanismo central usado por todos os fluxos abaixo e por outros domínios.
- **Movimentação de Estoque**: consulta/exclusão de movimentações manuais.
- **Baixa de Estoque (matéria-prima)**: ajuste manual de saída de estoque.
- **Inventário**: declaração periódica (mês/ano) de quantidade e valor de produtos em estoque.
- **Estoque em Terceiros**: registro de produto enviado/devolvido de terceiros, com natureza débito/crédito.

### Fora de escopo (pertence a outro domínio)
- O produto em si (cadastro, preço, características) — domínio **Cadastro Comercial**; este PRD só consome/atualiza a quantidade em estoque.
- Baixa de estoque decorrente de faturamento de pedido/venda — domínio **Vendas**, que aciona o mesmo mecanismo central documentado aqui como dependência.
- Entrada de estoque por compra/nota fiscal de entrada — domínios **Compras**/**Fiscal**.
- Movimentações geradas por corte/apontamento de produção — domínio **Produção/PCP**.
- Ajuste de estoque feito por telas historicamente nomeadas como "Expedição" — já documentado no PRD de Expedição/Logística como funcionalidade mal nomeada. Aqui apenas registramos que a exclusão de movimentação nesta tela delega, no sistema legado, para uma lógica cujo nome pertence ao domínio de Expedição — ou seja, **uma classe nomeada como Expedição implementa, na prática, parte do CRUD de Movimentação de Estoque deste domínio**. Reforça a recomendação já registrada no PRD de Expedição de decidir e renomear essa classe.
- Estoque por unidade/posto de produção — pertence ao domínio **Produção/PCP**, apenas mencionado aqui como consumidor do mesmo conceito de estoque.

## 3. Conceitos de dados

### Movimentação de Estoque (livro-razão central)
Campos: data da movimentação (obrigatória), tipo de movimentação — Entrada, Saída, Transferência ou Estoque Inicial (obrigatório), origem/motivo da movimentação (ver lista abaixo, obrigatória), produto (obrigatório), unidade (obrigatória), quantidade de entrada, quantidade de saída, unidade conforme nota fiscal (quando diferente da unidade de estoque), quantidade convertida (fator de conversão entre unidades), identificador do registro de origem (pedido, corte, nota, etc.), observação, usuário responsável (obrigatório), indicador de saldo inicial, terceiro envolvido (quando a movimentação envolve terceirizado), saldo após a movimentação (um retrato fixado no momento da movimentação, não recalculado dinamicamente depois).

Origens possíveis de uma movimentação (cada uma identifica de qual parte do sistema ela se origina): geração de corte, envio de corte, pedido faturado, pedido cancelado, apontamento de produção, embalagem de produção, etiquetagem de produção, expedição, nota fiscal de entrada, nota fiscal eletrônica, nota fiscal eletrônica cancelada, movimentação de estoque manual (**única origem editável manualmente por este domínio**), saldo inicial, fechamento.

### Baixa de Estoque
Campos: usuário responsável, data de cadastro, observação, e a lista de itens (produto e quantidade) incluídos na baixa.

### Inventário
Campos: mês, ano, data de lançamento (sempre o dia 31 do mês/ano informado, ver seção 4), valor total (soma), e a lista de produtos incluídos no inventário.

### Item de Inventário
Campos: data da operação, produto, quantidade, valor unitário, e um retrato dos dados fiscais da empresa no momento do lançamento (CNPJ, inscrição estadual, UF — ver regra de negócio 6), inventário ao qual pertence, valor total (sempre calculado a partir de quantidade × valor unitário, não armazenado como resultado fixo).

### Estoque em Terceiros
Campos: usuário responsável, data da operação, terceiro de destino (cliente/fornecedor), produto, quantidade, histórico (texto livre), natureza débito/crédito.

## 4. Fluxos funcionais

### Fluxo central — Atualização de saldo de estoque (usado por todos os domínios)
A atualização de saldo é feita como uma operação atômica de soma/subtração direta sobre o saldo atual do produto (não é uma leitura seguida de gravação em memória), o que a torna segura contra condição de corrida entre movimentações concorrentes do mesmo produto. Esse mecanismo central é usado pela Baixa de Estoque deste domínio, por telas de ajuste do domínio Expedição, e presumivelmente por fluxos de Vendas/Compras/Produção não cobertos neste PRD.

### Fluxo — Baixa de Estoque (matéria-prima)
1. Usuário pesquisa produtos e os adiciona a uma baixa; a quantidade padrão sugerida é o saldo atual do produto, e só produtos com saldo maior que zero podem ser adicionados.
2. Ao salvar, se a lista de itens não estiver vazia: grava a data de cadastro e o usuário responsável a partir da sessão autenticada.
3. Dentro de uma única operação, o cabeçalho da baixa e cada item são gravados, e o saldo do produto é sempre subtraído na quantidade informada — este fluxo **não gera registro na Movimentação de Estoque** (é independente do livro-razão central de movimentações).

### Fluxo — Movimentação de Estoque (ajuste manual com origem/destino)
A tela de gerenciamento permite pesquisar, filtrar e excluir movimentações. A criação de novas movimentações usa validações extensas (ver seção 5) sobre origem, destino, produto de origem/destino, cor, unidade e conversão — suportando transferência entre unidades de produção e conversão de unidade de medida, além de simples entrada/saída. A exclusão só permite selecionar movimentações cuja origem seja exatamente "movimentação de estoque manual" — ou seja, **movimentações originadas por outros domínios (corte, produção, pedido faturado, nota fiscal eletrônica, expedição) não podem ser excluídas por esta tela**, preservando a integridade do rastro de outros módulos.

### Fluxo — Inventário físico
1. Usuário informa mês/ano, pesquisa produtos e os adiciona a um inventário (quantidade e valor unitário são sugeridos a partir do cadastro do produto, mas ambos editáveis).
2. Ao incluir um item, o sistema copia da empresa (dados da sessão autenticada) o CNPJ, a inscrição estadual e a UF para o item do inventário — um retrato fiscal no momento do lançamento, não uma referência dinâmica.
3. Ao salvar, a data de lançamento é sempre montada como o dia 31 do mês/ano informado (ver risco na seção 8).
4. Este fluxo **não altera o saldo de estoque do produto** — é uma declaração/relatório de posição de estoque, não um ajuste operacional.

### Fluxo — Estoque em Terceiros
Registro de envio/retorno de produto para terceiros, com natureza débito/crédito. O comportamento detalhado das telas correspondentes não foi lido em profundidade nesta investigação — **requer validação com o time** se há reconciliação automática com o saldo de estoque do produto ou se é puramente informativo/de controle paralelo.

## 5. Regras de negócio

1. **Baixa manual só inclui produtos com saldo positivo**: apenas produtos com quantidade em estoque maior que zero podem ser adicionados a uma baixa.
2. **Baixa manual sempre subtrai do estoque** — não há opção de gerar entrada nesta tela (isso é feito por uma tela historicamente nomeada "Adiciona Expedição", documentada no domínio Expedição).
3. **Validações da Movimentação de Estoque manual**: tipo de movimentação obrigatório; origem e destino do estoque obrigatórios e **não podem ser iguais**; produto de origem e destino obrigatórios e **não podem ser o mesmo produto**; cor do produto obrigatória; quantidade obrigatória; data obrigatória; observação obrigatória; unidade de saída e conversão de saída obrigatórias quando aplicável; unidade de produção obrigatória em transferências; quantidade e unidade de conversão obrigatórias em transferências.
4. **Exclusão de movimentação restrita por origem**: só é possível selecionar/excluir movimentações cuja origem seja "movimentação de estoque manual" — todas as demais origens (produção, vendas, expedição, fiscal) são somente leitura nesta tela.
5. **Inventário sempre lançado no dia 31 do mês informado**, independentemente de o mês ter 28, 29, 30 ou 31 dias — ver risco na seção 8 sobre erro potencial em meses com menos de 31 dias.
6. **Retrato fiscal no Inventário**: CNPJ, inscrição estadual e UF são copiados da empresa no momento da inclusão do item, não recalculados depois — se a empresa mudar de endereço/UF, inventários antigos preservam o dado histórico correto (comportamento provavelmente intencional, mas não confirmado como tal na investigação).

## 6. Integrações e dependências

- **É dependência de (consumido por) praticamente todos os domínios operacionais**: Vendas (baixa por faturamento de pedido), Compras/Fiscal (entrada por nota fiscal), Produção/PCP (corte, apontamento, embalagem), Expedição (movimentação na expedição) — todos escrevem no saldo de estoque e/ou registram uma movimentação com a origem correspondente.
- **Depende de Cadastro Comercial**: produto, cor, unidade.
- **Depende de Cadastro & Segurança**: usuário (responsável pela movimentação/baixa), empresa (dados fiscais copiados no Inventário).
- **Relaciona-se com Produção/PCP (terceirização/facção)**: o destino do Estoque em Terceiros é um cliente/fornecedor — mesmo padrão de terceirização visto no subsistema de facção.
- **Possível alimentação do domínio Fiscal (Bloco H do SPED — Inventário Físico)**: os campos fiscais copiados no item do Inventário são consistentes com os dados exigidos nesse bloco, mas **não foi confirmado na investigação** que o processo de geração do SPED efetivamente utilize esses dados — **requer validação com o time**.

## 7. Requisitos não-funcionais relevantes

- A atualização de saldo de estoque deve ser uma operação atômica (soma/subtração direta), evitando o padrão "ler, alterar em memória, salvar" para não introduzir condição de corrida entre movimentações concorrentes do mesmo produto.
- O saldo registrado em cada movimentação é um retrato fixado no momento da movimentação, não uma coluna recalculada — qualquer correção retroativa de uma movimentação antiga não deve propagar automaticamente para as posteriores; é responsabilidade do processo de gravação calcular corretamente no momento da inclusão.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Bug de mensagem conhecido a não replicar**: no sistema legado, quando a lista de itens de uma Baixa de Estoque está vazia, o salvamento não grava nada mas exibe a mesma mensagem de sucesso (com estilo visual de erro) usada em caso de sucesso real — usuário não recebe feedback de que nada foi salvo. Mesmo padrão de bug de copy-paste já visto em outros domínios.
2. **A tela de gerenciamento de Movimentação de Estoque depende, no sistema legado, de uma lógica cujo nome pertence ao domínio Expedição** para excluir movimentações — reforça a recomendação, já registrada no PRD de Expedição/Logística, de esclarecer e possivelmente renomear essa lógica, pois hoje o nome não reflete a que domínio ela realmente pertence.
3. **Data de inventário fixada no dia 31**: montar a data de lançamento sempre com o dia 31, independentemente do mês, é um risco de erro em meses com menos de 31 dias (ex. abril, com 30 dias) — pode gerar uma data inválida ou rolar incorretamente para o mês seguinte, dependendo de como o sistema tratar essa conversão. O sistema novo deve usar o último dia real do mês informado.
4. **O valor total do item de Inventário é calculado apenas em tempo de exibição** (quantidade × valor unitário), sem ser persistido — qualquer consulta direta aos dados armazenados (fora da aplicação) não terá esse valor pronto, precisando recalcular. Decisão de implementação sobre persistir ou não esse valor calculado no sistema novo.
5. **Sem reconciliação automática visível entre Inventário físico e saldo corrente de estoque**: o módulo de Inventário parece ser puramente declarativo/documental no sistema legado, sem gerar automaticamente um ajuste de estoque quando a contagem física diverge do saldo do sistema — **requer validação com o time** se esse ajuste é feito manualmente por fora (ex. via Baixa de Estoque) ou se é uma lacuna funcional real a ser resolvida no sistema novo.
6. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado — o sistema novo não deve reproduzir esse padrão.

## 9. Critérios de aceite / Definition of Done

- [ ] Baixa manual de estoque continua atualizando corretamente o saldo do produto (sempre subtração) para os itens selecionados.
- [ ] Consulta/filtro de Movimentação de Estoque continua retornando os mesmos registros para os mesmos filtros, incluindo o agrupamento por origem.
- [ ] Exclusão de movimentação continua restrita a registros com origem "movimentação de estoque manual"; tentativa de excluir movimentação de outra origem continua bloqueada.
- [ ] Todas as validações obrigatórias de Movimentação de Estoque manual (origem ≠ destino, produto origem ≠ destino, cor, quantidade, data, observação, unidade/conversão) preservadas.
- [ ] Inventário físico continua permitindo lançar produtos por mês/ano, com retrato correto de CNPJ/inscrição estadual/UF da empresa no momento do lançamento, usando o último dia real do mês (risco 3 corrigido).
- [ ] Bug de mensagem na Baixa de Estoque com lista vazia (risco 1) não é replicado no sistema novo.
- [ ] Esclarecido e documentado se o Inventário físico deve alimentar o Bloco H do SPED (dependência com Fiscal, não confirmada neste PRD).
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de movimentação, baixa manual, inventário e estoque em terceiros.
