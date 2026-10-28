package com.umc.pediupatrao.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dashboard.DashboardRepository.Scope;
import com.umc.pediupatrao.dashboard.DashboardRepository.Totals;
import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceUnitTest {

    // 2026-10-28 01:00 em Sao Paulo
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T04:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

    @Mock
    private DashboardRepository repository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Captor
    private ArgumentCaptor<Scope> scope;

    private DashboardService tested;

    @BeforeEach
    void setUp() {
        tested = new DashboardService(repository, produtoRepository, CLOCK);
    }

    // ---- periodo e escopo ----

    @Test
    @DisplayName("UT kpis() when no period is given should use the last 30 days and leave cancelled orders out")
    void kpis_whenNoPeriodIsGiven_shouldUseLast30DaysAndLeaveCancelledOrdersOut() {
        // given
        when(repository.totals(any())).thenReturn(new Totals(money("0.00"), 0));

        // when
        Kpis result = tested.kpis(filter(null, null));

        // then
        verify(repository).totals(scope.capture());
        assertThat(scope.getValue().from()).isEqualTo(TODAY.minusDays(29));
        assertThat(scope.getValue().to()).isEqualTo(TODAY);
        assertThat(scope.getValue().statuses()).doesNotContain(StatusPedido.CANCELADO).hasSize(6);
        assertThat(scope.getValue().productIds()).isNull();
        assertThat(result.from()).isEqualTo(TODAY.minusDays(29));
        verifyNoInteractions(produtoRepository);
    }

    @Test
    @DisplayName("UT kpis() when only from is given should end the period today")
    void kpis_whenOnlyFromIsGiven_shouldEndPeriodToday() {
        // given
        when(repository.totals(any())).thenReturn(new Totals(money("0.00"), 0));

        // when
        Kpis result = tested.kpis(filter(OCT_1, null));

        // then
        assertThat(result.from()).isEqualTo(OCT_1);
        assertThat(result.to()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("UT kpis() when from is after to should throw RegraDeNegocioException without querying")
    void kpis_whenFromIsAfterTo_shouldThrowWithoutQuerying() {
        // when / then
        assertThatThrownBy(() -> tested.kpis(filter(OCT_31, OCT_1)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("from");
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("UT kpis() when period has 367 days should throw RegraDeNegocioException, 366 days is accepted")
    void kpis_whenPeriodHas367Days_shouldThrow() {
        // given
        LocalDate from = LocalDate.of(2025, 1, 1);
        when(repository.totals(any())).thenReturn(new Totals(money("0.00"), 0));

        // when / then
        assertThatThrownBy(() -> tested.kpis(filter(from, from.plusDays(366))))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("366");
        assertThat(tested.kpis(filter(from, from.plusDays(365))).orders()).isZero();
    }

    @Test
    @DisplayName("UT kpis() when a date falls outside 2000..2100 should throw RegraDeNegocioException, not reach Mongo")
    void kpis_whenDateFallsOutsideSupportedYears_shouldThrowWithoutQuerying() {
        // when / then
        assertThatThrownBy(() -> tested.kpis(filter(LocalDate.of(1999, 12, 31), LocalDate.of(2000, 1, 5))))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("2000");
        assertThatThrownBy(() -> tested.kpis(filter(LocalDate.MAX.minusDays(10), LocalDate.MAX)))
                .isInstanceOf(RegraDeNegocioException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("UT kpis() when category is blank should not filter by category")
    void kpis_whenCategoryIsBlank_shouldNotFilterByCategory() {
        // given
        when(repository.totals(any())).thenReturn(new Totals(money("0.00"), 0));

        // when
        tested.kpis(new DashboardFilter(OCT_1, OCT_31, null, "   ", null));

        // then
        verify(repository).totals(scope.capture());
        assertThat(scope.getValue().productIds()).isNull();
        verifyNoInteractions(produtoRepository);
    }

    @Test
    @DisplayName("UT kpis() when status is given should count exactly that status, CANCELADO included")
    void kpis_whenStatusIsGiven_shouldCountExactlyThatStatus() {
        // given
        when(repository.totals(any())).thenReturn(new Totals(money("8.00"), 1));

        // when
        tested.kpis(new DashboardFilter(OCT_1, OCT_31, StatusPedido.CANCELADO, null, FormaPagamento.PIX));

        // then
        verify(repository).totals(scope.capture());
        assertThat(scope.getValue().statuses()).containsExactly(StatusPedido.CANCELADO);
        assertThat(scope.getValue().paymentMethod()).isEqualTo(FormaPagamento.PIX);
    }

    @Test
    @DisplayName("UT kpis() when category is given should restrict to its products ignoring case and blanks")
    void kpis_whenCategoryIsGiven_shouldRestrictToItsProductsIgnoringCase() {
        // given
        givenCatalog(produto("p1", "Pizza"), produto("p2", "Bebida"), produto("p3", " pizza "));
        when(repository.totals(any())).thenReturn(new Totals(money("0.00"), 0));

        // when
        tested.kpis(new DashboardFilter(OCT_1, OCT_31, null, "PIZZA", null));

        // then
        verify(repository).totals(scope.capture());
        assertThat(scope.getValue().productIds()).containsExactlyInAnyOrder("p1", "p3");
    }

    @Test
    @DisplayName("UT kpis() when category has no product should filter by an empty product set, matching nothing")
    void kpis_whenCategoryHasNoProduct_shouldFilterByEmptyProductSet() {
        // given
        givenCatalog(produto("p1", "Pizza"));
        when(repository.totals(any())).thenReturn(new Totals(money("0.00"), 0));

        // when
        tested.kpis(new DashboardFilter(OCT_1, OCT_31, null, "Sobremesa", null));

        // then
        verify(repository).totals(scope.capture());
        assertThat(scope.getValue().productIds()).isEmpty();
    }

    // ---- RF01 ----

    @Test
    @DisplayName("UT kpis() when there are orders should divide revenue by orders rounding HALF_UP to cents")
    void kpis_whenThereAreOrders_shouldDivideRevenueByOrdersRoundingHalfUp() {
        // given
        when(repository.totals(any())).thenReturn(new Totals(money("171.95"), 4));

        // when
        Kpis result = tested.kpis(filter(OCT_1, OCT_31));

        // then
        assertThat(result).isEqualTo(new Kpis(OCT_1, OCT_31, money("171.95"), money("42.99"), 4));
    }

    @Test
    @DisplayName("UT kpis() when there are no orders should answer a zero average ticket")
    void kpis_whenThereAreNoOrders_shouldAnswerZeroAverageTicket() {
        // given
        when(repository.totals(any())).thenReturn(new Totals(money("0.00"), 0));

        // when
        Kpis result = tested.kpis(filter(OCT_1, OCT_31));

        // then
        assertThat(result.averageTicket()).isEqualTo(money("0.00"));
        assertThat(result.orders()).isZero();
    }

    // ---- RF02 ----

    @Test
    @DisplayName("UT salesTrend() when some days have no sale should fill them with zero in date order")
    void salesTrend_whenSomeDaysHaveNoSale_shouldFillThemWithZero() {
        // given
        LocalDate oct3 = LocalDate.of(2026, 10, 3);
        when(repository.salesByPeriod(any(), eq(Granularity.DAY)))
                .thenReturn(List.of(new SalesPoint(LocalDate.of(2026, 10, 2), money("50.00"), 2)));

        // when
        List<SalesPoint> result = tested.salesTrend(filter(OCT_1, oct3), Granularity.DAY);

        // then
        assertThat(result).containsExactly(
                SalesPoint.empty(OCT_1),
                new SalesPoint(LocalDate.of(2026, 10, 2), money("50.00"), 2),
                SalesPoint.empty(oct3));
    }

    @Test
    @DisplayName("UT salesTrend() when granularity is WEEK should start buckets on the Monday before from")
    void salesTrend_whenGranularityIsWeek_shouldStartBucketsOnMonday() {
        // given
        when(repository.salesByPeriod(any(), eq(Granularity.WEEK))).thenReturn(List.of());

        // when
        List<SalesPoint> result = tested.salesTrend(filter(OCT_1, OCT_31), Granularity.WEEK);

        // then
        assertThat(result).extracting(SalesPoint::period).containsExactly(
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26));
    }

    @Test
    @DisplayName("UT salesByCategory() should sum products per category, unknown product as Sem categoria, by revenue")
    void salesByCategory_shouldSumProductsPerCategoryOrderedByRevenue() {
        // given
        givenCatalog(produto("p1", "Pizza"), produto("p2", "Bebida"), produto("p3", "Pizza"));
        when(repository.salesByProduct(any(), eq(ProductRanking.REVENUE), eq(0))).thenReturn(List.of(
                productSales("p2", 5, "40.00"),
                productSales("p1", 2, "91.00"),
                productSales("p3", 1, "40.95"),
                productSales("removed", 1, "10.00")));

        // when
        List<CategorySales> result = tested.salesByCategory(filter(OCT_1, OCT_31));

        // then
        assertThat(result).containsExactly(
                new CategorySales("Pizza", 3, money("131.95")),
                new CategorySales("Bebida", 5, money("40.00")),
                new CategorySales(DashboardService.NO_CATEGORY, 1, money("10.00")));
    }

    @Test
    @DisplayName("UT salesByCategory() should merge categories differing in case and round only after summing")
    void salesByCategory_shouldMergeCategoriesDifferingInCaseAndRoundAfterSumming() {
        // given: two order shares of 9.045 each; rounding each first would give 18.10 vs the real 18.09
        givenCatalog(produto("p1", "Esfiha"), produto("p2", "esfiha"));
        when(repository.salesByProduct(any(), eq(ProductRanking.REVENUE), eq(0))).thenReturn(List.of(
                productSales("p1", 1, "9.045"),
                productSales("p2", 1, "9.045")));

        // when
        List<CategorySales> result = tested.salesByCategory(filter(OCT_1, OCT_31));

        // then
        assertThat(result).containsExactly(new CategorySales("Esfiha", 2, money("18.09")));
    }

    // ---- RF03 ----

    @Test
    @DisplayName("UT ordersByStatus() when no status is given should list every status in flow order with zeros")
    void ordersByStatus_whenNoStatusIsGiven_shouldListEveryStatusWithZeros() {
        // given
        when(repository.countByStatus(any())).thenReturn(Map.of(StatusPedido.FINALIZADO, 2L, StatusPedido.CANCELADO, 1L));

        // when
        List<StatusCount> result = tested.ordersByStatus(filter(OCT_1, OCT_31));

        // then
        verify(repository).countByStatus(scope.capture());
        assertThat(scope.getValue().statuses()).isEqualTo(EnumSet.allOf(StatusPedido.class));
        assertThat(result).extracting(StatusCount::status).containsExactly(StatusPedido.values());
        assertThat(result).filteredOn(count -> count.count() > 0).containsExactly(
                new StatusCount(StatusPedido.FINALIZADO, 2), new StatusCount(StatusPedido.CANCELADO, 1));
    }

    // ---- RF04 ----

    @Test
    @DisplayName("UT topProducts() should pass ranking and limit through and fill each product's category")
    void topProducts_shouldPassRankingAndLimitAndFillCategory() {
        // given
        givenCatalog(produto("p1", "Pizza"));
        when(repository.salesByProduct(any(), eq(ProductRanking.REVENUE), eq(5)))
                .thenReturn(List.of(productSales("p1", 3, "131.9500"), productSales("gone", 1, "8.004")));

        // when
        List<ProductSales> result = tested.topProducts(filter(OCT_1, OCT_31), ProductRanking.REVENUE, 5);

        // then
        assertThat(result).extracting(ProductSales::category).containsExactly("Pizza", DashboardService.NO_CATEGORY);
        assertThat(result).extracting(ProductSales::revenue).containsExactly(money("131.95"), money("8.00"));
        verify(repository).salesByProduct(scope.capture(), eq(ProductRanking.REVENUE), eq(5));
        assertThat(scope.getValue().statuses()).doesNotContain(StatusPedido.CANCELADO);
    }

    private void givenCatalog(Produto... produtos) {
        when(produtoRepository.findAll()).thenReturn(List.of(produtos));
    }

    private static DashboardFilter filter(LocalDate from, LocalDate to) {
        return new DashboardFilter(from, to, null, null, null);
    }

    private static Produto produto(String id, String tipo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setTipo(tipo);
        return produto;
    }

    private static ProductSales productSales(String id, long quantity, String revenue) {
        return new ProductSales(id, "Produto " + id, null, quantity, money(revenue));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
