# PRD — Cobrança Bancária

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio de **Cobrança Bancária** cobre o registro de títulos de contas a receber junto ao banco para cobrança: geração de **arquivo de remessa no padrão CNAB** (layout Bradesco de 400 posições, ou layout Banco do Brasil) e geração de **boletos em PDF**.

Objetivo de negócio: permitir que a empresa registre no banco (via arquivo-remessa) os títulos que o cliente deve pagar, para que o banco cobre e informe a liquidação, e/ou emitir boletos para envio direto ao cliente.

## 2. Escopo

### Incluído neste PRD
- **Configuração/registro de Remessa de Títulos**: selecionar títulos de contas a receber de uma conta bancária, agrupá-los em uma remessa e gerar o arquivo texto no layout do banco.
- **Geração de arquivo de remessa nos padrões CNAB 400 (Bradesco) e Banco do Brasil**.
- **Cálculo de "Nosso Número" e dígito verificador**, específico por banco (regras na seção 5).
- **Geração de Boleto em PDF**, a partir de uma lista de títulos.
- **Controle de sequência de remessa por conta bancária**, garantindo numeração sequencial própria por conta.

### Fora de escopo (pertence a outro domínio, ou não faz parte do sistema)
- O título em si (lançamento de contas a pagar/receber, juros, baixa) — domínio **Financeiro**. Este PRD apenas seleciona e agrupa títulos já existentes; não cria nem baixa títulos.
- Cadastro da conta bancária da empresa (saldo, percentual de mora/multa, instruções de cobrança) — domínio **Financeiro**. Consumido aqui apenas como referência (layout do banco, número de carteira, código da empresa junto ao banco, percentuais de mora/multa, instruções de cobrança).
- Cliente/Fornecedor (sacado/pagador do título) — domínio **Cadastro Comercial**.
- Desconto de duplicata junto ao banco — tela e fluxo próprios, que apenas compartilham a mesma busca de título deste domínio; pertence ao domínio **Financeiro**.

## 3. Conceitos de dados

### Remessa de Títulos
Campos: data da remessa (padrão é a data de criação), conta bancária usada, empresa (associada apenas no momento de gerar o arquivo, não fica gravada permanentemente com a remessa), valor total da remessa (soma dos títulos incluídos), quantidade de títulos incluídos, número sequencial da remessa (por conta bancária), e a lista dos títulos incluídos nesta remessa.

### Sequência de Remessa
Existe **um registro por conta bancária**, guardando o último número de remessa usado para aquela conta.

### Layout de Cobrança
Cadastro de referência simples que identifica o banco (código do banco e nome), usado para decidir qual gerador de remessa e qual cálculo de "Nosso Número" aplicar. Hoje há dois valores fixos: Banco do Brasil e Bradesco.

### Relacionamentos
Uma Remessa de Títulos pertence a uma conta bancária e contém um ou mais títulos de contas a receber (domínio Financeiro). A Sequência de Remessa se relaciona 1-para-1 com a conta bancária.

## 4. Fluxos funcionais

### Fluxo principal — Gerar remessa de títulos para o banco
1. Usuário acessa a área de Remessa de Título e cria uma nova remessa, selecionando a conta bancária.
2. A busca de títulos só pode ser aberta depois que a conta bancária estiver selecionada.
3. Usuário pesquisa títulos de contas a receber em aberto e marca os títulos desejados, que são somados ao total provisório (quantidade e valor).
4. Usuário move os títulos marcados da lista de pesquisa para a lista definitiva da remessa, somando ao total da remessa; é possível remover um título já adicionado, o que subtrai do total.
5. Ao gerar o arquivo:
   - O sistema associa a empresa (dados do usuário autenticado) à remessa, apenas para uso na geração do arquivo.
   - Se a remessa ainda não tiver um número sequencial definido, o sistema busca (ou cria, se for a primeira remessa daquela conta) o contador de sequência da conta bancária e o incrementa em 1.
   - Conforme o layout configurado na conta bancária, o sistema monta o texto do arquivo (cabeçalho + um registro por título + rodapé) no padrão do banco correspondente.
   - O arquivo texto resultante é oferecido para download ao usuário.
