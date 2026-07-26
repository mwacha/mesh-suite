# PRD — Contábil / Patrimonial

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio **Contábil/Patrimonial** cobre três funcionalidades relacionadas à escrituração contábil e ao controle patrimonial da empresa:

1. **Plano de Contas** — estrutura hierárquica de contas contábeis usada para classificar lançamentos financeiros (contas a pagar/receber) e o balancete.
2. **Balancete** — relatório de apuração de saldos por período/natureza/conta, somente leitura (não tem cadastro próprio).
3. **Ativo Imobilizado** — cadastro de bens do imobilizado e geração automática do cronograma de apropriação/depreciação mensal, que alimenta o **Bloco G do SPED Fiscal ICMS/IPI (CIAP – Controle de Crédito de ICMS do Ativo Permanente)**.

Também inclui o cadastro de **Contador**, que — assim como "Transportadora" no domínio de Expedição — não é um cadastro próprio: reaproveita o cadastro de Cliente/Fornecedor com o papel de contador.

Objetivo de negócio: fornecer a estrutura contábil (plano de contas) usada por outros domínios financeiros para classificar despesas/receitas, apurar saldos por período (balancete) e controlar o patrimônio da empresa para fins de depreciação/crédito fiscal do ativo imobilizado.

## 2. Escopo

### Incluído neste PRD
- Cadastro de **Plano de Contas** hierárquico.
- Cadastro de **Centro de Custo** — usado como classificação adicional em Ativo Imobilizado.
- **Balancete** — consultas somente leitura de saldo por período/natureza/conta.
- Cadastro de **Ativo Imobilizado** e a **geração automática das parcelas de movimentação/depreciação**.
- Cadastro de **Contador** — não é cadastro próprio, reaproveita o cadastro de Cliente/Fornecedor com o papel "Contador".

### Fora de escopo (pertence a outro domínio)
- Lançamentos financeiros que referenciam o Plano de Contas como classificação — domínio **Financeiro**. Este PRD documenta apenas a estrutura da árvore de contas, não os lançamentos.
- Cliente/Fornecedor (cadastro-base reaproveitado para "Contador") — domínio **Cadastro Comercial**.
- Geração do arquivo SPED ICMS/IPI em si — domínio **Fiscal/Tributário**; aqui documentamos apenas que o Ativo Imobilizado alimenta o Bloco G, sem detalhar o layout do bloco.

## 3. Conceitos de dados

### Plano de Contas
Campos: descrição, nível na hierarquia, conta pai (a estrutura é uma árvore, cada conta pode ter uma conta pai), natureza da conta, indicador de se está ativa, caminho da hierarquia (usado para localizar a posição da conta na árvore), indicador de se aceita lançamento direto (tipicamente só contas-folha, sem filhas), código contábil externo (usado para integração com o contador/sistema contábil externo), valor máximo permitido (condicional, conforme configuração da conta), tipo de despesa associado (condicional), indicador de tipo de conta, natureza contábil (ver códigos abaixo) e data de cadastro.

Códigos de natureza contábil: Conta do Ativo, Conta do Passivo, Patrimônio Líquido, Conta de Resultado, Conta de Compensação, Outras — códigos compatíveis com a classificação contábil padrão brasileira.

### Centro de Custo
Cadastro simples, sem hierarquia: código, descrição (obrigatória), data de cadastro, indicador de ativo.

### Ativo Imobilizado
Campos: código (obrigatório), descrição (obrigatória), tipo de mercadoria, função/uso do bem (obrigatória), vida útil em meses (obrigatória), bem principal (permite vincular um componente a um bem principal), plano de contas (classificação contábil do bem), centro de custo, tipo de movimentação (ver códigos abaixo), componentes do valor de ICMS do bem relevantes para o crédito de ICMS — ICMS, ICMS-frete, ICMS-ST, ICMS-DIFAL (cada um com valor próprio), valor de cada parcela mensal de apropriação, quantidade de parcelas mensais a gerar (padrão **48**, consistente com a regra legal do CIAP de apropriação em 1/48 ao mês), data-base para o cronograma de parcelas, indicador de ativo.

