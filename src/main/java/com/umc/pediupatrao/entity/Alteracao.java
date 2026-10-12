package com.umc.pediupatrao.entity;

/** Um campo modificado, com o valor anterior e o novo (como texto). Nunca carrega credenciais. */
public record Alteracao(String campo, String anterior, String novo) {
}