6. Ao salvar, a remessa é persistida e, para cada título incluído, o título é vinculado a esta remessa (domínio Financeiro).
7. Ao excluir uma remessa, cada título vinculado é primeiro desvinculado, antes do registro de remessa ser removido.

### Fluxo — Emitir boleto
A partir da empresa (cedente), do cliente (sacado) e de uma lista de títulos, o sistema monta um boleto por título:
- Nosso número e dígito verificador são calculados conforme o banco da conta bancária usada (regras na seção 5).
- Se a conta bancária tiver percentual de mora **e** percentual de multa configurados (ambos maiores que zero), o boleto recebe instruções de mora/multa com o valor calculado.
- Antes de gerar, o sistema valida que o nome do cedente (empresa) e o código da empresa junto ao banco estão preenchidos.
- Os boletos de uma mesma emissão são agrupados em um único PDF, usando um modelo visual de boleto configurado no sistema.

### Exceções
Falha na geração do texto de remessa ou do boleto deve reverter a operação e informar o usuário de forma específica sobre o problema. Erros de persistência seguem o padrão do sistema (reversão completa da operação e mensagem ao usuário).

## 5. Regras de negócio

1. **Sequência de remessa por conta bancária**: cada conta bancária tem seu próprio contador incremental, começando em 1 se não existir registro prévio para aquela conta. O número gerado é gravado tanto no arquivo de remessa (campo de sequência) quanto no registro da remessa.
2. **Layout do arquivo depende exclusivamente do layout configurado na conta bancária**: hoje só há dois layouts implementados (Bradesco 400 e Banco do Brasil). Se o layout não estiver configurado ou for de outro banco, a geração do arquivo não produz nenhum conteúdo — **não há validação explícita que impeça essa tentativa antes de o usuário chegar ao erro** (ver risco 2 na seção 8).
3. **Cálculo do "Nosso Número" — Bradesco**: concatena o número da carteira (2 dígitos) com o número do título completado com zeros à esquerda até 11 dígitos, e aplica módulo 11 com pesos de 2 a 7. Se o resto da divisão for 0, o dígito verificador é "0"; se o resultado de "11 − resto" for 10, o dígito verificador é "P" (regra específica do manual do banco); nos demais casos, o dígito verificador é "11 − resto".
4. **Cálculo do "Nosso Número" — Banco do Brasil**: segue algoritmo próprio desse banco, distinto do algoritmo Bradesco acima (regra assumida a partir da separação clara entre os dois geradores no sistema legado; o detalhe exato do algoritmo deve ser confirmado com a especificação oficial do Banco do Brasil ao reimplementar).
5. **Número do título usado como base do Nosso Número**: é a concatenação (não soma) do número do documento do título com o número da parcela — usado tanto no boleto quanto na remessa. Isso significa que o "número do documento" do título de contas a receber deve ser compatível em tamanho com o formato exigido pelo banco (11 dígitos numéricos após a concatenação); não foi identificada validação de formato nesse ponto (ver risco 3 na seção 8).
6. **Instruções de mora/multa no boleto**: só são exibidas se o percentual de mora **e** o percentual de multa da conta bancária estiverem configurados simultaneamente (maiores que zero) — se apenas um dos dois estiver configurado, nenhuma instrução de mora/multa aparece no boleto.
7. **Remover título da remessa recalcula os totais**: subtrai o valor do título removido do valor total da remessa e decrementa a quantidade de títulos.

## 6. Integrações e dependências

