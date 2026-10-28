# Dashboard Administrativo: entregáveis

| # | Entregável | Onde |
|---|---|---|
| 1 | Requisitos funcionais (RF01–RF07) e não funcionais, com rastreabilidade para endpoint, tela e teste | [requisitos.md](requisitos.md) |
| 2 | Documentação dos endpoints: parâmetros, exemplos de request e response, erros possíveis | [api.md](api.md) e [openapi.json](openapi.json) (Swagger UI em `/swagger-ui.html`) |
| 3 | Evidências de testes: unitários, de integração e chamadas reais à API | [tests.md](tests.md) e [tests/curl-dashboard.sh](tests/curl-dashboard.sh) |
| 4 | Dashboard funcional | `/dashboard` ([template](../../src/main/resources/templates/dashboard.html)), pacote [`dashboard`](../../src/main/java/com/umc/pediupatrao/dashboard) |
| 5 | Código no GitHub com gitflow | `main` (estável), `develop` (integração), `feature/dashboard-*` (um bloco de RF por branch); detalhes em [tests.md](tests.md#gitflow) |
| 6 | Roteiro do vídeo (até 3 min) | [roteiro-video.md](roteiro-video.md) |
