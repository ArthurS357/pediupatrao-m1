package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.dashboard.DashboardRepository.Scope;
import com.umc.pediupatrao.dashboard.DashboardRepository.Totals;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Regras do dashboard: periodo padrao e seus limites, o que conta como venda e a resolucao de
 * categoria. As somas e contagens sao do {@link DashboardRepository}, no banco.
 *
 * <p>Venda = pedido nao cancelado; com {@code status} informado, vale exatamente esse status. Com
 * {@code category}, indicadores por pedido (KPIs, serie, status) consideram os pedidos com algum item
 * da categoria; os por item (categoria, ranking) consideram so os itens dela.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    static final int DEFAULT_PERIOD_DAYS = 30;
    static final int MAX_PERIOD_DAYS = 366;
    static final String NO_CATEGORY = "Sem categoria";
    /** Fora disso a data nao cabe na conversao para Date do MongoDB (e nao ha pedido de verdade). */
    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;

    private static final Set<StatusPedido> SALE_STATUSES =
            EnumSet.complementOf(EnumSet.of(StatusPedido.CANCELADO));

    private final DashboardRepository repository;
    private final ProdutoRepository produtoRepository;
    private final Clock clock;

    /** RF01. */
    public Kpis kpis(DashboardFilter filter) {
        Scope scope = salesScope(filter, categoriesWhenFiltered(filter));
        Totals totals = repository.totals(scope);
        BigDecimal averageTicket = totals.orders() == 0
                ? ZERO
                : totals.revenue().divide(BigDecimal.valueOf(totals.orders()), 2, RoundingMode.HALF_UP);
        return new Kpis(scope.from(), scope.to(), totals.revenue(), averageTicket, totals.orders());
    }

    /** RF02: serie continua do primeiro ao ultimo intervalo do periodo, com zero onde nao houve venda. */
    public List<SalesPoint> salesTrend(DashboardFilter filter, Granularity granularity) {
        Scope scope = salesScope(filter, categoriesWhenFiltered(filter));
        Map<LocalDate, SalesPoint> sold = repository.salesByPeriod(scope, granularity).stream()
                .collect(Collectors.toMap(SalesPoint::period, Function.identity()));
        List<SalesPoint> series = new ArrayList<>();
        for (LocalDate bucket = granularity.truncate(scope.from()); !bucket.isAfter(scope.to());
                bucket = granularity.next(bucket)) {
            series.add(sold.getOrDefault(bucket, SalesPoint.empty(bucket)));
        }
        return series;
    }

    /**
     * RF02: da maior para a menor receita. Categorias que diferem so em maiusculas sao uma so (como no
     * filtro), com o nome da primeira encontrada; a receita e arredondada depois de somada.
     */
    public List<CategorySales> salesByCategory(DashboardFilter filter) {
        Map<String, String> categories = categoriesByProductId();
        Map<String, CategorySales> byCategory = new HashMap<>();
        for (ProductSales product : repository.salesByProduct(salesScope(filter, categories), ProductRanking.REVENUE, 0)) {
            String category = categories.getOrDefault(product.productId(), NO_CATEGORY);
            byCategory.merge(category.toLowerCase(Locale.ROOT),
                    new CategorySales(category, product.quantity(), product.revenue()),
                    (a, b) -> new CategorySales(a.category(), a.quantity() + b.quantity(), a.revenue().add(b.revenue())));
        }
        return byCategory.values().stream()
                .map(sales -> new CategorySales(sales.category(), sales.quantity(), cents(sales.revenue())))
                .sorted(Comparator.comparing(CategorySales::revenue).reversed().thenComparing(CategorySales::category))
                .toList();
    }

    /** RF03: todos os status, na ordem do fluxo, inclusive CANCELADO e os zerados. */
    public List<StatusCount> ordersByStatus(DashboardFilter filter) {
        Set<StatusPedido> statuses = filter.status() == null
                ? EnumSet.allOf(StatusPedido.class)
                : EnumSet.of(filter.status());
        Map<StatusPedido, Long> counts = repository.countByStatus(scope(filter, statuses, categoriesWhenFiltered(filter)));
        return Arrays.stream(StatusPedido.values())
                .map(status -> new StatusCount(status, counts.getOrDefault(status, 0L)))
                .toList();
    }

    /** RF04. */
    public List<ProductSales> topProducts(DashboardFilter filter, ProductRanking ranking, int limit) {
        Map<String, String> categories = categoriesByProductId();
        return repository.salesByProduct(salesScope(filter, categories), ranking, limit).stream()
                .map(product -> new ProductSales(product.productId(), product.name(),
                        categories.getOrDefault(product.productId(), NO_CATEGORY), product.quantity(),
                        cents(product.revenue())))
                .toList();
    }

    private Scope salesScope(DashboardFilter filter, Map<String, String> categories) {
        Set<StatusPedido> statuses = filter.status() == null ? SALE_STATUSES : EnumSet.of(filter.status());
        return scope(filter, statuses, categories);
    }

    private Scope scope(DashboardFilter filter, Set<StatusPedido> statuses, Map<String, String> categories) {
        LocalDate to = filter.to() != null ? filter.to() : LocalDate.now(clock);
        LocalDate from = filter.from() != null ? filter.from() : to.minusDays(DEFAULT_PERIOD_DAYS - 1);
        if (from.isAfter(to)) {
            throw new RegraDeNegocioException("A data inicial (from) não pode ser posterior à data final (to).");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
            throw new RegraDeNegocioException(
                    "O período consultado deve ter no máximo %d dias.".formatted(MAX_PERIOD_DAYS));
        }
        if (from.getYear() < MIN_YEAR || to.getYear() > MAX_YEAR) {
            throw new RegraDeNegocioException(
                    "O período consultado deve estar entre %d e %d.".formatted(MIN_YEAR, MAX_YEAR));
        }
        String category = category(filter);
        Set<String> productIds = category == null ? null : categories.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(category))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return new Scope(from, to, statuses, filter.paymentMethod(), productIds);
    }

    private Map<String, String> categoriesWhenFiltered(DashboardFilter filter) {
        return category(filter) == null ? Map.of() : categoriesByProductId();
    }

    /** Categoria vazia ou so com espacos vale como "todas". */
    private static String category(DashboardFilter filter) {
        return filter.category() == null || filter.category().isBlank() ? null : filter.category().strip();
    }

    private static BigDecimal cents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Categoria (tipo) de cada produto cadastrado. O item do pedido nao guarda a categoria, entao ela
     * e resolvida pelo cadastro atual.
     * ponytail: le o catalogo inteiro por consulta (dezenas de produtos); se crescer, gravar a
     * categoria no ItemPedido ao criar o pedido e agrupar direto no pipeline.
     */
    private Map<String, String> categoriesByProductId() {
        Map<String, String> categories = new HashMap<>();
        for (Produto produto : produtoRepository.findAll()) {
            String tipo = produto.getTipo();
            categories.put(produto.getId(), tipo == null || tipo.isBlank() ? NO_CATEGORY : tipo.strip());
        }
        return categories;
    }
}
