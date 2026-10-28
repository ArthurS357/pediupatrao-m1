package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import io.swagger.v3.oas.annotations.media.Schema;

/** RF03: quantidade de pedidos em um status; todos os status aparecem, mesmo com zero. */
@Schema(description = "Number of orders in one status")
public record StatusCount(
        @Schema(description = "Order status", example = "EM_PREPARACAO", requiredMode = Schema.RequiredMode.REQUIRED)
        StatusPedido status,

        @Schema(description = "Number of orders in the status", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        long count) {
}
