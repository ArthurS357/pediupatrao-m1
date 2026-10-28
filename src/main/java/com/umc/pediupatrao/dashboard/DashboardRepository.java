package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

/**
 * Agregacoes do dashboard sobre a colecao {@code pedidos}, todas executadas no MongoDB (sem N+1 e sem
 * carregar pedidos em memoria). O primeiro estagio ({@code $match}) passa pelo mapeamento tipado do
 * Spring Data, que converte datas e enums como na gravacao; os demais sao o pipeline literal.
 */
@Repository
@RequiredArgsConstructor
class DashboardRepository {

    /**
     * Spring Data grava LocalDateTime como Date usando o fuso padrao da JVM; agrupar no mesmo fuso
     * devolve o dia "de parede" gravado (America/Sao_Paulo, via Clock da aplicacao).
     */
    private static final ZoneId STORAGE_ZONE = ZoneId.systemDefault();
    private static final Decimal128 DECIMAL_ZERO = Decimal128.parse("0");

    private final MongoTemplate mongoTemplate;

    /**
     * Recorte de pedidos de uma consulta.
     *
     * @param statuses   status aceitos; nunca vazio (status legados de texto livre ficam de fora)
     * @param productIds produtos da categoria filtrada, ou {@code null} sem filtro de categoria
     */
    record Scope(LocalDate from, LocalDate to, Set<StatusPedido> statuses, FormaPagamento paymentMethod,
            Set<String> productIds) {
    }

    record Totals(BigDecimal revenue, long orders) {
    }

    Totals totals(Scope scope) {
        List<Document> rows = aggregate(scope,
                stage("$group", new Document("_id", null)
                        .append("revenue", sum(decimal("$valorTotal")))
                        .append("orders", sum(1))));
        return rows.isEmpty()
                ? new Totals(money(null), 0)
                : new Totals(money(rows.getFirst().get("revenue")), count(rows.getFirst().get("orders")));
    }

    /** Vendas por intervalo; so os intervalos com pedido aparecem (quem completa a serie e o service). */
    List<SalesPoint> salesByPeriod(Scope scope, Granularity granularity) {
        Document trunc = new Document("date", "$dataHoraEntrada")
                .append("unit", granularity.mongoUnit())
                .append("timezone", STORAGE_ZONE.getId());
        if (granularity == Granularity.WEEK) {
            trunc.append("startOfWeek", "monday");
        }
        return aggregate(scope,
                stage("$group", new Document("_id", new Document("$dateTrunc", trunc))
                        .append("revenue", sum(decimal("$valorTotal")))
                        .append("orders", sum(1))),
                stage("$sort", new Document("_id", 1)))
                .stream()
                .map(row -> new SalesPoint(row.get("_id", Date.class).toInstant().atZone(STORAGE_ZONE).toLocalDate(),
                        money(row.get("revenue")), count(row.get("orders"))))
                .toList();
    }

    /**
     * Vendas por produto, ordenadas pelo criterio (desempate pelo id) e limitadas a {@code limit}
     * (0 = todos). A receita do item e a fatia do {@code valorTotal} gravado proporcional ao seu valor
     * bruto: os itens de um pedido somam exatamente o total dele, entao categorias e ranking batem com o
     * faturamento mesmo com o arredondamento do desconto. A receita volta sem arredondar (quem arredonda
     * e o service, depois de somar); a categoria fica {@code null}, o item do pedido nao a guarda.
     * O nome e o de um dos pedidos do periodo ({@code $first}): se o produto foi renomeado, qualquer um.
     */
    List<ProductSales> salesByProduct(Scope scope, ProductRanking ranking, int limit) {
        List<AggregationOperation> stages = new ArrayList<>();
        stages.add(stage("$addFields", new Document("_bruto", new Document("$sum", new Document("$map",
                new Document("input", "$itens").append("as", "i").append("in",
                        new Document("$multiply", List.of(decimal("$$i.precoUnitario"), "$$i.quantidade"))))))));
        stages.add(stage("$unwind", new Document("path", "$itens")));
        if (scope.productIds() != null) {
            stages.add(stage("$match", new Document("itens.produtoId", new Document("$in", List.copyOf(scope.productIds())))));
        }
        stages.add(stage("$group", new Document("_id", "$itens.produtoId")
                .append("name", new Document("$first", "$itens.nome"))
                .append("quantity", sum("$itens.quantidade"))
                .append("revenue", sum(itemRevenue()))));
        stages.add(stage("$sort", new Document(ranking.field(), -1).append("name", 1).append("_id", 1)));
        if (limit > 0) {
            stages.add(stage("$limit", limit));
        }
        return aggregate(scope, stages.toArray(AggregationOperation[]::new)).stream()
                .map(row -> new ProductSales(row.getString("_id"), row.getString("name"), null,
                        count(row.get("quantity")), decimalValue(row.get("revenue"))))
                .toList();
    }

    Map<StatusPedido, Long> countByStatus(Scope scope) {
        Map<StatusPedido, Long> counts = new EnumMap<>(StatusPedido.class);
        aggregate(scope, stage("$group", new Document("_id", "$status").append("count", sum(1))))
                .forEach(row -> counts.put(StatusPedido.valueOf(row.getString("_id")), count(row.get("count"))));
        return counts;
    }

    private List<Document> aggregate(Scope scope, AggregationOperation... stages) {
        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(criteria(scope)));
        pipeline.addAll(Arrays.asList(stages));
        return mongoTemplate.aggregate(Aggregation.newAggregation(Pedido.class, pipeline), Document.class)
                .getMappedResults();
    }

    private static Criteria criteria(Scope scope) {
        Criteria criteria = Criteria.where("dataHoraEntrada")
                .gte(scope.from().atStartOfDay())
                .lt(scope.to().plusDays(1).atStartOfDay())
                .and("status").in(names(scope.statuses()));
        if (scope.paymentMethod() != null) {
            criteria.and("formaPagamento").is(scope.paymentMethod().name());
        }
        if (scope.productIds() != null) {
            criteria.and("itens.produtoId").in(scope.productIds());
        }
        return criteria;
    }

    /** valorTotal x (preco x quantidade) / bruto do pedido; pedido sem valor bruto conta zero. */
    private static Document itemRevenue() {
        Document share = new Document("$divide", List.of(
                new Document("$multiply", List.of(decimal("$valorTotal"), decimal("$itens.precoUnitario"),
                        "$itens.quantidade")),
                "$_bruto"));
        return new Document("$cond", List.of(new Document("$eq", List.of("$_bruto", 0)), DECIMAL_ZERO, share));
    }

    /** BigDecimal e gravado como texto pelo Spring Data; documentos sem o campo ou invalidos contam zero. */
    private static Document decimal(String field) {
        return new Document("$convert", new Document("input", field)
                .append("to", "decimal")
                .append("onError", DECIMAL_ZERO)
                .append("onNull", DECIMAL_ZERO));
    }

    private static Document sum(Object expression) {
        return new Document("$sum", expression);
    }

    private static AggregationOperation stage(String operator, Object body) {
        return context -> new Document(operator, body);
    }

    private static List<String> names(Collection<StatusPedido> statuses) {
        return statuses.stream().map(Enum::name).toList();
    }

    private static BigDecimal money(Object value) {
        return decimalValue(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimalValue(Object value) {
        return switch (value) {
            case null -> BigDecimal.ZERO;
            case Decimal128 decimal -> decimal.bigDecimalValue();
            case Number number -> new BigDecimal(number.toString());
            default -> throw new IllegalStateException("Unexpected monetary value: " + value.getClass());
        };
    }

    private static long count(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
