# PRD — Cadastro Comercial

> **Nota de contexto**: este PRD descreve **regras de negócio e requisitos funcionais/não-funcionais**, levantados por engenharia reversa do sistema legado, para orientar a construção de um sistema novo. Ele **não contém nenhuma referência de tecnologia** (linguagem, framework, banco de dados, nome de arquivo, classe, método, tabela ou coluna) — a implementação técnica é responsabilidade de skills dedicadas, fora deste documento. Onde o texto diz "o sistema deve..." ou "é esperado que...", está descrevendo comportamento a construir do zero, não a "preservar" um código existente.

> **Nota de multitenancy**: no sistema novo, todo dado e funcionalidade deste domínio opera dentro da fundação multitenant definida em `PRD-14-login-multitenant.md` — cada registro pertence a um tenant, e esse escopo é automático, não opcional, aplicado a toda entidade deste PRD. Qualquer regra de unicidade, sequência, contador ou código fixo descrito neste PRD vale **dentro de um tenant**, não globalmente entre tenants, salvo onde este PRD disser o contrário explicitamente. Dados de negócio (cadastros, lançamentos, saldos) são compartilhados entre todas as Empresas (matriz e filiais) de um mesmo tenant; apenas os dados cadastrais/fiscais próprios de cada Empresa (CNPJ, endereço, inscrições) são segregados por Empresa dentro do tenant.

## 1. Contexto e objetivo

O sisconf é um ERP para indústria de confecção/vestuário. O domínio **Cadastro Comercial** é o maior em volume de funcionalidade e reúne os dois cadastros-mestre mais reutilizados do sistema:

1. **Cliente/Fornecedor** — um **único conceito de dado que representa todo parceiro de negócio externo**: cliente, fornecedor, transportadora, contador, prestador de serviço/facção — diferenciados apenas por um papel específico. Esse padrão já foi observado e documentado nos PRDs de Expedição/Logística (Transportadora), Contábil/Patrimonial (Contador) e Produção/PCP (facção).
2. **Produto** — cadastro-mestre de tudo que circula fisicamente no sistema: matéria-prima, produto acabado, kit, embalagem — com características (cor, tamanho, tecido), regras fiscais padrão, e ficha técnica (Modelo).

**Correção feita nesta investigação**: a ficha técnica/BOM de consumo de matéria-prima (Modelo), inicialmente listada em Administração do Sistema, pertence a este domínio — está registrada no mesmo menu de cadastro que Produto/Estampa.

Objetivo de negócio: manter um cadastro único e confiável de todo parceiro de negócio e de todo produto/material, servindo de base para Vendas, Compras, Produção, Estoque, Financeiro e Fiscal.

## 2. Escopo

### Incluído neste PRD
- **Cliente / Fornecedor / Transportadora / Contador / Prestador de Serviço**: cadastro unificado de parceiros, com condições comerciais (tabela de preço, condição de pagamento, desconto, comissão) e classificação contábil.
- **Produto**: cadastro-mestre de produto/matéria-prima, com características físicas, fiscais e comerciais.
- **Modelo / Ficha Técnica**: consumo de matéria-prima por produto (rendimento de tecido, configuração de estampa).
- **Tabela de Preço**: tabelas de preço com índices de comissão e desconto, referenciadas tanto por Produto quanto por Cliente/Fornecedor.
- **Cadastros auxiliares de característica de produto**: Cor, Tamanho, Estampa, Tecido/Aviamento, Grupo de Produto, Unidade, Origem do Produto.

### Fora de escopo (pertence a outro domínio)
- Uso do cadastro com o papel de Transportadora — domínio **Expedição/Logística** (já documentado).
- Uso do cadastro com o papel de Contador — domínio **Contábil/Patrimonial** (já documentado).
- Uso do cadastro com o papel de prestador de serviço/facção — domínio **Produção/PCP** (já documentado, aqui apenas confirmamos a origem do cadastro).
- Saldo de estoque do produto em si — domínio **Estoque**; este PRD documenta apenas que o campo de quantidade existe no cadastro-mestre, não a lógica de movimentação.
- Tributação padrão do produto usada por Compra/Venda — mecanismo documentado nos PRDs de Compras/Vendas/Fiscal; aqui documentamos apenas os campos de tributação **padrão** presentes no próprio cadastro de Produto, que servem de origem para essa propagação.

## 3. Conceitos de dados

