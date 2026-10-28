# Dashboard Administrativo: requisitos

O responsável pelo estabelecimento (perfis **ADMIN** e **GERENTE**) acompanha as vendas, a situação dos pedidos e as tendências do Pediu Patrão numa tela própria, `/dashboard`. Essa tela lê os dados exclusivamente pela API `/api/dashboard/**`, que agrega a coleção `pedidos` do MongoDB.

> A numeração RF01–RF07 deste documento é a da atividade do dashboard. Ela **não** corresponde aos RF01–RF05 de [`../atividade-m1/`](../atividade-m1/README.md), que tratam de perfis, pedidos e auditoria.

## Requisitos funcionais

| RF | Requisito | Endpoint | Tela (`/dashboard`) | Teste que comprova |
|---|---|---|---|---|
| **RF01** | Indicadores gerais de vendas: faturamento total, ticket médio, número de pedidos e período consultado | `GET /api/dashboard/kpis` | Os quatro cartões do topo (Faturamento, Ticket médio, Pedidos, Período) | `DashboardServiceUnitTest` (`kpis_*`), `DashboardMongoIntegrationTest.kpis_whenPeriodIsOctober_*` |
| **RF02** | Análise de vendas em gráficos: vendas por período, por categoria e evolução temporal | `GET /api/dashboard/sales-trend` (`granularity=DAY\|WEEK\|MONTH`) e `GET /api/dashboard/sales-by-category` | Gráfico "Evolução das vendas" (faturamento em linha e pedidos em barras, com seletor dia/semana/mês) e gráfico "Vendas por categoria" | `salesTrend_*` e `salesByCategory_*` (unitários e de integração) |
| **RF03** | Situação dos pedidos: contagem por status | `GET /api/dashboard/orders-status` | Painel "Situação dos pedidos", com uma barra proporcional por status | `ordersByStatus_*`, `ordersStatus_*` |
| **RF04** | Produtos mais vendidos: ranking por quantidade e por receita | `GET /api/dashboard/top-products` (`sort=QUANTITY\|REVENUE`, `limit=1..50`) | Tabela "Produtos mais vendidos", com seletor de critério | `topProducts_*` |
| **RF05** | Filtros de consulta: data inicial e final, status, categoria e forma de pagamento | Query params comuns a todos os endpoints: `from`, `to`, `status`, `category`, `paymentMethod` | Barra de filtros (Aplicar e Limpar) | `DashboardControllerIntegrationTest` (binding e validação), `kpis_whenPaymentMethodIsPix_*`, `kpis_whenCategoryIsBebida_*` |
| **RF06** | Integração com a API: o front consome apenas os endpoints | Todos os acima, mais `GET /api/produtos` (já existente) para preencher a lista de categorias | Todo dado exibido chega por `fetch` e nada é calculado no navegador | Prévia do front com a API simulada ([tests.md](tests.md#front)) |
| **RF07** | Atualização e tratamento dos dados: refresh, loading, erro, vazio e formatação | — | Botão **Atualizar**; auto-refresh de 30 s, 1 min ou 5 min (pausado com a aba oculta); spinner e esmaecimento durante a carga; alerta com a mensagem do `ProblemDetail`; "Nenhuma venda no período" em cada painel vazio; R$ e datas no formato pt-BR | Prévia do front nos três estados (dados, vazio e erro) |

### Regras de negócio adotadas

- **Venda é todo pedido não cancelado.** O faturamento, o ticket médio, a série temporal, as categorias e o ranking ignoram `CANCELADO`. Se o filtro `status` vier preenchido, conta exatamente o status pedido, inclusive `CANCELADO`, o que permite medir o valor perdido com cancelamentos. A contagem por status (RF03) sempre inclui todos os status.
- **Faturamento** é a soma de `valorTotal`, com o desconto já aplicado. **Ticket médio** é faturamento ÷ pedidos, arredondado para centavos em HALF_UP, e vale 0 quando não há pedidos.
- **A receita por item** é a fatia do `valorTotal` gravado no pedido, proporcional ao valor bruto do item (`valorTotal × preço × qtd ÷ bruto do pedido`). Os itens de um pedido somam exatamente o total dele, e por isso as categorias e o ranking batem centavo a centavo com o faturamento, mesmo quando o desconto arredonda. O arredondamento para centavos só acontece depois da soma.
- **Período:** `from` e `to` são datas (`yyyy-MM-dd`) e ambos os dias entram na consulta. Sem `from`/`to`, o período são os últimos 30 dias até hoje. Se só um deles for informado, o outro é completado: `to` vira hoje e `from` vira `to − 29` dias. O período tem no máximo 366 dias, `from` não pode ser posterior a `to` e os anos ficam entre 2000 e 2100 (todos esses casos geram 422).
- **Categoria** é o `tipo` do produto (Pizza, Esfiha, Bebida, Lanche), comparado sem diferenciar maiúsculas. Uma categoria em branco vale como "todas". Nos indicadores por pedido (KPIs, série e status) entram os pedidos com ao menos um item da categoria, com o valor total do pedido. Nos indicadores por item (categorias e ranking) entram só os itens dela. Por isso, com `category=Bebida`, o cartão de faturamento pode ser maior que a barra de Bebida. Um produto removido do cadastro aparece como "Sem categoria" no gráfico, mas não pode ser filtrado por esse nome.
- **Forma de pagamento:** o pedido ganhou o campo opcional `formaPagamento` (`DINHEIRO`, `PIX`, `CARTAO_CREDITO`, `CARTAO_DEBITO`), registrado na tela "Novo pedido". Pedidos anteriores ficam sem forma de pagamento e só aparecem quando esse filtro não é usado.
- **Série temporal contínua:** dias, semanas (que começam na segunda-feira) e meses sem venda aparecem com zero, para o gráfico não "pular" intervalos.

## Requisitos não funcionais

| RNF | Categoria | Requisito | Como foi atendido |
|---|---|---|---|
| RNF01 | Desempenho | Toda agregação é feita no banco, sem carregar pedidos em memória nem fazer uma consulta por pedido (N+1) | Pipelines `$match → $unwind → $group → $sort → $limit` em `DashboardRepository`, com uma ida ao banco por endpoint. O único dado lido em memória é o catálogo de produtos (dezenas de itens), usado para resolver a categoria |
| RNF02 | Desempenho | Toda consulta é limitada por período | O período padrão é de 30 dias e o máximo, de 366 (422 acima disso). O ranking aceita no máximo 50 itens. O primeiro estágio do pipeline filtra por `dataHoraEntrada` |
| RNF03 | Segurança | Somente ADMIN e GERENTE acessam o dashboard | Regra em `SecurityConfig` para `/api/dashboard/**`, `/dashboard` e a documentação (`/v3/api-docs`, `/swagger-ui`). Sem sessão a resposta é 401, e o ATENDENTE recebe 403. Coberto por `SecurityRulesIntegrationTest` e `DashboardControllerIntegrationTest` |
| RNF04 | Segurança | Os filtros são validados na borda e as consultas não aceitam injeção | Parâmetros tipados: `LocalDate` ISO com anos entre 2000 e 2100, enums de status, pagamento, granularidade e ordenação, e `limit` entre 1 e 50. `category` tem até 50 caracteres e nenhum caractere de controle. Ao Mongo chegam só nomes de enum, datas e ids de produto lidos do próprio banco: a categoria é comparada em memória com o cadastro e nunca entra numa consulta. Um `category={"$ne":null}` é tratado como texto e simplesmente não encontra nada (há teste para isso) |
| RNF05 | Segurança | Nenhum detalhe interno vaza nas respostas de erro | Todo erro é um `ProblemDetail` (RFC 9457) gerado pelo `RestExceptionHandler`. O erro 500 traz uma frase fixa, e a causa fica só no log |
| RNF06 | Segurança | CORS restrito ao front local | O front é servido pela própria aplicação, na mesma origem, e por isso não existe configuração de CORS: o Spring recusa por padrão requisições de outras origens. Um front separado, como um Vite em `localhost:5173`, exigiria liberar só essa origem |
| RNF07 | Segurança | Sem segredos no repositório | A URI do MongoDB e as senhas vêm de variáveis de ambiente, e `atlas-credentials.env` está no `.gitignore`. O script de `curl` lê a senha de `DASH_PASSWORD` |
| RNF08 | Usabilidade | Valores legíveis e estados claros | Valores em R$ e datas em dd/MM/aaaa (Intl pt-BR). Há estados de carregando, vazio e erro, e o layout responde do celular ao desktop (grid Bootstrap) |
| RNF09 | Usabilidade e acessibilidade | Controles rotulados e conteúdo de banco seguro | Todo campo tem `<label>`, os gráficos têm `role="img"` e `aria-label` e o horário de atualização usa `aria-live`. Nomes vindos do banco são inseridos com `textContent` (sem XSS) |
| RNF10 | Disponibilidade | A falha de um painel não derruba a tela | Um erro mostra o alerta e mantém os dados anteriores na tela. O auto-refresh tenta de novo no próximo ciclo e pula ciclos com a aba oculta |
| RNF11 | Precisão | Dinheiro nunca usa `double` | O dinheiro trafega como `BigDecimal` no Java e `Decimal128` no pipeline, com 2 casas e arredondamento HALF_UP. Valores gravados como texto ou ausentes são convertidos com `$convert`, e o que não puder ser convertido conta 0 |
| RNF12 | Portabilidade | O dashboard não depende de nada além do MongoDB | Operadores padrão do MongoDB 5.0+ (`$dateTrunc`, `$convert`), compatíveis com o Atlas. O fuso de agrupamento é o mesmo usado pelo Spring Data na gravação. Nenhuma biblioteca de front nova: o Chart.js 2.9.4 já estava em `static/plugins` |
| RNF13 | Manutenibilidade | O domínio do dashboard fica isolado | Pacote `com.umc.pediupatrao.dashboard` (controller, service, repository e DTOs `record`) e rotas sob `/api/dashboard/**`. O domínio de pedidos só ganhou o campo `formaPagamento` |
| RNF14 | Documentação | O contrato da API é gerado a partir do código | springdoc-openapi: `/v3/api-docs` e `/swagger-ui.html` (logado como ADMIN ou GERENTE), com cópia em [`openapi.json`](openapi.json) |

## Decisões de projeto

| Decisão | Por quê |
|---|---|
| Agregar no MongoDB (pipeline) e não em Java | O volume cresce com o tempo, enquanto o resultado é pequeno (poucas linhas). Assim, só o resultado trafega pela rede |
| DTOs como `record` | Imutáveis, sem boilerplate, com o contrato JSON explícito e os campos documentados com `@Schema` |
| `ProblemDetail` para erros | Padrão RFC 9457, já usado no projeto. O front lê `detail` e `errors` sem precisar de um formato próprio |
| Filtros num `record` `DashboardFilter` | Os mesmos 5 filtros valem para os 5 endpoints. O binding e a validação ficam em um só lugar |
| `formaPagamento` opcional | Não quebra o `POST /api/pedidos` de quem ainda não envia o campo, e a tela de novo pedido passou a enviá-lo |
| Categoria resolvida pelo catálogo | `ItemPedido` não guarda a categoria. Ler dezenas de produtos é barato. Se o catálogo crescer, a evolução é copiar a categoria para o item no momento do pedido (há um comentário `ponytail:` no código) |
| Sem CORS e sem nova dependência de front | A mesma origem dispensa CORS, e o Chart.js já estava no projeto. A única dependência nova é o springdoc, usado na documentação |
