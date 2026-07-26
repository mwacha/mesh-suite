# PRD — RH / Departamento Pessoal

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

Dentro do sistema, o módulo de **Departamento Pessoal** é o único módulo de RH existente: permite que o setor de DP registre ocorrências de **falta/atraso de funcionário** e emita um relatório dessas ocorrências.

Não há folha de pagamento, controle de férias, cargos/salários ou ponto eletrônico — o escopo de RH se limita ao apontamento de faltas. O sistema não tem um cadastro de "Funcionário" separado: ele reaproveita o cadastro de usuários do sistema (o mesmo usado para login) como registro de funcionário, restringindo a seleção a determinados perfis (ver regra de negócio 3).

Objetivo de negócio: dar visibilidade e histórico de faltas/atrasos por funcionário, com justificativa (documento aceito ou não) e observações, para consulta e impressão gerencial.

## 2. Escopo

### Incluído neste PRD
- Cadastro, edição e exclusão de "Falta de Funcionário".
- Listagem/pesquisa de faltas por período e nome do funcionário.
- Seleção de funcionário via busca.
- Cadastro de referência "Tipo de Falta".
- Emissão de relatório (documento para impressão) de faltas.

### Fora de escopo (pertence a outro domínio, ou não faz parte do sistema)
- Cadastro de usuário/login e permissões — domínio **Cadastro & Segurança**. Este PRD apenas consome o cadastro de usuário como referência, sem alterá-lo.
- Folha de pagamento, férias, cargos, salários, ponto eletrônico — **não fazem parte do sistema atual**, não presumir.
- **Calendário de dias úteis/feriados/horário de expediente**: existe como conceito de dados no sistema legado, mas **sem nenhuma tela ou funcionalidade ativa que o utilize** — a única lógica que o referencia é um cálculo isolado e inacabado (nunca chega a executar de fato). Por não ter funcionalidade ativa hoje, fica **fora do escopo funcional** deste PRD. Se for retomado, avaliar se pertence ao domínio Produção/PCP (cálculo de tempo de produção) — **requer validação com o time**.
- Pagamento por peça a costureiras terceirizadas ("facção") — apesar de aparentar RH, é um subsistema de produção terceirizada; pertence ao domínio **Produção/PCP**, não a este.

## 3. Conceitos de dados

### Falta de Funcionário
| Campo | Observação |
|---|---|
| funcionário | referência a um usuário do sistema, tratado como "funcionário" |
| horas de ausência | numérico, padrão zero |
| minutos de ausência | numérico, padrão zero |
| tipo de falta | referência ao cadastro de Tipo de Falta |
| documento aceito | sim/não, padrão "sim" — indica se atestado/justificativa foi aceito |
| observação | texto livre |
| data da falta | data em que ocorreu a ausência |
| data de registro | preenchida automaticamente no momento do salvamento (apenas na criação) |
| responsável | usuário logado que registrou o lançamento |

### Tipo de Falta
Cadastro de referência simples: descrição do tipo (ex. atestado médico, falta injustificada, atraso). **Não foi identificada, no sistema legado, nenhuma tela para o usuário final cadastrar novos tipos** — requer validação com o time sobre como isso deve funcionar no sistema novo (tela de cadastro própria vs. carga administrativa).

### Filtro de pesquisa
Período (data início/fim) e nome do funcionário.

### Dados do relatório
Por lançamento: identificador, funcionário, data da falta, status, se o documento foi aceito, observação, horas, minutos.

### Relacionamentos
Uma Falta de Funcionário se relaciona a um Funcionário, um Responsável e um Tipo de Falta. Não há registros filhos.

## 4. Fluxos funcionais

### Fluxo principal — Registrar falta
1. Usuário acessa a área de Departamento Pessoal e chega à listagem de faltas.
2. Na listagem, pode filtrar por período e nome do funcionário, e iniciar um novo registro.
3. Usuário busca e seleciona o funcionário; **apenas funcionários com perfil "Produção" ou "Administrativo" aparecem disponíveis para seleção** (ver regra de negócio 3).
4. Usuário preenche data da falta, tipo de falta, horas/minutos de ausência, se o documento foi aceito e observação.
5. Ao salvar: validações obrigatórias são aplicadas (seção 5); se for um registro novo, data de registro e responsável são preenchidos automaticamente a partir do usuário logado; o registro é persistido.
6. O registro passa a aparecer na listagem, com opção de edição por linha.

### Fluxo alternativo — Excluir falta
A exclusão só é possível a partir da tela de detalhe de um lançamento específico. **Não existe exclusão a partir da tela de listagem nem exclusão em lote.**

### Fluxo alternativo — Emitir relatório
A partir do filtro de pesquisa, o usuário pode emitir um documento (relatório) com os lançamentos filtrados, apresentado para impressão/download.

### Exceções
Falha ao salvar ou excluir deve reverter a operação por completo e informar o usuário; no sistema legado, a maioria dos caminhos de erro mostra apenas uma mensagem genérica em vez de uma mensagem específica do problema — **o sistema novo deve tratar isso melhor**, não replicar a mensagem genérica (ver também o comportamento indevido descrito na seção 8, item 2).

## 5. Regras de negócio

