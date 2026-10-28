package com.umc.pediupatrao.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

/** Criterio do ranking de produtos mais vendidos (RF04). */
@Schema(description = "Ranking criterion: QUANTITY (units sold) or REVENUE", enumAsRef = true)
public enum ProductRanking {
    QUANTITY("quantity"), REVENUE("revenue");

    private final String field;

    ProductRanking(String field) {
        this.field = field;
    }

    /** Campo do resultado da agregacao usado na ordenacao. */
    String field() {
        return field;
    }
}
