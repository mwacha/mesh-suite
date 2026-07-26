# PRD — Produção / PCP

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio **Produção/PCP** (Planejamento e Controle da Produção) é o mais extenso e operacionalmente complexo do sistema: rastreia uma peça de roupa desde o **corte do tecido** até a **embalagem final**, passando por uma sequência configurável de setores de produção (costura, acabamento, etc.), incluindo o envio de lotes para **costura terceirizada (facção)**.

**Correções feitas nesta investigação em relação ao levantamento inicial de domínios:**
- O conceito de **mescla** (mistura/reagrupamento de peças entre lotes de corte), inicialmente listado no domínio Administração do Sistema, pertence a este domínio — ver seção 3.
- O subsistema de **"Mesada"** (facção/terceirização), que no levantamento inicial de tamanho de domínios apareceu como um conjunto extenso de artefatos, é o subsistema de envio de peças cortadas para costura terceirizada paga por peça — está descrito aqui.

Objetivo de negócio: planejar e rastrear cada etapa da confecção de uma peça (corte → costura interna ou terceirizada → arremate/acabamento → embalagem), com controle de quantidade, custo e ocorrências (defeitos, atrasos) em cada etapa.

## 2. Escopo

### Incluído neste PRD
- **Corte**: registro do lote de corte de um pedido/produto, com quantidade de peças, defeitos e inutilizadas.
- **Controle de Corte / Roteiro de Produção**: acompanhamento de cada lote de peças cortadas através de uma sequência de setores de produção, incluindo trechos enviados para arremate externo.
- **Setor de Produção / Unidade de Produção**: cadastro dos locais/postos físicos ou lógicos de produção.
- **Sequência de Produção**: define, por produto/modelo, a ordem de setores pelos quais um lote deve passar (roteiro de fabricação).
- **Apontamento de Produção**: registro de quanto cada usuário produziu, em qual setor, em determinado período — a base para cálculo de produtividade e, presumivelmente, remuneração por peça.
- **Arremate**: controle específico da etapa de acabamento/arremate, com peso enviado/retornado.
- **Ocorrência de Produção**: registro de incidentes (defeito, atraso, falta de material) vinculados a um lote/etapa.
- **Mesada / Facção**: controle de lotes de corte enviados para costura terceirizada, pagos por peça — inclui controle de enfesto (camadas de tecido cortadas juntas), vínculo fiscal do envio a terceiros, ocorrências, produtos, e controle de sobra de tecido (retalho).
- **Mescla**: mistura/reagrupamento de peças entre lotes de corte, com status próprio e conferente.
- **Conferência de Produção**: validação/conferência de quantidades produzidas.
- **Gerar Produto Embalado**: última etapa, conversão de peças produzidas em produto embalado pronto para estoque/expedição.
- **Configuração de Corte**: parâmetros usados no módulo de corte.

