# PRD — Cadastro & Segurança

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente. Em particular, o fluxo de login/autenticação descrito aqui é o **ponto de partida de regra de negócio** para `PRD-14-login-multitenant.md`, que o redesenha do zero (multitenant) — não uma implementação a estender.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant. Usuário e login, especificamente, já nascem escopados por tenant no `PRD-14` (unicidade de e-mail por tenant, não global).

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio **Cadastro & Segurança** é a base sobre a qual todos os demais domínios se apoiam: cadastro da(s) empresa(s) que operam o sistema, cadastro de usuários (que também representam "funcionários" em outros domínios, conforme já documentado no PRD de RH), autenticação/login, e controle de acesso por menu e por permissão.

Objetivo de negócio: autenticar usuários, controlar o que cada um pode acessar (por tela/menu e por tipo de permissão), e manter os dados cadastrais da(s) empresa(s) (inclusive dados fiscais usados pelos módulos de nota fiscal eletrônica/SPED de outros domínios).

## 2. Escopo

### Incluído neste PRD
- **Login/Autenticação**: validação de usuário/senha, criação de sessão, cálculo de permissões de menu.
- **Usuário**: cadastro de usuários do sistema, com dados bancários próprios (para reembolso/pagamento, sobreposição com o domínio Financeiro/RH), vínculos de menu e de permissão.
- **Menu**: estrutura hierárquica de itens de menu da aplicação.
- **Empresa**: dados cadastrais e fiscais da(s) empresa(s) operadas no sistema. O cadastro suporta múltiplos registros de empresa, com exatamente um marcado como matriz, mas **nenhum mecanismo liga um usuário a uma empresa específica** — ver correção de escopo abaixo sobre "Rede de Empresa".
- **Rede de Empresa** — **correção de escopo**: na primeira leitura deste PRD, esse conceito foi presumido como "múltiplas empresas sob a mesma operação sisconf". A investigação mostrou que isso está **incorreto**: é uma **rede comercial de clientes** (liga um cliente/fornecedor a uma rede, com condições comerciais especiais — ex. tabela de preço/desconto diferenciada para uma rede de lojas/franquia). Não tem relação com hospedar múltiplas empresas operadoras no mesmo sistema. Mantido aqui apenas porque existe um vínculo Usuário × Rede de Empresa cujo uso exato não foi confirmado nesta investigação, mas **não é** um mecanismo de seleção de empresa operadora.
- **Meus Dados / Nova Senha**: autoatendimento do usuário logado.
- **Módulo/Página**: cadastro técnico de módulos/páginas do sistema, usado para montar a estrutura de menu/permissão.

### Fora de escopo (pertence a outro domínio)
- Uso do usuário como "funcionário" para apontamento de falta — domínio **RH/Departamento Pessoal** (já documentado).
- Uso de horários de expediente da empresa pelo cálculo de fechamento de produção — domínio **Produção/PCP** (funcionalidade inativa, já sinalizada no PRD de RH).
- Cliente/Fornecedor (inclusive quando usado com o papel de "Contador" no cadastro da empresa) — domínio **Cadastro Comercial**.

## 3. Conceitos de dados

### Usuário
Campos: nome, endereço, bairro, cidade, CPF, indicador de ativo, data de cadastro, e-mail, **senha** (armazenada com hash — ver seção 5), dados bancários do próprio usuário (banco, agência, conta, dígitos — usados por um fluxo não coberto nesta investigação, plausivelmente reembolso/pagamento de comissão), vínculos de menu concedidos, vínculos de permissão concedidos, data do último acesso, e um tipo de usuário (Administrativo/Representante/Produção/Terceirizado/Administrador, já documentado no PRD de RH).

### Menu
Campos: identificador, nome, título, item pai (hierarquia, mesmo padrão de árvore visto no Plano de Contas do domínio Contábil), ordem, nível, indicador de ativo.

### Vínculo Usuário × Menu
Concede a um usuário acesso a um item de menu específico.

### Vínculo Usuário × Permissão
Concede a um usuário um tipo de permissão mais amplo (não por item de menu, mas por categoria).

**Nota de arquitetura**: o sistema tem **dois mecanismos de controle de acesso coexistindo** — por item de menu e por tipo de permissão — sem que a relação entre os dois esteja documentada nesta investigação. **Requer validação com o time** se um complementa o outro ou se há sobreposição/redundância.

