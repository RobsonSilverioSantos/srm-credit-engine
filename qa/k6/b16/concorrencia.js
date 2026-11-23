import { check, fail } from 'k6';
import http from 'k6/http';

const valida = '/api/v1/lotes/recebiveis/validacoes';
const confirma = '/api/v1/lotes/recebiveis';
const cabecalho = 'referenciaExterna;cedenteId;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento';
const csvA = open('../../../docs/validacao/lotes/SRM_LOTE_A_200_RECEBIVEIS.csv');
const csvB = open('../../../docs/validacao/lotes/SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv');
const conflitosEsperados = ['SRM-DEMO-A-0020', 'SRM-DEMO-A-0075', 'SRM-DEMO-A-0120', 'SRM-DEMO-A-0175'];

export const options = {
  vus: 1,
  iterations: 1,
  batch: 2,
  batchPerHost: 2,
  maxRedirects: 0,
  thresholds: { checks: ['rate==1'] },
};

function exigir(condicao, criterio) {
  if (!check(condicao, { [criterio]: (v) => v === true })) fail(criterio);
}

function config() {
  const origem = __ENV.SRM_B16_BASE_URL || '';
  const jwt = __ENV.SRM_B16_JWT || '';
  const cedente = __ENV.SRM_B16_CEDENTE_ID || '';
  exigir(__ENV.SRM_B16_AMBIENTE_DESCARTAVEL === 'sim', 'opt-in explícito para ambiente descartável');
  exigir(/^http:\/\/(localhost|127\.0\.0\.1|\[::1\]):[1-9][0-9]{0,4}$/.test(origem), 'URL HTTP restrita ao loopback com porta explícita');
  exigir(/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(jwt), 'JWT de teste fornecido por variável de ambiente');
  exigir(/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(cedente), 'cedente sintético com UUID explícito');
  return { origem, jwt, cedente };
}

function massa(csv, cedente) {
  const [linhaCabecalho, ...linhas] = csv.trimEnd().split(/\r?\n/);
  const dados = linhas.map((linha) => linha.split(';'));
  exigir(linhaCabecalho === cabecalho && dados.length === 200 && dados.every((linha) => linha.length === 7),
    'massa versionada contém cabeçalho canônico e 200 linhas');
  const refs = new Set(dados.map((linha) => linha[0]));
  exigir(refs.size === 200, 'referências são únicas dentro de cada massa');
  return dados.map((linha) => [linha[0], cedente, ...linha.slice(2)]);
}

function parametros(c, operacao, adicionais = {}) {
  return {
    headers: { Authorization: 'Bearer ' + c.jwt, ...adicionais },
    tags: { name: operacao },
    redirects: 0,
    timeout: '30s',
  };
}

function lerJson(res, etapa) {
  try {
    return res.json();
  } catch (_) {
    fail(etapa + ': resposta não JSON');
  }
}

function previa(c, dados, etapa, existe = new Set()) {
  const arquivo = [cabecalho, ...dados.map((linha) => linha.join(';'))].join('\n') + '\n';
  const res = http.post(c.origem + valida, { arquivo: http.file(arquivo, 'lote-sintetico.csv', 'text/csv') },
    parametros(c, 'B16 pré-validação'));
  exigir(res.status === 200, etapa + ': prévia HTTP 200');
  const corpo = lerJson(res, etapa);
  const refsEsperadas = dados.map((linha) => linha[0]);
  exigir(!!corpo && Array.isArray(corpo.itens) && corpo.itens.length === refsEsperadas.length,
    etapa + ': uma resposta por linha, sem descarte');
  exigir(corpo.confirmavel === !refsEsperadas.some((ref) => existe.has(ref)), etapa + ': apenas duplicidades bloqueiam cadastro neste cenário');
  exigir(corpo.itens.every((item, i) => item && item.linha === i + 2 &&
    (existe.has(refsEsperadas[i])
      ? item.status === 'JA_CADASTRADO'
      : item.status === 'NOVO' && item.comando && item.comando.referenciaExterna === refsEsperadas[i])),
    etapa + ': classificação por referência e na ordem do arquivo');
  return corpo.itens.map((item) => item.comando);
}

