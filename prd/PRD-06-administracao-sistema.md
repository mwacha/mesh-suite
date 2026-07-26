# PRD — Administração do Sistema

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio **Administração do Sistema** cobre duas funcionalidades transversais e independentes entre si:

1. **Backup do banco de dados** — configuração de agenda de backup e execução da cópia de segurança.
2. **Mensagens internas** — caixa de mensagens entre usuários do sistema, incluindo notificação automática por grupo quando determinados eventos de negócio ocorrem (ex. nota fiscal faturada na expedição, pedido liberado para faturar).

**Correções feitas nesta investigação em relação ao levantamento inicial de domínios:**
- Um conceito de chão de fábrica (mistura/reagrupamento de peças cortadas entre lotes de produção) estava provisoriamente listado neste domínio, mas na verdade pertence ao domínio **Produção/PCP**. Foi removido deste PRD.
- Um conceito de **ficha técnica/BOM do produto** (rendimento de tecido, consumo de matéria-prima por estampa/cor) também estava aqui provisoriamente, mas pertence ao domínio **Cadastro Comercial** (está registrado no mesmo menu de cadastro que estampa/produto). Foi removido deste PRD.

## 2. Escopo

### Incluído neste PRD
- **Backup do banco de dados**: configuração de dias da semana/horário preferido e execução manual da cópia de segurança.
- **Mensagens internas**: envio de mensagem de um usuário para um ou mais usuários selecionados, com controle de leitura.
- **Grupo de Mensagem / notificação automática por evento**: define quais usuários recebem notificação automática quando um evento de sistema pré-definido ocorre.
- **Textos fiscais reutilizáveis** — cadastro de textos/observações fiscais reutilizáveis (ver nota de fronteira ambígua abaixo).

### Fronteira ambígua (documentada, não decidida)
- **Textos fiscais reutilizáveis**: pelo nome e conteúdo (descrição + texto de "informação fiscal"), pode ser um cadastro de textos padronizados para preenchimento de observações em Nota Fiscal — mais próximo do domínio **Fiscal/Tributário** do que de "mensagens internas entre usuários". Mantido neste PRD por estar historicamente agrupado com o cadastro de Mensagens no sistema legado, mas **requer validação com o time** sobre se deveria migrar para o domínio Fiscal.

### Fora de escopo (pertence a outro domínio)
- Usuário (destinatários das mensagens, remetente) — domínio **Cadastro & Segurança**.
- Os dois conceitos removidos deste PRD (ver nota de correção acima).
- Backup de arquivos além do banco de dados (imagens, arquivos de nota fiscal eletrônica, etc.) — não encontrado no sistema legado; não presumir que exista.

## 3. Conceitos de dados

### Backup
Campos: um indicador para cada dia da semana (segunda a domingo), horário preferido, data do último backup realizado, e a configuração de agenda derivada dos dias marcados (ver seção 4).

### Mensagem
Campos: assunto (obrigatório), texto (obrigatório, até 1000 caracteres), data de envio, remetente, indicador de ativo, uma referência de origem (aparentemente destinada a vincular a mensagem a um registro de outro módulo, mas sem uso identificado nos fluxos investigados — ver risco na seção 8), situação da mensagem (ver valores abaixo), e a lista de destinatários.

### Destinatário de Mensagem
Campos: usuário destinatário, mensagem associada, data de leitura (vazia enquanto não lida), indicador de ativo.

### Grupo de Mensagem
Associa um tipo de evento de notificação a uma lista de usuários inscritos para receber notificação quando aquele evento ocorrer.

### Tipo de Evento de Mensagem
Valores fixos: "Nota fiscal faturada na expedição" e "Pedido liberado para faturar" — confirma que o sistema dispara notificação automática para grupos de usuários quando: (a) uma nota fiscal é faturada na expedição, ou (b) um pedido é liberado para faturamento. O comportamento que efetivamente dispara essas notificações não foi localizado nos fluxos investigados neste PRD — está presumivelmente nos fluxos de Vendas/Expedição/Fiscal que tratam esses eventos — **requer validação com o time / investigação adicional se for necessário alterar esse comportamento**.

