package com.umc.pediupatrao.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/** RF02: um ponto da serie temporal de vendas; periodos sem venda aparecem com zero. */
@Schema(description = "Sales of one period of the time series")
public record SalesPoint(
        @Schema(description = "First day of the bucket (day, Monday of the week or first day of the month)",
                example = "2026-10-06", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate period,

        @Schema(description = "Revenue of the bucket, discounts applied", example = "350.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal revenue,

        @Schema(description = "Number of orders in the bucket", example = "8",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long orders) {

    static SalesPoint empty(LocalDate period) {
        return new SalesPoint(period, DashboardService.ZERO, 0);
    }
}
