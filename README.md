# PediuPatrão

Sistema de pedidos para pizzaria, escrito em Java 21 com Spring Boot 3.4.4, Spring Security, MongoDB (Atlas) e Thymeleaf. O build usa Maven (`mvnw`).
Esta é a entrega da Atividade Prática M1 de Engenharia de Software, que pede controle de acesso por perfil, gestão de pedidos e auditoria.
O enunciado completo fica em [`docs/atividade-m1/`](docs/atividade-m1/README.md).

## Como executar

Você vai precisar do JDK 21 e de um MongoDB **com replica set**. O Atlas já vem assim; numa instância local, suba com `--replSet` e rode `rs.initiate()`. Isso é necessário porque o pedido (ou o cliente) e o evento de auditoria são gravados na mesma transação. **O repositório não guarda nenhuma credencial**: todas vêm de variáveis de ambiente.

| Variável | Para quê |
|---|---|
| `SPRING_DATA_MONGODB_URI` | URI de conexão do MongoDB (obrigatória) |
| `APP_SEED_ADMIN_PASSWORD` | cria o usuário `admin` (perfil ADMIN) se ele não existir |
| `APP_SEED_GERENTE_PASSWORD` | cria o usuário `gerente` (perfil GERENTE) se ele não existir |
| `APP_SEED_ATENDENTE_PASSWORD` | cria o usuário `atendente` (perfil ATENDENTE) se ele não existir |

A aplicação usa o banco `pizzaria` (`spring.data.mongodb.database`). Cada senha de seed precisa ter pelo menos 8 caracteres e é salva com BCrypt. Caso o usuário `admin` já exista sem perfil, ele vira ADMIN quando a aplicação inicia.

```powershell
$env:SPRING_DATA_MONGODB_URI = "<sua-uri>"
$env:APP_SEED_ADMIN_PASSWORD = "<senha-admin>"
$env:APP_SEED_GERENTE_PASSWORD = "<senha-gerente>"
$env:APP_SEED_ATENDENTE_PASSWORD = "<senha-atendente>"
.\mvnw.cmd spring-boot:run
```

Depois disso, abra <http://localhost:8081>. A porta está definida em `application.properties`.

Para entrar, use os logins `admin`, `gerente` ou `atendente`. A senha de cada um é a que você colocou na variável `APP_SEED_*_PASSWORD` correspondente, já que nenhuma senha fica fixa no repositório.

Os testes rodam sem MongoDB:

```powershell
.\mvnw.cmd test      # 96 testes
.\mvnw.cmd verify
```

## Requisitos implementados

| Requisito | O que foi feito |
|---|---|
| **RF01** Perfis e permissões | Três perfis, `ADMIN`, `GERENTE` e `ATENDENTE`, no enum `Perfil`. A autorização acontece no backend, em `SecurityConfig`, por rota e por método HTTP; o CSRF está ativo e qualquer chamada a `/api/**` sem login recebe 401. Só o ADMIN administra usuários, e mesmo ele não pode mudar o próprio perfil nem se excluir. Perfis inválidos são recusados. Quando o perfil ou a senha de alguém muda, ou quando o usuário é excluído, as sessões abertas dele caem. Todo acesso negado fica no log com usuário, método e caminho. |
| **RF02** Cadastro de pedidos | O `Pedido` guarda itens (produto, com nome e preço copiados do cadastro, e quantidade), desconto, total, responsável e data/hora de entrada. Do lado do cliente chegam apenas o cliente, o tipo (entrega ou retirada) e os itens: **quem calcula o total é o servidor**. |
| **RF03** Fluxo e transições | O pedido segue `RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA` (entrega) ou `RETIRADO` (retirada) `→ FINALIZADO`. Uma transição fora dessa ordem devolve 409. Na saída, ficam registrados a data/hora e o responsável. |
| **RF04** Cancelamento e desconto | Só o GERENTE pode fazer as duas coisas, e a regra está tanto no `SecurityConfig` **quanto** no service, via `@PreAuthorize`; nem o ADMIN consegue. O desconto precisa ser maior que 0 e no máximo 20%, e só vale antes da saída. O cancelamento também só vale antes da saída, pede justificativa, mantém os dados do pedido e trava qualquer alteração depois dele. |
| **RF05** Auditoria | Os eventos vão para a coleção `auditoria` no MongoDB, que só aceita inserção: o repositório não tem delete nem update, e não existe endpoint de escrita. Em cada evento ficam usuário, perfil, data/hora, operação, entidade e id, os campos alterados (valor anterior e novo) e a justificativa. A consulta, em `/auditoria` e `GET /api/auditoria`, é restrita a ADMIN e GERENTE, e os telefones aparecem mascarados. Campos cujo nome indica credencial (`senha`, `password`, `token`, `secret`) nunca entram no registro. O pedido ou cliente e seu evento de auditoria são gravados juntos, numa só transação. |

### Quem pode o quê

| Recurso | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Usuários (listar, criar, alterar perfil, excluir) | ✅ | ❌ | ❌ |
| Auditoria (consulta) | ✅ | ✅ | ❌ |
| Pedidos: consultar | ✅ | ✅ | ✅ |
| Pedidos: registrar | ❌ | ✅ | ✅ |
| Pedidos: avançar status | ❌ | ✅ | ❌ |
| Pedidos: desconto e cancelamento | ❌ | ✅ | ❌ |
| Clientes: consultar | ❌ | ✅ | ✅ |
| Clientes: cadastrar e atualizar | ❌ | ❌ | ✅ |
| Clientes: excluir (bloqueado se houver pedidos) | ❌ | ✅ | ❌ |
| Produtos: consultar | ✅ | ✅ | ✅ |
| Produtos: alterar | ✅ | ✅ | ❌ |

