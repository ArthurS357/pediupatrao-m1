package com.umc.pediupatrao.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.umc.pediupatrao.controller.AuditoriaController;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.ProdutoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Holds down the access matrix of SecurityConfig: which profile reaches which endpoint. Services are
 * mocks, so "allowed" means authorization let the request through (any status but 401/403).
 */
@WebMvcTest(controllers = {PedidoController.class, UsuarioController.class, ClienteController.class,
        AuditoriaController.class, ProdutoController.class})
@Import(SecurityConfig.class)
class SecurityRulesIntegrationTest {

    private static final boolean ALLOWED = true;
    private static final boolean DENIED = false;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private ClienteService clienteService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private ProdutoService produtoService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void setUp() {
        when(usuarioService.listarTodos()).thenReturn(List.of());
    }

    @ParameterizedTest(name = "{0} {1} {2} -> allowed={3}")
    @MethodSource("accessMatrix")
    @DisplayName("IT securityFilterChain when profile calls endpoint should allow or deny as the matrix says")
    void securityFilterChain_whenProfileCallsEndpoint_shouldAllowOrDenyAsMatrixSays(
            String role, HttpMethod method, String url, boolean allowed) throws Exception {
        // when
        int status = mockMvc.perform(authenticated(role, method, url)).andReturn().getResponse().getStatus();

        // then
        if (allowed) {
            assertThat(status).as("%s %s as %s", method, url, role).isNotIn(401, 403);
        } else {
            assertThat(status).as("%s %s as %s", method, url, role).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("IT securityFilterChain when request is anonymous should answer 401 on the API")
    void securityFilterChain_whenRequestIsAnonymous_shouldAnswer401OnApi() throws Exception {
        // when
        int status = mockMvc.perform(request(HttpMethod.GET, "/api/pedidos")).andReturn().getResponse().getStatus();

        // then
        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("IT securityFilterChain when state-changing request has no CSRF token should answer 403")
    void securityFilterChain_whenStateChangingRequestHasNoCsrfToken_shouldAnswer403() throws Exception {
        // when
        int status = mockMvc.perform(request(HttpMethod.POST, "/api/pedidos/o1/desconto")
                        .with(user("gerente01").roles("GERENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentual\":10}"))
                .andReturn().getResponse().getStatus();

        // then
        assertThat(status).isEqualTo(403);
    }

    private MockHttpServletRequestBuilder authenticated(String role, HttpMethod method, String url) {
        return request(method, url)
                .with(user("u-" + role.toLowerCase()).roles(role))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }

    private static Stream<Arguments> accessMatrix() {
        return Stream.of(
                // ADMIN: users and audit and read orders; never discount/cancel, never register orders
                Arguments.of("ADMIN", HttpMethod.GET, "/api/usuarios", ALLOWED),
                Arguments.of("ADMIN", HttpMethod.POST, "/api/usuarios", ALLOWED),
                Arguments.of("ADMIN", HttpMethod.GET, "/api/auditoria", ALLOWED),
                Arguments.of("ADMIN", HttpMethod.GET, "/api/pedidos", ALLOWED),
                Arguments.of("ADMIN", HttpMethod.POST, "/api/pedidos", DENIED),
                Arguments.of("ADMIN", HttpMethod.PUT, "/api/pedidos/o1/status?status=PRONTO", DENIED),
                Arguments.of("ADMIN", HttpMethod.POST, "/api/pedidos/o1/desconto", DENIED),
                Arguments.of("ADMIN", HttpMethod.POST, "/api/pedidos/o1/cancelamento", DENIED),
                Arguments.of("ADMIN", HttpMethod.GET, "/api/clientes", DENIED),
                Arguments.of("ADMIN", HttpMethod.POST, "/api/produtos", ALLOWED),

                // GERENTE: runs orders, discount, cancel, reads customers and audit; no user admin
                Arguments.of("GERENTE", HttpMethod.GET, "/api/usuarios", DENIED),
                Arguments.of("GERENTE", HttpMethod.POST, "/api/usuarios", DENIED),
                Arguments.of("GERENTE", HttpMethod.GET, "/api/auditoria", ALLOWED),
                Arguments.of("GERENTE", HttpMethod.POST, "/api/pedidos", ALLOWED),
                Arguments.of("GERENTE", HttpMethod.PUT, "/api/pedidos/o1/status?status=PRONTO", ALLOWED),
                Arguments.of("GERENTE", HttpMethod.POST, "/api/pedidos/o1/desconto", ALLOWED),
                Arguments.of("GERENTE", HttpMethod.POST, "/api/pedidos/o1/cancelamento", ALLOWED),
                Arguments.of("GERENTE", HttpMethod.GET, "/api/clientes", ALLOWED),
                Arguments.of("GERENTE", HttpMethod.POST, "/api/clientes", DENIED),
                Arguments.of("GERENTE", HttpMethod.DELETE, "/api/clientes/c1", ALLOWED),
                Arguments.of("GERENTE", HttpMethod.POST, "/api/produtos", ALLOWED),

                // ATENDENTE: registers orders, manages customers; nothing privileged
                Arguments.of("ATENDENTE", HttpMethod.GET, "/api/usuarios", DENIED),
                Arguments.of("ATENDENTE", HttpMethod.PUT, "/api/usuarios/u1", DENIED),
                Arguments.of("ATENDENTE", HttpMethod.GET, "/api/auditoria", DENIED),
                Arguments.of("ATENDENTE", HttpMethod.POST, "/api/pedidos", ALLOWED),
                Arguments.of("ATENDENTE", HttpMethod.GET, "/api/pedidos", ALLOWED),
                Arguments.of("ATENDENTE", HttpMethod.PUT, "/api/pedidos/o1/status?status=PRONTO", DENIED),
                Arguments.of("ATENDENTE", HttpMethod.POST, "/api/pedidos/o1/desconto", DENIED),
                Arguments.of("ATENDENTE", HttpMethod.POST, "/api/pedidos/o1/cancelamento", DENIED),
                Arguments.of("ATENDENTE", HttpMethod.GET, "/api/clientes", ALLOWED),
                Arguments.of("ATENDENTE", HttpMethod.POST, "/api/clientes", ALLOWED),
                Arguments.of("ATENDENTE", HttpMethod.PUT, "/api/clientes/c1", ALLOWED),
                Arguments.of("ATENDENTE", HttpMethod.DELETE, "/api/clientes/c1", DENIED),
                Arguments.of("ATENDENTE", HttpMethod.GET, "/api/produtos", ALLOWED),
                Arguments.of("ATENDENTE", HttpMethod.POST, "/api/produtos", DENIED));
    }
}
