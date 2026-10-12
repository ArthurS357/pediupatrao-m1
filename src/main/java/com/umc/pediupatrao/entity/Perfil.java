package com.umc.pediupatrao.entity;

import com.umc.pediupatrao.exception.RegraDeNegocioException;
import java.util.Arrays;
import java.util.Optional;

/** Perfis de acesso; o nome da constante e a role do Spring Security (sem prefixo ROLE_). */
public enum Perfil {
    ADMIN, GERENTE, ATENDENTE;

    /** Converte a role persistida; nula, vazia ou legada (ex.: USER) vira ATENDENTE, o menor privilegio. */
    public static Perfil deRole(String role) {
        return procurar(role).orElse(ATENDENTE);
    }

    /** Converte a role vinda de formulario ou API, rejeitando valores desconhecidos. */
    public static Perfil exigir(String role) {
        return procurar(role).orElseThrow(() ->
                new RegraDeNegocioException("Perfil inválido: use ADMIN, GERENTE ou ATENDENTE."));
    }

    private static Optional<Perfil> procurar(String role) {
        return Arrays.stream(values()).filter(p -> p.name().equals(role)).findFirst();
    }
}
