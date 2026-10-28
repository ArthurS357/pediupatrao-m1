package com.umc.pediupatrao.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The dashboard aggregations against a real MongoDB, through the whole HTTP stack, with a controlled set
 * of orders whose expected numbers are worked out in the comments. Opt-in, so the default build keeps
 * running without MongoDB: {@code ./mvnw test -Ddashboard.it=true} with SPRING_DATA_MONGODB_URI set. It
 * uses a database of its own and writes and removes only its own ids.
 *
 * <p>October 2026, sales (not cancelled): o1 99.00 + o2 40.95 + o4 24.00 + o6 8.00 = 171.95 in 4 orders.
 * Pizza = 2x45.50 (o1) + 45.50 x 0.9 (o2) = 131.95 in 3 units; Bebida = 8 + 24 + 8 = 40.00 in 5 units.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.data.mongodb.database=" + DashboardMongoIntegrationTest.DATABASE)
@EnabledIfSystemProperty(named = "dashboard.it", matches = "true")
@EnabledIfEnvironmentVariable(named = "SPRING_DATA_MONGODB_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardMongoIntegrationTest {

    static final String DATABASE = "pizzaria_dashboard_it";

    private static final String PIZZA_ID = "66aa00000000000000000001";
    private static final String BEBIDA_ID = "66aa00000000000000000002";
    private static final String ESFIHA_ID = "66aa00000000000000000003";
    private static final List<String> ORDER_IDS = List.of("it-dash-o1", "it-dash-o2", "it-dash-o3", "it-dash-o4",
            "it-dash-o5", "it-dash-o6", "it-dash-d1", "it-dash-d2");
    private static final String OCTOBER = "from=2026-10-01&to=2026-10-31";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeAll
    void seed() {
        assertThat(mongoTemplate.getDb().getName()).as("never seed outside the test database").isEqualTo(DATABASE);
        removeSeed();
        mongoTemplate.insert(produto(PIZZA_ID, "Calabresa", "Pizza"));
        mongoTemplate.insert(produto(BEBIDA_ID, "Guarana", "Bebida"));
        mongoTemplate.insert(pedido("it-dash-o1", at(5, 12, 0), FormaPagamento.PIX, StatusPedido.FINALIZADO, "0",
                "99.00", calabresa(2), guarana(1)));
        mongoTemplate.insert(pedido("it-dash-o2", at(5, 20, 0), FormaPagamento.DINHEIRO, StatusPedido.EM_PREPARACAO,
                "10", "40.95", calabresa(1)));
        mongoTemplate.insert(pedido("it-dash-o3", at(7, 19, 0), FormaPagamento.PIX, StatusPedido.CANCELADO, "0",
                "8.00", guarana(1)));
        mongoTemplate.insert(pedido("it-dash-o4", at(20, 18, 30), FormaPagamento.CARTAO_CREDITO, StatusPedido.RECEBIDO,
                "0", "24.00", guarana(3)));
        // outside the period: 2 November
        mongoTemplate.insert(pedido("it-dash-o5", LocalDateTime.of(2026, 11, 2, 12, 0), FormaPagamento.PIX,
                StatusPedido.FINALIZADO, "0", "45.50", calabresa(1)));
        // last day, late at night: must still count as 31 October
        mongoTemplate.insert(pedido("it-dash-o6", at(31, 23, 30), FormaPagamento.PIX, StatusPedido.FINALIZADO, "0",
                "8.00", guarana(1)));
        // December, rounding drift: 10.05 x 0.9 = 9.045, stored by PedidoService as 9.05 per order
        mongoTemplate.insert(produto(ESFIHA_ID, "Esfiha de carne", "Esfiha"));
        ItemPedido esfiha = new ItemPedido(ESFIHA_ID, "Esfiha de carne", new BigDecimal("10.05"), 1);
        for (String id : List.of("it-dash-d1", "it-dash-d2")) {
            mongoTemplate.insert(pedido(id, LocalDateTime.of(2026, 12, 10, 19, 0), FormaPagamento.PIX,
                    StatusPedido.FINALIZADO, "10", "9.05", esfiha));
        }
    }

    @AfterAll
    void removeSeed() {
        mongoTemplate.remove(Query.query(Criteria.where("_id").in(ORDER_IDS)), Pedido.class);
        mongoTemplate.remove(Query.query(Criteria.where("_id").in(PIZZA_ID, BEBIDA_ID, ESFIHA_ID)), Produto.class);
    }

    @Test
    @DisplayName("IT salesByCategory() and topProducts() when discounts round should still add up to the KPIs (18.10)")
    void salesByCategoryAndTopProducts_whenDiscountsRound_shouldStillAddUpToKpis() throws Exception {
        String december = "from=2026-12-01&to=2026-12-31";
        mockMvc.perform(asGerente("/api/dashboard/kpis?" + december))
                .andExpect(jsonPath("$.revenue").value(18.10));
        mockMvc.perform(asGerente("/api/dashboard/sales-by-category?" + december))
                .andExpect(jsonPath("$[0].category").value("Esfiha"))
                .andExpect(jsonPath("$[0].revenue").value(18.10));
        mockMvc.perform(asGerente("/api/dashboard/top-products?" + december))
                .andExpect(jsonPath("$[0].revenue").value(18.10));
    }

    @Test
    @DisplayName("IT kpis() when period is October should sum non-cancelled orders inside the period only")
    void kpis_whenPeriodIsOctober_shouldSumNonCancelledOrdersInsidePeriodOnly() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/kpis?" + OCTOBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(171.95))
                .andExpect(jsonPath("$.orders").value(4))
                .andExpect(jsonPath("$.averageTicket").value(42.99));
    }

    @Test
    @DisplayName("IT kpis() when paymentMethod is PIX should keep only PIX sales (o1 and o6)")
    void kpis_whenPaymentMethodIsPix_shouldKeepOnlyPixSales() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/kpis?" + OCTOBER + "&paymentMethod=PIX"))
                .andExpect(jsonPath("$.revenue").value(107.00))
                .andExpect(jsonPath("$.orders").value(2));
    }

    @Test
    @DisplayName("IT kpis() when category is Bebida should count orders holding a drink (o1, o4 and o6)")
    void kpis_whenCategoryIsBebida_shouldCountOrdersHoldingDrink() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/kpis?" + OCTOBER + "&category=bebida"))
                .andExpect(jsonPath("$.revenue").value(131.00))
                .andExpect(jsonPath("$.orders").value(3));
    }

    @Test
    @DisplayName("IT kpis() when status is CANCELADO should count the cancelled order only")
    void kpis_whenStatusIsCancelado_shouldCountCancelledOrderOnly() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/kpis?" + OCTOBER + "&status=CANCELADO"))
                .andExpect(jsonPath("$.revenue").value(8.00))
                .andExpect(jsonPath("$.orders").value(1));
    }

    @Test
    @DisplayName("IT salesTrend() by DAY should give 31 points with sales on the 5th, 20th and 31st")
    void salesTrend_byDay_shouldGive31PointsWithSalesOnTheRightDays() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/sales-trend?" + OCTOBER + "&granularity=DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(31)))
                .andExpect(jsonPath("$[4].period").value("2026-10-05"))
                .andExpect(jsonPath("$[4].revenue").value(139.95))
                .andExpect(jsonPath("$[4].orders").value(2))
                .andExpect(jsonPath("$[6].orders").value(0))
                .andExpect(jsonPath("$[19].revenue").value(24.00))
                .andExpect(jsonPath("$[30].period").value("2026-10-31"))
                .andExpect(jsonPath("$[30].revenue").value(8.00));
    }

    @Test
    @DisplayName("IT salesTrend() by WEEK should bucket on Mondays, the first one before the period starts")
    void salesTrend_byWeek_shouldBucketOnMondays() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/sales-trend?" + OCTOBER + "&granularity=WEEK"))
                .andExpect(jsonPath("$[*].period").value(
                        contains("2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26")))
                .andExpect(jsonPath("$[*].orders").value(contains(0, 2, 0, 1, 1)));
    }

    @Test
    @DisplayName("IT salesTrend() by MONTH should give one bucket equal to the KPIs")
    void salesTrend_byMonth_shouldGiveOneBucketEqualToKpis() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/sales-trend?" + OCTOBER + "&granularity=MONTH"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].revenue").value(171.95))
                .andExpect(jsonPath("$[0].orders").value(4));
    }

    @Test
    @DisplayName("IT salesByCategory() should split the revenue by category, discount applied, adding up to the KPIs")
    void salesByCategory_shouldSplitRevenueByCategoryAddingUpToKpis() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/sales-by-category?" + OCTOBER))
                .andExpect(jsonPath("$[*].category").value(contains("Pizza", "Bebida")))
                .andExpect(jsonPath("$[0].quantity").value(3))
                .andExpect(jsonPath("$[0].revenue").value(131.95))
                .andExpect(jsonPath("$[1].quantity").value(5))
                .andExpect(jsonPath("$[1].revenue").value(40.00));
    }

    @Test
    @DisplayName("IT ordersStatus() should count every order of the period, cancelled included")
    void ordersStatus_shouldCountEveryOrderOfPeriodCancelledIncluded() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/orders-status?" + OCTOBER))
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[*].count").value(contains(1, 1, 0, 0, 0, 2, 1)));
    }

    @Test
    @DisplayName("IT topProducts() should rank by quantity by default and by revenue on request")
    void topProducts_shouldRankByQuantityByDefaultAndByRevenueOnRequest() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/top-products?" + OCTOBER))
                .andExpect(jsonPath("$[*].name").value(contains("Guarana", "Calabresa")))
                .andExpect(jsonPath("$[0].category").value("Bebida"))
                .andExpect(jsonPath("$[0].quantity").value(5));
        mockMvc.perform(asGerente("/api/dashboard/top-products?" + OCTOBER + "&sort=REVENUE&limit=1"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Calabresa"))
                .andExpect(jsonPath("$[0].revenue").value(131.95));
    }

    @Test
    @DisplayName("IT topProducts() when category is Pizza should leave the drinks out")
    void topProducts_whenCategoryIsPizza_shouldLeaveDrinksOut() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/top-products?" + OCTOBER + "&category=Pizza"))
                .andExpect(jsonPath("$[*].name").value(contains("Calabresa")));
    }

    @Test
    @DisplayName("IT kpis() when the period has no order should answer zeros, not an error")
    void kpis_whenPeriodHasNoOrder_shouldAnswerZeros() throws Exception {
        mockMvc.perform(asGerente("/api/dashboard/kpis?from=2020-01-01&to=2020-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(0))
                .andExpect(jsonPath("$.orders").value(0))
                .andExpect(jsonPath("$.averageTicket").value(0));
    }

    private MockHttpServletRequestBuilder asGerente(String url) {
        return get(url).with(user("gerente01").roles("GERENTE"));
    }

    private static LocalDateTime at(int october, int hour, int minute) {
        return LocalDateTime.of(2026, 10, october, hour, minute);
    }

    private static ItemPedido calabresa(int quantidade) {
        return new ItemPedido(PIZZA_ID, "Calabresa", new BigDecimal("45.50"), quantidade);
    }

    private static ItemPedido guarana(int quantidade) {
        return new ItemPedido(BEBIDA_ID, "Guarana", new BigDecimal("8.00"), quantidade);
    }

    private static Pedido pedido(String id, LocalDateTime entrada, FormaPagamento forma, StatusPedido status,
            String desconto, String total, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setClienteId("it-dash-cliente");
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        pedido.setFormaPagamento(forma);
        pedido.setItens(List.of(itens));
        pedido.setDescontoPercentual(new BigDecimal(desconto));
        pedido.setValorTotal(new BigDecimal(total));
        pedido.setStatus(status);
        pedido.setCriadoPor("it");
        pedido.setDataHoraEntrada(entrada);
        return pedido;
    }

    private static Produto produto(String id, String nome, String tipo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(45.50);
        produto.setAtivo(true);
        return produto;
    }
}