### Cliente/Fornecedor (cadastro unificado de parceiro)
Campos: nome (obrigatório), papel do parceiro (Cliente, Fornecedor, Transportadora, Contador, Prestador de Serviço), endereço completo (logradouro, número, complemento, bairro, CEP, município, UF), telefones, e-mail, contato e seus dados de contato, tipo e número de documento (CPF ou CNPJ), inscrição estadual, CPF, registro profissional de contador, código de participante, inscrição no SUFRAMA, condições comerciais (condição de pagamento, tabela de preço, percentual de desconto sobre condição de pagamento, percentual de desconto sobre valor), forma de pagamento, custo de serviço (para prestador de serviço), classificação contábil, a lista de serviços que um prestador/facção oferece (vinculados a um tipo de setor de produção), contador de pedidos, status do cliente e motivo de inatividade quando aplicável, nome fantasia, nome e telefone do contato financeiro, vínculo com uma rede comercial (domínio Cadastro & Segurança), indicador de ativo, status geral, data de cadastro. Existe um conjunto definido de status que desabilitam o parceiro para operações (inclui pelo menos o status "Inativo") — usado presumivelmente para validações de bloqueio (ver risco 4 na seção 8 sobre o detalhamento exato).

### Produto
Campos: referência, descrição, código de barras, tipo de produto (matéria-prima, produto acabado, etc.), classificação fiscal (NCM, CEST), quantidade mínima, quantidade máxima e quantidade em estoque (esta última é o saldo corrente, atualizado pelo domínio Estoque), unidade de medida e fatores de conversão (incluindo conversão para transferência), características (tecido/aviamento, grupo de produto, tamanho, cor, tipo de cor), valores (custo, preço-base, dois valores extras adicionais sem propósito de negócio claramente documentado — ver risco 4 na seção 8, valor de crédito), peso, origem do produto, produto-kit (permite modelar um produto como composição de outros produtos), percentual de grupo, quantidade de grade, ficha técnica associada (Modelo), tipo e configuração de embalagem, matéria-prima de origem (vincula um produto processado à matéria-prima da qual ele deriva), fornecedores homologados (com código do fornecedor), variantes de cor, preço por tabela, imagens do produto, gênero, indicador de produto de uso interno (distingue produto de uso interno, ex. insumo de produção, de produto comercializável), campos de tributação padrão (CST do ICMS de saída e de entrada, modalidade de ICMS, percentual de redução de ICMS, CST do Simples Nacional, indicadores de crédito/débito de ICMS — usados para propagar tributação a itens de Compra/Venda, mecanismo documentado nos respectivos PRDs), status, indicador de cadastro completo, indicador de ativo, data de cadastro.

### Tabela de Preço
Campos: código (curto), descrição, valor mínimo, índice de comissão, índice de comissão para pagamento parcelado, índice de reajuste da tabela sobre o preço-base, índice de desconto.

### Modelo / Item / Rendimento / Item-Quantidade — ficha técnica
Campos do Modelo: descrição, indicador de ativo, os itens do modelo, os rendimentos associados. As telas relacionadas incluem configuração de estampa por cor, consumo de matéria-prima e rendimento de tecido — confirma ser a ficha técnica de quanto de cada matéria-prima (tecido, aviamento) é necessário para produzir uma unidade do produto, por combinação de estampa/cor. **Estrutura detalhada dos itens e rendimentos não foi confirmada em profundidade nesta investigação.**

## 4. Fluxos funcionais

### Fluxo — Cadastrar Cliente / Fornecedor / Facção
O cadastro expõe operações de salvamento **distintas por papel**, todas operando sobre o mesmo conceito de dado:
- Salvar como cliente — recebe também uma lista de usuários (provavelmente um vínculo usuário-cliente, relevante para representantes/carteira de clientes).
- Salvar como fornecedor.
- Salvar como facção — grava também a lista de serviços oferecidos, vinculando o prestador aos tipos de setor de produção que atende, usado depois pelo domínio Produção/PCP para localizar prestadores aptos a um serviço.
- Validação de documento único por papel (ver regra de negócio 2).
- Consultas especializadas por papel: busca por CNPJ, busca por município, busca por tipo de serviço, busca de serviços por prestador.

### Fluxo — Cadastrar Produto
Cadastro, edição, exclusão e consulta associando características (cor, tamanho, tecido, grupo), regras fiscais padrão, fornecedores homologados, preços por tabela e, opcionalmente, a ficha técnica (Modelo). O comportamento detalhado não foi confirmado em profundidade nesta investigação.

### Fluxo — Ficha Técnica (Modelo)
Usuário define, para um Modelo, o consumo de matéria-prima por combinação de estampa/cor/tecido e o rendimento esperado — usado presumivelmente para calcular necessidade de compra de matéria-prima a partir da demanda de produção (**relação exata com o domínio de Produção/PCP e com Compras não confirmada nesta investigação — requer investigação adicional se for objeto de trabalho**).

## 5. Regras de negócio

