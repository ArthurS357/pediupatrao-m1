package com.umc.pediupatrao.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HTTP contract of the dashboard API: binding of the filters, validation and errors as ProblemDetail. */
@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerIntegrationTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    @DisplayName("IT kpis() when every filter is sent should bind them all and answer the indicators")
    void kpis_whenEveryFilterIsSent_shouldBindThemAllAndAnswerIndicators() throws Exception {
        // given
        DashboardFilter expected = new DashboardFilter(OCT_1, OCT_31, StatusPedido.FINALIZADO, "Pizza", FormaPagamento.PIX);
        when(dashboardService.kpis(expected))
                .thenReturn(new Kpis(OCT_1, OCT_31, new BigDecimal("171.95"), new BigDecimal("42.99"), 4));

        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/kpis")
                        .param("from", "2026-10-01").param("to", "2026-10-31").param("status", "FINALIZADO")
                        .param("category", "Pizza").param("paymentMethod", "PIX")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-01"))
                .andExpect(jsonPath("$.revenue").value(171.95))
                .andExpect(jsonPath("$.averageTicket").value(42.99))
                .andExpect(jsonPath("$.orders").value(4));
    }

    @Test
    @DisplayName("IT salesTrend() when granularity is absent should default to DAY")
    void salesTrend_whenGranularityIsAbsent_shouldDefaultToDay() throws Exception {
        // given
        when(dashboardService.salesTrend(any(), eq(Granularity.DAY))).thenReturn(List.of());

        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/sales-trend"))).andExpect(status().isOk());
        verify(dashboardService).salesTrend(new DashboardFilter(null, null, null, null, null), Granularity.DAY);
    }

    @Test
    @DisplayName("IT topProducts() when sort and limit are absent should rank by QUANTITY with 10 products")
    void topProducts_whenSortAndLimitAreAbsent_shouldRankByQuantityWith10Products() throws Exception {
        // given
        when(dashboardService.topProducts(any(), eq(ProductRanking.QUANTITY), eq(10))).thenReturn(List.of());

        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/top-products"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("IT topProducts() when limit is 51 should answer 400 ProblemDetail naming limit, without calling the service")
    void topProducts_whenLimitIs51_shouldAnswer400NamingLimitWithoutCallingService() throws Exception {
        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/top-products").param("limit", "51")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("limit"))
                .andExpect(jsonPath("$.errors[0].message").value("limit must be at most 50"));
        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("IT kpis() when category looks like a Mongo operator should hand it to the service as plain text")
    void kpis_whenCategoryLooksLikeMongoOperator_shouldHandItToServiceAsPlainText() throws Exception {
        // given
        DashboardFilter expected = new DashboardFilter(null, null, null, "{\"$ne\":null}", null);
        when(dashboardService.kpis(expected)).thenReturn(new Kpis(OCT_1, OCT_31, BigDecimal.ZERO, BigDecimal.ZERO, 0));

        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/kpis").param("category", "{\"$ne\":null}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").value(0));
    }

    @Test
    @DisplayName("IT topProducts() when category is too long should answer 400 naming category, not the Java parameter")
    void topProducts_whenCategoryIsTooLong_shouldAnswer400NamingCategory() throws Exception {
        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/top-products").param("category", "x".repeat(51))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"))
                .andExpect(jsonPath("$.errors[0].message").value("category must be at most 50 characters"));
        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("IT kpis() when from is not an ISO date should answer 400 naming the field, no Java type in the message")
    void kpis_whenFromIsNotIsoDate_shouldAnswer400NamingFieldWithoutJavaTypes() throws Exception {
        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/kpis").param("from", "01/10/2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].message").value("from has an invalid value: 01/10/2026"));
        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("IT ordersStatus() when status is unknown should answer 400")
    void ordersStatus_whenStatusIsUnknown_shouldAnswer400() throws Exception {
        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/orders-status").param("status", "ENTREGUE")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("IT salesTrend() when granularity is unknown should answer 400 ProblemDetail")
    void salesTrend_whenGranularityIsUnknown_shouldAnswer400() throws Exception {
        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/sales-trend").param("granularity", "YEAR")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("IT kpis() when the service rejects the period should answer 422 with its message")
    void kpis_whenServiceRejectsPeriod_shouldAnswer422WithItsMessage() throws Exception {
        // given
        when(dashboardService.kpis(any())).thenThrow(new RegraDeNegocioException("O período consultado deve ter no máximo 366 dias."));

        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/kpis").param("from", "2024-01-01").param("to", "2026-01-01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O período consultado deve ter no máximo 366 dias."));
    }

    @Test
    @DisplayName("IT kpis() when the service fails unexpectedly should answer 500 with a fixed sentence, no cause")
    void kpis_whenServiceFailsUnexpectedly_shouldAnswer500WithFixedSentence() throws Exception {
        // given
        when(dashboardService.kpis(any())).thenThrow(new IllegalStateException("Mongo timeout at cluster0-shard-00"));

        // when / then
        mockMvc.perform(asGerente(get("/api/dashboard/kpis")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("The operation could not be completed"));
    }

    @Test
    @DisplayName("IT kpis() when caller is ATENDENTE should answer 403 without calling the service")
    void kpis_whenCallerIsAtendente_shouldAnswer403() throws Exception {
        // when / then
        mockMvc.perform(get("/api/dashboard/kpis").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("IT kpis() when caller is anonymous should answer 401")
    void kpis_whenCallerIsAnonymous_shouldAnswer401() throws Exception {
        // when / then
        mockMvc.perform(get("/api/dashboard/kpis")).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder asGerente(MockHttpServletRequestBuilder request) {
        return request.with(user("gerente01").roles("GERENTE"));
    }
}