Códigos de tipo de movimentação do imobilizado: Alienação/Transferência, Baixa do Bem, Conclusão da Imobilização, Imobilização em Andamento, Imobilização Individual, Imobilização Circulante, Outras Saídas, Deterioração, Saldo Inicial — **estes códigos correspondem exatamente à tabela oficial de tipo de movimentação do Bloco G do SPED EFD-ICMS/IPI (registro G125)**, confirmando o vínculo direto com a apuração fiscal; qualquer novo tipo de movimentação deve continuar seguindo essa tabela legal externa.

### Movimentação do Ativo Imobilizado
Campos: ativo imobilizado associado (obrigatório), data da movimentação (obrigatória), tipo de movimentação (obrigatório, mesmos códigos acima), número da parcela, valor da parcela. **Gerada inteiramente pelo sistema, nunca digitada pelo usuário diretamente** — ver seção 4.

### Contador
Não é um cadastro próprio. Como "Transportadora" no domínio de Expedição, o Contador é um registro de Cliente/Fornecedor com o papel "Contador".

## 4. Fluxos funcionais

### Fluxo principal — Cadastrar Plano de Contas
1. Usuário define descrição, conta pai (opcional), natureza contábil e indicador de tipo de conta.
2. Conforme o indicador de tipo de conta selecionado, os campos de tipo de despesa e valor máximo ficam visíveis/editáveis apenas quando aplicável — a condição exata para cada caso **requer validação com o time**.
3. Ao salvar, o sistema recalcula o nível e o caminho da hierarquia a partir da conta pai selecionada — mecanismo padrão de árvore hierárquica; o algoritmo específico de montagem do caminho não foi confirmado em detalhe nesta investigação (**requer validação com o time / investigação adicional se for preciso reproduzir exatamente**).
4. Excluir uma conta não tem, hoje, validação visível de que não existam lançamentos financeiros vinculados a ela (ver risco na seção 8).

### Fluxo — Balancete (somente consulta)
O balancete expõe consultas somente leitura, todas baseadas em um filtro (período e empresa, presumido):
- Saldo por período.
- Valor consolidado (resultado).
- Saldo agrupado por natureza da conta.
- Detalhamento por natureza/conta/tipo de despesa, usado presumivelmente para aprofundar a consulta a partir de um agrupamento anterior.

Não há cadastro próprio de "Balancete" — é inteiramente calculado a partir dos lançamentos financeiros existentes (fora deste domínio) classificados por Plano de Contas/natureza da conta.

### Fluxo principal — Cadastrar Ativo Imobilizado e gerar cronograma de depreciação
1. Usuário cadastra o bem: código, descrição, tipo de mercadoria, função, vida útil, plano de contas, centro de custo, tipo de movimentação, valores de ICMS, valor da parcela, quantidade total de parcelas (padrão 48) e data inicial.
2. Ao salvar, antes de gravar o novo estado, o sistema **apaga todas as movimentações já geradas para aquele bem**.
3. Em seguida, o sistema **gera automaticamente a quantidade configurada de movimentações**, uma por mês, começando no dia 1 do mês da data inicial e avançando um mês por parcela, cada uma com o mesmo valor de parcela e tipo de movimentação do bem.
4. Excluir o bem também remove todas as movimentações associadas a ele.
5. Essas movimentações mensais são posteriormente lidas pelo processo de geração do arquivo SPED para compor o Bloco G da EFD-ICMS/IPI (apuração do crédito de ICMS sobre o ativo permanente, CIAP).

### Fluxo — Cadastro de Contador
Segue o mesmo padrão do cadastro de Transportadora (domínio Expedição): ao salvar, o sistema força o papel "Contador" no registro de Cliente/Fornecedor associado.

## 5. Regras de negócio

1. **Regenerar cronograma sempre que o Ativo Imobilizado é salvo**: o sistema sempre apaga e recria todas as movimentações do bem, mesmo em uma edição simples — se o usuário editar apenas a descrição de um bem cujas parcelas já foram parcialmente "consumidas" (competências passadas fechadas), o cronograma inteiro é recriado do zero a partir da data inicial, sem preservar histórico de alterações anteriores. **Requer validação com o time** sobre se isso é aceitável quando já houve apuração/fechamento de períodos anteriores.
2. **Total de parcelas padrão é 48**, consistente com a regra legal do CIAP (apropriação do crédito de ICMS do ativo permanente em 1/48 por mês) — não há, porém, validação que impeça um valor diferente de 48.
3. **Data de cada parcela**: sempre o dia 1 do mês, a partir do mês da data inicial, incrementando um mês por parcela, independentemente do dia informado na data inicial.
4. **O valor de cada parcela é fixo e replicado igualmente em todas as parcelas** — não há cálculo de depreciação decrescente/variável; é um valor definido manualmente pelo usuário (o sistema não calcula o valor da parcela a partir de um valor total do bem dividido pela vida útil — esse cálculo, se existir, é feito fora do sistema e digitado diretamente).
5. **Os códigos de tipo de movimentação do imobilizado seguem a tabela oficial do SPED Bloco G** (registro G125) — qualquer novo tipo de movimentação deve continuar seguindo essa tabela legal externa; não é um valor de livre escolha do negócio.

