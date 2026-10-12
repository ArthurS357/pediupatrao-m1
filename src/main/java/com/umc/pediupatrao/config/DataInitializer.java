package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seed de usuarios. A senha de cada perfil vem de APP_SEED_{ADMIN|GERENTE|ATENDENTE}_PASSWORD;
 * sem a variavel o usuario nao e criado. Nenhuma senha fica no codigo nem no log.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final int SENHA_MIN = 8;

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder, Environment env) {
        return args -> {
            for (Perfil perfil : Perfil.values()) {
                semear(repo, encoder, perfil, env.getProperty("APP_SEED_" + perfil.name() + "_PASSWORD"));
            }
        };
    }

    private void semear(UsuarioRepository repo, PasswordEncoder encoder, Perfil perfil, String senha) {
        String username = perfil.name().toLowerCase();
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            if (existente.getRole() == null || existente.getRole().isBlank()) {
                existente.setRole(perfil.name());
                repo.save(existente);
                log.info("Usuario '{}' sem perfil recebeu {}", username, perfil);
            }
        }, () -> {
            if (senha == null || senha.isBlank()) {
                return;
            }
            if (senha.length() < SENHA_MIN) {
                log.warn("Senha de seed de '{}' tem menos de {} caracteres; usuario nao criado", username, SENHA_MIN);
                return;
            }
            Usuario novo = new Usuario();
            novo.setUsername(username);
            novo.setPassword(encoder.encode(senha));
            novo.setRole(perfil.name());
            repo.save(novo);
            log.info("Usuario '{}' criado com perfil {}", username, perfil);
        });
    }
}
