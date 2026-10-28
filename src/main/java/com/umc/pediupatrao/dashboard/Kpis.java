package com.umc.pediupatrao.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/** RF01: indicadores gerais de venda no periodo consultado. */
@Schema(description = "General sales indicators for the period")
public record Kpis(
        @Schema(description = "First day of the period actually used", example = "2026-10-01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate from,

        @Schema(description = "Last day of the period actually used", example = "2026-10-31",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate to,

        @Schema(description = "Sum of the whole order totals, discounts applied (with a category filter, of the "
                + "orders holding the category, not only its items)", example = "1234.50",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal revenue,

        @Schema(description = "Revenue divided by the number of orders, 0 when there are none", example = "41.15",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal averageTicket,

        @Schema(description = "Number of orders in the period", example = "30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long orders) {
}
