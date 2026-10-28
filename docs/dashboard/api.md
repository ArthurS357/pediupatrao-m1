# API do Dashboard: endpoints

- **Base:** `http://localhost:8081/api/dashboard`
- **Formato:** JSON, somente `GET`.
- **Acesso:** sessão autenticada como **ADMIN** ou **GERENTE** (login por formulário em `/login`).
- **Contrato gerado do código:** [`openapi.json`](openapi.json). Com a aplicação rodando, o mesmo contrato fica em `/v3/api-docs` e em `/swagger-ui.html`.

Os exemplos de resposta usam a massa controlada do teste de integração ([tests.md](tests.md)), em outubro de 2026: 4 vendas que somam R$ 171,95, mais 1 pedido cancelado e 1 pedido fora do período.

## Filtros comuns (RF05)

Os cinco endpoints aceitam os mesmos parâmetros de query, todos opcionais:

| Parâmetro | Tipo | Regra | Padrão |
|---|---|---|---|
| `from` | data `yyyy-MM-dd` | primeiro dia, incluído; ano entre 2000 e 2100 | `to − 29` dias |
| `to` | data `yyyy-MM-dd` | último dia, incluído; `from ≤ to`; até 366 dias de período | hoje (America/Sao_Paulo) |
| `status` | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO` | conta exatamente esse status | indicadores de venda: todos menos `CANCELADO`; `orders-status`: todos |
| `category` | texto, até 50 caracteres | `tipo` do produto, sem diferenciar maiúsculas; em branco = todas | todas |
| `paymentMethod` | `DINHEIRO`, `PIX`, `CARTAO_CREDITO`, `CARTAO_DEBITO` | forma de pagamento do pedido | todas |

Com `category`, os indicadores **por pedido** (`kpis`, `sales-trend`, `orders-status`) consideram os pedidos que têm ao menos um item da categoria, com o valor total do pedido. Os indicadores **por item** (`sales-by-category`, `top-products`) consideram só os itens da categoria.

O mapa de status pedido na atividade ficou assim: pendente → `RECEBIDO`; em preparo → `EM_PREPARACAO` e `PRONTO`; entregue → `SAIU_PARA_ENTREGA`, `RETIRADO` e `FINALIZADO`; cancelado → `CANCELADO`.

## Erros

Todo erro da API é um `ProblemDetail` (RFC 9457), com `Content-Type: application/problem+json`. Os erros de validação trazem também `errors[]`, com o nome do parâmetro em `field`.

| Status | Quando | Exemplo de `detail` / `errors` |
|---|---|---|
| **400** | parâmetro com tipo ou formato inválido (data fora do ISO, enum desconhecido, `limit` fora de 1..50, `category` com mais de 50 caracteres) | `"errors":[{"field":"limit","message":"limit must be at most 50"}]` |
| **401** | sem sessão (corpo vazio) | — |
| **403** | logado, mas sem perfil ADMIN ou GERENTE (corpo de erro padrão do Spring Boot) | — |
| **404** | rota inexistente sob `/api/dashboard` | — |
| **422** | período inconsistente: `from` posterior a `to`, mais de 366 dias ou ano fora de 2000..2100 | `"detail":"O período consultado deve ter no máximo 366 dias."` |
| **500** | falha inesperada, como o banco fora do ar; a causa fica só no log | `"detail":"The operation could not be completed"` |

Os corpos reais estão em [`tests/evidencias-erros-mockmvc.txt`](tests/evidencias-erros-mockmvc.txt). Por exemplo:

```http
GET /api/dashboard/kpis?from=01/10/2026
HTTP/1.1 400
Content-Type: application/problem+json

{"type":"about:blank","title":"Bad Request","status":400,"detail":"Validation failed",
 "instance":"/api/dashboard/kpis","errors":[{"field":"from","message":"from has an invalid value: 01/10/2026"}]}
```

```http
GET /api/dashboard/kpis?from=2026-10-31&to=2026-10-01
HTTP/1.1 422
Content-Type: application/problem+json

{"type":"about:blank","title":"Unprocessable Entity","status":422,
 "detail":"A data inicial (from) não pode ser posterior à data final (to).","instance":"/api/dashboard/kpis"}
