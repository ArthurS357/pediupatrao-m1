# Roteiro do vídeo (até 3 minutos)

Grave com a aplicação rodando, logado como **gerente**, e com pedidos do mês já cadastrados: pelo menos uma pizza e uma bebida, um pedido com PIX, um cancelado e um com desconto. Deixe abertas três abas: `/dashboard`, `/swagger-ui.html` e o terminal com os testes.

| Tempo | Tela | Fala (resumo) |
|---|---|---|
| 0:00–0:20 | `/dashboard` com dados | **Problema:** "O dono da pizzaria não tinha visão do negócio. Os pedidos existiam, mas ninguém via faturamento, ticket médio ou o que mais vende. O dashboard responde isso em uma tela, só para ADMIN e GERENTE." |
| 0:20–0:55 | VS Code: `DashboardRepository` (pipeline) e `DashboardApi` | **Decisões:** "1) A agregação acontece no MongoDB, com `$match`, `$group` e `$sort`. Só o resultado trafega, sem N+1, e por isso a consulta continua rápida mesmo com muitos pedidos. 2) As respostas são `record`s imutáveis, com o dinheiro em `BigDecimal`, nunca `double`. 3) Os erros saem como `ProblemDetail`, o padrão RFC 9457: o front lê `detail` e `errors` sem precisar de um formato próprio. 4) Os filtros ficam num único `DashboardFilter`, validado na borda, e o período é limitado a 366 dias." |
| 0:55–1:55 | `/dashboard` | **Demo RF01–RF07:** mostrar os cartões de KPI (RF01). Trocar o gráfico de evolução para Semana e depois Mês, e mostrar as vendas por categoria (RF02). Comentar a situação dos pedidos, com o cancelado contado à parte (RF03). Alternar o ranking entre Quantidade e Receita (RF04). Filtrar por PIX e depois pela categoria Bebida, e clicar em Limpar (RF05). Abrir o F12 na aba Network para mostrar que tudo vem de `/api/dashboard/*` (RF06). Ativar o auto-refresh de 30 s, colocar a data inicial depois da final e mostrar o alerta de erro, depois filtrar um período sem pedidos e mostrar o estado vazio (RF07). |
| 1:55–2:25 | Terminal e `/swagger-ui.html` | **Evidências:** rodar `./mvnw test` e mostrar o resultado verde. Mostrar no `DashboardMongoIntegrationTest` a massa controlada, em que 171,95 é o resultado esperado. Rodar o `curl-dashboard.sh` e mostrar um 200 e um 422. Abrir o Swagger com os 5 endpoints documentados. |
| 2:25–2:50 | `git log --graph` e GitHub | **Gitflow:** "`main` é a versão estável, `develop` integra o trabalho e cada bloco de RF teve uma `feature/dashboard-*` própria, integrada por merge `--no-ff`. A entrega vai para `main` por PR." |
| 2:50–3:00 | `/dashboard` | **Fechamento:** "O resultado é um dashboard que consome somente a API, com dados agregados no banco, validados e testados." |

**Dicas:** grave em 1080p com zoom de 125% no navegador. Ensaie a parte de demonstração (1 minuto) com os cliques já planejados. Se passar de 3 minutos, corte a parte de código e fique com a demonstração e as evidências.
