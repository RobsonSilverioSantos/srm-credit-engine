# Gestão de usuários organizacionais — proposta de escopo S02/F04

**Estado:** proposta para revisão e aprovação do responsável técnico antes da implementação. **Requisito explícito:** deve existir um usuário **MASTER previamente provisionado**, capaz de criar os demais perfis por uma interface autenticada. **Motivação:** cadastro de participantes sem SQL durante os testes ponta a ponta, usabilidade no celular e defesa do SRM Credit Engine.

## Situação atual comprovada

- `usuarios` contém `id`, `organizacao_id`, `email`, `senha_hash`, `ativo` e instantes de auditoria; não possui coluna de perfil.
- `UsuarioJdbc` concede somente `ROLE_USER`. A autenticação JWT/BCrypt valida identidade e estado ativo da conta e da organização; nenhuma conta MASTER existe automaticamente.
- A interface implementa login, cadastro de cedentes e recebíveis; não há endpoint HTTP nem tela de administração de contas. O procedimento SQL atual cria usuários comuns de demonstração.
- Esta proposta amplia a política de autorização; requer revisão e documentação da decisão antes de alterar `SPEC.md` ou `DECISIONS.md`.

## Hierarquia proposta para o MVP demonstrativo

| Perfil | Provisionamento | Pode criar | Gestão permitida |
|---|---|---|---|
| **MASTER** | Criado previamente por procedimento local **fora do HTTP**, de modo explícito e controlado | ADMIN e OPERADOR da organização à qual pertence | Listar, ativar e inativar ADMIN/OPERADOR da própria organização, sem poder inativar a si mesmo |
| **ADMIN** | Criado por MASTER autenticado | Somente OPERADOR da própria organização | Listar, ativar e inativar OPERADORES da própria organização |
| **OPERADOR** | Criado por MASTER ou ADMIN autenticado | Ninguém | Apenas os fluxos financeiros permitidos no MVP; acesso administrativo negado |

**Limite de segurança:** MASTER, nesta primeira implementação demonstrativa, é o administrador-raiz **da organização inicial**, não um superusuário global. A designação MASTER não concede leitura/escrita em outras organizações, não autoriza selecionar `organizacaoId` livremente nem altera os invariantes financeiros. Administração de múltiplas organizações exigiria nova decisão de arquitetura/autorização.

**Observação de homologação:** os poderes descritos para MASTER/ADMIN constituem a política proposta, sujeita à revisão humana. O requisito de existir um MASTER antes do uso administrativo é obrigatório.

## Pré-condição — provisionar MASTER antes da primeira utilização

1. Criar a organização demonstrativa de modo controlado (bootstrap opt-in existente ou procedimento de administração local).
2. Aplicar uma **nova migration Flyway** de perfis (`MASTER`, `ADMIN`, `OPERADOR`) com restrição no PostgreSQL, sem modificar migrations V1–V3. Usuários legados passam a **OPERADOR** por padrão; não promover silenciosamente contas anteriores.
3. Disponibilizar script de provisionamento administrativo local (por exemplo, `backend/scripts/bootstrap-master.sh` ou comando CLI equivalente), que:
   - exija ação explícita de quem opera o ambiente, com organização existente e dados de conta fornecidos na execução;
   - receba a senha sem exibição nem gravação em histórico/argumentos de processo, valide robustez e armazene exclusivamente hash BCrypt;
   - não tenha login, senha ou token fixos no repositório e **não** seja chamado automaticamente a cada inicialização;
   - rejeite execução repetida que sobrescreva conta, senha ou papel de um MASTER existente; exija recuperação offline controlada se necessário;
   - registre resultado administrativo sem expor senha ou hash e documente o passo a passo reproduzível para demonstração.
4. Executar o provisionamento antes de abrir a UI para criar ADMIN ou OPERADOR. A **primeira conta MASTER não pode ser criada** por `/api/v1/usuarios`, `/bff/v1/usuarios` nem por cadastro público.
5. Para ambientes além da demonstração, provisionamento e recuperação de conta privilegiada exigem desenho operacional adicional; não afirmar prontidão produtiva.

## Modelo de autorização e integridade

