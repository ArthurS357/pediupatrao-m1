# Rastreabilidade RF01–RF05: PediuPatrão M1

Este documento acompanha o [README](../../README.md) e aponta, para cada sub-requisito de `docs/Atividade_Pratica_M1_Parte2.docx`, o arquivo e a linha do código onde ele está implementado.
As linhas foram conferidas em 2026-10-11.

Em **Linha(s)**, o primeiro número indica onde está a evidência. Se o trecho for um método ou um bloco, o intervalo inteiro aparece entre parênteses. Todo caminho Java parte de `src/main/java/com/umc/pediupatrao/`.

## Resumo

| RF | Pontos | Arquivos-chave |
|---|---|---|
| RF01 | 2,0 | `Perfil.java`, `SecurityConfig.java`, `UsuarioService.java` |
| RF02 | 1,0 | `Pedido.java`, `ItemPedido.java`, `PedidoService.java` |
| RF03 | 1,5 | `StatusPedido.java`, `PedidoService.java` |
| RF04 | 1,5 | `PedidoService.java`, `PedidoController.java` |
| RF05 | 3,0 | `AuditoriaEvento.java`, `AuditoriaService.java`, `AuditoriaRepository.java` |

## RF01: Controle de usuários e permissões (2,0)

| Sub-requisito (do enunciado) | Arquivo | Linha(s) | Evidência (trecho) |
|---|---|---|---|
| Três perfis (ADMIN, GERENTE, ATENDENTE) | `entity/Perfil.java` | 8 (8–9) | `public enum Perfil {` |
| Constantes dos perfis | `entity/Perfil.java` | 9 | `ADMIN, GERENTE, ATENDENTE;` |
| Perfil legado ou vazio (ex.: `USER`) vira o menor privilégio | `entity/Perfil.java` | 12 (12–14) | `public static Perfil deRole(String role)` |
| Perfil inválido rejeitado no cadastro/alteração | `entity/Perfil.java` | 17 (17–20) | `public static Perfil exigir(String role)` |
| Login atribui a role do perfil persistido | `service/UsuarioDetailsService.java` | 26 | `.roles(Perfil.deRole(usuario.getRole()).name())` |
| ADMIN: administra usuários e perfis | `config/SecurityConfig.java` | 94 | `.requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole(ADMIN)` |
| ADMIN e GERENTE: consultam a auditoria | `config/SecurityConfig.java` | 97 | `.requestMatchers("/api/auditoria/**", "/auditoria/**").hasAnyRole(ADMIN, GERENTE)` |
| ADMIN, GERENTE e ATENDENTE: consultam/acompanham pedidos | `config/SecurityConfig.java` | 104 (104–105) | `.requestMatchers(HttpMethod.GET, "/api/pedidos/**", "/pedidos/**")` |
| GERENTE: status, desconto e cancelamento (nem o ADMIN) | `config/SecurityConfig.java` | 100 (100–101) | `"/api/pedidos/*/status", "/api/pedidos/*/desconto", "/api/pedidos/*/cancelamento"` |
| ATENDENTE e GERENTE: registram pedidos | `config/SecurityConfig.java` | 102 | `.requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole(ATENDENTE, GERENTE)` |
| GERENTE: consulta clientes (junto com ATENDENTE) | `config/SecurityConfig.java` | 109 | `.requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes/**").hasAnyRole(ATENDENTE, GERENTE)` |
| GERENTE: exclui clientes | `config/SecurityConfig.java` | 108 | `.requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole(GERENTE)` |
| ATENDENTE: cadastra e atualiza clientes | `config/SecurityConfig.java` | 110 | `.requestMatchers("/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)` |
| ATENDENTE: cadastro/alteração de cliente barrado também no service | `service/ClienteService.java` | 57 | `@PreAuthorize("hasRole('ATENDENTE')")` |
| Desconto só GERENTE, no service | `service/PedidoService.java` | 113 | `@PreAuthorize("hasRole('GERENTE')")` |
| Cancelamento só GERENTE, no service | `service/PedidoService.java` | 138 | `@PreAuthorize("hasRole('GERENTE')")` |
| `@PreAuthorize` habilitado | `config/SecurityConfig.java` | 36 | `@EnableMethodSecurity` |
| API sem login responde 401 (não redireciona) | `config/SecurityConfig.java` | 88 | `new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")` |
| Acesso negado vai para o log (sem corpo nem credenciais) | `config/SecurityConfig.java` | 135 | `log.warn("Acesso negado: usuario={} metodo={} caminho={}"` |
| Autoelevação: não altera o próprio perfil ou username | `service/UsuarioService.java` | 64 (64–66) | `if (ehOProprio && mudouPerfilOuNome) {` |
| Autoelevação: não exclui o próprio usuário | `service/UsuarioService.java` | 84 (84–86) | `if (usuario.getUsername().equals(usernameAtual)) {` |
| Perfil validado ao criar usuário | `service/UsuarioService.java` | 44 | `Perfil perfil = Perfil.exigir(dados.role());` |
| Sessões do usuário alterado são encerradas | `service/UsuarioService.java` | 77 | `sessoes.encerrar(usernameAnterior);` |
| CSRF ativo (nenhum `csrf().disable()`) | `config/SecurityConfig.java` | 83 | `// CSRF ativo` |
| CSRF: token exposto às páginas | `src/main/resources/templates/layout.html` | 6 (6–7) | `<meta name="_csrf" th:content="${_csrf.token}">` |
| CSRF: `fetch()` de escrita envia o token só para a própria origem | `src/main/resources/static/js/script.js` | 10 (10–12) | `if (mesmaOrigem && !["GET", "HEAD", "OPTIONS"].includes(metodo)) {` |
| CSRF: formulários com `th:action` (token automático) | `src/main/resources/templates/clientes/form.html` | 22 | `<form th:action="${cliente.id != null}` |
| Backfill: usuário de seed sem perfil recebe o perfil correto (ex.: `admin` → ADMIN) | `config/DataInitializer.java` | 36 (36–40) | `if (existente.getRole() == null \|\| existente.getRole().isBlank()) {` |

