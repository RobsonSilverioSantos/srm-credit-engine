# ADR-026 — Política B15 de elegibilidade cambial PTAX e MANUAL

**Status:** aprovada pelo responsável técnico em 22/11/2026, com validade operacional condicionada à integração humana do PR documental em `develop`. **B15 ainda não implementada.**
**Escopo:** motor demonstrativo de cotação BRL/USD e sua interface futura F06; não determina cotação executável de uma operação bancária, homologação produtiva ou movimentação de dinheiro.
**Referências:** [B15](../engenharia/B15_PTAX_FECHAMENTO_ELEGIBILIDADE_CAMBIAL.md), [F06](../engenharia/F06_CADASTRO_MANUAL_COTACOES.md), [issue B15 #52](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/52), ADR-008, ADR-010, ADR-011 e ADR-025.

## Contexto e decisão

A configuração P0 de **30 minutos** para cotação mockada não representa a natureza diária do boletim PTAX de fechamento. O responsável aprovou dois modos cambiais explícitos, destinados a preservar operações em fins de semana e feriados, com tolerância limitada à defasagem, e possibilidade de entrada MANUAL excepcional quando a fonte PTAX estiver indisponível ou desatualizada. Não há fallback automático.

### Fonte padrão `BCB_PTAX`

- Para converter valor presente de BRL para USD, usar **`cotacaoVenda`** do boletim BCB de tipo **`Fechamento`**, expressa em BRL por USD. A PTAX é referência de mercado, não cotação negociada. Nenhum spread cambial adicional foi aprovado.
- Elegibilidade por **último fechamento conhecido, publicado, persistido e disponível à aplicação no corte** da operação. Não selecionar boletins futuros, publicações/revisões observadas posteriormente ao corte nem extrapolar horário publicado a partir de `dataHoraCotacao` sem aferição de sua semântica e fuso.
- **Defasagem máxima de 96 horas corridas**, configurável por configuração administrativa confiável e versionada, medida a partir do instante de referência do boletim até o instante de corte em UTC. Aceitar exatamente 96h; acima de 96h rejeitar. Reimportar boletim antigo não renova idade. A integração deve aferir e documentar a semântica do campo oficial `dataHoraCotacao` e sua conversão para `Instant`, preservando data do boletim, fuso/fonte e instante de primeira observação.
- Sábado, domingo, feriado e ausência de novo fechamento **não suspendem** a contagem nem renovam validade. Em feriados prolongados superiores a 96h, PTAX pode expirar: bloquear USD e permitir alternativa MANUAL **somente se explicitamente autorizada**. Alertar operacionalmente sobre ausência de publicação esperada.
- O backend pode continuar com fechamento **já persistido e elegível** quando o BCB estiver fora do ar; sincronização ocorre fora de transações financeiras. Sem fechamento elegível, responder indisponibilidade de dados financeiros, sem liquidação parcial. Operações exclusivamente BRL continuam disponíveis.

### Fonte excepcional `MANUAL`

- Cotação BRL por USD positiva, registrada como string decimal e `BigDecimal`, com data e hora confiáveis, justificativa e autor autenticado; apenas **MASTER/ADMIN** da organização podem cadastrar e **autorizar/ativar explicitamente** a utilização. `OPERADOR` pode consumir a cotação já autorizada, mas não publicar/ativar.
- Cadastro **não ativa automaticamente** uma cotação. Manter autorização auditável, escopo de organização e versão da política/fonte. Proibir seleção de outra organização e uso retroativo. A entrada é aceita para uso apenas **desde o instante efetivo de autorização**, sem vigência retroativa ou agendamento futuro neste MVP.
- A cotação MANUAL é elegível até **00:00:00 do dia seguinte, exclusive, no fuso `America/Sao_Paulo`**, relativo ao dia local da autorização. Em outras palavras, vale somente até o final do dia civil autorizado, independentemente da hora em que foi cadastrada; não há TTL MANUAL de 30 minutos. O cálculo no servidor usa timezone IANA, converte para UTC e não usa o fuso do navegador como autoridade.
- No início do próximo dia, exigir **nova cotação e autorização** para continuar no modo MANUAL. Não renovar automaticamente, não herdar cotação de dia anterior e não estender a vigência por feriados. Corrigir erros por nova versão auditável; sem `UPDATE`/`DELETE` no histórico imutável.
- Não exigir segunda pessoa aprovadora nesta versão demonstrativa; o mesmo MASTER/ADMIN pode registrar e autorizar, com risco residual documentado. Uma segregação de funções seria evolução futura.

### Seleção explícita de fonte e indisponibilidade

- **Modo padrão PTAX**. Ativar modo MANUAL somente por decisão/ação autorizada e auditável no escopo organizacional. Uma cotação cadastrada não passa a ser usada automaticamente. O servidor aplica a política efetiva na prévia e na liquidação; o cliente nunca impõe origem por parâmetro não autorizado.
- Na ausência de PTAX elegível, não executar sequência implícita `PTAX → MANUAL → DEMO`; exigir autorização explícita MANUAL. Se MANUAL expirar, não retornar automaticamente a PTAX nem à DEMO; uma mudança explícita exige nova prévia e confirmação financeira.
- **`DEMO`** fica isolada como fonte de aferição/teste, inclusive fixture C3 com 5,4321 BRL/USD, sem atuar como contingência automática. A configuração legada da DEMO não passa a definir a validade da PTAX ou MANUAL.
- Registrar versão da seleção/política e da cotação no snapshot. Fonte, lado, taxa, boletim, instante de referência/corte, observação/disponibilidade, autorização MANUAL e versão histórica devem permanecer auditáveis.

### Precisão financeira e idempotência

- Manter a fórmula existente: VP BRL arredondado por HALF_EVEN, depois pagamento USD = `VP_BRL / cotacaoVenda` arredondado a duas casas HALF_EVEN; taxas como `BigDecimal` e strings decimais nos DTOs. Não alterar spreads de recebíveis ou a precificação dos golden cases.
- Simulação não reserva cotação; confirmação revalida fonte, preço, versão e elegibilidade. Mudança de modo, taxa ou versão após prévia exige **nova prévia e reconfirmação**, nunca troca silenciosa nem sucesso parcial (ADR-025).
- Replay idempotente do **mesmo comando/chave** retorna a resposta financeira original, mesmo após expiração/indisponibilidade da fonte, sem reprecificar. Mesma chave com comando diferente: conflito HTTP 409. Incorporar escolha de origem autorizada ao comando/hash canônico quando material ao resultado, sem timestamps recalculados a cada tentativa.
- **C1** R$ 92.859,94, **C2** R$ 23.337,77, **C3 DEMO** US$ 17.094,67 com cotação 5,4321 permanecem testes separados; PTAX real tem resultado USD variável.

## Verificações de aceitação

1. Testar limite PTAX de 96h exatas (aceita) e 96h+1ms (rejeita), sábado/domingo, feriado prolongado além do limite, dia útil sem publicação e boletim não conhecido no corte.
2. Testar relógio e conversão de fuso `America/Sao_Paulo` para `Instant`; registro MANUAL antes e depois da autorização, às 23:59, 00:00 do dia seguinte, e impossibilidade de retroagir.
3. Validar RBAC MASTER/ADMIN/OPERADOR e organização; cadastro sem autorização não muda política; persistir proveniência, auditoria, autor, justificativa, versão e snapshot sem atualização/destruição histórica.
4. Testar troca explícita PTAX/MANUAL, 503 sem fonte elegível, queda do BCB com fechamento local válido, falta de fallback silencioso e operações BRL independentes.
5. Testar revalidação da prévia, mudança de preço/fonte no corte, replays idempotentes após expiração, `409`, rollback integral e golden cases C1–C3.
6. Validar integração OData com fixtures oficiais, inclusive `cotacaoCompra`, `cotacaoVenda`, `tipoBoletim`, `dataHoraCotacao`, publicação e revisão tardias; CI deve ser reproduzível sem dependência da rede BCB ao vivo.

## Consequências e governança

- Esta ADR **especializa/substitui a duração uniforme da ADR-011 para PTAX e MANUAL**, preservando o histórico da ADR-011 como hipótese DEMO/P0. A B15 deve adaptar a aplicação e documentar eventual compatibilidade de dados legados sem reclassificá-los como PTAX.
- Não modificar migrations Flyway já aplicadas. B15 usa worktree/branch exclusivas, revisão `architect`, `qa`, `reviewer` e PR para `develop`, sem merge do agente.
- B15 só começa após integração humana do PR documental que registrar este ADR e o status literal `Liberada para iniciar` em `origin/develop:TASKS.md`. A F06 continua bloqueada até a B15 integrada e novo PR de liberação.
- Esta é uma política aprovada para o desafio demonstrativo, **não obrigação/regra de validade prescrita pelo BCB nem homologação para produção**.

## Fontes de referência pública

- [BCB — Dólar comercial, compra e venda, boletins diários](https://dadosabertos.bcb.gov.br/dataset/dolar-americano-usd-todos-os-boletins-diarios).
- [BCB — Serviço PTAX OData v1](https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/documentacao).
- [BCB — metodologia de publicação de fechamento](https://normativos.bcb.gov.br/Lists/Normativos/Attachments/49545/Circ_3506_v4_L.pdf).
