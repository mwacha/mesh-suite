# PRD — Fiscal / Tributário

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant. Numeração de documentos fiscais (NF-e, NFC-e) segue a série/numeração própria de cada Empresa dentro do tenant, não do tenant como um todo — a fundação multitenant deve preservar esse segundo nível de escopo especificamente para numeração fiscal.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio **Fiscal/Tributário** é responsável por três frentes:

1. **Configuração da regra tributária** (cadastro fiscal, CST/CFOP/alíquotas): a "matriz de tributação" que outros domínios (Vendas, Compras) consultam para popular automaticamente ICMS/IPI/PIS/COFINS/IBS/CBS de cada item de nota.
2. **Emissão eletrônica de documentos fiscais**: Nota Fiscal Eletrônica (NF-e), Nota Fiscal de Consumidor Eletrônica (NFC-e) — comunicação real com a autoridade fiscal (assinatura digital, envio, consulta, cancelamento, carta de correção, inutilização de numeração).
3. **Escrituração fiscal**: geração de arquivo SPED Fiscal (EFD ICMS/IPI), apuração de ICMS, Sintegra.

Este é o único domínio do sistema com **integração eletrônica real com um órgão externo (a autoridade fiscal estadual)** — os demais domínios fiscais-adjacentes (Compra, CT-e) apenas importam ou registram documentos, sem emiti-los digitalmente.

## 2. Escopo

### Incluído neste PRD
- **Cadastro Fiscal**: regra de tributação por natureza de operação (CFOP, CST/CSON de ICMS, modalidade e alíquota de ICMS, indicadores de crédito de ICMS, ICMS-ST, estoque próprio/terceiro, campos de IBS/CBS da reforma tributária).
- **Emissão de NF-e**: montagem do documento, assinatura digital, envio à autoridade fiscal, consulta de status/recibo, cancelamento, carta de correção, inutilização de numeração, impressão do documento auxiliar (DANFE).
- **Emissão de NFC-e**: variante para consumidor final.
- **SPED Fiscal (EFD ICMS/IPI)**: geração dos blocos do arquivo (incluindo o Bloco G/CIAP já documentado no PRD Contábil/Patrimonial).
- **Apuração SPED de ICMS**.
- **Sintegra**.
- **Cadastros auxiliares de tributação**: CST de ICMS/IPI/PIS/COFINS, CFOP, alíquota de ICMS, natureza de operação, tabela IBPT.

