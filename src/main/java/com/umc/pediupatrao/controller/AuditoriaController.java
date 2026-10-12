package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta somente leitura da auditoria (ADMIN e GERENTE); nao ha endpoint de escrita ou exclusao. */
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    public List<AuditoriaEvento> consultar(
            @RequestParam(value = "entidade", required = false) EntidadeAuditada entidade,
            @RequestParam(value = "entidadeId", required = false) String entidadeId) {
        return auditoriaService.consultar(entidade, entidadeId);
    }
}