### Empresa
Campos: dados cadastrais (nome, contato, endereço completo, município, telefones), dados fiscais (CNPJ, inscrição estadual, inscrição municipal, CNAE, regime tributário, tipo de empresa, tipo de natureza jurídica, tipo de atividade, perfil da empresa no SPED, tipo de incidência tributária, tipo de método de apropriação, tipo de contribuição, tipo de escrituração, indicadores de tributação por ICMS/ISS/IPI/PIS/COFINS), dados operacionais (potencial de produção, quantidade média de peças por caixa de expedição — usado no domínio Expedição), dados financeiros (alíquota do Simples Nacional, taxa de juros diária), horários de expediente (usados por uma funcionalidade inativa de fechamento de produção do domínio RH), contador (referência ao cadastro de cliente/fornecedor), indicador de envio web, indicador de uso de CTI, indicador de ativo.

### Rede de Empresa / Vínculo Cliente-Fornecedor × Rede / Vínculo Usuário × Rede
É uma **rede comercial de clientes** (conceitualmente pertence ao domínio Cadastro Comercial, não a Cadastro & Segurança), mas está descrita aqui porque existe um vínculo Usuário × Rede de Empresa cujo propósito exato não foi confirmado nesta investigação. Campos: nome, indicador de condição especial, indicador de ativo, condição especial associada, e a lista de clientes/fornecedores vinculados a essa rede. **Não presumir que resolve seleção de empresa operadora no login** (ver correção de escopo acima).

### Não existe, hoje, nenhuma associação Usuário ↔ Empresa
Confirmado na investigação: não há, no modelo de dados do usuário, nenhum campo que o associe a uma empresa específica. O cadastro de Empresa suporta múltiplos registros, mas nada no modelo de dados liga um usuário a uma empresa específica — a única empresa "ativa" em qualquer momento é a que está na sessão do usuário autenticado, sempre populada com a matriz no login (ver seção 4). **Este é o dado-chave para qualquer trabalho de multi-tenant**: o sistema hoje é efetivamente single-tenant por instalação, com um conceito de "empresa corrente" global de sessão, não por usuário/tenant.

## 4. Fluxos funcionais

### Fluxo principal — Login
1. Usuário informa e-mail e senha na tela de login.
2. O sistema limpa todos os dados de sessão existentes e valida o usuário e a senha informada — a senha informada é **transformada por hash** antes da comparação (ver risco 1 na seção 8).
3. Se válido, popula a sessão com: usuário autenticado, empresa (**sempre a mesma empresa matriz para todos os usuários**, independentemente de quem logou; não há seleção de empresa por usuário porque não existe vínculo Usuário↔Empresa no modelo de dados atual — ver seção 3), mapa de permissões de acesso calculado para aquele usuário, contagem de mensagens não lidas, data atual, data do último acesso.
4. Atualiza a data do último acesso do usuário.
5. Em caso de falha, uma mensagem de login inválido é exibida.

### Fluxo — Validação de Backup no Login (funcionalidade sem efeito real)
Existe, no fluxo de login do sistema legado, uma referência a um caminho de arquivo fixo de backup e a uma chamada para a mesma funcionalidade de backup vazia já identificada no PRD de Administração do Sistema (não faz nada). Essa validação também não parece ser chamada a partir do fluxo principal de login investigado — **requer confirmação se esse comportamento é sequer acionado no fluxo de login real, ou se é código sem efeito** (ver risco 4 na seção 8).

### Fluxo — Cadastro de Usuário / Empresa / Rede de Empresa
Cadastro, edição, exclusão e consulta padrão para os três conceitos — o comportamento detalhado não foi confirmado em profundidade nesta investigação; presume-se seguir o mesmo padrão transacional dos demais domínios.

## 5. Regras de negócio

1. **Senha de usuário é protegida por hash usando um algoritmo criptográfico fraco (MD5)** — ver risco 1 na seção 8.
2. **Login sempre carrega a Empresa matriz na sessão**, não uma empresa selecionada/associada ao usuário, mesmo havendo suporte a múltiplas empresas no cadastro — **requer validação com o time**.
3. **A sessão é completamente limpa a cada tentativa de login**, antes de popular os novos dados — evita vazamento de estado entre sessões de usuários diferentes no mesmo navegador/aba.
4. **Tempo de expiração de sessão configurado em 60 minutos** no sistema legado — valor de referência para o sistema novo, a confirmar com o time se deve ser mantido.
5. **Existe, no sistema legado, uma configuração de autenticação declarada que não corresponde ao fluxo real de login** (que é feito por uma tela própria da aplicação) — **requer validação com o time** se essa configuração é vestigial/sem efeito ou se há alguma rota protegida por um mecanismo de autenticação paralelo ao login customizado.

