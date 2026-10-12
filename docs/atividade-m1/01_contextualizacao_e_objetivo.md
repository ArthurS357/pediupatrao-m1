# Atividade Prática M1 – Contextualização e situação inicial

## 1. Contextualização e objetivo

Uma pizzaria utiliza o sistema PediuPatrão para cadastrar clientes, gerenciar produtos e registrar pedidos. Entretanto, o crescimento do estabelecimento trouxe a necessidade de diferenciar as responsabilidades dos funcionários, controlar a entrada e a saída dos pedidos e identificar as alterações realizadas nas informações comerciais e cadastrais.

Você deverá evoluir individualmente o projeto fornecido, aproveitando sua arquitetura e suas tecnologias, preservando as funcionalidades existentes e desenvolvendo as regras de negócio, os mecanismos de autorização e o registro de auditoria descritos neste enunciado.

## 2. Situação inicial do projeto

O projeto disponibilizado possui organização em camadas, com entidades, repositórios, serviços e controladores, autenticação com Spring Security e persistência no MongoDB. A atividade parte das seguintes lacunas identificadas:

| Componente | Situação inicial |
|---|---|
| Usuários | Perfis ADMIN e USER; a autenticação atribui a role USER aos usuários autenticados. |
| Pedidos | Criação, listagem e atualização de status sem transições suficientemente controladas. |
| Clientes | Cadastro, consulta, alteração e exclusão. |
| Descontos | Funcionalidade não implementada. |
| Entrada e saída | Sem controle específico de horários e transições. |
| Auditoria | Não implementada. |
| Autorização | Sem diferenciação efetiva por perfil; revisar proteção das APIs e tratamento de CSRF. |
