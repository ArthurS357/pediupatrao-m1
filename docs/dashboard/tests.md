# Dashboard Administrativo: evidências de teste

## Resumo

| Nível | Classe | Testes | Status em 2026-10-28 | O que garante |
|---|---|---:|---|---|
| Unitário | `DashboardServiceUnitTest` | 17 | ✅ 17/17 | Período padrão e limites (30, 366 e anos 2000–2100), venda sem `CANCELADO`, filtro por status, categoria sem diferenciar maiúsculas e categoria em branco, ticket médio em HALF_UP, série contínua com zeros, semana começando na segunda-feira, soma por categoria arredondada só no fim, status sempre completos, ranking com categoria |
| Integração (HTTP) | `DashboardControllerIntegrationTest` | 13 | ✅ 13/13 | Binding dos 5 filtros, valores padrão de `granularity`, `sort` e `limit`, 400 com `errors[]` (data, enum, `limit`, `category`), 422 e 500 como `ProblemDetail` sem vazar a causa, 401 sem login, 403 para ATENDENTE, texto "parecido com operador Mongo" tratado como dado |
| Integração (segurança) | `SecurityRulesIntegrationTest` (+3 linhas) | 40 | ✅ 40/40 | Matriz perfil × rota com o dashboard incluído: ADMIN e GERENTE passam, ATENDENTE recebe 403 |
| Integração (MongoDB real) | `DashboardMongoIntegrationTest` | 13 | ⏸️ opt-in, não executado aqui | Os pipelines contra o banco: totais, PIX, categoria, CANCELADO, série dia/semana/mês, último dia às 23:30, categorias somando o faturamento, contagem por status, ranking, período vazio e o caso de arredondamento (2 × R$ 10,05 com 10% de desconto, que precisam dar R$ 18,10 em todos os endpoints) |
| Regressão | demais testes do projeto | 59 | ✅ 59/59 | Nada quebrou no Pediu Patrão. `PedidoServiceUnitTest` passou a verificar também a `formaPagamento` |
| **Total** | `./mvnw verify` | **142** | **129 ✅ · 0 ❌ · 13 ⏸️** | Saída completa em [`tests/evidencias-mvn-verify.txt`](tests/evidencias-mvn-verify.txt) |

### Como rodar

```bash
./mvnw test                                   # sem MongoDB: unitários, HTTP e segurança
./mvnw test -Ddashboard.it=true               # + integração com MongoDB real (precisa de SPRING_DATA_MONGODB_URI)
./mvnw test -Dtest='Dashboard*'               # só o dashboard
```

O teste com MongoDB real usa o banco `pizzaria_dashboard_it`. Antes de gravar, ele confere o nome do banco e, ao terminar, remove **só os ids que ele mesmo inseriu**. Nada é apagado no banco `pizzaria`.

### Massa controlada (`DashboardMongoIntegrationTest`)

| Pedido | Data/hora | Pagamento | Status | Itens | Total |
|---|---|---|---|---|---:|
| o1 | 05/10 12:00 | PIX | FINALIZADO | 2× Calabresa (45,50) + 1× Guaraná (8,00) | 99,00 |
| o2 | 05/10 20:00 | DINHEIRO | EM_PREPARACAO | 1× Calabresa, 10% de desconto | 40,95 |
| o3 | 07/10 19:00 | PIX | **CANCELADO** | 1× Guaraná | 8,00 |
| o4 | 20/10 18:30 | CARTAO_CREDITO | RECEBIDO | 3× Guaraná | 24,00 |
| o5 | **02/11** 12:00 | PIX | FINALIZADO | 1× Calabresa | 45,50 |
| o6 | 31/10 **23:30** | PIX | FINALIZADO | 1× Guaraná | 8,00 |
| d1, d2 | 10/12 19:00 | PIX | FINALIZADO | 1× Esfiha (10,05), 10% de desconto | 9,05 cada |

Resultados esperados para outubro (os mesmos exemplos de [api.md](api.md)):
- **Faturamento:** 171,95 em 4 pedidos (o3 está cancelado e o5 fica fora do período), ticket médio de 42,99.
- **Por categoria:** Pizza 131,95 (3 un.) e Bebida 40,00 (5 un.); a soma dá 171,95.
- **Com PIX:** 107,00 em 2 pedidos.

## Chamadas reais à API (curl)

