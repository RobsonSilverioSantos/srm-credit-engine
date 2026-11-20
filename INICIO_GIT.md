# Início do novo repositório — procedimento para o responsável técnico

Este guia deve ser executado **por você**, após criar o novo repositório no GitHub. **O pacote contém documentação e agentes, não um repositório Git pré-inicializado.** O repositório anterior não deve ser apagado.

## 1. Criar o repositório remoto

Na sua conta GitHub, crie um repositório **privado vazio** (sugestão: `srm-credit-engine-v2`). **Não marque** as opções de gerar README, `.gitignore` ou licença: estes arquivos já vêm no pacote. Você pode escolher outro nome. Guarde a URL SSH/HTTPS exibida.

Requisitos locais: `git`, acesso ao GitHub (SSH ou HTTPS autenticado), `gh` instalado/autenticado para abertura automática de PR. Docker e Java serão utilizados após o PR de bootstrap do backend.

## 2. Publicar uma baseline documental única

Extraia o ZIP em uma pasta nova, incluindo arquivos ocultos `.github/` e `.cursor/`. No terminal, **dentro dessa pasta**:

```bash
git init -b main
git status --short
git add .
git status --short
git commit -m "docs: iniciar baseline do novo SRM Credit Engine"
git branch develop
# Substitua pelos dados EXATOS do repositório vazio recém-criado:
git remote add origin git@github.com:SEU_USUARIO/SEU_REPOSITORIO.git
git push -u origin main
git push -u origin develop
```

Na interface do GitHub: definir **`develop` como branch padrão** para facilitar os PRs; manter `main` como release. Verificar branches e os 7 agentes no conteúdo versionado. `git branch -a -vv`, `git worktree list`, `git status -sb` e `gh auth status` ajudam a diagnosticar.

## 3. Configurar gates sem bloquear a própria entrega

- Se disponível no seu plano, configurar regra de PR para `develop` (evitar push direto, proteger contra force-push e deleção); regras similares para `main`. **Não definir como obrigatório um status check que ainda não existe**: B12 criará CI primeiro.
- **Importante:** o GitHub não permite que o autor do PR aprove formalmente o próprio PR. Se o agente usar a mesma identidade da sua conta, **não exija aprovação de terceiros inexistentes**. Você revisará o diff e executará deliberadamente o merge. Caso um segundo revisor real seja incluído, a política poderá mudar.
- A disponibilidade de branch protection/rulesets para repositórios privados varia com o plano/organização. As instruções dos agentes continuam proibindo push direto, mesmo quando não houver bloqueio técnico.
- Desabilitar auto-merge para os agentes; não conceder permissões desnecessárias a bots/apps ou tokens.

### 3.1. Ativar controles compensatórios no GitHub Free

A ausência de Rulesets para este repositório privado foi confirmada por resposta HTTP 403 com indicação de GitHub Pro ou repositório público. ADR-022 documenta o corte de custo e o risco aceito.

1. **Após o merge** do PR que adiciona `.github/workflows/guard-main.yml`, em **Settings → Actions → General** conferir que Actions está habilitado e que a política de permissões permite ao workflow solicitar `pull-requests: write`.
2. Ativar o hook versionado em **cada clone** do repositório (os worktrees do mesmo clone compartilham configuração):
   ```bash
   git config core.hooksPath .githooks
   git config --get core.hooksPath
   ```
   O arquivo `.githooks/pre-push` está versionado com modo executável. Não usar `--no-verify` ou outra forma de desativar o hook nos agentes.
3. Realizar teste seguro: abrir PR descartável de `feature/*` para `main` e verificar que foi fechado pelo workflow; realizar ensaio local do hook com entrada simulada ou tentativa controlada, **sem push destrutivo**.
4. O workflow **não** bloqueia push direto/merge no servidor; as medidas são mitigação e devem constar na defesa. Para bloqueio obrigatório, reavaliar o plano e aplicar branch protection/Ruleset nativo.

## 4. Criar primeiro worktree: backend

O primeiro PR de backend é `B02`; o `F00` pode ocorrer em paralelo **depois do G00 finalizado**. Os comandos abaixo são exemplos e a branch real deve sempre derivar do ID e do título `Trabalho` em `TASKS.md`.

```bash
git status -sb
git worktree list
# Executar a partir da pasta do repositório, que contém a branch develop local.
git worktree add -b feature/b02-preparar-backend-kotlin-spring-boot-gradle-e-docker-compose ../srm-b02 develop
# Abrir ../srm-b02 como workspace dedicado ao agente backend.
```

Depois do trabalho e dos testes observados, o agente autorizado poderá usar `git add <arquivos_da_tarefa>`, `git commit`, `git push -u origin feature/...` e `gh pr create --base develop --head feature/...`. **Nunca** `git flow feature finish`, porque ele executa merge. Após sua revisão, **você** realiza o merge; nenhuma sessão pode assumir que o merge ocorreu por ter aberto o PR.

Para abrir uma segunda feature a partir de `develop`, primeiro confirme que as dependências já estão integradas. Atualize a branch local de base por `git fetch origin` e `git switch develop && git pull --ff-only origin develop` **apenas no worktree principal limpo e sem operações pendentes**. Evitar rebase/reset/force para "arrumar" divergências.

## 5. O que NÃO fazer

- Não deletar a pasta `.git` antiga, branches antigas, ou repositório antigo como parte do reset; o histórico anterior é evidência de AI_USAGE e pode servir como backup.
- Não executar `git reset --hard`, `git clean -fd`, `git push --force`, `git gc` para contornar conflitos sem investigação.
- Não usar a mesma pasta para múltiplos agentes; cada agente deve trabalhar em seu worktree e branch.
- Não commitar segredos, `.env`, credenciais, tokens, nomes reais de clientes ou comprovantes financeiros.

## 6. Depois do primeiro PR

Atualizar `TASKS.md` e `AI_USAGE.md` **somente com dados reais**: URL do PR, hash do commit, comando e resultado de testes, verificação humana e merge. PRs com testes falhos devem reportar o bloqueio; sem alegações de sucesso fictícias.