```

---

## `GET /api/dashboard/kpis` (RF01)

Retorna o faturamento, o ticket médio, o número de pedidos e o período efetivamente usado.

```bash
curl -b cookies.txt "http://localhost:8081/api/dashboard/kpis?from=2026-10-01&to=2026-10-31"
```

```json
{ "from": "2026-10-01", "to": "2026-10-31", "revenue": 171.95, "averageTicket": 42.99, "orders": 4 }
```

| Campo | Significado |
|---|---|
| `revenue` | soma de `valorTotal` (desconto aplicado) dos pedidos do recorte |
| `averageTicket` | `revenue ÷ orders`, arredondado HALF_UP a centavos; 0 sem pedidos |
| `orders` | número de pedidos |
| `from`, `to` | período usado, já com os padrões aplicados |

Com `paymentMethod=PIX`, o resultado é `{"revenue":107.00,"orders":2,...}`. Com `status=CANCELADO`, é `{"revenue":8.00,"orders":1,...}`. Num período sem pedidos, a resposta é `200` com zeros, nunca um erro.

**Erros possíveis:** 400, 401, 403 e 422.

## `GET /api/dashboard/sales-trend` (RF02)

Retorna uma série temporal contínua, com zeros nos intervalos sem venda.

| Parâmetro extra | Valores | Padrão |
|---|---|---|
| `granularity` | `DAY`, `WEEK` (semanas que começam na segunda-feira) ou `MONTH` | `DAY` |

```bash
curl -b cookies.txt "http://localhost:8081/api/dashboard/sales-trend?from=2026-10-01&to=2026-10-31&granularity=WEEK"
```

```json
[
  { "period": "2026-09-28", "revenue": 0.00,   "orders": 0 },
  { "period": "2026-10-05", "revenue": 139.95, "orders": 2 },
  { "period": "2026-10-12", "revenue": 0.00,   "orders": 0 },
  { "period": "2026-10-19", "revenue": 24.00,  "orders": 1 },
  { "period": "2026-10-26", "revenue": 8.00,   "orders": 1 }
]
```

`period` é o primeiro dia do intervalo. Por isso, a primeira semana pode começar antes de `from`; mesmo assim, só entram pedidos de dentro do período.

**Erros possíveis:** 400 (inclusive para `granularity` desconhecida), 401, 403 e 422.

## `GET /api/dashboard/sales-by-category` (RF02)

Retorna as vendas por categoria, da maior para a menor receita.

```bash
curl -b cookies.txt "http://localhost:8081/api/dashboard/sales-by-category?from=2026-10-01&to=2026-10-31"
```

```json
[
  { "category": "Pizza",  "quantity": 3, "revenue": 131.95 },
  { "category": "Bebida", "quantity": 5, "revenue": 40.00 }
]
```

A soma de `revenue` das categorias é igual ao `revenue` de `/kpis` sem filtro de categoria (131,95 + 40,00 = 171,95). Produtos removidos do cadastro entram como `"Sem categoria"`.

**Erros possíveis:** 400, 401, 403 e 422.

## `GET /api/dashboard/orders-status` (RF03)

Retorna a contagem por status. Os 7 status aparecem sempre, na ordem do fluxo e mesmo com zero, e `CANCELADO` também entra.

```bash
curl -b cookies.txt "http://localhost:8081/api/dashboard/orders-status?from=2026-10-01&to=2026-10-31"
```

```json
[
  { "status": "RECEBIDO", "count": 1 }, { "status": "EM_PREPARACAO", "count": 1 },
  { "status": "PRONTO", "count": 0 }, { "status": "SAIU_PARA_ENTREGA", "count": 0 },
  { "status": "RETIRADO", "count": 0 }, { "status": "FINALIZADO", "count": 2 },
  { "status": "CANCELADO", "count": 1 }
]
```

**Erros possíveis:** 400 (inclusive para `status` desconhecido, como `ENTREGUE`), 401, 403 e 422.

## `GET /api/dashboard/top-products` (RF04)

Retorna o ranking dos produtos mais vendidos.

| Parâmetro extra | Valores | Padrão |
|---|---|---|
| `sort` | `QUANTITY` (unidades) ou `REVENUE` (receita) | `QUANTITY` |
| `limit` | 1 a 50 | 10 |

```bash
curl -b cookies.txt "http://localhost:8081/api/dashboard/top-products?from=2026-10-01&to=2026-10-31&sort=QUANTITY&limit=5"
```

```json
[
  { "productId": "66aa00000000000000000002", "name": "Guarana",   "category": "Bebida", "quantity": 5, "revenue": 40.00 },
  { "productId": "66aa00000000000000000001", "name": "Calabresa", "category": "Pizza",  "quantity": 3, "revenue": 131.95 }
]
```

Com `sort=REVENUE&limit=1`, a resposta é só `Calabresa` (131,95). O `name` é o nome gravado no pedido, que fica como histórico. A receita de cada item é a sua fatia do total do pedido, com o desconto já aplicado.

**Erros possíveis:** 400 (`sort` desconhecido, `limit` fora de 1..50), 401, 403 e 422.

---

## Como autenticar o `curl`

A API usa a mesma sessão do sistema: o login é feito por formulário com token CSRF. O script [`tests/curl-dashboard.sh`](tests/curl-dashboard.sh) faz tudo: busca o token, faz login, chama cada endpoint e grava as respostas. À mão, os passos são:

```bash
csrf=$(curl -s -c cookies.txt http://localhost:8081/login | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//;s/"$//')
curl -s -b cookies.txt -c cookies.txt -X POST http://localhost:8081/login \
     --data-urlencode username=gerente --data-urlencode "password=$DASH_PASSWORD" --data-urlencode "_csrf=$csrf"
curl -s -b cookies.txt "http://localhost:8081/api/dashboard/kpis"
```

Requisições `GET` não exigem CSRF; só o login exige.
