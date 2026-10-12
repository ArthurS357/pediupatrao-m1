# Requisitos técnicos e critérios de implementação

- Manter Java, Spring Boot, Spring Security, Thymeleaf e MongoDB, aproveitando as camadas existentes do projeto.
- Implementar as regras de negócio na camada de serviços e a autorização no backend; não depender somente de validação no navegador.
- Revisar `SecurityConfig`, `UsuarioDetailsService`, `Pedido`, `PedidoService`, `ClienteService` e os controladores e formulários correspondentes.
- Criar entidade, repositório e serviço próprios para auditoria.
- Revisar a segurança das rotas de cadastro e gestão de usuários e o tratamento de CSRF em formulários e operações sensíveis.
- Manter código organizado, com nomenclatura consistente e tratamento apropriado de erros e tentativas de acesso indevido.
