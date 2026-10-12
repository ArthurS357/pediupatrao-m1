# Requisitos funcionais

## RF01 — Controle de usuários e permissões (2,0 pontos)

Adequar o cadastro, a autenticação e a autorização para três perfis de acesso:

| Perfil | Responsabilidades |
|---|---|
| ADMIN | Administrar usuários, atribuir perfis, consultar pedidos e visualizar a auditoria. |
| GERENTE | Gerenciar pedidos, aplicar descontos, cancelar pedidos, consultar clientes e visualizar a auditoria. |
| ATENDENTE | Cadastrar e atualizar clientes, registrar pedidos e acompanhar seu andamento. |

Somente o GERENTE poderá aplicar descontos ou cancelar pedidos, inclusive quando comparado ao ADMIN. A proteção deve ser aplicada no backend por meio do Spring Security; ocultar botões não é suficiente. Restringir o cadastro e a alteração de perfis para impedir a atribuição indevida de privilégios.

## RF02 — Evolução do cadastro de pedidos (1,0 ponto)

Complementar a entidade Pedido e as operações correspondentes para registrar itens, quantidades, preços, descontos, valor total, responsável pelo registro e data/hora de entrada. O valor final deve ser calculado no servidor com base nos itens e nos descontos autorizados; requisições HTTP não poderão definir arbitrariamente esse total.

## RF03 — Controle de entrada, saída e transições dos pedidos (1,5 ponto)

Implementar e validar o fluxo obrigatório de pedidos:

1. RECEBIDO — registrar data/hora de entrada e usuário responsável.
2. EM PREPARAÇÃO — pedido encaminhado à cozinha.
3. PRONTO — aguardando retirada ou entrega.
4. SAIU PARA ENTREGA / RETIRADO — registrar obrigatoriamente data/hora e responsável pela saída.
5. FINALIZADO — atendimento concluído.

**CANCELADO é um estado excepcional, permitido apenas ao GERENTE enquanto o pedido não tiver saído para entrega ou retirada. O cancelamento exige justificativa e auditoria. Impedir transições inválidas (por exemplo, finalizar um pedido em preparação), bem como qualquer alteração de status após o cancelamento.**

## RF04 — Cancelamentos e descontos (1,5 ponto)

Desenvolver operações exclusivas do GERENTE para cancelar pedidos e aplicar descontos. O cancelamento exige justificativa, preserva os dados históricos e impede alterações posteriores. O desconto será percentual, limitado a 20% do valor do pedido, e somente poderá ocorrer antes da saída para entrega ou retirada. Registrar ambas as operações na auditoria, incluindo usuário, data/hora e dados anteriores e posteriores pertinentes.

## RF05 — Auditoria das alterações de pedidos e clientes (3,0 pontos)

Implementar mecanismo de auditoria persistente no MongoDB. Devem ser auditadas inclusões e alterações de pedidos, mudanças de status, descontos, cancelamentos e alterações dos dados de clientes, especialmente endereços e telefones. Cada evento deverá registrar:

- Identificação do usuário autenticado e seu perfil;
- Data e hora da operação;
- Tipo de operação e entidade afetada, incluindo seu identificador;
- Campos modificados e respectivos valores anteriores e novos;
- Justificativa, quando exigida pela regra de negócio.

Disponibilizar consulta aos registros de auditoria exclusivamente para ADMIN e GERENTE. Não permitir alteração ou exclusão dos registros pelas interfaces ou APIs comuns. Não registrar senhas, tokens ou outras credenciais em logs. Quando aplicável, restringir a exposição de dados pessoais de acordo com a finalidade da auditoria.

### Exemplo de registro de auditoria

| Campo | Exemplo |
|---|---|
| Usuário | atendente01 |
| Operação | ALTERAÇÃO DE CLIENTE |
| Campo alterado | logradouro |
| Valor anterior | Rua das Flores |
| Novo valor | Av. Brasil |
| Data e hora | 23/09/2026 — 14:35 |
