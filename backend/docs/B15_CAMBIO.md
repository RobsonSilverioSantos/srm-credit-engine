# B15: integração e operação cambial

Implementação da ADR-026. A janela de 96 horas é política do produto, não
exigência do Banco Central. Não há negociação nem movimentação real de moeda.

## Evidência do serviço oficial

Consulta aferida em 22/11/2026:

```text
https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)?@moeda=%27USD%27&@dataInicial=%2711-19-2026%27&@dataFinalCotacao=%2711-20-2026%27&$format=json
```

O [metadata oficial](https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/$metadata)
declara `dataHoraCotacao` como string sem offset. `CotacaoMoedaPeriodo` inclui
`tipoBoletim`; `CotacaoDolarPeriodo` não fornece esse discriminador. Portanto,
usamos o primeiro recurso, moeda USD, selecionando exatamente `Fechamento`,
nunca o último elemento arbitrário ou `Fechamento Interbancário`.

A resposta pública preservada em `app/src/test/resources/ptax/bcb-usd-2026-11-19.json`
contém abertura, três intermediários e fechamento: compra 5.88830, venda 5.88890,
`2026-11-19 13:04:18.325373`. A
[consulta pública BCB](https://ptax.bcb.gov.br/ptax_internet/consultarTodasAsMoedas.do?method=consultaTodasMoedas)
indicava para esse dia: “às 13:00 (horário de Brasília), conforme fechamento PTAX”.
Interpretamos a referência local usando `America/Sao_Paulo`, obtendo
`2026-11-19T16:04:18.325373Z`; preservamos também a string original e o dia.
O teste usa essa amostra e não acessa a internet.

**Limite da evidência:** `dataHoraCotacao` é a referência do boletim, não prova
do instante exato em que se tornou publicamente acessível. Não inventamos esse
instante: a seleção exige também primeira observação persistida não posterior
ao corte. Revisões têm IDs/versões próprios; reimportação idêntica não altera
essa observação nem reinicia as 96 horas.

## Operação e contratos

PTAX é o padrão. A sincronização é administrativa, explícita e externa à
liquidação; não ocorre ao digitar no navegador. Todas as rotas abaixo existem em
`/api/v1/cotacoes-cambiais` e `/bff/v1/cotacoes-cambiais` e estão no OpenAPI:

- `POST /ptax/sincronizacoes`: MASTER/ADMIN atualizam os sete dias anteriores.
- `POST /manuais`: MASTER/ADMIN cadastram `{ "taxaBrlPorUsd": "5.4321",
  "justificativa": "Motivo operacional" }`; cadastrar não ativa.
- `POST /selecoes`: MASTER/ADMIN selecionam `{ "fonte": "MANUAL",
  "cotacaoId": "<uuid>", "justificativa": "Autorização explícita" }` ou
  `{ "fonte": "BCB_PTAX", "justificativa": "Retorno explícito à PTAX" }`.
- `GET ?pagina=0&tamanho=20&fonte=MANUAL`: histórico paginado de cotações.
- `GET /selecoes?pagina=0&tamanho=20`: histórico de seleções/autorizações.

Organização e autoria vêm da identidade validada. OPERADOR consulta/consome,
mas não cadastra, seleciona nem sincroniza; autor/justificativa são restritos
ao perfil administrativo. MANUAL é isolada por organização, exige cadastro no
mesmo dia civil e autorização única e expira na próxima meia-noite de São Paulo,
exclusive. Expiração não muda a fonte automaticamente.

Sem cotação elegível, USD retorna 503; BRL independe de câmbio. A indisponibilidade
do BCB não invalida um fechamento local ainda elegível. Não há fallback.
`srm.cambio.modo-demo=true` habilita explicitamente a fonte DEMO para aferição
isolada; o padrão é false. DEMO mantém sua validade configurada e C3.
Históricos preexistentes sem proveniência passam a LEGADO, não a PTAX.

Simulação devolve `revisaoCambial`: apesar do nome, o hash vincula a **prévia
financeira completa**, incluindo face, prazo, modalidade, moeda, IDs de taxa
base/spread/câmbio, política, seleção/autorização e valores arredondados.
Confirmação USD PTAX/MANUAL exige esse token. Mudança exige nova prévia,
nova chave e reconfirmação (409); resposta incerta reutiliza exatamente comando
e chave. Replay concluído retorna o original antes de consultar taxas atuais.
A UI existente somente transporta o token e permite reconfirmação; não inclui F06.

## Persistência e concorrência

V7 é aditiva; V1–V6 permanecem intactas. Histórico, políticas e seleções são
append-only. A política inicial versionada tem 345600 segundos. Alterações
administrativas exigem nova linha confiável em `politicas_cambiais`, nunca
UPDATE, revisão humana e coordenação com as operações financeiras. Não há
endpoint público para alterar a política.

A coordenação entre instâncias usa advisory lock de **sessão** 27 durante
HTTP e gravação, numa conexão autocommit: não abre transação enquanto espera
rede. Outra sincronização recebe 503 e pode tentar novamente depois. Persistir
usa transação curta e lock 26; liquidação compartilha esse lock e o da organização
para revalidar seleção/versão/validade antes e depois das escritas.
Não há SELECT FOR UPDATE explícito no recebível; permanece UPDATE condicional.

O adaptador usa timeout de conexão/requisição, três tentativas limitadas para
IO/408/429/5xx, backoff e limite de concorrência. Erro permanente, JSON inválido
ou ausência de boletim não criam cotação fictícia. A operação administrativa
deve ser monitorada: ausência de novos boletins não estende validade.
Snapshots financeiros preservam fonte, VENDA, versão, referência, primeira
observação, corte, política, seleção e autorização; nunca são reprecificados.