- Perfis são definidos e verificados no servidor a partir da identidade JWT e do **estado atual do PostgreSQL**. Não confiar em campo de perfil enviado pelo cliente como prova de autorização; o perfil solicitado é somente um alvo sujeito ao RBAC.
- Toda criação, listagem e alteração usa `organizacao_id` do principal autenticado; não há opção de selecionar organização no formulário nem atribuição de outra organização por body.
- Nenhuma rota de criação por HTTP aceita `MASTER`. Somente MASTER pode criar ADMIN. MASTER/ADMIN podem criar OPERADOR. OPERADOR recebe **403** em operações administrativas.
- Não permitir inativar a própria conta MASTER pelo fluxo de gestão. Impedir inativação de MASTER por esses endpoints. Bloquear criação/perfil fora da hierarquia e acessos entre organizações, preferencialmente com **404** que não revele a existência do recurso.
- Desativação do usuário revoga o acesso do JWT já emitido conforme validação de estado ativo existente; alterações de perfil devem surtir efeito na próxima autorização (evitar privilégio desatualizado preso ao token).
- Evitar exposição de `senha_hash`, senha ou JWT em respostas, logs, métricas, erros, traces e PRs. Sempre validar senha no backend e usar BCrypt.
- Sem cadastro público, mudança de perfil livre, recuperação de senha pública, MFA ou SSO neste incremento. Explicitar essas limitações na defesa.

## S02 — Backend (P0)

- Novas portas/casos de uso, adaptadores JDBC e API/BFF; arquitetura hexagonal obrigatória.
- Migrar banco com novo perfil e restrições; implementar bootstrap reproduzível do primeiro MASTER.
- Contratos em `/api/v1/usuarios` e `/bff/v1/usuarios` que chamam os mesmos casos de uso:
  - `GET /usuarios/me` — dados mínimos do próprio usuário autenticado, perfil, organização e capacidades para renderização da UI; acessível ao OPERADOR, sem segredo.
  - `GET /usuarios` — lista sanitizada de usuários **da própria organização**; autorização MASTER/ADMIN e escopo de visualização conforme perfil.
  - `POST /usuarios` — criar usuário da própria organização com `email`, `senha`, `perfil` (`ADMIN` ou `OPERADOR`); somente MASTER pode escolher ADMIN; ninguém pode escolher MASTER; `201`, sem campos secretos.
  - `PATCH /usuarios/{id}/status` — `{"ativo":true|false}` com escopo e RBAC; não permitir bloquear a si mesmo nem MASTER.
- Distinguir respostas `401` (sem sessão), `403` (perfil sem permissão), `404` (usuário de outra organização), `409` (e-mail duplicado), `400` (entrada inválida). Não vazar existência de usuário de outra organização nas mensagens.
- Validar e-mail e políticas mínimas de senha; rejeitar campos extras que tentem configurar `organizacaoId`, atribuir MASTER ou elevar privilégios sem autorização.
- Testes PostgreSQL e HTTP: bootstrap inicial, segunda execução segura, hash BCrypt, MASTER cria ADMIN/OPERADOR, ADMIN cria somente OPERADOR, OPERADOR recebe 403, outra organização isolada, autocancelamento negado, e-mail único case-insensitive, dados sensíveis omitidos, JWT de inativado recusado e autorização atualizada após mudança de papel.

## F04 — Frontend (P0; somente após S02 integrada)

- Administração de usuários a partir de sessão já autenticada: MASTER consegue criar ADMIN e OPERADOR; ADMIN só OPERADOR; OPERADOR não vê ação administrativa e recebe 403 caso tente via API.
- A interface identifica papel/capacidades via `GET /usuarios/me`; **controle real de acesso sempre no backend**, independentemente de menu oculto.
- Tela MUI responsiva para desktop e celular com criação, lista, ativo/inativo, confirmação de inativação, validações, estados loading/vazio/erro/sucesso e feedback acessível.
- Campos do formulário: e-mail, senha e perfil **somente entre perfis que o servidor permite ao usuário logado**. Nenhuma seleção de MASTER ou organização.
- Utilizar portas/casos de uso frontend e adaptadores BFF segundo arquitetura hexagonal; não executar chamadas HTTP na apresentação.
- Não persistir senha ou token desnecessariamente nem mostrar segredos em mensagens ou logs. Mensagens claras para 401/403/409.
- Testar interface e fluxo: provisionar MASTER offline → login MASTER → cadastrar ADMIN → login ADMIN → cadastrar OPERADOR → login OPERADOR → simular, cadastrar, liquidar e consultar extrato; validar layout mobile e acessibilidade.

## Governança e validação

- A política de três perfis e suas permissões deve ser **aprovada pelo responsável técnico antes de implementar S02**. Esta especificação é uma proposta rastreável, não um endpoint implementado.
- S02 deve ser testada e integrada em `develop` antes de iniciar F04. Cada uma usa branch/worktree exclusivos, PR para `develop`, CI e revisão humana.
- Se F04 não estiver pronta para o teste presencial, provisionar previamente contas por procedimento administrativo controlado; não confundir esse procedimento com a futura UI.
- Atualizar D03/A01 após os merges com dados efetivamente testados e evidências verificáveis. Recuperação de senha, MFA, gestão multi-organização e provisionamento produtivo permanecem fora de escopo, salvo decisão adicional.
