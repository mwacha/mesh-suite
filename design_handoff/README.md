# Handoff: Dashboard de Produção — Mesh Suite (Conceito 2 recolorido)

## Overview
Tela de dashboard de produção para o ERP Mesh Suite (confecções). Baseada no conceito visual "Modern SaaS" (fundo claro, minimalista), com o menu lateral recolorido usando a paleta do conceito "Industrial Premium" (petróleo escuro + dourado). Conteúdo focado em indicadores de produção têxtil (corte, costura, facção, qualidade).

## About the Design Files
Os arquivos neste pacote são **referências de design feitas em HTML** — protótipos que mostram a aparência e a estrutura pretendidas, não código de produção para copiar diretamente. A tarefa é **recriar este design no ambiente já existente do sistema de vocês** (React, Vue, Angular, etc.), usando os componentes e padrões que já existem no código-base — ou, se ainda não existir um front-end, escolher o framework mais adequado ao projeto.

`Dashboard_Producao.dc.html` abre direto no navegador para visualização (usa um runtime auxiliar, `support.js`, só para renderizar a prévia — isso não faz parte do que deve ser portado).

## Fidelity
**Alta fidelidade (hifi)**: cores, tipografia, espaçamentos e layout exatos devem ser reproduzidos pixel a pixel usando os componentes/bibliotecas já existentes no sistema de vocês.

## Screens / Views

### Dashboard de Produção
**Propósito**: visão do dia a dia de chão de fábrica — indicadores de corte/OEE/qualidade, progresso da ordem de produção ativa e alertas.

**Layout**: `display:flex` de tela cheia (100vw x 100vh).
- Sidebar fixa à esquerda, 248px de largura.
- Área de conteúdo principal `flex:1`, com scroll vertical (`overflow-y:auto`), padding 44px 56px, `display:flex; flex-direction:column; gap:28px`.

**Componentes**:

1. **Sidebar** (248px, fundo `#0E2530`, borda direita `1px solid #1E4552`)
   - Logo: quadrado dourado 10x10px (`#C9A15A`, border-radius 2px) + texto "Mesh Suite" (Manrope 800, 18px, `#EAF2F4`).
   - Item ativo "Dashboard": fundo `#2E8B9933`, texto `#EAF2F4` 14px peso 600, borda esquerda 3px sólida `#C9A15A`, border-radius 8px, padding 9px 12px.
   - Cabeçalhos de seção (Comercial / Produto & Produção / Suprimentos / Gestão): 12px, peso 700, `#C9A15A`, uppercase, letter-spacing 0.05em.
   - Itens de menu inativos: 14px, `#8FB0BA`, padding 9px 12px.
   - Itens: CRM, Vendas & Orçamentos, Pedidos, Engenharia & Ficha Técnica, Coleções & Grade, PCP & Produção, Facções & Qualidade, Compras, Estoque & Almoxarifado, Financeiro & Fiscal, BI & Relatórios, Configurações.

2. **Header da área principal**
   - Texto pequeno "Planta São Paulo · turno da manhã" (14px, `#6B6B70`).
   - Título "Painel de produção" (30px, peso 800, `#17171A`, letter-spacing -0.01em).
   - Campo de busca (placeholder "Buscar ordem, peça, facção…"): fundo branco, borda `1px solid #E5E5E3`, border-radius 10px, padding 10px 16px, texto `#6B6B70` 14px.
   - Avatar: círculo 36px, fundo `#17171A`.

3. **KPIs** — grid de 4 colunas, gap 16px. Cards brancos, borda `#E5E5E3`, border-radius 16px, padding 20px 22px:
   - Peças cortadas hoje: 2.480
   - OEE da linha: 91%
   - Ordens em aberto: 37
   - Retrabalho / qualidade: 1,8% — card com fundo gradiente `linear-gradient(135deg,#5B5BF0,#7A6BFA)`, texto branco (destaque visual).

4. **Esteira de produção** (card branco, border-radius 16px, padding 24px 26px)
   - Título: "Esteira de produção — OP-4821 · Coleção Verão · Loja Aurora" + contador "1.200 peças".
   - 5 colunas (Corte, Costura, Facção, Qualidade, Expedição), cada uma com barra de progresso 8px de altura, border-radius 4px: concluídas em `#5B5BF0` sólido, etapa atual (Facção) em gradiente 55% preenchido, pendentes em `#E5E5E3`. Label abaixo de cada barra, 13px.

5. **Linha inferior** (`display:flex; gap:20px`)
   - **Coleção em produção** (flex 1.3): grid 4 colunas de placeholders de foto de produto (proporção livre, listras diagonais `repeating-linear-gradient` cinza claro, texto monoespaçado "foto do produto" centralizado).
   - **Alertas de chão de fábrica** (flex 1): lista vertical, cada item com borda esquerda colorida por severidade — vermelho `#D0453A` (atraso), amarelo `#C98A1B` (estoque/parada), verde `#1F9D66` (aprovação) — padding-left 12px, texto 14px `#17171A`.

## Design Tokens

**Cores**
- Sidebar / dark: fundo `#0E2530`, borda `#1E4552`, texto `#EAF2F4`, texto secundário `#8FB0BA`
- Accent dourado: `#C9A15A`
- Accent petróleo: `#2E8B99` (usado a 20% de opacidade no item ativo: `#2E8B9933`)
- Fundo principal: `#FAFAF9`
- Cards: `#FFFFFF`, borda `#E5E5E3`
- Texto principal: `#17171A`, texto secundário: `#6B6B70`
- Indigo (destaque/gráficos): `#5B5BF0`
- Sucesso: `#1F9D66` · Alerta: `#C98A1B` · Erro: `#D0453A`

**Tipografia**
- Família única: Manrope (400/500/600/700/800)
- Rótulos monoespaçados (placeholders de imagem): IBM Plex Mono
- Títulos: 28–30px peso 800; corpo/labels: 13–15px

**Espaçamento / raio**
- Padding de conteúdo: 44px 56px
- Gap padrão entre blocos: 16–28px
- Border-radius: 8px (itens de menu), 10px (inputs), 14–16px (cards)

## Interactions & Behavior
Protótipo estático — sem interações implementadas. Comportamento esperado na implementação real:
- Item de menu ativo muda conforme a rota atual (mesmo tratamento visual: fundo petróleo translúcido + borda dourada).
- Cards de KPI e esteira de produção devem refletir dados reais/tempo real.
- Alertas devem ser clicáveis, levando ao registro relacionado (ordem, item de estoque etc).

## State Management
Não aplicável ao protótipo (é uma tela estática). Na implementação: dados de KPI, progresso da ordem e alertas devem vir de chamadas de API/estado da aplicação, não hardcoded.

## Assets
Nenhuma imagem real usada — os quadros "foto do produto" são placeholders com listras diagonais; substituir por fotos reais de produto/coleção na implementação.

## Files
- `Dashboard_Producao.dc.html` — arquivo de referência do design (abre no navegador).
- `support.js` — runtime necessário apenas para essa prévia rodar; não faz parte do design em si.
- `screenshot-dashboard.png` — captura de tela do design para referência visual rápida.
