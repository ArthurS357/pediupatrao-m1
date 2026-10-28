package com.umc.pediupatrao.dashboard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API somente leitura do dashboard (RF01-RF05). Acesso: ADMIN e GERENTE, no SecurityConfig.
 * Documentacao e restricoes de entrada ficam em {@link DashboardApi}.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;

    @GetMapping("/kpis")
    @Override
    public Kpis kpis(@ModelAttribute DashboardFilter filter) {
        return dashboardService.kpis(filter);
    }

    @GetMapping("/sales-trend")
    @Override
    public List<SalesPoint> salesTrend(@ModelAttribute DashboardFilter filter,
                                       @RequestParam(value = "granularity", defaultValue = "DAY") Granularity granularity) {
        return dashboardService.salesTrend(filter, granularity);
    }

    @GetMapping("/sales-by-category")
    @Override
    public List<CategorySales> salesByCategory(@ModelAttribute DashboardFilter filter) {
        return dashboardService.salesByCategory(filter);
    }

    @GetMapping("/orders-status")
    @Override
    public List<StatusCount> ordersStatus(@ModelAttribute DashboardFilter filter) {
        return dashboardService.ordersByStatus(filter);
    }

    @GetMapping("/top-products")
    @Override
    public List<ProductSales> topProducts(@ModelAttribute DashboardFilter filter,
                                          @RequestParam(value = "sort", defaultValue = "QUANTITY") ProductRanking sort,
                                          @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return dashboardService.topProducts(filter, sort, limit);
    }
}