## RF02: Cadastro de pedidos (1,0)

| Sub-requisito (do enunciado) | Arquivo | Linha(s) | Evidência (trecho) |
|---|---|---|---|
| Entidade `Pedido` (coleção `pedidos`) | `entity/Pedido.java` | 17 | `@Document(collection = "pedidos")` |
| Cliente e tipo de entrega | `entity/Pedido.java` | 27 (27–28) | `private String clienteId;` |
| Itens | `entity/Pedido.java` | 29 | `private List<ItemPedido> itens;` |
| Desconto e valor total | `entity/Pedido.java` | 30 (30–31) | `private BigDecimal descontoPercentual = BigDecimal.ZERO;` |
| Responsável pelo registro e data/hora de entrada | `entity/Pedido.java` | 34 (34–35) | `private String criadoPor;` |
| `ItemPedido` (produtoId, nome, precoUnitario, quantidade) | `entity/ItemPedido.java` | 6 | `public record ItemPedido(String produtoId, String nome, BigDecimal precoUnitario, int quantidade)` |
| Requisição não tem campo de total, preço ou status | `dto/PedidoRequest.java` | 11 (11–15) | `public record PedidoRequest(` |
| Validação: quantidade entre 1 e 100, produto obrigatório | `dto/ItemRequest.java` | 9 (9–11) | `@NotBlank(message = "produtoId must not be blank") String produtoId,` |
| Validação: cliente, tipo e ao menos um item | `dto/PedidoRequest.java` | 12 (12–14) | `@NotBlank(message = "clienteId must not be blank") String clienteId,` |
| `@Valid` no endpoint de criação | `controller/PedidoController.java` | 36 | `@RequestBody @Valid PedidoRequest pedido` |
| Cliente precisa existir | `service/PedidoService.java` | 45 (45–46) | `clienteService.buscarPorId(dados.clienteId())` |
| Produto precisa existir e estar ativo; preço vem do cadastro | `service/PedidoService.java` | 169 (169–176) | `private ItemPedido montarItem(ItemRequest item) {` |
| Total calculado no servidor ao criar | `service/PedidoService.java` | 53 | `pedido.setValorTotal(calcularTotal(pedido.getItens(), BigDecimal.ZERO));` |
| Responsável e entrada definidos pelo servidor | `service/PedidoService.java` | 55 (55–56) | `pedido.setCriadoPor(ator.username());` |
| `calcularTotal`: soma(preço × quantidade) com desconto, 2 casas | `service/PedidoService.java` | 163 (163–167) | `static BigDecimal calcularTotal(List<ItemPedido> itens, BigDecimal descontoPercentual) {` |

## RF03: Entrada, saída e transições (1,5)