### Situação da Mensagem
Valores fixos: "Enviado" e "Recebido" — ver risco 3 na seção 8 sobre um defeito de configuração desses valores no sistema legado.

### Texto Fiscal Reutilizável
Campos: descrição (obrigatória), texto de informação fiscal, indicador de ativo.

## 4. Fluxos funcionais

### Fluxo — Configurar e executar Backup
1. Usuário acessa a tela de Backup, marca os dias da semana desejados e informa o horário.
2. Ao salvar, o sistema grava a configuração como histórico, mas **não chega a agendar de fato o backup automático** — a expressão de agenda é calculada e descartada, sem qualquer job efetivamente registrado (ver risco 1 na seção 8).
3. A execução real do backup roda o utilitário de cópia de segurança do banco de dados como um processo externo, usando um caminho e um host de banco configurados no ambiente. O processo é executado e sua saída de erro é acompanhada linha a linha, mas o sistema não verifica se o processo terminou com sucesso ou falha.
4. Não há evidência de que essa execução seja disparada a partir de um agendamento automático real ou de qualquer botão de tela mapeado — **requer validação com o time** sobre como o backup é efetivamente disparado em produção hoje (manual? script externo?).

### Fluxo — Enviar Mensagem
1. Usuário compõe assunto e texto, seleciona um ou mais usuários destinatários.
2. Ao salvar: o sistema valida os dados (seção 5), define a data de envio como o momento atual, marca a situação como "Enviado", registra o remetente a partir do usuário autenticado; para cada usuário marcado, cria um registro de destinatário (sem data de leitura) e associa à mensagem.
3. Destinatário acessa sua caixa de mensagens e pode marcar uma mensagem como lida, o que registra a data de leitura no vínculo correspondente.
4. Excluir a mensagem inteira (ação do remetente) e remover apenas o vínculo com um destinatário específico (ação por destinatário) são operações distintas.

### Fluxo — Grupo de Mensagem (notificação automática por evento)
O cadastro associa um tipo de evento a uma lista de usuários que devem ser notificados quando aquele evento ocorrer no sistema. O disparo em si (criação da mensagem e dos destinatários quando o evento realmente acontece) não foi encontrado nos arquivos deste domínio — presumivelmente reside nos domínios de negócio que geram o evento (Vendas, Expedição, Fiscal).

## 5. Regras de negócio

1. **Validação obrigatória de mensagem**: assunto obrigatório, texto obrigatório, pelo menos um usuário destinatário selecionado.
2. **A configuração de agenda do Backup nunca é aplicada de fato** (ver fluxo 2 acima) — a configuração de dias/horário salva não tem efeito operacional detectável na investigação; ver risco 1 na seção 8.
3. **Notificação automática restrita a dois eventos pré-definidos**: nota fiscal faturada na expedição e pedido liberado para faturar — qualquer novo tipo de evento notificável exigiria alteração de sistema. Requer validação com o time se o sistema novo deve tornar esses eventos configuráveis pelo usuário.

## 6. Integrações e dependências

- **Depende de Cadastro & Segurança**: usuário (remetente, destinatários, membros de grupo de notificação).
- **Depende de infraestrutura de execução de cópia de segurança do banco de dados** — a execução do backup é um processo externo ao sistema, não uma operação feita inteiramente dentro da aplicação.
- **Consumido implicitamente por Vendas/Expedição/Fiscal** através do mecanismo de notificação por evento (eventos "nota fiscal faturada na expedição" e "pedido liberado para faturar") — o comportamento que dispara essas notificações não foi localizado neste PRD; qualquer alteração nesses fluxos de outros domínios deve considerar se também precisa disparar notificação via este mecanismo.
- Nenhuma integração externa (autoridade fiscal, gateway de pagamento) neste domínio.

