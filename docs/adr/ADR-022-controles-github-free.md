# ADR-022 — Controles compensatórios de branches em repositório privado sem GitHub Pro

**Data:** 20/11/2026  
**Status:** decisão aprovada; implementação por PR para `develop`, validação operacional pendente  
**Contexto:** governança de desenvolvimento com agentes Cursor/Copilot, Git Flow simplificado e worktrees.

## Problema

O projeto exige PRs de `feature/*` com base `develop`, integração humana e `main` reservada a releases. O repositório `RobsonSilverioSantos/srm-credit-engine` está privado. Em 20/11/2026, a consulta à API de Rulesets retornou HTTP **403** com a mensagem: **"Upgrade to GitHub Pro or make this repository public to enable this feature."** As branches `main` e `develop` estavam sem proteção nativa.

A branch padrão já é `develop`, mas isso é apenas preferência de interface: não impede PR de feature apontado a `main` nem push direto.

## Alternativas consideradas

| Alternativa | Vantagem | Trade-off | Resultado |
|---|---|---|---|
| Contratar GitHub Pro e configurar Rulesets/branch protection | Restrição imposta pelo servidor, inclusive regras de merge/push | Custo adicional fora do escopo do exercício | Adiado; reavaliar para colaboração ampliada/produção |
| Tornar o repositório público | Disponibilizaria recursos de proteção do plano Free | Mudança de visibilidade indesejada durante exercício | Rejeitado |
| Apenas orientar agentes a usar `develop` | Nenhum custo; simples | Erro humano/agente pode desviar PR ou push | Insuficiente isoladamente |
| GitHub Actions + hook `pre-push` + instruções de agentes | Reduz erros sem custo de upgrade; evidencia prevenção e trade-offs | Não impede todas as operações no servidor; depende de Actions/hook ativo | **Escolhido para o MVP** |

## Decisão

1. Preservar repositório privado e não contratar plano adicional durante o exercício.
2. Manter `develop` como branch padrão e `main` apenas para release deliberada.
3. Adicionar `.github/workflows/guard-main.yml`, acionado por `pull_request_target` para `main` (`opened`, `reopened`, `edited`, `synchronize`). O job verifica se `head.ref == develop` **e** se `head.repo.full_name == github.repository`; qualquer outra origem será fechada e receberá comentário explicativo.
4. Não fazer checkout/execução de código não confiável no workflow privilegiado. Usar somente permissão `pull-requests: write` mais `contents: read` e `gh pr close`.
5. Versionar `.githooks/pre-push` com bloqueio de pushes para `refs/heads/main` ou `refs/heads/develop`. Ativar em cada clone via `git config core.hooksPath .githooks`.
6. Registrar nas instruções dos agentes: `feature/*` → PR com `--base develop`, sem push/merge direto ou bypass de hooks.
7. A aprovação/integração de PR e release continuam exclusivas do responsável técnico.

## Consequências e riscos residuais aceitos

- GitHub Actions fecha PRs **após** o evento; não configura um bloqueio obrigatório de abertura ou merge. Há possível janela de corrida antes do fechamento.
- Push direto para `main` ou `develop` via GitHub UI/API ou clone sem hook continua tecnicamente possível.
- Hook local pode ser ignorado/desabilitado; não é regra de autorização no servidor.
- Workflow depende de GitHub Actions habilitado, permissões do token e ausência de falhas/limites de execução. Não presumir funcionamento sem executar teste.
- Não exigir revisão formal por outro usuário quando PR for aberto com a mesma identidade da conta. A revisão técnica e o merge são deliberados, mas a autoaprovação formal não é possível.
- O compromisso de não usar recursos pagos é uma decisão de **custo/prazo**, não alegação de segurança equivalente a branch protection.

## Evidência de aceite

- [x] Repositório privado, default `develop`, sem branches protegidas e resposta API 403 observados em 20/11/2026.
- [ ] PR de governança integrado em `develop` com workflow e hook versionados.
- [ ] Actions ativo e com permissões necessárias.
- [ ] PR de teste `feature/*` → `main` fechado automaticamente com comentário e execução verificáveis.
- [ ] `git config core.hooksPath .githooks` executado no clone, teste local do hook comprovado.
- [ ] Proteção server-side reavaliada se o projeto passar a exigir colaboração produtiva.

## Relação com a defesa

O registro permite demonstrar julgamento de **custo, risco e prevenção**: medida compensatória verificável, suas limitações e o gatilho futuro para proteção nativa. Consultar `DECISIONS.md`, `README.md` e `docs/defesa/RASTREABILIDADE_STAFF_TECH_LEAD.md`.