| Sub-requisito (do enunciado) | Arquivo | Linha(s) | Evidência (trecho) |
|---|---|---|---|
| Enum `StatusPedido` | `entity/StatusPedido.java` | 9 (9–10) | `public enum StatusPedido {` |
| Os 7 estados | `entity/StatusPedido.java` | 10 | `RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO;` |
| Tabela de transições válidas | `entity/StatusPedido.java` | 13 (13–21) | `public Set<StatusPedido> proximos(TipoEntrega tipo) {` |
| PRONTO segue para SAIU_PARA_ENTREGA ou RETIRADO conforme o tipo | `entity/StatusPedido.java` | 17 | `case PRONTO -> Set.of(tipo == TipoEntrega.ENTREGA ? SAIU_PARA_ENTREGA : RETIRADO);` |
| FINALIZADO e CANCELADO não têm próximo estado | `entity/StatusPedido.java` | 19 | `case FINALIZADO, CANCELADO -> Set.of();` |
| Entrada: status RECEBIDO na criação | `service/PedidoService.java` | 54 | `pedido.setStatus(StatusPedido.RECEBIDO);` |
| Avanço de status (só GERENTE) | `service/PedidoService.java` | 80 (80–110) | `@PreAuthorize("hasRole('GERENTE')")` |
| Bloqueio de alteração após cancelamento | `service/PedidoService.java` | 86 (86–88) | `if (anterior == StatusPedido.CANCELADO) {` |
| CANCELADO não é alcançável pela troca de status | `service/PedidoService.java` | 89 (89–91) | `if (destino == StatusPedido.CANCELADO) {` |
| Transição fora da tabela é recusada | `service/PedidoService.java` | 92 (92–95) | `if (!anterior.proximos(pedido.getTipoEntrega()).contains(destino)) {` |
| Saída registra data/hora e responsável | `service/PedidoService.java` | 99 (99–102) | `if (destino.registraSaida()) {` |
| Campos de saída no pedido | `entity/Pedido.java` | 37 (37–38) | `private LocalDateTime saidaEm;` |
| Estados que registram saída | `entity/StatusPedido.java` | 29 (29–31) | `public boolean registraSaida() {` |
| `TransicaoInvalidaException` | `exception/TransicaoInvalidaException.java` | 4 | `public class TransicaoInvalidaException extends RuntimeException {` |
| `TransicaoInvalidaException` → 409 | `exception/RestExceptionHandler.java` | 44 (44–47) | `@ExceptionHandler(TransicaoInvalidaException.class)` |
| Mudança de status auditada (antes/depois) | `service/PedidoService.java` | 105 | `auditoria.registrar(ator, Operacao.PEDIDO_STATUS_ALTERADO` |

## RF04: Cancelamento e desconto (1,5)

| Sub-requisito (do enunciado) | Arquivo | Linha(s) | Evidência (trecho) |
|---|---|---|---|
| Desconto: rota exclusiva do GERENTE | `config/SecurityConfig.java` | 100 (100–101) | `"/api/pedidos/*/desconto"` |
| Desconto: `@PreAuthorize` GERENTE | `service/PedidoService.java` | 113 (113–135) | `@PreAuthorize("hasRole('GERENTE')")` |
| Desconto: endpoint com `@Valid` | `controller/PedidoController.java` | 56 (56–60) | `@PostMapping("/{id}/desconto")` |
| Desconto: percentual obrigatório | `dto/DescontoRequest.java` | 7 | `@NotNull(message = "percentual must not be null") BigDecimal percentual` |
| Desconto: só antes da saída | `service/PedidoService.java` | 117 | `exigirAntesDaSaida(pedido, "receber desconto");` |
| Desconto: 0 < p ≤ 20 | `service/PedidoService.java` | 119 (119–121) | `if (arredondado == null \|\| arredondado.signum() <= 0 \|\| arredondado.compareTo(DESCONTO_MAXIMO) > 0) {` |
| Desconto: limite de 20% | `service/PedidoService.java` | 32 | `static final BigDecimal DESCONTO_MAXIMO = BigDecimal.valueOf(20);` |
| Desconto: total recalculado no servidor | `service/PedidoService.java` | 126 | `pedido.setValorTotal(calcularTotal(pedido.getItens(), pedido.getDescontoPercentual()));` |
| Desconto: auditoria com desconto e total antes/depois | `service/PedidoService.java` | 129 (129–133) | `auditoria.registrar(ator, Operacao.PEDIDO_DESCONTO_APLICADO` |
| Cancelamento: `@PreAuthorize` GERENTE | `service/PedidoService.java` | 138 (138–160) | `@PreAuthorize("hasRole('GERENTE')")` |
| Cancelamento: endpoint com `@Valid` | `controller/PedidoController.java` | 62 (62–66) | `@PostMapping("/{id}/cancelamento")` |
| Cancelamento: justificativa obrigatória (até 500) | `dto/CancelamentoRequest.java` | 8 (8–9) | `@NotBlank(message = "justificativa must not be blank")` |
| Cancelamento: só antes da saída | `service/PedidoService.java` | 142 | `exigirAntesDaSaida(pedido, "ser cancelado");` |
| Cancelamento: justificativa revalidada no service | `service/PedidoService.java` | 143 (143–146) | `if (justificativa == null \|\| justificativa.isBlank() \|\| justificativa.length() > TAMANHO_MAX_JUSTIFICATIVA) {` |
| Cancelamento: dados preservados, só status e campos de cancelamento mudam | `service/PedidoService.java` | 149 (149–152) | `pedido.setStatus(StatusPedido.CANCELADO);` |
| Cancelamento: auditoria com status antes/depois e justificativa | `service/PedidoService.java` | 155 (155–158) | `auditoria.registrar(ator, Operacao.PEDIDO_CANCELADO` |
| Estados que aceitam desconto/cancelamento | `entity/StatusPedido.java` | 24 (24–26) | `public boolean antesDaSaida() {` |