## 6. Integrações e dependências

- **É dependência de todos os demais domínios**: usuário (autor/responsável em praticamente todo registro do sistema), empresa (dados fiscais usados por nota fiscal eletrônica/SPED, dados operacionais usados por Expedição/Produção).
- **Depende de Cadastro Comercial**: o contador da empresa é um registro de cliente/fornecedor.
- **Consumido por RH**: o usuário é reaproveitado como registro de funcionário (já documentado).
- **Consumido por Administração do Sistema**: o usuário é remetente/destinatário de mensagens internas.

## 7. Requisitos não-funcionais relevantes

- Autenticação e controle de sessão são centrais à segurança de todo o sistema — qualquer alteração neste domínio deve ser tratada com cautela adicional e testada em todos os fluxos de login/logout/expiração de sessão.
- O cálculo de permissões roda a cada login e fica armazenado na sessão — alterações de permissão de um usuário só têm efeito após novo login (a sessão não é recalculada dinamicamente).

## 8. Riscos e comportamentos conhecidos a decidir

1. **Hash de senha com algoritmo criptográfico fraco (MD5)**: é um algoritmo quebrado para armazenamento de senhas (rápido de forçar por força bruta), sem indicação de uso de salt. É um risco de segurança real — o sistema novo deve usar um algoritmo moderno de hash de senha (ex. bcrypt/Argon2/PBKDF2) com salt, com plano de migração gradual (rehash no próximo login bem-sucedido de cada usuário existente).
2. **Login sempre usa a Empresa matriz para todos os usuários**, e o modelo de dados não tem nenhum vínculo Usuário↔Empresa que permitisse fazer diferente — se a intenção de negócio é que diferentes usuários operem diferentes empresas (multiempresa) ou que o sistema hospede múltiplos clientes isolados (multitenant), isso exige trabalho de modelagem novo, não apenas um ajuste pontual no login. **Requer validação com o time** — este é exatamente o ponto de partida tratado por `PRD-14-login-multitenant.md`.
3. **Um comentário no sistema legado sobre o tempo de expiração de sessão está desatualizado** em relação ao valor efetivamente configurado — risco de confusão em manutenções futuras; não é um bug funcional, mas o sistema novo deve garantir que documentação e configuração estejam sempre alinhadas.
4. **Uma terceira ocorrência de mecanismo de backup divergente**, encontrada no fluxo de login, referencia um caminho de arquivo totalmente diferente dos já documentados no PRD de Administração do Sistema, e aciona uma funcionalidade vazia sem efeito real. Reforça a recomendação, já registrada naquele PRD, de consolidar toda a lógica de backup em um único mecanismo, testado e configurável por ambiente.
5. **Dois mecanismos de controle de acesso não claramente relacionados** (por item de menu vs. por tipo de permissão) — risco de inconsistência entre os dois ao conceder/revogar acesso a um usuário; **requer validação com o time** sobre a relação pretendida entre eles antes de qualquer trabalho de simplificação no sistema novo.
6. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado — neste domínio o risco é direto, pois essas credenciais controlam o acesso a toda a base de usuários e senhas (ainda que hasheadas com um algoritmo fraco); o sistema novo não deve reproduzir esse padrão.

## 9. Critérios de aceite / Definition of Done

- [ ] Login continua autenticando corretamente e populando a sessão (usuário, empresa, permissões de acesso, contagem de mensagens, último acesso).
- [ ] Sessão continua sendo completamente limpa a cada nova tentativa de login.
- [ ] Cadastro de Usuário, Empresa e Rede de Empresa preservados.
- [ ] Controle de acesso por menu e por permissão continua funcionando exatamente como hoje, com a relação entre os dois esclarecida (risco 5) antes de qualquer simplificação.
- [ ] Decisão registrada e plano definido para uso de um algoritmo moderno de hash de senha desde o primeiro dia do sistema novo (risco 1).
- [ ] Esclarecido com o time se o login deveria carregar uma empresa por usuário/tenant em vez de sempre a matriz (risco 2) — ver também `PRD-14-login-multitenant.md`, que trata esse ponto como requisito central de uma nova frente de trabalho.
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de usuário, empresa, login, rede de empresa, nova senha e meus dados.
