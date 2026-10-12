# Avaliação

## Distribuição por complexidade

A pontuação máxima é 10,0 pontos. Auditoria e controle de acesso têm maior peso por envolverem autenticação, regras de negócio, persistência e segurança.

| Critério | Pontos |
|---|---|
| RF01 — Controle de usuários e permissões | 2,0 |
| RF02 — Evolução do cadastro de pedidos | 1,0 |
| RF03 — Controle de entrada, saída e transições | 1,5 |
| RF04 — Cancelamentos e descontos exclusivos do gerente | 1,5 |
| RF05 — Auditoria de pedidos e clientes | 3,0 |
| Qualidade do código, testes e demonstração individual | 1,0 |
| **TOTAL** | **10,0** |

## Detalhamento da pontuação

| Item | Evidência exigida | Pontos |
|---|---|---|
| RF01 | Três perfis implementados e corretamente atribuídos | 0,7 |
| RF01 | Autorização efetiva no backend e proteção das APIs | 0,9 |
| RF01 | Administração segura dos perfis e proteção contra autoelevação | 0,4 |
| RF02 | Modelo do pedido, itens, quantidades, responsável e entrada | 0,5 |
| RF02 | Cálculo de valores no servidor e validações | 0,5 |
| RF03 | Estados e transições válidas, com bloqueios | 0,9 |
| RF03 | Registros de entrada, saída e responsáveis | 0,6 |
| RF04 | Cancelamento restrito, com justificativa e bloqueios | 0,8 |
| RF04 | Desconto restrito ao gerente, limitado e validado | 0,7 |
| RF05 | Auditoria persistente das mudanças de pedidos | 1,0 |
| RF05 | Auditoria das alterações cadastrais de clientes | 0,8 |
| RF05 | Identificação, data/hora e valores anteriores/novos | 0,7 |
| RF05 | Consulta restrita, integridade e proteção de dados | 0,5 |
| Qualidade | Organização, tratamento de erros e testes | 0,5 |
| Qualidade | README e demonstração individual em vídeo | 0,5 |

A atribuição dos pontos será proporcional à implementação funcional demonstrada e verificável no código. Interfaces visuais que não possuam a regra correspondente no backend não atendem aos requisitos de autorização.