1. **Um único cadastro (Cliente/Fornecedor) serve a cinco papéis diferentes** (Cliente, Fornecedor, Transportadora, Contador, Prestador de Serviço/Facção), diferenciados por um indicador de papel — qualquer alteração estrutural nesse cadastro tem impacto em pelo menos cinco fluxos de negócio distintos, em domínios diferentes.
2. **Documento (CPF/CNPJ) deve ser único por papel** — não fica claro, nesta investigação, se a checagem é por papel isolado ou global (ex. o mesmo CNPJ pode ser Cliente E Transportadora simultaneamente, como dois registros?) — **requer validação com o time**.
3. **Facção (prestador de serviço) é vinculada a tipos de setor de produção**, permitindo localizar quais terceirizados atendem a qual etapa de produção.
4. **Existe um conjunto de status que desabilitam o parceiro** — indica que existe (ou deveria existir) validação para impedir operações (venda, por exemplo) com clientes marcados como inativos ou em outros status desse conjunto — **a lista completa de status desabilitantes e onde é efetivamente checada não foram confirmadas em detalhe nesta investigação**.
5. **Produto-kit e matéria-prima de origem são auto-relacionamentos** que permitem modelar composição (kit) e rastreabilidade de processamento (produto derivado de uma matéria-prima) dentro do mesmo cadastro — qualquer relatório/consulta que assuma "um produto = uma linha simples" deve considerar essas duas relações recursivas.

## 6. Integrações e dependências

- **É dependência direta de todos os domínios operacionais**: Vendas, Compras, Estoque, Produção/PCP, Expedição/Logística, Financeiro (via Cliente/Fornecedor), Fiscal (tributação padrão do produto), Contábil/Patrimonial (Contador).
- **Depende de Cadastro & Segurança**: rede comercial, usuário (vínculo usuário-cliente).
- **Depende de Contábil/Patrimonial**: Plano de Contas (classificação de cada parceiro).
- **Depende de Financeiro**: forma de recebimento, tabela de preço como condição comercial padrão do parceiro.
- **Alimenta Produção/PCP**: o vínculo entre prestador de serviço e tipo de setor de produção é usado para localização de terceirizados de facção.

## 7. Requisitos não-funcionais relevantes

- Sendo o cadastro mais reutilizado do sistema, qualquer alteração de estrutura no cadastro de Cliente/Fornecedor ou de Produto tem raio de impacto muito amplo — mudanças devem ser cuidadosamente avaliadas contra todos os domínios consumidores antes de implementação.
- Volume de dados: Produto e Cliente/Fornecedor tendem a ser os cadastros mais consultados do sistema — qualquer otimização de busca/consulta aqui tem efeito amplificado.

## 8. Riscos e comportamentos conhecidos a decidir

1. **Modelagem "papel único via indicador" no cadastro de Cliente/Fornecedor**: embora funcional, essa modelagem impede que a mesma pessoa jurídica tenha simultaneamente dois papéis (ex. um fornecedor que também é transportadora) sem duplicar o cadastro — **requer validação com o time** se essa é uma limitação conhecida/aceita ou um problema real já reportado por usuários, e se o sistema novo deve resolvê-la.
2. **Regra de unicidade de documento por papel não totalmente esclarecida** (já citada na regra de negócio 2) — potencial de cadastro duplicado de fato (mesmo CNPJ, registros diferentes) se a checagem for por papel isolado.
3. **Os itens e rendimentos da ficha técnica (Modelo) não foram confirmados em detalhe nesta investigação** — qualquer especificação de execução específica para ficha técnica/BOM deve começar por investigação dedicada.
4. **Dois campos de valor adicional do Produto sem nome de negócio claro** — nomenclatura genérica sugere campos adicionados sob pressão de prazo sem definição clara de propósito; **requer validação com o time** sobre o significado atual de uso antes de qualquer decisão sobre reaproveitá-los ou não no sistema novo.

## 9. Critérios de aceite / Definition of Done

- [ ] Cadastro de Cliente, Fornecedor, Transportadora, Contador e Prestador de Serviço/Facção continuam funcionando através das respectivas operações especializadas por papel.
- [ ] Validação de documento único preservada com o comportamento atual (mesmo que ambíguo — qualquer mudança de comportamento deve ser uma decisão explícita, não um efeito colateral).
- [ ] Cadastro de Produto (características, fornecedores homologados, preços por tabela, tributação padrão) preservado.
- [ ] Ficha técnica (Modelo/consumo de matéria-prima/rendimento) preservada.
- [ ] Vínculo de prestador de serviço/facção a tipos de setor de produção preservado, com impacto verificado no domínio Produção/PCP.
- [ ] Esclarecida com o time a regra real de unicidade de documento (risco 2) antes de qualquer decisão de validação no sistema novo.
- [ ] Esclarecido o propósito atual dos dois campos de valor adicional do Produto (risco 4) antes de decidir reaproveitá-los.
- [ ] Nenhuma lacuna funcional em relação a este PRD nas áreas de cliente/fornecedor, produto, modelo/ficha técnica, tabela de preço e cadastros auxiliares de característica de produto.