## 6. Integrações e dependências

- **Depende de Cadastro Comercial**: cadastro de Cliente/Fornecedor (base do cadastro de Contador).
- **Alimenta o domínio Fiscal/Tributário**: os dados de Ativo Imobilizado e suas Movimentações são lidos pelo processo de geração do arquivo SPED EFD-ICMS/IPI para compor o Bloco G (CIAP). Contrato: o domínio Fiscal consome estes dados como fonte de leitura; nenhuma escrita de volta.
- **É referenciado por Financeiro**: lançamentos de contas a pagar/programação de pagamento usam o Plano de Contas como campo de classificação obrigatório em vários pontos.
- Nenhuma integração externa (sem chamada a autoridade fiscal, banco, etc.) neste domínio isoladamente — a geração do arquivo SPED em si é responsabilidade do domínio Fiscal.

## 7. Requisitos não-funcionais relevantes

- A regeneração das movimentações mensais e a gravação do Ativo Imobilizado devem ocorrer como uma única operação atômica: uma falha parcial não deve deixar o bem salvo sem suas parcelas correspondentes.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Regeneração destrutiva do cronograma de depreciação a cada salvamento** (ver regra de negócio 1): pode apagar histórico relevante para auditoria fiscal se períodos já apurados forem regenerados. Recomenda-se ao time avaliar se deveria haver bloqueio de edição após determinada competência já fechada/exportada no SPED.
2. **Exclusão de Plano de Conta sem validação de uso**: não foi encontrada validação impedindo excluir uma conta do plano de contas que já esteja referenciada por lançamentos financeiros ou por um Ativo Imobilizado — risco de referência quebrada. O sistema novo deve impedir ou avisar sobre exclusão de conta em uso.
3. **Balancete não tem cadastro/registro próprio** — é inteiramente derivado por consulta; qualquer mudança na estrutura do Plano de Contas ou dos lançamentos financeiros pode alterar silenciosamente o resultado do balancete sem nenhuma trilha de auditoria específica deste relatório.
4. **O valor da parcela do Ativo Imobilizado não é calculado automaticamente** a partir de um valor total do bem — depende de o usuário calcular e digitar corretamente o valor mensal; não há campo de "valor total do bem" nem validação cruzada entre valor da parcela × quantidade de parcelas e o valor de aquisição. Requer validação com o time se o sistema novo deve adicionar esse cálculo automático.
5. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado — o sistema novo não deve reproduzir esse padrão.

## 9. Critérios de aceite / Definition of Done

- [ ] Cadastro de Plano de Contas (criação, edição, exclusão, hierarquia pai/filho, cálculo de nível/caminho) preservado.
- [ ] Cadastro de Centro de Custo preservado.
- [ ] As consultas de Balancete (por período, resultado consolidado, por natureza, e o detalhamento por natureza/conta/tipo de despesa) continuam retornando os mesmos agrupamentos/valores para os mesmos filtros.
- [ ] Cadastro de Ativo Imobilizado preservado, incluindo a regeneração automática das movimentações mensais ao salvar, com as mesmas datas (dia 1 do mês, incrementando mensalmente) e valores.
- [ ] Exclusão de um Ativo Imobilizado continua removendo todas as suas movimentações associadas.
- [ ] Dados de Ativo Imobilizado e suas movimentações continuam disponíveis e no mesmo formato para o gerador do Bloco G do SPED ICMS/IPI.
- [ ] Cadastro de Contador continua criando/editando um registro de Cliente/Fornecedor do tipo Contador.
- [ ] Decisão registrada sobre a regeneração destrutiva do cronograma de depreciação (risco 1) e sobre a ausência de validação de uso ao excluir Plano de Conta (risco 2).
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de plano de contas, ativo imobilizado e cadastro de contador.
