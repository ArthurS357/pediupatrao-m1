package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Evento de auditoria imutavel: so e criado, nunca alterado nem removido pela aplicacao. */
@Document(collection = "auditoria")
public record AuditoriaEvento(
        @Id String id,
        LocalDateTime dataHora,
        String usuario,
        String perfil,
        Operacao operacao,
        EntidadeAuditada entidade,
        String entidadeId,
        List<Alteracao> alteracoes,
        String justificativa) {
}
