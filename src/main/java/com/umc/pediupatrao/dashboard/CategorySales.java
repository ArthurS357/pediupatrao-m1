package com.umc.pediupatrao.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** RF02: vendas agrupadas pela categoria (tipo) do produto. */
@Schema(description = "Sales grouped by product category")
public record CategorySales(
        @Schema(description = "Product category, Sem categoria for products no longer registered", example = "Pizza",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String category,

        @Schema(description = "Units sold in the category", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long quantity,

        @Schema(description = "Revenue of the category items, order discount applied", example = "1890.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal revenue) {
}
