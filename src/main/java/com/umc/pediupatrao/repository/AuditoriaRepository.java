package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import org.springframework.data.repository.Repository;

/** Somente insercao: sem delete, sem update, sem findAll exposto. A consulta fica no AuditoriaService. */
public interface AuditoriaRepository extends Repository<AuditoriaEvento, String> {

    AuditoriaEvento save(AuditoriaEvento evento);
}
