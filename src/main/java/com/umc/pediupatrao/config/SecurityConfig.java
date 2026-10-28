package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Autorizacao por perfil, aplicada no backend. As regras de acesso por rota ficam todas aqui;
 * as operacoes exclusivas do GERENTE (desconto e cancelamento) repetem a regra com
 * {@code @PreAuthorize} no service.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    /** Registro de sessoes: permite encerrar a sessao de um usuario cujo perfil mudou ou que foi excluido. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF ativo: formularios Thymeleaf recebem o token sozinhos; fetch() usa o helper do layout.
                .sessionManagement(sessao -> sessao.maximumSessions(-1)
                        .sessionRegistry(sessionRegistry()).expiredUrl("/login?expired"))
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**"))
                        .accessDeniedHandler(acessoNegado()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // Administracao de usuarios
                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole(ADMIN)

                        // Auditoria: somente consulta
                        .requestMatchers("/api/auditoria/**", "/auditoria/**").hasAnyRole(ADMIN, GERENTE)

                        // Dashboard administrativo: indicadores de venda, so leitura
                        .requestMatchers("/api/dashboard/**", "/dashboard", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").hasAnyRole(ADMIN, GERENTE)

                        // Pedidos: operacoes exclusivas do GERENTE (nem o ADMIN)
                        .requestMatchers("/api/pedidos/*/status", "/api/pedidos/*/desconto", "/api/pedidos/*/cancelamento")
                                .hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers("/pedidos/novo").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/**", "/pedidos/**")
                                .hasAnyRole(ADMIN, GERENTE, ATENDENTE)

                        // Clientes: ATENDENTE cadastra/atualiza, GERENTE consulta e exclui
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes/**").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)

                        // Produtos: todos leem, GERENTE e ADMIN alteram
                        .requestMatchers(HttpMethod.GET, "/produtos/**", "/api/produtos/**").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole(GERENTE, ADMIN)

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    /** Registra a tentativa de acesso indevido (usuario, metodo e caminho; nunca corpo ou credenciais). */
    private AccessDeniedHandler acessoNegado() {
        return (request, response, negado) -> {
            log.warn("Acesso negado: usuario={} metodo={} caminho={}",
                    request.getRemoteUser(), request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        };
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }
}