- **Depende de Financeiro**: título de contas a receber (leitura para seleção; escrita apenas do vínculo com a remessa), conta bancária (dados bancários, layout, carteira, percentuais de mora/multa).
- **Depende de Cadastro & Segurança**: dados da empresa (nome, dados do cedente), obtidos a partir da sessão do usuário autenticado.
- **Depende de Cadastro Comercial**: cliente/fornecedor (sacado/pagador do título, endereço, CPF/CNPJ) — usado tanto no arquivo de remessa (campos de endereço do pagador) quanto no boleto.
- **Depende de capacidade de geração de arquivo texto de largura fixa e de cálculo de dígito verificador bancário**, e de **geração de boleto em PDF a partir de um modelo visual** — capacidades técnicas específicas deste domínio, cuja implementação cabe ao time.
- Nenhuma integração eletrônica direta com o banco (não há envio automático via API/protocolo de transferência de arquivo) — o arquivo de remessa é apenas **gerado para download manual** pelo usuário, que presumivelmente o envia ao banco por fora do sistema (internet banking, etc.) — **requer validação com o time** se existe algum processo de envio automatizado esperado no sistema novo.

## 7. Requisitos não-funcionais relevantes

- A geração do arquivo de remessa deve preservar corretamente a codificação de caracteres na resposta ao usuário, relevante para caracteres acentuados em nome/endereço do pagador.
- Falha ao gerar remessa ou boleto deve reverter a operação por completo e informar o usuário.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Uma funcionalidade de "controle de remessa" (sequência de envio/segmento) existe no modelo de dados legado, mas está desconectada do restante do sistema** — nenhuma tela ou fluxo ativo a referencia, e a investigação encontrou nela um erro de implementação que a tornaria inutilizável se fosse acionada (busca por um tipo de dado incompatível com o que a funcionalidade deveria manipular). **Tratar como funcionalidade inexistente hoje**; se o negócio precisar de um controle de sequência de envio/segmento mais granular do que a sequência de remessa por conta já coberta neste PRD, é um requisito novo a especificar para o sistema novo, não uma regra herdada.
2. **Sem validação prévia de layout suportado**: se a conta bancária usada não tiver um layout reconhecido (Bradesco ou Banco do Brasil) configurado, a geração do arquivo de remessa chega ao fim sem produzir conteúdo e a tentativa de download falha de forma abrupta, em vez de mostrar uma mensagem de erro amigável ao usuário informando que aquele banco/layout não é suportado.
3. **Concatenação (não soma) do número do documento com o número da parcela** como base numérica do Nosso Número: se o número do documento não for estritamente numérico ou exceder o tamanho esperado, o cálculo do dígito verificador falha silenciosamente ou gera um Nosso Número incorreto — não há validação de formato antes do cálculo. O sistema novo deve validar o formato do número do documento antes de calcular o Nosso Número.
4. Reafirmando o achado transversal já registrado no índice geral: credenciais de acesso em texto puro no sistema legado afetam o acesso a estes dados financeiros/bancários também — risco elevado neste domínio específico por lidar com dados bancários da empresa e de clientes; o sistema novo não deve reproduzir esse padrão.

## 9. Critérios de aceite / Definition of Done

- [ ] Fluxo de criação de remessa (selecionar conta, buscar e adicionar/remover títulos, totais recalculados corretamente) preservado.
- [ ] Geração de arquivo de remessa nos padrões CNAB 400 Bradesco e Banco do Brasil continua produzindo saída equivalente à atual para os mesmos dados de entrada (cálculo de Nosso Número, cabeçalho, detalhe, rodapé).
- [ ] Sequência de remessa por conta bancária continua incrementando corretamente e não se repete entre remessas da mesma conta.
- [ ] Geração de boleto em PDF continua funcionando com as mesmas regras de instrução de mora/multa (só exibidas quando ambos os percentuais são maiores que zero).
- [ ] O vínculo entre título e remessa continua sendo atualizado ao salvar/excluir uma remessa (vínculo/desvínculo do título).
- [ ] Validação explícita adicionada para conta bancária sem layout suportado, antes de tentar gerar o arquivo de remessa (risco 2), com mensagem de erro clara ao usuário.
- [ ] Validação de formato do número do documento adicionada antes do cálculo do Nosso Número (risco 3).
- [ ] Decisão registrada sobre se a funcionalidade de controle de remessa mais granular (risco 1) é necessária no sistema novo.
- [ ] Nenhuma lacuna funcional em relação a este PRD na área de remessa e emissão de boleto.