## 7. Requisitos não-funcionais relevantes

- O backup depende de infraestrutura externa ao sistema para efetivamente rodar — o sistema novo deve decidir explicitamente como esse agendamento e execução serão feitos (job interno confiável, agendador externo, ou backup gerenciado pelo provedor de banco de dados), já que o sistema legado tem uma tela de configuração que na prática não agenda nada (ver risco 1).
- Envio de mensagem grava um destinatário por usuário selecionado (todos no momento do envio) — para volumes grandes de destinatários isso significa um registro por usuário; aceitável na escala observada do sistema (ERP interno de uma empresa), sem necessidade de processamento assíncrono.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Agendamento de backup não funcional**: no sistema legado, a tela de configuração de Backup dá a falsa impressão de que marcar dias/horário agenda o backup automaticamente; na prática, isso **não acontece** — nenhum agendamento real chega a ser criado. Há também vestígios de uma segunda tentativa abandonada da mesma ideia, comentada e nunca ativada. O sistema novo deve decidir explicitamente se implementa agendamento automático de fato, ou se documenta claramente que o backup é sempre disparado manualmente/por infraestrutura externa.
2. **Credenciais de acesso ao banco hardcoded no sistema legado, em dois lugares distintos e divergentes**: uma senha está embutida diretamente no código-fonte da execução do backup, e outra senha diferente está embutida em um script externo de backup, com um caminho de rede fixo — dois mecanismos de backup paralelos e desalinhados, um provavelmente vestígio de uma versão anterior do sistema. Reforça o achado transversal já registrado no índice geral sobre credenciais em texto puro — aqui agravado por serem **duas fontes divergentes** para a mesma finalidade. O sistema novo não deve reproduzir esse padrão: credenciais via variável de ambiente/secret manager.
3. **Os dois valores de situação de mensagem ("Enviado" e "Recebido") compartilham, por erro, o mesmo identificador no sistema legado** — bug de copy-paste; qualquer lógica que dependa de distinguir os dois por identificador não funcionaria corretamente (na prática, apenas "Enviado" parece ser usado no fluxo de envio). O sistema novo deve garantir que cada situação tenha um identificador próprio.
4. **O campo de referência de origem da mensagem existe no modelo mas não foi encontrado uso em nenhum fluxo investigado** — possível campo planejado para vincular mensagens a registros de outros módulos (ex. um pedido, uma nota) e nunca implementado, ou usado em um fluxo não coberto por esta investigação — **requer validação com o time** se essa vinculação deve ser um requisito real do sistema novo.
5. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado — este domínio adiciona mais duas ocorrências (risco 2 acima); o sistema novo não deve reproduzir esse padrão.

## 9. Critérios de aceite / Definition of Done

- [ ] Envio de mensagem para um ou mais usuários continua funcionando com as três validações obrigatórias (assunto, texto, destinatário).
- [ ] Marcação de mensagem como lida e exclusão (da mensagem inteira e do vínculo por usuário) preservadas.
- [ ] Cadastro de Grupo de Mensagem (evento × usuários notificados) preservado.
- [ ] Execução manual de backup continua gerando o arquivo de cópia de segurança no destino configurado.
- [ ] Decisão registrada sobre o agendamento automático de backup (risco 1): implementar de fato, documentar que é sempre manual, ou substituir por solução de infraestrutura gerenciada.
- [ ] Credenciais de backup (risco 2) migradas para variável de ambiente/secrets manager e unificadas em um único mecanismo.
- [ ] Bug de identificador duplicado entre as situações de mensagem (risco 3) corrigido.
- [ ] Esclarecido com o time se o cadastro de Textos Fiscais Reutilizáveis pertence a este domínio ou ao domínio Fiscal.
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de backup, mensagens e grupo de mensagem.
