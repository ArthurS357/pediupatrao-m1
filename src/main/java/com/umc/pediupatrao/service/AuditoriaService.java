package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Alteracao;
import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.Operacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Registro e consulta da auditoria. A escrita e so insercao; a consulta e restrita a ADMIN e GERENTE
 * e mascara telefones. Campos que parecam credenciais nunca sao gravados.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final int LIMITE_CONSULTA = 200;
    private static final Pattern CAMPO_CREDENCIAL = Pattern.compile("(?i)senha|password|token|secret");

    private final AuditoriaRepository repository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    /**
     * Registra a diferenca entre {@code antes} e {@code depois}. Para criacao passe {@code antes} vazio;
     * para exclusao, {@code depois} vazio. Sem diferenca, nada e gravado.
     */
    public void registrar(Ator ator, Operacao operacao, EntidadeAuditada entidade, String entidadeId,
            Map<String, String> antes, Map<String, String> depois, String justificativa) {
        List<Alteracao> alteracoes = diferencas(antes, depois);
        if (alteracoes.isEmpty()) {
            return;
        }
        repository.save(new AuditoriaEvento(null, LocalDateTime.now(clock), ator.username(), ator.perfil(),
                operacao, entidade, entidadeId, alteracoes, justificativa));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<AuditoriaEvento> consultar(EntidadeAuditada entidade, String entidadeId) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "dataHora")).limit(LIMITE_CONSULTA);
        if (entidade != null) {
            query.addCriteria(Criteria.where("entidade").is(entidade));
        }
        if (entidadeId != null && !entidadeId.isBlank()) {
            query.addCriteria(Criteria.where("entidadeId").is(entidadeId));
        }
        return mongoTemplate.find(query, AuditoriaEvento.class).stream().map(AuditoriaService::mascarado).toList();
    }

    /** Monta o mapa campo-valor (texto) usado em {@code registrar}; pares nome, valor, nome, valor... */
    public static Map<String, String> campos(Object... paresNomeValor) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (int i = 0; i < paresNomeValor.length; i += 2) {
            Object valor = paresNomeValor[i + 1];
            campos.put(String.valueOf(paresNomeValor[i]), valor == null ? null : String.valueOf(valor));
        }
        return campos;
    }

    static List<Alteracao> diferencas(Map<String, String> antes, Map<String, String> depois) {
        Set<String> nomes = new LinkedHashSet<>(antes.keySet());
        nomes.addAll(depois.keySet());
        List<Alteracao> alteracoes = new ArrayList<>();
        for (String nome : nomes) {
            boolean credencial = CAMPO_CREDENCIAL.matcher(nome).find();
            if (!credencial && !Objects.equals(antes.get(nome), depois.get(nome))) {
                alteracoes.add(new Alteracao(nome, antes.get(nome), depois.get(nome)));
            }
        }
        return alteracoes;
    }

    private static AuditoriaEvento mascarado(AuditoriaEvento e) {
        List<Alteracao> alteracoes = e.alteracoes().stream()
                .map(a -> "telefone".equals(a.campo())
                        ? new Alteracao(a.campo(), mascarar(a.anterior()), mascarar(a.novo()))
                        : a)
                .toList();
        return new AuditoriaEvento(e.id(), e.dataHora(), e.usuario(), e.perfil(), e.operacao(), e.entidade(),
                e.entidadeId(), alteracoes, e.justificativa());
    }

    /** Mantem so os 4 ultimos caracteres do telefone. */
    static String mascarar(String telefone) {
        if (telefone == null || telefone.length() <= 4) {
            return telefone;
        }
        return "*".repeat(telefone.length() - 4) + telefone.substring(telefone.length() - 4);
    }
}