O script [`tests/curl-dashboard.sh`](tests/curl-dashboard.sh) faz login como gerente e chama os 5 endpoints com os filtros de RF05, além dos casos de erro (400, 422 e 401). Cada resposta, com status e tempo, fica em `tests/evidencias/`.

```bash
DASH_PASSWORD='<senha do gerente>' ./docs/dashboard/tests/curl-dashboard.sh http://localhost:8081
```

> ⚠️ **Ainda não executado.** Nesta máquina, o MongoDB Atlas recusou a conexão TLS (`SSLException: Received fatal alert: internal_error`), o que no Atlas indica que o IP não está em *Network Access*. Sem banco, a aplicação não sobe, e por isso o script e o `-Ddashboard.it=true` ficam para quando o IP for liberado. A pasta `tests/evidencias/` só é gerada nessa execução.

### Corpos de erro reais (MockMvc)

Os erros de validação e de acesso acontecem antes de qualquer acesso ao banco. Por isso, foram capturados com o filtro de segurança e o `RestExceptionHandler` reais, via MockMvc: veja [`tests/evidencias-erros-mockmvc.txt`](tests/evidencias-erros-mockmvc.txt). O arquivo mostra os 400 com `errors[]` (sem nomes de classe Java), o 422 de período, o 403 do ATENDENTE e o 401 sem login.

## Front

O front foi conferido com a API simulada (os mesmos números da massa de outubro), servida por um servidor local temporário:
- **Dados:** os 4 cartões, o gráfico de evolução (faturamento em linha e pedidos em barras), as categorias, os 7 status com barras proporcionais e o ranking.
- **Vazio:** "Nenhuma venda no período" em cada painel e cartões zerados.
- **Erro (422):** alerta com o `detail` do `ProblemDetail`, sem quebrar a tela.
- **XSS:** um produto chamado `Guaraná <b>2L</b>` aparece como texto (`&lt;b&gt;`), sem HTML injetado.
- **Console:** sem erros. O layout funciona do celular (cartões empilhados) ao desktop.
- **Script:** a sintaxe foi conferida com `node --check`.

**Pendente com o app real:** prints de `/dashboard` com dados do Atlas para o relatório e o vídeo.

## Revisão de código

A revisão (`java-reviewer`, ver [requisitos.md](requisitos.md#decisões-de-projeto)) não encontrou nada CRITICAL ou HIGH e apontou 3 achados MEDIUM, todos corrigidos com teste:
1. A receita por categoria e por produto se afastava do faturamento em cerca de R$ 0,005 por pedido com desconto. Agora ela é a fatia do total gravado e o arredondamento é feito só depois da soma.
2. Em `/top-products`, `errors[].field` mostrava o parâmetro Java `filter`. Agora mostra o campo do cliente (`category`).
3. A semântica do filtro de categoria estava sem documentação. Agora está no `@Schema` e neste diretório.

Os achados LOW também foram tratados:
- datas extremas devolviam 500 e agora devolvem 422;
- a categoria agora é agrupada sem diferenciar maiúsculas, e em branco vale como "todas";
- o ranking ganhou desempate pelo id;
- o `@Pattern` da categoria passou a recusar só caracteres de controle;
- o Swagger ficou restrito a ADMIN e GERENTE.

## Gitflow

```
*   merge: feature/dashboard-docs into develop
*   merge: feature/dashboard-front-rf06-rf07 into develop
|\  * feat(dashboard): RF06-RF07 - tela /dashboard consumindo a API
*   merge: feature/dashboard-api-rf01-rf04 into develop
|\  * feat(dashboard): RF01-RF04 - API de indicadores agregados no MongoDB
*   merge: feature/dashboard-api-rf05-filtros into develop
|\  * feat(dashboard): RF05 - filtros transversais e forma de pagamento
* (main) docs: matriz de rastreabilidade RF01-RF05
```

| Branch | Papel |
|---|---|
| `main` | estável: recebe `develop` por Pull Request ao fim da entrega |
| `develop` | integração das features (merge `--no-ff`, um commit de merge por bloco) |
| `feature/dashboard-api-rf05-filtros` | `formaPagamento`, `DashboardFilter`, regra de acesso, springdoc |
| `feature/dashboard-api-rf01-rf04` | repository, service, controller, DTOs e testes |
| `feature/dashboard-front-rf06-rf07` | tela `/dashboard` e menu |
| `feature/dashboard-docs` | este diretório e o README |

As mensagens de commit seguem o padrão `feat(dashboard): RFxx - ...`.
