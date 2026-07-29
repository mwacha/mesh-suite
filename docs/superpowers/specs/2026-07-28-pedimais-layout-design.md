# Rebrand para PediMais + Novo Sistema Visual — Spec de Design

**Data**: 2026-07-28
**Referência**: pasta `layout/` na raiz do repo (protótipo React/HTML de um sistema real chamado "PediMais", com capturas de tela em `layout/scraps/` e `layout/uploads/`)
**Escopo relacionado**: telas já implementadas na fatia PRD-14 slice 1 (login, recuperação de senha) e a `DashboardView` placeholder

## 1. Contexto e decisão

O produto passa a se chamar **PediMais** do ponto de vista do usuário (nome exibido, logo, paleta de cores, textos da UI). Esta é uma decisão consciente, tomada nesta rodada de conversa, para adotar o sistema visual já validado em `layout/` (que tem capturas reais de tela, não só wireframes) em vez do sistema petróleo/dourado usado até agora (`design_handoff/`, agora considerado desatualizado — não será removido, mas deixa de ser a referência vigente).

**Escopo explicitamente limitado ao que o usuário vê.** Nomes internos continuam como estão: pacotes Java (`com.meshsuite.*`), diretórios (`mesh-suite-backend/`, `mesh-suite-frontend/`), nome do banco (`meshsuite`), repositório Git (`mwacha/mesh-suite`). Renomear isso é um refactor grande e arriscado, tratado como tarefa futura separada caso o usuário queira.

**Nenhuma mudança de backend.** Este spec é puramente frontend — visual e estrutural. O endpoint `/api/auth/me` continua retornando só `{ nome, papel }`; a UI não exibe nada que dependa de dado que o backend não fornece hoje (ver seção 4, Topbar).

## 2. Escopo desta rodada

- Reestilizar `LoginView.vue`, `ForgotPasswordView.vue`, `ResetPasswordView.vue` para a paleta e marca PediMais, mantendo toda a estrutura funcional já existente (campos, validações, toggle de senha, mensagens de erro genéricas, link inerte).
- Criar um sistema de tokens de design compartilhado (cores/espaçamento) usado por todos os componentes novos e atualizados.
- Criar uma Shell de aplicação reutilizável (`AppShell` = `AppSidebar` + `AppTopbar`), usada pela `DashboardView` — e que as próximas fatias (Pedidos, Produtos, etc.) vão reaproveitar diretamente.
- Atualizar `DashboardView.vue` para usar a nova Shell.

### Fora de escopo desta rodada

- Qualquer tela de domínio de negócio real (Pedidos, Produtos, Clientes, etc. — a Shell só lista esses itens no menu, inertes, como preparação visual para quando cada fatia for implementada).
- Seletor de empresa funcional (multi-empresa) — mostrado como texto estático.
- Notificações reais — sino fica presente visualmente, inerte (sem painel funcional).
- Responsividade mobile — ferramenta interna, desktop-first, como o resto do projeto até aqui.
- Renomear qualquer coisa no backend/infraestrutura (ver seção 1).

## 3. Tokens de design

Arquivo `mesh-suite-frontend/src/styles/tokens.css`, importado uma vez em `main.ts`, usando CSS custom properties no `:root`. Valores extraídos diretamente do protótipo de referência (`layout/PediMais Prototipo.html`, objeto `C`) e conferidos contra as capturas de tela reais:

```
--pm-sidebar-bg: #1e293b       /* fundo da sidebar e do painel escuro do login */
--pm-sidebar-border: #334155   /* divisórias dentro da sidebar */
--pm-text-dark: #1e293b        /* texto principal em fundo claro */
--pm-text-mid: #475569
--pm-text-muted: #94a3b8       /* texto secundário, itens de menu inativos */
--pm-border: #374151
--pm-border-light: #e2e8f0     /* bordas de input/card em fundo claro */
--pm-bg: #f1f5f9               /* fundo da área de conteúdo */
--pm-white: #ffffff
--pm-accent: #2563eb           /* azul PediMais — botões, item ativo, links */
--pm-accent-bg: #eff6ff
--pm-accent-text: #1d4ed8
--pm-success: #15803d
--pm-success-bg: #dcfce7
--pm-warning: #d97706
--pm-warning-bg: #fef3c7
--pm-error: #dc2626
--pm-error-bg: #fee2e2
--pm-font: 'Inter', system-ui, sans-serif
```

`Inter` já não está carregada no projeto — adicionar via `@fontsource/inter` (pacote npm, sem depender de CDN do Google Fonts, consistente com a prática de não depender de recursos externos em produção) ou, alternativa mais simples, link do Google Fonts no `index.html` (aceitável para esta fatia, é o que o próprio protótipo de referência faz). Decisão: usar o link do Google Fonts em `index.html`, igual ao protótipo — mais simples, sem nova dependência.

Ícones: emoji simples (🏠 👥 🏢 🏷 💳 📋 🔒 📦 💰 👤), exatamente como no protótipo de referência — zero dependência nova, consistente com o material de origem.

## 4. Shell da aplicação