### Fora de escopo (pertence a outro domínio)
- Pedido/item de pedido — domínio **Vendas**; a produção é disparada a partir de um pedido, mas o pedido em si é documentado lá.
- Produto, cor, tamanho, estampa, tecido/aviamento, ficha técnica (rendimento/consumo de matéria-prima) — domínio **Cadastro Comercial**.
- Embalagem de Expedição, Volume do Pedido — domínio **Expedição/Logística**; este domínio termina com o produto pronto e embalado internamente, antes da montagem dos volumes de despacho.
- Movimentação de estoque em si — domínio **Estoque**; este domínio dispara movimentações com origens específicas de produção (geração de corte, envio de corte, apontamento de produção, embalagem de produção, etiquetagem de produção — já documentadas no PRD de Estoque).
- Usuário como "funcionário" produtivo — domínio **Cadastro & Segurança**/**RH**.
- Estoque por posto de produção — mencionado no PRD de Estoque como pertencente conceitualmente a este domínio, mas não investigado em detalhe aqui — **requer investigação adicional se for objeto de trabalho**.

## 3. Conceitos de dados

### Corte
Campos: data do corte, data de envio, data de início, facção associada (**um corte pode já nascer vinculado a uma operação de terceirização**), quantidade de peças, quantidade de peças com defeito, quantidade de peças inutilizadas, data de conclusão, quantidade de controle, os itens do corte, observação, quantidade atual.

### Controle de Corte (roteiro/rastreamento de um lote pelas etapas)
Este é o registro central de rastreamento: cada instância representa a passagem de um lote (ou parte dele) por uma etapa/setor, com datas, responsável, peso e, opcionalmente, vínculo a um arremate externo ou a uma mescla. Campos: item de corte associado, data de início/fim do período em que o lote esteve nesta etapa, setor de produção (opcional), posição na sequência de etapas (opcional), usuário responsável pela etapa, peso de envio/peso de retorno (controle por peso, comum em facção/arremate externo), datas de envio/retorno quando a etapa é de arremate, arremate associado (opcional), mescla associada (opcional), as ocorrências vinculadas, custo desta etapa, quantidade original do corte, quantidade "alterada + controle + produção", e quantidade removida/adicionada do controle — estas duas últimas **explicitamente identificadas como "não contabilizam na produção"** (ver regra de negócio 1).

### Apontamento e Ocorrência do Controle de Corte
Registram, respectivamente, o apontamento de produção e as ocorrências (defeitos, atrasos) associados a uma etapa do controle de corte — estrutura de campos não detalhada nesta investigação.

### Arremate
Entidade de acabamento externo/interno — campos não detalhados nesta investigação; referenciada pelo controle de corte com peso enviado/retornado, sugerindo cobrança por peso do serviço de arremate.

### Setor de Produção, Unidade de Produção, Sequência de Produção
Cadastros de referência que definem, respectivamente, os postos de trabalho e a ordem em que um produto/modelo deve passar por eles — a "receita" do roteiro de produção. Campos não detalhados nesta investigação.

### Mesada e conceitos relacionados (facção/terceirização)
Subsistema robusto (confirmado por um volume grande de artefatos de código, entidades, consultas e relatórios investigados), cobrindo: controle de enfesto (camadas de tecido cortadas juntas), mescla dentro do contexto de mesada, vínculo fiscal do envio a terceiros, ocorrências, produtos, controle de sobra de tecido (retalho) e tipos de referência associados. **Campos individuais não detalhados nesta investigação** — qualquer trabalho de implementação nesse subsistema específico deve começar por uma investigação dedicada.

### Mescla
Campos: data de cadastro, quantidade de peças, quantidade de peças original, produto, usuário, status da mescla, conferente (segunda pessoa que confere a operação), observação. Usada para misturar/reagrupar peças entre lotes de corte diferentes (ex. juntar sobras de dois cortes do mesmo produto/cor para completar uma quantidade).

### Ocorrência de Produção
Registro de incidentes de produção, vinculável a um setor/unidade de produção e a um tipo de operação — usado tanto em Mescla quanto em Controle de Corte.

## 4. Fluxos funcionais

### Fluxo principal — Do corte ao produto embalado
1. **Corte**: usuário registra um corte para um pedido/produto (quantidade de peças, data), possivelmente já vinculando a uma operação de facção se a peça será costurada externamente.
2. **Roteiro de produção**: para cada item do corte, o sistema (ou o usuário) cria registros de controle que definem por quais setores de produção, em qual ordem, o lote deve passar — presumivelmente pré-definido pela sequência de produção cadastrada para aquele produto/modelo (a relação exata entre a sequência cadastrada e a geração automática desses registros de controle não foi confirmada nesta investigação — **requer investigação adicional**).
3. **Apontamento de produção**: usuários de produção registram quanto produziram em cada etapa; o sistema calcula a produção acumulada por usuário/setor/dia e permite finalizar o controle de produção de uma etapa.
4. **Envio a arremate/facção (opcional)**: se a etapa envolve arremate externo ou facção, o controle de corte registra peso enviado e data de envio e, no retorno, peso retornado e data de retorno — controle por peso é o método usado para conferência de quantidade em operações terceirizadas de costura.
5. **Mescla (se necessário)**: peças de lotes diferentes podem ser combinadas via mescla, com conferência por um segundo usuário e status controlado.
6. **Conferência de Produção**: validação final das quantidades produzidas.
7. **Gerar Produto Embalado**: converte peças produzidas em produto pronto/embalado, disparando uma movimentação de estoque com a origem "embalagem de produção" (documentada no PRD de Estoque).

### Fluxo — Ocorrência de Produção
Registro de incidentes vinculado a uma etapa/setor — usado tanto isoladamente quanto embutido no Controle de Corte/Mescla via listas de ocorrência.

## 5. Regras de negócio

1. **As quantidades removida/adicionada do controle de corte são explicitamente excluídas do cálculo de produção** (confirmado por anotação do desenvolvedor original no próprio sistema legado: "não contabiliza na produção") — servem apenas para ajuste de controle/estoque de peças, não para métricas de produtividade.
2. **Controle por peso em operações externas** (arremate/facção): peso enviado e peso retornado são ambos registrados, permitindo identificar divergência (perda/sobra) na operação terceirizada — a regra de tolerância/alerta para divergência de peso, se existir, não foi localizada nesta investigação.
3. **Mescla exige conferente**: a mescla tem um campo dedicado para um segundo usuário conferente, sugerindo segregação de função (quem mistura não é necessariamente quem confere) — regra de negócio não confirmada como obrigatória na validação do sistema legado (não investigada em detalhe).
4. **Corte pode nascer vinculado a uma facção**: a decisão de terceirizar pode já ser tomada no momento do corte, não apenas depois.

## 6. Integrações e dependências

- **Depende de Vendas**: pedido/item de pedido como origem da necessidade de produção.
- **Depende de Cadastro Comercial**: produto, características (cor/tamanho/estampa/tecido), ficha técnica (rendimento, já reclassificada para lá).
- **Alimenta Estoque**: dispara movimentações de estoque com múltiplas origens específicas de produção (geração de corte, envio de corte, apontamento de produção, embalagem de produção, etiquetagem de produção).
- **Relaciona-se com Cadastro & Segurança**: usuário como operador de cada etapa (e, no caso da Mesada, potencialmente como referência de custo/pagamento por peça — a ligação exata entre Mesada e o domínio Financeiro/RH, se existir, **requer validação com o time**, pois não foi encontrada nesta investigação uma geração automática de título financeiro a partir da Mesada).
- **Relaciona-se com Expedição/Logística**: o produto embalado gerado aqui é o insumo do fluxo de montagem de volumes documentado naquele domínio; e, como já registrado no PRD de Expedição, uma tela nomeada como se fosse de Expedição implementa, na prática, parte da lógica de Mescla deste domínio — **acoplamento cruzado real entre os dois domínios que deve ser resolvido junto com a limpeza de nomenclatura já recomendada no PRD de Expedição**.

## 7. Requisitos não-funcionais relevantes

- Volume de dados potencialmente alto: cada peça/lote passa por múltiplos registros de controle de corte (um por etapa), portanto consultas de rastreamento devem ser projetadas para paginação/filtro eficiente.
- Rastreabilidade completa da peça (corte → etapas → arremate/facção → mescla → embalagem) é um requisito implícito de negócio (permite saber onde está e por quem passou cada lote) — deve ser preservada no sistema novo.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Subsistema Mesada/Facção não foi investigado em profundidade nesta rodada** (volume grande de artefatos de código associados) — este PRD documenta sua existência, papel geral e principais conceitos, mas qualquer implementação/alteração real nesse subsistema exige investigação dedicada, não coberta em detalhe aqui.
2. **Acoplamento cruzado com o domínio Expedição** (já documentado no PRD de Expedição/Logística): uma tela nomeada como se fosse de expedição implementa, na prática, parte do fluxo de Mescla deste domínio. Recomenda-se que qualquer decisão de reorganização trate os dois domínios (Expedição e este) em conjunto.
3. **Relação entre a Sequência de Produção (roteiro cadastrado) e a geração real dos registros de controle por etapa não confirmada nesta investigação** — pode ser automática (o sistema cria os registros de controle a partir da sequência cadastrada) ou manual (o usuário cria cada etapa manualmente). **Requer investigação adicional antes de qualquer decisão sobre o fluxo de criação do roteiro no sistema novo.**
4. **Vínculo financeiro/RH da Mesada não confirmado**: não foi encontrada, nesta investigação, geração automática de título financeiro a partir da conclusão de um lote de Mesada, apesar de ser um serviço terceirizado pago por peça — **requer validação com o time** se esse pagamento é calculado/lançado manualmente fora do sistema, ou se existe um fluxo não coberto nesta investigação.

## 9. Critérios de aceite / Definition of Done

- [ ] Fluxo completo corte → controle por etapas → (opcional) arremate/facção → (opcional) mescla → conferência → produto embalado preservado, incluindo geração das movimentações de estoque correspondentes em cada etapa.
- [ ] Apontamento de produção por usuário/setor/dia continua calculando corretamente a produção acumulada, excluindo as quantidades removida/adicionada do total (regra de negócio 1).
- [ ] Controle de peso enviado/retornado em arremate e facção preservado.
- [ ] Mescla continua exigindo/registrando conferente e status corretamente.
- [ ] Cadastro de Setor de Produção, Unidade de Produção e Sequência de Produção preservados.
- [ ] Esclarecida a relação entre Sequência de Produção e a criação do Controle de Corte (risco 3) antes de qualquer decisão sobre esse fluxo no sistema novo.
- [ ] Esclarecido com o time como o pagamento de Mesada/facção é hoje calculado e lançado (risco 4).
- [ ] Investigação dedicada ao subsistema Mesada realizada antes de qualquer especificação de execução detalhada para ele (risco 1) — este documento não deve ser tratado como suficiente para implementação direta nessa área.
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de corte, controle de produção, setor/unidade, sequência de produção, ocorrência, configuração de corte, conferência, mescla, mesada e gerar produto embalado.
