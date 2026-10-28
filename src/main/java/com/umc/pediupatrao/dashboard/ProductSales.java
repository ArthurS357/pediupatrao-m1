package com.umc.pediupatrao.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** RF04: vendas de um produto; o nome e o gravado no pedido (historico), nao o cadastro atual. */
@Schema(description = "Sales of one product")
public record ProductSales(
        @Schema(description = "Id of the product", example = "6711f0c2a1b2c3d4e5f60718",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,

        @Schema(description = "Product name as recorded in the order", example = "Calabresa",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Product category, Sem categoria for products no longer registered", example = "Pizza",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String category,

        @Schema(description = "Units sold", example = "18", requiredMode = Schema.RequiredMode.REQUIRED)
        long quantity,

        @Schema(description = "Revenue of the product, order discount applied", example = "819.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal revenue) {
}