### API de pedidos

| Método e rota | Corpo | Perfis |
|---|---|---|
| `POST /api/pedidos` | `{ "clienteId", "tipoEntrega": "ENTREGA\|RETIRADA", "itens": [{ "produtoId", "quantidade" }] }` | ATENDENTE, GERENTE |
| `GET /api/pedidos`, `GET /api/pedidos/{id}` | sem corpo | ADMIN, GERENTE, ATENDENTE |
| `PUT /api/pedidos/{id}/status?status=EM_PREPARACAO` | sem corpo | GERENTE |
| `POST /api/pedidos/{id}/desconto` | `{ "percentual": 10 }` | GERENTE |
| `POST /api/pedidos/{id}/cancelamento` | `{ "justificativa": "..." }` | GERENTE |
| `GET /api/auditoria?entidade=PEDIDO&entidadeId=...` | sem corpo | ADMIN, GERENTE |

As respostas de erro seguem o formato `ProblemDetail` (RFC 9457): 400 para validação, 401 sem login, 403 sem permissão, 404, 409 para transição inválida e 422 para regra de negócio.

## Demonstração de bloqueio no backend

Como o CSRF está ativo, toda requisição que altera estado precisa levar o token. Entre como **ATENDENTE**, abra qualquer página do sistema e rode no console do navegador (F12):

```js
const h = { 'Content-Type': 'application/json',
  [document.querySelector('meta[name=_csrf_header]').content]: document.querySelector('meta[name=_csrf]').content };
fetch('/api/pedidos/ID_DO_PEDIDO/desconto', { method: 'POST', headers: h, body: '{"percentual":10}' }).then(r => r.status)  // 403
```

Sem estar logado, `curl -i http://localhost:8081/api/pedidos` → `401`. O `SecurityRulesIntegrationTest` cobre automaticamente a matriz inteira de perfis × rotas.

## Principais alterações em relação ao projeto base

No projeto base havia CRUD de usuários, clientes, produtos e pedidos, mas nada de perfis, de fluxo de estados ou de auditoria. O que esta entrega acrescentou:

- Perfis (RF01): o enum `Perfil` e as regras por rota e método HTTP em `SecurityConfig`. Desconto e cancelamento têm a mesma regra repetida com `@PreAuthorize` em `PedidoService`, de modo que nem o ADMIN passa. O `UsuarioService` impede que alguém eleve o próprio perfil, e o `SessoesAtivas` derruba as sessões afetadas.
- Pedido e fluxo (RF02 e RF03): o `Pedido` foi reescrito com `ItemPedido`, `StatusPedido` (que conhece as transições válidas e responde 409 às demais) e `TipoEntrega`. O total sai de `PedidoService`; o cliente HTTP não o envia.
- Desconto e cancelamento (RF04): `DescontoRequest` aceita valores acima de 0 e até 20%, e `CancelamentoRequest` exige justificativa. As duas operações só valem antes da saída, e um pedido cancelado não muda mais.
- Auditoria (RF05): `AuditoriaEvento` e `Alteracao`, `AuditoriaService` e `AuditoriaRepository`, este último só com inserção. Pedido ou cliente e evento são gravados na mesma transação, e a consulta fica em `AuditoriaController` e na tela `/auditoria`.
- Erros e telas: respostas `ProblemDetail` montadas em `exception/`, mais as telas `pedidos/novo` e `auditoria`.
- Testes: são 96 ao todo. Entre eles, a matriz perfil × rota (`SecurityRulesIntegrationTest`) e a checagem de autorização no service (`PedidoServiceSecurityIntegrationTest`).
- `application.properties` não traz mais a URI do Mongo nem a senha fixa do Spring Security.
- A senha saiu do código do `DataInitializer`; o seed agora lê variáveis de ambiente.
- `/api/usuarios` não é mais público. As respostas pararam de devolver a senha, e o `PUT` passou a criptografá-la.
- Classes e pastas novas: `Perfil`, `StatusPedido`, `TipoEntrega`, `ItemPedido`, `AuditoriaEvento`, `AuditoriaService`, os DTOs (`dto/`), as exceções e handlers (`exception/`) e as telas `pedidos/novo` e `auditoria`.
- Do `Pedido` antigo saíram os campos `tipoPizza`, `sabor` e `quantidade`. **Pedidos já gravados no banco não são compatíveis**, porque o status era texto livre; ao migrar, esvazie a coleção `pedidos`.
- Dependências adicionadas: `spring-boot-starter-validation` e `thymeleaf-extras-springsecurity6`, além de `spring-boot-starter-test` e `spring-security-test` no escopo de teste.

## Estrutura

```
src/main/java/com/umc/pediupatrao/
├── config/       SecurityConfig, AppConfig (Clock e transações), DataInitializer
├── controller/   REST (/api/**) e HomeController (páginas Thymeleaf)
├── dto/          requests e responses (records)
├── entity/       documentos MongoDB e enums
├── exception/    exceções de negócio e handlers (REST e páginas)
├── repository/   Spring Data MongoDB
└── service/      regras de negócio e auditoria
```