function requisicao(c, comandos, chave) {
  return {
    method: 'POST',
    url: c.origem + confirma,
    body: JSON.stringify({ itens: comandos }),
    params: {
      ...parametros(c, 'B16 confirmação', { 'Content-Type': 'application/json', 'Idempotency-Key': chave }),
      responseCallback: http.expectedStatuses(201, 409),
    },
  };
}

function confirmar(r) {
  return http.request(r.method, r.url, r.body, r.params);
}

function conferirCadastro(res, refs, etapa) {
  exigir(res.status === 201, etapa + ': confirmação HTTP 201');
  const corpo = lerJson(res, etapa);
  exigir(!!corpo && typeof corpo.loteId === 'string' && corpo.loteId.length > 0 &&
    corpo.cadastrados === refs.size && Array.isArray(corpo.itens) && corpo.itens.length === refs.size,
    etapa + ': total de itens confirmado');
  const ids = new Set(corpo.itens.map((item) => item && item.recebivelId));
  const confirmadas = new Set(corpo.itens.map((item) => item && item.referenciaExterna));
  exigir(ids.size === refs.size && [...ids].every((id) => typeof id === 'string' && id.length > 0) &&
    confirmadas.size === refs.size && [...confirmadas].every((ref) => refs.has(ref)),
    etapa + ': um recebível por referência esperada');
  return corpo;
}

export default function () {
  const c = config();
  const lotes = [massa(csvA, c.cedente), massa(csvB, c.cedente)];
  const refs = lotes.map((lote) => new Set(lote.map((linha) => linha[0])));
  const shared = [...refs[0]].filter((ref) => refs[1].has(ref));
  exigir(shared.length === 4 && conflitosEsperados.every((ref) => shared.includes(ref)),
    'A e B têm somente as quatro referências compartilhadas previstas');

  // Duas prévias sem escrita: as duas precisam estar confirmáveis antes da corrida.
  const comandos = [previa(c, lotes[0], 'A inicial'), previa(c, lotes[1], 'B inicial')];
  const rodada = 'qa-b16-' + Date.now() + '-' + Math.floor(Math.random() * 1000000000);
  const requests = comandos.map((itens, i) => requisicao(c, itens, rodada + '-' + i));
  const respostas = http.batch(requests);
  exigir(respostas.length === 2 && respostas.filter((r) => r.status === 201).length === 1 &&
    respostas.filter((r) => r.status === 409).length === 1, 'corrida HTTP: um cadastro integral e um conflito');
  const vencedor = respostas.findIndex((r) => r.status === 201);
  const perdedor = 1 - vencedor;
  conferirCadastro(respostas[vencedor], refs[vencedor], 'lote vencedor');

  // A união A/B revela 200 identidades cadastradas; nenhuma das 196 exclusivas
  // do perdedor pode ter persistido. As quatro compartilhadas contam uma só vez.
  previa(c, lotes[vencedor], 'vencedor após corrida', refs[vencedor]);
  previa(c, lotes[perdedor], 'perdedor após corrida', refs[vencedor]);

  const respostaReplay = confirmar(requests[vencedor]);
  conferirCadastro(respostaReplay, refs[vencedor], 'replay do vencedor');
  // A ordem das propriedades retornadas por res.json() pode variar no k6;
  // comparar os corpos HTTP originais evita falso negativo na idempotência.
  exigir(typeof respostas[vencedor].body === 'string' &&
    typeof respostaReplay.body === 'string' &&
    respostaReplay.body === respostas[vencedor].body,
  'mesma chave e comando devolvem o corpo HTTP original');

  const corrigido = lotes[perdedor].filter((linha) => !refs[vencedor].has(linha[0]));
  const refsCorrigidas = new Set(corrigido.map((linha) => linha[0]));
  exigir(refsCorrigidas.size === 196, 'lote corrigido tem 196 referências novas');
  const novos = previa(c, corrigido, 'lote corrigido');
  conferirCadastro(confirmar(requisicao(c, novos, rodada + '-corrigido')), refsCorrigidas, 'confirmação explícita corrigida');

  const todas = new Set([...refs[0], ...refs[1]]);
  exigir(todas.size === 396, 'as duas massas contêm 396 identidades distintas');
  previa(c, lotes[0], 'A após confirmação corrigida', todas);
  previa(c, lotes[1], 'B após confirmação corrigida', todas);
}
