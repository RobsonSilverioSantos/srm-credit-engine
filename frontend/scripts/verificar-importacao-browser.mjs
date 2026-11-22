import assert from 'node:assert/strict'
import { spawn, spawnSync } from 'node:child_process'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { once } from 'node:events'
import { createServer } from 'node:net'

// Real Chrome/production UI; BFF responses are controlled fixtures, not PostgreSQL evidence.
const diretorio = await mkdtemp(join(tmpdir(), 'srm-f09-browser-'))
let chrome, vite, ws
try {
  const preparacao = spawnSync('python3', ['-B', 'scripts/fixtures_importacao.py', join(diretorio, 'arquivos')], { encoding: 'utf8', timeout: 10000 })
  assert.equal(preparacao.status, 0, preparacao.stderr)
  const fixtures = JSON.parse(preparacao.stdout)
  const [loteA, loteB] = fixtures.slice(-2)
  function previaDaFixture(fixture) {
    return {
      confirmavel: true,
      itens: fixture.linhas.slice(1).map((valores, i) => ({
        linha: i + 2, status: 'NOVO', erros: [], avisoSimulacao: null, recebivelId: null, simulacao: null,
        comando: { referenciaExterna: valores[0], cedenteId: '00000000-0000-4000-8000-000000000001',
          modalidade: valores[2], valorFaceBrl: valores[3], prazoMeses: Number(valores[4]),
          vencimento: valores[5] || null, moedaPagamento: valores[6] },
      })),
    }
  }
  const servidor = createServer().listen(0, '127.0.0.1')
  await once(servidor, 'listening')
  const porta = servidor.address().port
  await new Promise((resolve) => servidor.close(resolve))
  const origem = `http://127.0.0.1:${porta}`
  vite = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', '127.0.0.1', '--port', `${porta}`, '--strictPort'], { stdio: 'pipe' })
  async function esperar(acao, descricao) {
    for (let i = 0; i < 200; i++) {
      if (await acao()) return
      await new Promise((resolve) => setTimeout(resolve, 50))
    }
    throw new Error(`Timeout: ${descricao}`)
  }
  await esperar(async () => { try { return (await fetch(origem)).ok } catch { return false } }, 'preview HTTP')
  chrome = spawn(process.env.CHROME_BIN || 'google-chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--remote-debugging-port=0', `--user-data-dir=${join(diretorio, 'perfil')}`, 'about:blank',
  ], { stdio: ['ignore', 'ignore', 'pipe'] })
  let endpoint = ''
  chrome.stderr.on('data', (bytes) => { endpoint += bytes.toString() })
  await esperar(() => /DevTools listening on (ws:\/\/\S+)/.test(endpoint), 'Chrome CDP')
  const endereco = endpoint.match(/DevTools listening on (ws:\/\/\S+)/)[1]
  const versao = await (await fetch(`http://127.0.0.1:${new URL(endereco).port}/json/list`)).json()
  ws = new WebSocket(versao.find((aba) => aba.type === 'page').webSocketDebuggerUrl)
  await once(ws, 'open')
  let sequencia = 0
  const pendentes = new Map(), requisicoes = [], falhas = []
  function cdp(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++sequencia
      const timer = setTimeout(() => { pendentes.delete(id); reject(new Error(`Timeout CDP: ${method}`)) }, 5000)
      pendentes.set(id, { resolve, reject, timer })
      ws.send(JSON.stringify({ id, method, params }))
    })
  }
  function rejeitarPendentes() {
    for (const { reject, timer } of pendentes.values()) {
      clearTimeout(timer)
      reject(new Error('Conexão CDP encerrada'))
    }
    pendentes.clear()
  }
  ws.addEventListener('close', rejeitarPendentes)
  ws.addEventListener('error', rejeitarPendentes)
  const avaliar = async (expression) => {
    const retorno = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (retorno.exceptionDetails) throw new Error(retorno.exceptionDetails.text)
    return retorno.result.value
  }
  const previa = previaDaFixture(loteA)
  const cadastro = { loteId: 'lote-a', cadastrados: 200,
    itens: previa.itens.map((item, i) => ({ referenciaExterna: item.comando.referenciaExterna, recebivelId: `titulo-${i}` })) }
  const financeira = {
    loteId: 'lote-a', referencia: '2026-11-22T12:00:00Z', aptoParaLiquidar: true, hashPrevia: 'a'.repeat(64),
    totaisPorMoeda: { BRL: '123.00', USD: '123.00' },
    itens: cadastro.itens.map(({ recebivelId }, i) => ({
      recebivelId, versao: 0, estado: 'DISPONIVEL', moedaPagamento: previa.itens[i].comando.moedaPagamento,
      valorFaceBrl: previa.itens[i].comando.valorFaceBrl,
      valorPresenteBrl: '92859.94', desagioBrl: '7140.06', valorPagamento: '92859.94',
      taxaBaseId: 'base', taxaBaseMensal: '0.01', taxaBaseVigenteEm: '2026-11-22T10:00:00Z',
      spreadId: 'spread', spreadMensal: '0.015', spreadVigenteEm: '2026-11-22T10:00:00Z',
      cotacaoId: 'cotacao', taxaBrlPorUsd: '5.4321', cotacaoVigenteEm: '2026-11-22T10:00:00Z',
      cotacaoValidaAte: '2099-11-22T10:30:00Z', fonteCambio: 'DEMO', impedimento: null,
    })),
  }
  const liquidacao = { loteId: 'lote-a', liquidadaEm: '2026-11-22T12:01:00Z',
    itens: financeira.itens.map((item, i) => ({ recebivelId: item.recebivelId, moedaPagamento: item.moedaPagamento,
      liquidacaoId: `liquidacao-${i}`, valorPresenteBrl: '92859.94', desagioBrl: '7140.06', valorPagamento: '92859.94' })) }
  let validacaoAtual = previa
  const referenciasLiquidadas = new Set()
  ws.addEventListener('message', async ({ data }) => {
    const mensagem = JSON.parse(data)
    if (mensagem.id) {
      const promessa = pendentes.get(mensagem.id)
      pendentes.delete(mensagem.id)
      clearTimeout(promessa?.timer)
      if (mensagem.error) promessa?.reject(new Error(mensagem.error.message))
      else promessa?.resolve(mensagem.result)
    } else if (mensagem.method === 'Fetch.requestPaused') {
      const { requestId, request } = mensagem.params
      try {
        const caminho = new URL(request.url).pathname
        assert.equal(request.url, `${origem}${caminho}`, 'BFF sem parâmetros ou dados sensíveis na URL')
        requisicoes.push({ caminho, corpo: request.postData, headers: request.headers })
        let corpo
        if (caminho === '/api/v1/auth/token') corpo = { accessToken: 'jwt-ficticio-browser', expiresIn: 3600 }
        else if (caminho === '/bff/v1/cedentes') corpo = []
        else if (caminho === '/bff/v1/usuarios/me') corpo = { id: 'operador', email: 'operador@example.test', perfil: 'OPERADOR',
          capacidades: { perfisCriaveis: [], podeListarUsuarios: false, podeAlterarStatus: false } }
        else if (caminho.endsWith('/validacoes')) corpo = validacaoAtual
        else if (caminho === '/bff/v1/lotes/recebiveis') corpo = cadastro
        else if (caminho.endsWith('/previas-liquidacao')) corpo = financeira
        else if (caminho.endsWith('/liquidacoes')) {
          corpo = liquidacao
          previa.itens.forEach((item) => referenciasLiquidadas.add(item.comando.referenciaExterna))
        }
        else throw new Error('Endpoint inesperado no teste')
        await cdp('Fetch.fulfillRequest', { requestId, responseCode: 200,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
          body: Buffer.from(JSON.stringify(corpo)).toString('base64') })
      } catch (erro) { falhas.push(erro.message) }
    }
  })
  await cdp('Fetch.enable', { patterns: [{ urlPattern: '*/bff/*' }, { urlPattern: '*/api/*' }] })
  await cdp('Page.navigate', { url: `${origem}/#/importacao` })
  await esperar(() => avaliar('Boolean(document.querySelector(\'input[type="email"]\'))'), 'login')
  await avaliar(`for (const [tipo, valor] of [['email','operador@example.test'],['password','senha-ficticia']]) {
    const input = document.querySelector('input[type="'+tipo+'"]');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,valor);
    input.dispatchEvent(new Event('input',{bubbles:true}));
  }`)
  const clicar = async (texto) => {
    await esperar(() => avaliar(`Boolean([...document.querySelectorAll('button')].find(b=>b.textContent===${JSON.stringify(texto)}&&!b.disabled))`), texto)
    await avaliar(`{const b=[...document.querySelectorAll('button')].find(b=>b.textContent===${JSON.stringify(texto)}&&!b.disabled);b.focus();b.click()}`)
  }
  const textoPresente = (texto) => avaliar(`document.body.textContent.includes(${JSON.stringify(texto)})`)
  await clicar('Entrar')
  await esperar(() => textoPresente('Baixar modelo CSV por CNPJ'), 'tela F09')
  for (const largura of [390, 1280]) {
    await cdp('Emulation.setDeviceMetricsOverride', { width: largura, height: 900, deviceScaleFactor: 1, mobile: largura < 600 })
    assert.ok(await avaliar('document.documentElement.scrollWidth <= innerWidth'), `overflow na largura ${largura}`)
    assert.equal(await avaliar('document.querySelectorAll(\'a[download][href*="modelo-lote"]\').length'), 4)
  }
  async function upload(fixture) {
    validacaoAtual = previaDaFixture(fixture)
    for (const item of validacaoAtual.itens) {
      if (referenciasLiquidadas.has(item.comando.referenciaExterna)) {
        item.status = 'JA_LIQUIDADO'
        item.erros = [{ campo: 'referenciaExterna', mensagem: 'Recebível já liquidado.' }]
      }
    }
    const validas = validacaoAtual.itens.filter((item) => item.status === 'NOVO').length
    validacaoAtual.confirmavel = validas === validacaoAtual.itens.length
    const { root } = await cdp('DOM.getDocument')
    const { nodeId } = await cdp('DOM.querySelector', { nodeId: root.nodeId, selector: 'input[type=file]' })
    await cdp('DOM.setFileInputFiles', { nodeId, files: [fixture.arquivo] })
    await clicar('Pré-validar arquivo')
    await esperar(() => textoPresente(`${validas}/${validacaoAtual.itens.length} linhas válidas`), 'pré-validação completa')
    assert.equal(await avaliar('document.querySelector(\'table[aria-label="Pré-validação de todas as linhas"]\').rows.length'), fixture.linhas.length)
    for (const item of validacaoAtual.itens) assert.ok(await textoPresente(item.comando.referenciaExterna))
  }
  // Both published formats, numeric/alphanumeric/masked documents and UUID compatibility.
  for (const fixture of fixtures.slice(0, -2)) await upload(fixture)
  assert.equal(loteA.linhas.length, 201)
  assert.equal(loteB.linhas.length, 201)
  await upload(loteA)
  await cdp('Emulation.setDeviceMetricsOverride', { width: 390, height: 900, deviceScaleFactor: 1, mobile: true })
  assert.ok(await avaliar('document.documentElement.scrollWidth <= innerWidth'), 'tabela sem overflow da página mobile')
  assert.ok(await avaliar('document.querySelector(\'[role=region][tabindex="0"]\').scrollWidth > document.querySelector(\'[role=region][tabindex="0"]\').clientWidth'), 'grade com rolagem própria e foco')
  assert.equal(requisicoes.filter((r) => r.caminho === '/bff/v1/lotes/recebiveis').length, 0)
  await clicar('Conferir cadastro')
  await esperar(() => avaliar('document.querySelector(\'[role=dialog]\')?.textContent.includes("Confirmar cadastro")'), 'modal cadastro')
  await cdp('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await cdp('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await esperar(() => avaliar('!document.querySelector(\'[role=dialog]\')'), 'Escape')
  await esperar(() => avaliar('document.activeElement?.textContent==="Conferir cadastro"'), 'restauração de foco')
  await clicar('Conferir cadastro')
  await clicar('Confirmar cadastro')
  await esperar(() => textoPresente('200 recebíveis cadastrados'), 'cadastro A')
  assert.equal(requisicoes.filter((r) => r.caminho.endsWith('/liquidacoes')).length, 0)
  const registro = requisicoes.find((r) => r.caminho === '/bff/v1/lotes/recebiveis')
  assert.deepEqual(JSON.parse(registro.corpo), { itens: previa.itens.map((i) => i.comando) })
  await clicar('Obter prévia financeira de todos os itens')
  await esperar(() => textoPresente('200/200 simulações válidas'), 'prévia B17')
  await clicar('Liquidar lote')
  await esperar(() => avaliar('document.querySelector(\'[role=dialog]\')?.textContent.includes("Confirmar liquidação")'), 'modal financeiro independente')
  await clicar('Confirmar liquidação')
  await esperar(() => textoPresente('Todos os 200 títulos foram liquidados'), 'liquidação A')
  const financeiro = requisicoes.find((r) => r.caminho.endsWith('/liquidacoes'))
  const chave = (r) => Object.entries(r.headers).find(([nome]) => nome.toLowerCase() === 'idempotency-key')[1]
  assert.notEqual(chave(registro), chave(financeiro))
  await clicar('Iniciar outro lote')
  await upload(loteB)
  assert.ok(await textoPresente('196/200 linhas válidas'))
  const repetidas = validacaoAtual.itens.filter((item) => item.status === 'JA_LIQUIDADO')
  assert.equal(repetidas.length, 4)
  assert.equal(repetidas.filter((item) => previa.itens[item.linha - 2].comando.referenciaExterna === item.comando.referenciaExterna).length, 2)
  assert.equal(await avaliar('document.querySelectorAll(\'nav[aria-label="Linhas com impedimentos"] a\').length'), 4)
  assert.ok(await avaliar('[...document.querySelectorAll("button")].find(b=>b.textContent==="Conferir cadastro").disabled'))
  assert.ok(await avaliar('localStorage.length===0 && sessionStorage.length===0'))
  assert.deepEqual(falhas, [])
  console.log('Chrome: CSV/XLSX CNPJ e UUID; documentos textuais; desktop/mobile; foco/Escape; A/B 200+200; dois modais e chaves independentes OK (BFF simulado).')
} finally {
  ws?.close()
  for (const processo of [chrome, vite]) {
    if (processo && processo.exitCode === null) {
      const encerrado = once(processo, 'exit')
      processo.kill('SIGTERM')
      await encerrado
    }
  }
  await rm(diretorio, { recursive: true, force: true })
}
