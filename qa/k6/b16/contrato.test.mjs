import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const ler = (p) => readFileSync(new URL(p, import.meta.url), 'utf8');
const fonte = ler('./concorrencia.js').replace(/^import .*;$/gm, '')
  .replace('export const options', 'const options')
  .replace('export default function ()', 'function cenario()');
const cedente = '10000000-0000-4000-8000-000000000001';

function executar({ vencedor = 0, parcial = false, organizacaoOcupada = false, origem = 'http://127.0.0.1:8080' } = {}) {
  const recebiveis = new Map(organizacaoOcupada ? [['SRM-DEMO-A-0001', 'externo']] : []);
  const idempotencia = new Map();
  let batchs = 0;
  let repositorios = 0;
  const res = (status, corpo = {}) => ({ status, json: () => corpo });
  function commit(comandos, chave) {
    const corpo = {
      loteId: 'lote-' + idempotencia.size,
      cadastrados: comandos.length,
      itens: comandos.map(({ referenciaExterna }) => ({ referenciaExterna, recebivelId: 'id-' + referenciaExterna })),
    };
    for (const item of corpo.itens) recebiveis.set(item.referenciaExterna, item.recebivelId);
    idempotencia.set(chave, corpo);
    return res(201, corpo);
  }
  const ambiente = {
    __ENV: {
      SRM_B16_AMBIENTE_DESCARTAVEL: 'sim',
      SRM_B16_BASE_URL: origem,
      SRM_B16_JWT: 'fake.jwt.value',
      SRM_B16_CEDENTE_ID: cedente,
    },
    open: ler,
    fail: (msg) => { throw new Error(msg); },
    check: (v, regras) => Object.values(regras).every((regra) => regra(v)),
    http: {
      expectedStatuses: (...status) => status,
      file: (dados) => dados,
      post: (url, { arquivo }, opts) => {
        repositorios++;
        assert.equal(url, 'http://127.0.0.1:8080/api/v1/lotes/recebiveis/validacoes');
        assert.equal(opts.redirects, 0);
        const itens = arquivo.trimEnd().split('\n').slice(1).map((l, i) => {
          const [referenciaExterna, cedenteId] = l.split(';');
          assert.equal(cedenteId, cedente);
          const existente = recebiveis.has(referenciaExterna);
          return { linha: i + 2, status: existente ? 'JA_CADASTRADO' : 'NOVO',
            comando: existente ? null : { referenciaExterna, cedenteId } };
        });
        return res(200, { confirmavel: itens.every((item) => item.status === 'NOVO'), itens });
      },
      request: (_metodo, _url, corpo, { headers }) => {
        const chave = headers['Idempotency-Key'];
        return idempotencia.has(chave)
          ? res(201, idempotencia.get(chave))
          : commit(JSON.parse(corpo).itens, chave);
      },
      batch: (requests) => {
        batchs++;
        assert.equal(recebiveis.size, 0);
        assert.equal(repositorios, 2);
        assert.equal(requests.length, 2);
        const chaves = requests.map((r) => r.params.headers['Idempotency-Key']);
        assert.notEqual(chaves[0], chaves[1]);
        const comandos = requests.map((r) => JSON.parse(r.body).itens);
        const sucesso = commit(comandos[vencedor], chaves[vencedor]);
        if (parcial) {
          const sobras = comandos[1 - vencedor].filter((item) => !recebiveis.has(item.referenciaExterna));
          recebiveis.set(sobras[0].referenciaExterna, 'vazamento-parcial');
        }
        const conflito = res(409);
        return vencedor === 0 ? [sucesso, conflito] : [conflito, sucesso];
      },
    },
  };
  vm.runInNewContext(fonte + '\ncenario()', ambiente);
  return { cadastrados: recebiveis.size, lotes: idempotencia.size, batchs };
}

for (const vencedor of [0, 1]) {
  test('reconhece qualquer vencedor e exige reimportação explícita: ' + ['A', 'B'][vencedor], () => {
    assert.deepEqual(executar({ vencedor }), { cadastrados: 396, lotes: 2, batchs: 1 });
  });
}
test('um 409 que deixa linha exclusiva persistida não passa', () => {
  assert.throws(() => executar({ parcial: true }), /perdedor após corrida/);
});
test('organização com uma referência já cadastrada não inicia a corrida', () => {
  assert.throws(() => executar({ organizacaoOcupada: true }), /A inicial: apenas duplicidades/);
});
test('recusa destino fora do loopback antes de enviar pedidos', () => {
  assert.throws(() => executar({ origem: 'https://example.com' }), /URL HTTP restrita ao loopback/);
});
