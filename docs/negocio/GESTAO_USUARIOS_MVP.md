# Gestão de usuários organizacionais — proposta de escopo S02/F04

**Estado:** proposta para aprovação do responsável técnico antes da implementação. **Motivação:** disponibilizar a criação de contas na interface para testes de ponta a ponta, testes de usabilidade em celular e defesa do SRM Credit Engine.

## Situação comprovada no código

- O esquema existente `usuarios` possui `id`, `organizacao_id`, `email`, `senha_hash`, `ativo` e auditoria temporal; não possui perfil administrativo.
- `UsuarioJdbc` atribui somente `ROLE_USER`. A autenticação local verifica BCrypt e emite JWT; o validador confere se usuário e organização continuam ativos.
- A UI oferece login, cadastro de cedentes e recebíveis, mas não de usuários. O README do backend utiliza provisionamento SQL para contas demonstrativas.
- A alteração da autorização constitui ampliação deliberada do escopo de segurança; registrar decisão no artefato de decisão apropriado **após revisão humana**, sem alterar unilateralmente `SPEC.md` ou `DECISIONS.md`.

## Decisão proposta — autenticação administrativa limitada

1. Perfis **ADMIN** e **OPERADOR** vinculados a `usuarios.organizacao_id`. Migrar por **nova versão Flyway** (sem editar V1–V3); usuários legados permanecem **OPERADOR** por padrão, sem escalonamento automático de privilégios.
2. O **primeiro ADMIN** é provisionado **uma única vez, fora do HTTP público**, por procedimento local controlado e documentado para ambiente de demonstração, com senha não fixa e hash BCrypt. Não habilitar autoprovisionamento silencioso de ADMIN, credenciais padronizadas nem endpoint público de registro.
3. Somente ADMIN ativo pode criar, listar e ativar/inativar **OPERADORES da própria organização**. Neste MVP, a UI **não cria ADMINs**, não muda perfil e não expõe seleção de organização; qualquer promoção de perfil exige procedimento privilegiado separado e auditável.
4. Identidade, organização e autorização são derivadas **somente do principal JWT validado e do estado atual do banco**, nunca do corpo ou de claims não revalidados; alteração de papel/desativação deve valer imediatamente para operações administrativas. Não aceitar `organizacaoId` do cliente.
5. Não revelar `senha_hash`, senhas, JWT ou credenciais em GET, erros, logs, métricas e respostas. Senha recebida em TLS, validada no backend e hash BCrypt no servidor. **Não persistir senhas em claro ou em logs**. Para a demonstração, senha inicial temporária comunicada ao participante por canal apropriado; fluxo de troca de senha no primeiro acesso é uma limitação a documentar caso não seja implementado.
6. **Não alterar** autorização de liquidação, isolamento de cedentes/recebíveis, regras de precificação ou contratos monetários.

## S02 — Backend (P0)

- Novas portas/casos de uso de gestão de usuários com adaptadores PostgreSQL e HTTP, mantendo arquitetura hexagonal e o isolamento `organizacao_id`.
- Contratos simétricos em `/api/v1/usuarios` e `/bff/v1/usuarios`, utilizando os mesmos casos de uso:
  - `GET /usuarios` — listar apenas contas da organização autenticada, sem dados de credenciais.
  - `POST /usuarios` — criar OPERADOR com `email` e `senha`; organização e perfil são atribuídos pelo servidor. Resposta `201` sem dados secretos.
  - `PATCH /usuarios/{id}/status` — ativar/inativar operador por `{"ativo": true|false}`, com verificação de autorização e organização.
- Rejeitar e-mails inválidos/duplicados, senhas insuficientes e propriedades extras indevidas; não aceitar perfil/organização do comando, nem permitir que ADMIN bloqueie a própria conta pelo fluxo de operadores.
- Respostas previstas: `401` sem autenticação, `403` para OPERADOR tentando gerir usuários, `404` para recurso de outra organização, `409` para e-mail duplicado e `400` para dados inválidos.
- Testar em PostgreSQL real: ADMIN cria/lista/inativa, OPERADOR recebe 403, tenant cruzado não vaza dados, e-mail case-insensitive único, hash BCrypt, dados sensíveis ausentes, JWT de conta inativada recusado e ausência de escalonamento de privilégios.
- Procedimento repetível de bootstrap do primeiro ADMIN de demonstração, com confirmação explícita e sem credencial fixa. Não permitir que um operador comum se torne administrador.

## F04 — Frontend (P0, depende S02 integrada)

- Tela **Administração de usuários** acessível após login por ADMIN, com criação, listagem, ativação e desativação de OPERADORES. Integrar navegação ao painel principal sem interferir na sessão do extrato.
- Formulários responsivos MUI para celular, labels, mensagens por campo, estados carregando/vazio/erro/sucesso, confirmações acessíveis para ação destrutiva e contraste/foco adequados.
- Utilizar porta de aplicação e adaptador BFF específicos; **nunca** criar gestão de contas por HTTP direto na camada de apresentação. Não expor controle de organização/perfil nem guardar senha além do necessário ao envio.
- Não confiar somente em ocultar botão: backend é responsável por devolver 403 para não ADMIN. Tratar `401/403/409` com mensagens claras, sem revelar recursos de outros tenants.
- Testes de componentes e integração de porta/adaptador, layout mobile, acessibilidade e fluxo criar → novo usuário autenticar → simular → liquidar → consultar extrato.

## Validação humana e limitação temporal

- A revisão deste documento deve aprovar explicitamente o mecanismo de bootstrap e a política mínima ADMIN/OPERADOR antes da mudança de segurança.
- S02 e F04 são entregas novas; a existência deste documento **não** significa que a interface ou a API estejam disponíveis.
- Para testes antes da integração F04, ainda é necessário provisionar contas pelo procedimento documentado em `backend/README.md`. Não afirmar cadastro via UI sem testes de ponta a ponta.
- Após os merges, atualizar evidências de D03/A01 e roteiro de defesa. Limitações sobre alteração de senha, recuperação de senha e MFA devem permanecer explícitas caso fora de escopo.