### Fora de escopo (pertence a outro domínio)
- Venda/Compra em si — domínios **Vendas**/**Compras**; este PRD documenta como a nota fiscal eletrônica é gerada **a partir** de uma venda, não a venda em si.
- Conhecimento de Transporte (CT-e) — domínio **Expedição/Logística**; apenas referencia CST do ICMS, município e situação do documento fiscal deste domínio.
- Ativo Imobilizado e suas movimentações (fonte do Bloco G) — domínio **Contábil/Patrimonial**.
- Importação de arquivo de nota fiscal eletrônica de terceiros para registrar uma Compra — capacidade compartilhada, já referenciada no PRD de Compras; aqui é apenas mencionada como usuária do mesmo padrão de leitura de documento fiscal eletrônico.

## 3. Conceitos de dados

### Cadastro Fiscal
Campos: descrição, natureza da operação (obrigatória), origem da operação (dentro ou fora do estado, padrão dentro do estado), indicadores de crédito de ICMS/cálculo de ICMS-ST/estoque de terceiro/estoque próprio, CFOP, CST do ICMS, código de situação da operação no Simples Nacional (CSON — reaproveita a mesma estrutura de dados do CST do ICMS, ver risco 1 na seção 8), alíquota de ICMS (calculada em tempo de uso, não persistida) e alíquota interna de ICMS (persistida), modalidade de cálculo de ICMS, percentual de redução de base de ICMS, além de um conjunto extenso de campos de IPI/PIS/COFINS/IBS-CBS.

Esta estrutura de tributação é a mesma reaproveitada na tributação padrão do produto e na tributação padrão de itens de Compra e de Venda, para propagar a tributação a cada item de nota.

### Emissão de NF-e — sem registro de persistência próprio centralizado
A emissão de NF-e não tem um registro de dados próprio para "a NF-e"; ela opera sobre a Venda (já um documento fiscal completo, documentado no PRD de Compras) e produz/consome o documento eletrônico correspondente. O documento final assinado/aprovado é persistido ao longo do fluxo de emissão — o local exato de armazenamento (banco de dados ou sistema de arquivos) não foi confirmado nesta investigação (ver risco 3 na seção 8).

### Situação da NF-e
Códigos de status de retorno da autoridade fiscal (autorizada, rejeitada, cancelada, etc.) — não enumerados individualmente nesta investigação.

### Apuração SPED
Registro de apuração de ICMS por período/regra — campos não detalhados nesta investigação.

## 4. Fluxos funcionais

### Fluxo principal — Emitir NF-e a partir de uma Venda
1. O processo de emissão é iniciado a partir da empresa emitente e da venda a ser faturada — carrega o certificado digital e monta o contexto necessário (endereços de serviço por UF, especificação do documento eletrônico).
2. O sistema monta o documento da NF-e a partir dos dados da venda, calculando os impostos por item conforme o regime tributário da empresa e a tributação de cada item da venda (ICMS normal, ICMS do Simples Nacional, ICMS-UF de destino, IPI, PIS, COFINS) — o processo completo de montagem do envelope de envio é extenso e **não foi lido linha a linha nesta investigação**; qualquer alteração no cálculo de impostos da NF-e deve começar por investigação dedicada desse processo, dado o risco fiscal.
3. O documento é assinado digitalmente (o mecanismo exato de assinatura não foi localizado nesta investigação — presumivelmente usa um certificado digital A1/A3).
4. O documento assinado é enviado à autoridade fiscal.
5. A resposta é processada; se aprovada, o documento aprovado é salvo; se houver erro, é tratado e reportado ao usuário.
6. O documento auxiliar (DANFE, representação em PDF) é gerado e pode ser salvo/impresso a partir do documento aprovado.

### Fluxo — Cancelamento e Carta de Correção
Cancelamento e carta de correção seguem o mesmo padrão: montar o documento do evento correspondente, enviar à autoridade fiscal, salvar o resultado.

### Fluxo — Inutilização de numeração
Usado quando uma faixa de numeração de NF-e não foi utilizada (ex. falha antes da emissão) e precisa ser formalmente inutilizada junto à autoridade fiscal, obrigatório para não deixar "buraco" de numeração sem justificativa.

### Fluxo — Consulta de status/recibo
O sistema consulta se o serviço da autoridade fiscal está operante; consulta o resultado de um envio assíncrono (processamento em lote); e consulta uma nota específica pela chave de acesso.

### Fluxo — NFC-e (Nota Fiscal de Consumidor)
Segue o mesmo padrão da NF-e, mas com regras próprias do modelo de venda direta ao consumidor final, sem necessidade de identificação do destinatário.

### Fluxo — SPED Fiscal e Apuração
O sistema gera o arquivo texto posicional da EFD ICMS/IPI a partir de Compra, Venda, Conhecimento de Transporte e Ativo Imobilizado/suas movimentações (Bloco G) do período. A Apuração SPED permite cadastro, exclusão e consulta de registros de apuração de ICMS por período/regra.

## 5. Regras de negócio

1. **A tributação do item é derivada do Cadastro Fiscal** via natureza da operação, não digitada item a item manualmente na maioria dos casos — mecanismo de propagação já documentado no PRD de Compras e presumivelmente espelhado em Vendas.
2. **A origem da operação (dentro/fora do estado) afeta a montagem do CFOP/tributação** — padrão é operação dentro do estado.
3. **Inutilização de numeração é um evento fiscal formal**, obrigatório perante a autoridade fiscal sempre que uma faixa de numeração não for usada — não é uma simples exclusão local.
4. **O CSON reaproveita a mesma estrutura de dados do CST do ICMS** para representar o CSON do Simples Nacional — ver risco 1 na seção 8 sobre a adequação desse modelo.

## 6. Integrações e dependências

- **Integração eletrônica real com a autoridade fiscal**: comunicação por serviço específico por UF, validação contra a especificação oficial do documento, uso de certificado digital (A1/A3). É a única integração externa síncrona de todo o sistema além do envio de e-mail.
- **Depende de Cadastro Comercial**: produto, tributação padrão do produto.
- **Depende de Vendas**: venda e seus itens como fonte de dados da NF-e/NFC-e.
- **Depende de Cadastro & Segurança**: empresa (dados do emitente, certificado digital).
- **Alimenta/é alimentado por Contábil/Patrimonial**: o Bloco G do SPED consome os dados de Ativo Imobilizado e suas movimentações.
- **Relaciona-se com Expedição/Logística**: o Conhecimento de Transporte referencia CST do ICMS, situação do documento fiscal e município deste domínio.
- **Relaciona-se com Compras**: a importação de documento fiscal eletrônico de fornecedor usa capacidades deste domínio para interpretar tributação.

## 7. Requisitos não-funcionais relevantes

- **Criticidade regulatória alta**: erros neste domínio têm consequência fiscal/legal direta (multas, autuação, impossibilidade de faturar). Qualquer alteração exige testes cuidadosos, idealmente contra o ambiente de homologação da autoridade fiscal antes de produção.
- Comunicação com a autoridade fiscal deve tratar indisponibilidade do serviço (contingência) — a existência de campos de data e justificativa de contingência na estrutura de nota fiscal (já vista no PRD de Compras) confirma que o sistema legado tem noção desse cenário, mas o fluxo de contingência em si não foi investigado em detalhe nesta investigação.
- Mudanças de leiaute fiscal (ex. reforma tributária IBS/CBS) são recorrentes e externas ao controle do time — o sistema legado já demonstra estar em processo de adaptação a essas mudanças (campos de IBS/CBS espalhados por Cadastro Fiscal, Compra, Conhecimento de Transporte, etc.); o sistema novo deve ser desenhado para acomodar mudanças de leiaute fiscal com o menor atrito possível.

## 8. Riscos e comportamentos conhecidos a decidir

1. **O CSON reaproveita a mesma estrutura de dados do CST do ICMS**, mas o CSON (Código de Situação da Operação no Simples Nacional) tem uma tabela de valores conceitualmente distinta do CST normal — risco de listar/validar valores de CST incompatíveis com CSON na tela, ou de confusão de manutenção futura. **Requer validação com o time** se isso é uma simplificação intencional (dado que ambos são códigos curtos) ou uma modelagem a corrigir no sistema novo.
2. **O processo completo de montagem e envio da NF-e não foi lido linha a linha nesta investigação** — este PRD documenta o fluxo geral e a interface de alto nível, mas qualquer alteração no cálculo de impostos ou no documento gerado exige investigação direta e cuidadosa antes de qualquer mudança, dado o risco fiscal.
3. **Local de persistência do documento da NF-e não confirmado** (banco de dados vs. sistema de arquivos) — relevante para entender requisitos de backup/retenção (a legislação brasileira exige guarda do documento fiscal por 5 anos). O sistema novo deve definir explicitamente essa estratégia.
4. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado — neste domínio, adicionalmente, **verificar se o certificado digital e sua senha também estão armazenados de forma segura e não expostos** (não confirmado nesta investigação, mas é um risco típico desse tipo de integração — **requer verificação dedicada por ser um risco potencialmente crítico**); o sistema novo não deve reproduzir o padrão de credenciais em texto puro.

## 9. Critérios de aceite / Definition of Done

- [ ] Emissão de NF-e (montagem, assinatura, envio, aprovação) continua funcionando de ponta a ponta para os cenários de tributação já suportados (ICMS normal, Simples Nacional, ICMS-ST, IBS/CBS).
- [ ] Cancelamento, carta de correção e inutilização de numeração continuam funcionando e gerando o documento correto do evento.
- [ ] Emissão de NFC-e preservada.
- [ ] Geração do documento auxiliar (DANFE, para NF-e e NFC-e) preservada.
- [ ] Geração do arquivo SPED Fiscal (todos os blocos, incluindo Bloco G) e Sintegra preservada, validada contra o validador oficial da Receita/autoridade fiscal antes de considerar concluído.
- [ ] Cadastro Fiscal (regra de tributação por natureza de operação) continua propagando corretamente para itens de Compra/Venda.
- [ ] Esclarecido com o time se a modelagem do CSON reaproveitando a estrutura do CST do ICMS é adequada para o sistema novo (risco 1) antes de qualquer decisão de modelagem.
- [ ] Confirmado onde e como o certificado digital deve ser armazenado no sistema novo, garantindo que nunca fique exposto em texto puro (risco 4).
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de NF-e, NFC-e, SPED Fiscal, Sintegra, apuração SPED e cadastros auxiliares de tributação.