1. **Validação obrigatória no salvamento**: data da falta é obrigatória; funcionário é obrigatório; tipo de falta é obrigatório; tempo de ausência deve ser maior que zero (não é permitido salvar com horas e minutos ambos zerados).
2. **Auditoria automática de criação**: apenas na criação de um novo registro, a data de registro e o responsável são preenchidos automaticamente a partir do usuário autenticado. Edições subsequentes **não** atualizam esses dois campos — não há, hoje, rastro de quem editou um lançamento, apenas de quem o criou. Se rastrear edição for necessário no sistema novo, é um requisito novo, não uma regra herdada.
3. **Restrição de perfil na seleção de funcionário**: apenas usuários com perfil "Produção" ou "Administrativo" podem ser selecionados como funcionário para apontamento de falta. Usuários com perfil "Representante", "Terceirizado" ou "Administrador" não podem ser selecionados.
4. **"Documento aceito" é apenas informativo**: por padrão é "sim", mas nenhuma regra bloqueia o salvamento ou exige observação quando o valor é "não".
5. **Sem regra de unicidade**: nada impede dois lançamentos de falta para o mesmo funcionário na mesma data — múltiplos registros no mesmo dia são permitidos.
6. **Exclusão irrestrita**: não há verificação de estado (ex. "já constou em relatório fechado") que bloqueie a exclusão de um lançamento.

## 6. Integrações e dependências

- **Depende do cadastro de Usuário/Perfil** (domínio Cadastro & Segurança): o "funcionário" e o "responsável" de cada falta são referências ao cadastro de usuários do sistema. Este domínio apenas lê usuário (identificador, nome) e perfil (identificador, descrição) para filtrar e exibir; não escreve nem altera dados de usuário.
- **Depende de sessão autenticada**: o "responsável" é derivado do usuário logado no momento do salvamento — assume que a autenticação já ocorreu antes (fora deste escopo; ver `PRD-14`).
- **Depende de capacidade de geração de relatório/documento**, compartilhada com o restante do sistema.
- Nenhuma integração externa (autoridade fiscal, gateway de pagamento, bancos) neste domínio.
- Nenhuma rotina agendada/em segundo plano pertence a este domínio.

## 7. Requisitos não-funcionais relevantes

- A listagem de faltas deve usar busca paginada (não carregar todos os registros de uma vez).
- Não existe hoje trilha de auditoria de exclusão ou edição — apenas a criação é registrada (data/responsável). Se o negócio precisar de trilha de auditoria mais completa (quem editou, quando, o quê), é um requisito a ser definido explicitamente para o sistema novo, não uma regra herdada.
- Não foi identificado controle de acesso específico para esta tela além do login geral do sistema — se controle de acesso mais granular (por perfil, por unidade) for esperado, **requer validação com o time**.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Definição de filtro de pesquisa duplicada no sistema legado**: o filtro usado para pesquisar faltas existe, de forma idêntica, em dois lugares diferentes do código legado — risco de divergência se só um for atualizado. O sistema novo deve ter uma única definição.
2. **Bug de usabilidade conhecido a não replicar**: no sistema legado, o fluxo de salvar uma falta reaproveita, por engano, elementos de outra tela (agenda/cronograma de visita a cliente) — a mensagem de sucesso é exibida com estilo visual de erro, e a navegação após excluir um lançamento usa um rótulo de outro módulo. Isso é um defeito conhecido do sistema legado; o sistema novo não deve reproduzi-lo.
3. **Funcionalidade de calendário inativa**: existe um conceito de calendário de dias úteis/feriados/horário de expediente no modelo de dados legado, com operações completas de cadastro, mas **sem nenhuma tela ativa nem uso funcional real** — a única lógica que o referencia nunca chega a executar seu cálculo principal (fonte de dados fixada vazia). Está fora de escopo aqui; decisão sobre reviver ou descartar fica para quando/se o domínio de Produção/PCP for tratado.
4. **Sem prevenção de duplicidade** de lançamento de falta para o mesmo funcionário na mesma data (ver regra de negócio 5) — avaliar se isso deve virar validação no sistema novo.
5. **Cadastro de novos "Tipos de Falta" sem caminho claro pela interface** — no sistema legado, não há tela de cadastro visível para isso; **requer validação com o time** sobre como esse cadastro deve funcionar no sistema novo.

## 9. Critérios de aceite / Definition of Done

- [ ] Registrar, editar e excluir falta de funcionário funciona, aplicando as 4 validações obrigatórias (data, funcionário, tipo de falta, tempo de ausência > 0).
- [ ] Seleção de funcionário permanece restrita aos perfis Produção e Administrativo.
- [ ] Ao criar um novo registro, data de registro e responsável são preenchidos automaticamente a partir do usuário logado; edições não alteram esses dois campos.
- [ ] Emissão de relatório de faltas funciona a partir do mesmo conjunto de filtros (período + nome do funcionário).
- [ ] O bug de usabilidade descrito na seção 8, item 2, **não é replicado** no sistema novo (mensagem de sucesso exibida corretamente, navegação de exclusão correta).
- [ ] Decisão registrada sobre o destino da funcionalidade de calendário (fora de escopo, migrar para Produção/PCP, ou descartar).
- [ ] Definição única de filtro de pesquisa (sem duplicidade).
- [ ] Esclarecido com o time como novos "Tipos de Falta" serão cadastrados no sistema novo.
- [ ] Nenhuma lacuna funcional em relação a este PRD nas telas de registro, listagem, pesquisa e emissão de relatório de faltas.
