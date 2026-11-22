import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createServer } from 'node:net'

// Production UI in Chrome; controlled BFF fixtures, not a backend/PostgreSQL E2E.
const diretorio = await mkdtemp(join(tmpdir(), 'srm-f06-browser-'))
let chrome, vite, ws
try {
  const servidor = createServer().listen(0, '127.0.0.1')
  await once(servidor, 'listening')
  const porta = servidor.address().port
  await new Promise((resolve) => servidor.close(resolve))
  const origem = `http://127.0.0.1:${porta}`
  async function esperar(acao, descricao) {
    for (let i = 0; i < 200; i++) {
      if (await acao()) return
      await new Promise((resolve) => setTimeout(resolve, 50))
    }
    throw new Error(`Timeout: ${descricao}`)
  }
  vite = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', '127.0.0.1', '--port', `${porta}`, '--strictPort'], { stdio: 'pipe' })
  await esperar(async () => { try { return (await fetch(origem)).ok } catch { return false } }, 'preview HTTP')
  chrome = spawn(process.env.CHROME_BIN || 'google-chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--remote-debugging-port=0', `--user-data-dir=${join(diretorio, 'perfil')}`, 'about:blank',
  ], { stdio: ['ignore', 'ignore', 'pipe'] })
  let endpoint = ''
  chrome.stderr.on('data', (bytes) => { endpoint += bytes.toString() })
  await esperar(() => /DevTools listening on (ws:\/\/\S+)/.test(endpoint), 'Chrome CDP')
  const endereco = endpoint.match(/DevTools listening on (ws:\/\/\S+)/)[1]
  const abas = await (await fetch(`http://127.0.0.1:${new URL(endereco).port}/json/list`)).json()
  ws = new WebSocket(abas.find((aba) => aba.type === 'page').webSocketDebuggerUrl)
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
  const avaliar = async (expression) => {
    const retorno = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (retorno.exceptionDetails) throw new Error(retorno.exceptionDetails.text)
    return retorno.result.value
  }
  const cotacao = {
    id: '00000000-0000-4000-8000-000000000001', taxaBrlPorUsd: '5.4321000000',
    moedaBase: 'BRL', moedaCotada: 'USD', fonte: 'MANUAL', lado: 'VENDA', versao: 1,
    referencia: '2026-11-22T22:00:00Z', observadaEm: '2026-11-22T22:00:00Z',
    dataBoletim: null, horarioOriginal: null, boletim: null, fuso: null, autorId: 'autor-fixture', justificativa: 'Auditoria-fixture',
  }
  const selecao = {
    id: '00000000-0000-4000-8000-000000000002', fonte: 'MANUAL', cotacaoId: cotacao.id,
    autorizadoEm: '2026-11-22T22:01:00Z', validaAte: '2026-11-23T03:00:00Z',
    politicaId: '00000000-0000-4000-8000-000000000003', autorId: 'autor-fixture', justificativa: 'Auditoria-fixture',
  }
  let perfil = 'ADMIN'
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
        const url = new URL(request.url), caminho = url.pathname
        requisicoes.push({ caminho, metodo: request.method, corpo: request.postData, query: url.search })
        let corpo
        if (caminho === '/api/v1/auth/token') corpo = { accessToken: 'jwt-fixture', expiresIn: 3600 }
        else if (caminho === '/bff/v1/cedentes') corpo = []
        else if (caminho === '/bff/v1/usuarios/me') corpo = { id: 'usuario-fixture', email: 'fixture@example.test', perfil,
          capacidades: { perfisCriaveis: [], podeListarUsuarios: false, podeAlterarStatus: false } }
        else if (caminho === '/bff/v1/cotacoes-cambiais/manuais') corpo = cotacao
        else if (caminho === '/bff/v1/cotacoes-cambiais/ptax/sincronizacoes') corpo = { versoesIncluidas: 1 }
        else if (caminho === '/bff/v1/cotacoes-cambiais/selecoes' && request.method === 'POST') corpo = selecao
        else if (caminho === '/bff/v1/cotacoes-cambiais/selecoes') corpo = { itens: [selecao], pagina: 0, tamanho: 20, total: 1 }
        else if (caminho === '/bff/v1/cotacoes-cambiais') corpo = { itens: [cotacao], pagina: 0, tamanho: 20, total: 1 }
        else throw new Error(`Endpoint inesperado: ${caminho}`)
        await cdp('Fetch.fulfillRequest', {
          requestId, responseCode: request.method === 'POST' && /\/(manuais|selecoes)$/.test(caminho) ? 201 : 200,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
          body: Buffer.from(JSON.stringify(corpo)).toString('base64'),
        })
      } catch (erro) { falhas.push(erro.message) }
    }
  })
  await cdp('Fetch.enable', { patterns: [{ urlPattern: '*/bff/*' }, { urlPattern: '*/api/*' }] })
  await cdp('Emulation.setFocusEmulationEnabled', { enabled: true })
  await cdp('Emulation.setLocaleOverride', { locale: 'pt-BR' })
  const preencher = (rotulo, valor) => avaliar(`{
    const label=[...document.querySelectorAll('label')].find(l=>l.textContent.startsWith(${JSON.stringify(rotulo)}));
    const input=document.getElementById(label.htmlFor);
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,${JSON.stringify(valor)});
    input.dispatchEvent(new Event('input',{bubbles:true}));
  }`)
  async function clicar(texto) {
    await esperar(() => avaliar(`Boolean([...document.querySelectorAll('button')].find(b=>b.textContent===${JSON.stringify(texto)}&&!b.disabled))`), texto)
    await avaliar(`{const b=[...document.querySelectorAll('button')].find(b=>b.textContent===${JSON.stringify(texto)}&&!b.disabled);b.focus();b.click()}`)
  }
  async function entrar(recarregar = false) {
    if (recarregar) await cdp('Page.reload')
    else await cdp('Page.navigate', { url: `${origem}/#/cambio` })
    await esperar(() => avaliar('Boolean(document.querySelector(\'input[type="email"]\'))'), 'login')
    await preencher('E-mail', 'fixture@example.test')
    await preencher('Senha', 'senha-fixture')
    await clicar('Entrar')
    await esperar(() => avaliar('Boolean(document.querySelector(\'section[aria-labelledby="historico-cotacoes"] table\'))'), 'histórico Câmbio')
  }
  await entrar()
  for (const largura of [390, 1280]) {
    await cdp('Emulation.setDeviceMetricsOverride', { width: largura, height: 900, deviceScaleFactor: 1, mobile: largura < 600 })
    await esperar(() => avaliar(`Boolean(document.querySelector(${JSON.stringify(largura < 600 ? 'button[aria-label="Abrir menu de navegação"]' : 'aside nav')}))`), 'layout responsivo')
    assert.ok(await avaliar('document.documentElement.scrollWidth <= innerWidth'), `overflow de página: ${largura}`)
    if (largura < 600) {
      assert.ok(await avaliar('document.querySelector(\'[role=region][tabindex="0"]\').scrollWidth > document.querySelector(\'[role=region][tabindex="0"]\').clientWidth'), 'tabela com rolagem própria')
      await clicar('Menu')
      await esperar(() => avaliar('Boolean(document.querySelector(\'[role=presentation] a[href="#/cambio"]\'))'), 'menu mobile Câmbio')
      await avaliar('document.querySelector(\'[role=presentation] a[href="#/cambio"]\').click()')
    }
  }
  assert.ok(await avaliar('document.querySelector(\'nav[aria-label="Caminho de navegação"]\').textContent.includes("Administração") && document.querySelector("h1").textContent==="Câmbio"'))
  await preencher('Taxa BRL por USD', '5,4321')
  await preencher('Justificativa do cadastro', 'Exceção operacional')
  await clicar('Cadastrar MANUAL')
  await esperar(() => avaliar('Boolean(document.querySelector(\'[role=dialog]\'))'), 'confirmação cadastro')
  assert.equal(requisicoes.filter((item) => item.caminho.endsWith('/manuais')).length, 0)
  await esperar(() => avaliar('document.querySelector(\'[role=dialog]\')?.contains(document.activeElement) && document.activeElement.textContent==="Cancelar"'), 'foco inicial no cancelamento')
  await cdp('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await cdp('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await esperar(() => avaliar('!document.querySelector(\'[role=dialog]\')'), 'Escape')
  assert.equal(await avaliar('document.activeElement?.textContent'), 'Cadastrar MANUAL', 'restauração do foco')
  await clicar('Cadastrar MANUAL')
  await clicar('Confirmar cadastro MANUAL')
  await esperar(() => avaliar('document.body.textContent.includes("cadastrada, ainda não autorizada") && !document.querySelector(\'[role=dialog]\')'), 'cadastro inativo')
  assert.equal(requisicoes.filter((item) => item.metodo === 'POST' && item.caminho.endsWith('/selecoes')).length, 0)
  assert.deepEqual(JSON.parse(requisicoes.find((item) => item.caminho.endsWith('/manuais')).corpo), {
    taxaBrlPorUsd: '5.4321', justificativa: 'Exceção operacional',
  })
  await preencher('Justificativa da seleção', 'Autorizar exceção')
  await clicar('Autorizar uso da MANUAL')
  await clicar('Confirmar autorização MANUAL')
  await esperar(() => avaliar('!document.querySelector(\'[role=dialog]\')'), 'autorização')
  assert.deepEqual(JSON.parse(requisicoes.find((item) => item.metodo === 'POST' && item.caminho.endsWith('/selecoes')).corpo), {
    fonte: 'MANUAL', cotacaoId: cotacao.id, justificativa: 'Autorizar exceção',
  })
  assert.equal(requisicoes.filter((item) => item.caminho.endsWith('/sincronizacoes')).length, 0)
  assert.ok(await avaliar('localStorage.length===0 && sessionStorage.length===0'))
  perfil = 'OPERADOR'
  await entrar(true)
  assert.ok(await avaliar('!document.querySelector("#nova-cotacao-manual") && !document.querySelector("#selecao-cambial")'))
  assert.ok(await avaliar('!document.body.textContent.includes("Auditoria-fixture") && !document.body.textContent.includes("autor-fixture")'))
  assert.deepEqual(falhas, [])
  console.log('Chrome F06: 390/1280px, menu/breadcrumbs, tabelas com rolagem, Escape/foco, cadastro e autorização separados, payload textual, RBAC e JWT em memória OK (BFF simulado).')
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
