package com.umc.pediupatrao.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Tamanho do intervalo da serie temporal (RF02); a semana comeca na segunda-feira, como no MongoDB. */
@Schema(description = "Time series bucket: DAY, WEEK (starting on Monday) or MONTH", enumAsRef = true)
public enum Granularity {
    DAY("day"), WEEK("week"), MONTH("month");

    private final String mongoUnit;

    Granularity(String mongoUnit) {
        this.mongoUnit = mongoUnit;
    }

    /** Unidade do operador {@code $dateTrunc}. */
    String mongoUnit() {
        return mongoUnit;
    }

    /** Primeiro dia do intervalo que contem {@code date}. */
    LocalDate truncate(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    LocalDate next(LocalDate bucket) {
        return switch (this) {
            case DAY -> bucket.plusDays(1);
            case WEEK -> bucket.plusWeeks(1);
            case MONTH -> bucket.plusMonths(1);
        };
    }
}