### `AppSidebar.vue`
- Largura 200px expandida / 48px colapsada (`transition: width .18s`), fundo `--pm-sidebar-bg`.
- Cabeçalho: quadrado azul 26×26 arredondado com "P" + texto "PediMais" (oculto quando colapsado) + botão de colapsar/expandir (◀/▶).
- Lista de itens, nesta ordem exata (igual ao protótipo): Home, Clientes, Empresa, Marcas, Pagamentos, Pedidos, Permissões, Produtos, Tab. Preços, Usuários.
- Só **Home** é funcional (navega para `/`, rota já autenticada da `DashboardView`). Os demais 9 itens são renderizados normalmente (ícone + label, mesmo estilo visual) mas com `cursor: not-allowed`, sem `@click`/navegação — mesmo padrão já estabelecido para o link "Fale com o time comercial" no login. Item ativo (Home, quando a rota atual é `/`) tem fundo `--pm-accent`.
- Rodapé: avatar circular (inicial do nome) + nome + e-mail do usuário logado, lidos do Pinia store `useAuthStore()` já existente (`usuario.nome`; e-mail não está no `MeResponse` atual — mostrar só o nome e o papel, sem inventar um e-mail que a API não fornece).

### `AppTopbar.vue`
- Breadcrumb simples: recebe um título de página via prop (ex.: "Dashboard") — sem navegação de breadcrumb multi-nível ainda, só o título, já que só existe uma página protegida hoje.
- Nome da empresa como texto estático — placeholder fixo "Empresa Principal" por enquanto (não há dado de empresa no `/me` atual; não inventamos uma chamada de API nova nesta rodada, ver seção 1).
- Sino de notificação: ícone presente, sem badge/contador (não há dado real), sem painel ao clicar (inerte).
- Avatar do usuário + menu dropdown: "Meu Perfil" e "Configurações" inertes (mesmo tratamento not-allowed); "Sair" é funcional e só client-side — limpa `authStore.usuario`/`checked` (Pinia) e navega para `/login`. Não existe endpoint de logout no backend nesta fatia, então nenhuma chamada de API é feita (ver risco 2).

### `AppShell.vue`
Compõe `AppSidebar` + `AppTopbar` (recebendo o título da página) + um `<slot>` para o conteúdo, replicando a estrutura `Shell` do protótipo (sidebar fixa à esquerda, coluna à direita com topbar + área de conteúdo rolável).

## 5. Telas

### `LoginView.vue`
Mesma estrutura já implementada (painel escuro à esquerda com logo/tagline, card à direita com formulário) — só a paleta e a marca mudam: fundo do painel/card `--pm-sidebar-bg`, botão de submit `--pm-accent` (em vez do dourado atual), logo vira o quadrado azul com "P", nome "PediMais", tagline "Gestão inteligente de pedidos para o seu negócio" (texto exato do protótipo de referência). Toggle de mostrar/ocultar senha, mensagens de erro genéricas (401/429/rede) e o link inerte "Fale com o time comercial" continuam exatamente como estão hoje — só a cor dos links/acentos muda de teal para `--pm-accent`.

### `ForgotPasswordView.vue` / `ResetPasswordView.vue`
Mesma estrutura de card centralizado já implementada, recolorida para a mesma paleta (fundo `--pm-sidebar-bg` no card, `--pm-accent` no botão/links). Nenhuma mudança de comportamento.

### `DashboardView.vue`
Passa a renderizar dentro de `AppShell` (título "Dashboard"). Conteúdo desta rodada: mensagem de boas-vindas simples com o nome do usuário logado (via `authStore.usuario.nome`) — sem KPIs/gráficos fake, já que não há dado real de nenhum domínio de negócio ainda. Isso substitui o stub atual ("Login bem-sucedido").

## 6. Testes

- Testes existentes de Login/Esqueci-senha/Redefinir-senha continuam válidos (testam comportamento, não cor) — revisão rápida para garantir que nenhum assert dependia do texto "Mesh Suite" (não depende, conferido).
- Novos testes: `AppSidebar.spec.ts` (item ativo navega, itens inertes não disparam navegação, colapsar/expandir alterna a largura), `AppTopbar.spec.ts` (menu do usuário abre/fecha, "Sair" limpa o store e navega para `/login`), `AppShell.spec.ts` (renderiza o conteúdo do slot).
- `DashboardView.spec.ts` (novo ou atualizado): renderiza a Shell e o nome do usuário logado.

## 7. Riscos e notas abertas

1. Sem e-mail no rodapé da sidebar (API atual não fornece) — mostra nome + papel só. Se o usuário quiser o e-mail ali, é uma extensão pequena e futura do `/me`.
2. "Sair" nesta fatia é só client-side (limpa cookie via um endpoint de logout ainda inexistente, ou simplesmente deixa o cookie expirar/o usuário fecha a sessão) — **decisão**: sem endpoint de logout no backend hoje, o botão "Sair" limpa o estado do Pinia e redireciona para `/login`; o cookie `HttpOnly` da JWT continua válido no navegador até expirar (8h/30 dias) ou até o usuário limpar cookies manualmente. Isso é uma limitação aceita nesta rodada, não um bug — revogação de sessão já está listada como nota aberta no spec original do PRD-14 slice 1.
3. `layout/` e `design_handoff/` ficam os dois no repo como material de referência; `design_handoff/` desatualizado deve ser ignorado por implementações futuras a partir de agora.