## RF05: Auditoria (3,0)

| Sub-requisito (do enunciado) | Arquivo | Linha(s) | Evidência (trecho) |
|---|---|---|---|
| Entidade `AuditoriaEvento` (coleção `auditoria`) | `entity/AuditoriaEvento.java` | 10 (10–20) | `public record AuditoriaEvento(` |
| Usuário e perfil | `entity/AuditoriaEvento.java` | 13 (13–14) | `String usuario,` |
| Data e hora | `entity/AuditoriaEvento.java` | 12 | `LocalDateTime dataHora,` |
| Operação, entidade e identificador | `entity/AuditoriaEvento.java` | 15 (15–17) | `Operacao operacao,` |
| Campos alterados (anterior/novo) e justificativa | `entity/AuditoriaEvento.java` | 18 (18–19) | `List<Alteracao> alteracoes,` |
| Usuário e perfil vêm da sessão, nunca do corpo | `service/Ator.java` | 11 (11–18) | `public static Ator de(UserDetails usuario) {` |
| Operações auditadas (pedido e cliente) | `entity/Operacao.java` | 4 (4–12) | `public enum Operacao {` |
| Repositório só de inserção (sem delete/update) | `repository/AuditoriaRepository.java` | 7 (7–10) | `public interface AuditoriaRepository extends Repository<AuditoriaEvento, String> {` |
| Único método de escrita: `save` | `repository/AuditoriaRepository.java` | 9 | `AuditoriaEvento save(AuditoriaEvento evento);` |
| Registro grava só o que mudou | `service/AuditoriaService.java` | 45 (45–53) | `public void registrar(Ator ator, Operacao operacao, EntidadeAuditada entidade, String entidadeId,` |
| Inclusão de pedido auditada | `service/PedidoService.java` | 59 | `auditoria.registrar(ator, Operacao.PEDIDO_CRIADO` |
| Inclusão de cliente auditada | `service/ClienteService.java` | 62 | `auditoria.registrar(ator, Operacao.CLIENTE_CRIADO` |
| Alteração de cliente auditada (antes/depois) | `service/ClienteService.java` | 70 (70–71) | `auditoria.registrar(ator, Operacao.CLIENTE_ALTERADO` |
| Exclusão de cliente auditada | `service/ClienteService.java` | 52 | `auditoria.registrar(ator, Operacao.CLIENTE_EXCLUIDO` |
| Endereço e telefone auditados campo a campo | `service/ClienteService.java` | 75 (75–87) | `private static Map<String, String> campos(Cliente c) {` |
| Consulta restrita a ADMIN e GERENTE (service) | `service/AuditoriaService.java` | 55 | `@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")` |
| Consulta restrita a ADMIN e GERENTE (rota) | `config/SecurityConfig.java` | 97 | `.requestMatchers("/api/auditoria/**", "/auditoria/**").hasAnyRole(ADMIN, GERENTE)` |
| API de auditoria só tem leitura | `controller/AuditoriaController.java` | 21 | `@GetMapping` |
| Telefone mascarado na consulta | `service/AuditoriaService.java` | 64 | `.map(AuditoriaService::mascarado)` |
| Máscara: mantém só os 4 últimos dígitos | `service/AuditoriaService.java` | 101 (101–106) | `static String mascarar(String telefone) {` |
| Campos com nome de credencial nunca gravados | `service/AuditoriaService.java` | 35 | `Pattern.compile("(?i)senha\|password\|token\|secret")` |
| Descarte aplicado ao montar as alterações | `service/AuditoriaService.java` | 82 (82–83) | `boolean credencial = CAMPO_CREDENCIAL.matcher(nome).find();` |
| Transação: `MongoTransactionManager` | `config/AppConfig.java` | 21 (21–23) | `MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {` |
| Transação: pedido + evento | `service/PedidoService.java` | 43 | `@Transactional` |
| Transação: cliente + evento | `service/ClienteService.java` | 58 | `@Transactional` |

## Como conferir

Basta abrir o arquivo na linha indicada pela IDE. Outra opção é rodar, a partir da raiz do projeto:

```powershell
Select-String -Path "<arquivo>" -Pattern "<trecho>" -SimpleMatch
```

Quando o número de linha devolvido coincide com o da tabela, a evidência está confirmada. Além disso, `src/test/java/com/umc/pediupatrao/config/SecurityRulesIntegrationTest.java` testa automaticamente a matriz perfil × rota.
