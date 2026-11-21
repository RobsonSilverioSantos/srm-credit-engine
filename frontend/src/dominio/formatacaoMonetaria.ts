type EntradaValida = { valida: true; valorCanonico: string }
type EntradaInvalida = { valida: false; erro: string }
export type ResultadoEntradaMonetaria = EntradaValida | EntradaInvalida

export function normalizarEntradaMonetaria(valor: string, locale: string): ResultadoEntradaMonetaria {
  const texto = normalizarDigitos(valor.trim(), locale)
  if (!texto) return { valida: false, erro: 'Informe um valor.' }

  const { decimal, agrupamento, largurasGrupos } = separadoresLocale(locale)
  const partes = texto.split(decimal)
  if (partes.length > 2) return { valida: false, erro: 'Informe um valor válido para o idioma selecionado.' }

  const [inteiroDigitado, fracaoDigitada] = partes
  if (!inteiroDigitado || (partes.length === 2 && !/^[0-9]{1,2}$/.test(fracaoDigitada ?? ''))) {
    return { valida: false, erro: 'Informe um valor válido com até 2 casas decimais.' }
  }

  let inteiro: string
  if (agrupamento && inteiroDigitado.includes(agrupamento)) {
    const grupos = inteiroDigitado.split(agrupamento)
    if (grupos.some((grupo) => !/^[0-9]+$/.test(grupo)) || !agrupamentoValido(grupos, largurasGrupos)) {
      return { valida: false, erro: 'Use os separadores do idioma selecionado.' }
    }
    inteiro = grupos.join('')
  } else {
    if (!/^[0-9]+$/.test(inteiroDigitado)) {
      return { valida: false, erro: 'Use os separadores do idioma selecionado.' }
    }
    inteiro = inteiroDigitado
  }

  inteiro = inteiro.replace(/^0+(?=[0-9])/, '')
  if (inteiro.length > 17) {
    return { valida: false, erro: 'O valor deve ter no máximo 17 dígitos antes dos centavos.' }
  }

  const fracao = (fracaoDigitada ?? '').padEnd(2, '0')
  if (BigInt(inteiro) === 0n && BigInt(fracao) === 0n) {
    return { valida: false, erro: 'Informe um valor maior que zero.' }
  }

  return { valida: true, valorCanonico: `${inteiro}.${fracao}` }
}

export function separadorDecimalMonetario(locale: string): string {
  const decimal = new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 })
    .formatToParts(0n)
    .find((parte) => parte.type === 'decimal')?.value
  if (!decimal) throw new Error(`Separador decimal indisponível para o idioma ${locale}.`)
  return decimal
}

export function formatarMonetario(valor: string, moeda: 'BRL' | 'USD', locale: string): string {
  const arredondado = arredondarDecimal(valor, 2)
  const [inteiro, fracao] = arredondado.split('.')
  const formatador = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: moeda,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  const partes = formatador.formatToParts(BigInt(inteiro ?? '0'))
  const fracaoLocalizada = localizarDigitos(fracao ?? '00', locale)
  return partes.map((parte) => parte.type === 'fraction' ? fracaoLocalizada : parte.value).join('')
}

export function formatarEntradaMonetaria(valorCanonico: string, locale: string): string {
  const correspondencia = /^([0-9]+)\.([0-9]{2})$/.exec(valorCanonico)
  if (!correspondencia) throw new Error('Valor decimal canônico inválido.')

  const inteiro = new Intl.NumberFormat(locale, { useGrouping: true, maximumFractionDigits: 0 })
    .format(BigInt(correspondencia[1] ?? '0'))
  const decimal = separadorDecimalMonetario(locale)
  return `${inteiro}${decimal}${localizarDigitos(correspondencia[2] ?? '00', locale)}`
}

export function formatarPercentual(fracao: string, locale: string): string {
  const percentual = deslocarDecimal(fracao, 2)
  const partesNumero = partesDecimalLocalizado(percentual, locale)
  const partesModelo = new Intl.NumberFormat(locale, {
    style: 'percent',
    maximumFractionDigits: 0,
  }).formatToParts(0n)
  let inseriuNumero = false
  return partesModelo.map((parte) => {
    if (parte.type === 'integer' && !inseriuNumero) {
      inseriuNumero = true
      return partesNumero.map((parteNumero) => parteNumero.value).join('')
    }
    return parte.value
  }).join('')
}

export function formatarCotacao(valor: string, locale: string): string {
  const correspondencia = /^(-?)([0-9]+)(?:\.([0-9]+))?$/.exec(valor)
  if (!correspondencia) throw new Error('Valor decimal canônico inválido.')

  // Exibir no mínimo quatro casas (PTAX) e remover apenas zeros à direita
  // além da quarta casa. Dígitos significativos adicionais são preservados.
  // A formatação não altera a string recebida nem arredonda a taxa financeira.
  const fracao = (correspondencia[3] ?? '').padEnd(4, '0')
  const fracaoExibida = fracao.slice(0, 4) + fracao.slice(4).replace(/0+$/, '')
  const valorExibido = `${correspondencia[1] ?? ''}${correspondencia[2]}.${fracaoExibida}`
  return partesDecimalLocalizado(valorExibido, locale).map((parte) => parte.value).join('')
}

function arredondarDecimal(valor: string, escala: number): string {
  const correspondencia = /^(-?)([0-9]+)(?:\.([0-9]+))?$/.exec(valor)
  if (!correspondencia) throw new Error('Valor decimal canônico inválido.')

  const sinal = correspondencia[1] ?? ''
  const inteiro = correspondencia[2] ?? '0'
  const fracao = correspondencia[3] ?? ''
  const retida = fracao.slice(0, escala).padEnd(escala, '0')
  const descartada = fracao.slice(escala)
  let escalado = BigInt(`${inteiro}${retida}`)
  const primeiroDescartado = descartada[0] ?? '0'
  const demaisDescartadosNaoNulos = /[1-9]/.test(descartada.slice(1))
  const metadeParaCima = primeiroDescartado > '5'
    || (primeiroDescartado === '5' && demaisDescartadosNaoNulos)
    || (primeiroDescartado === '5' && escalado % 2n === 1n)
  if (metadeParaCima) escalado += 1n

  const divisao = 10n ** BigInt(escala)
  const inteiroArredondado = (escalado / divisao).toString()
  const fracaoArredondada = (escalado % divisao).toString().padStart(escala, '0')
  return `${sinal}${inteiroArredondado}.${fracaoArredondada}`
}

function deslocarDecimal(valor: string, casas: number): string {
  const correspondencia = /^(-?)([0-9]+)(?:\.([0-9]+))?$/.exec(valor)
  if (!correspondencia) throw new Error('Valor decimal canônico inválido.')

  const sinal = correspondencia[1] ?? ''
  const inteiro = correspondencia[2] ?? '0'
  const fracao = correspondencia[3] ?? ''
  const digitos = `${inteiro}${fracao}`
  const posicao = inteiro.length + casas
  const resultado = posicao >= digitos.length
    ? `${digitos}${'0'.repeat(posicao - digitos.length)}`
    : `${digitos.slice(0, posicao)}.${digitos.slice(posicao)}`
  const [inteiroAjustado, fracaoAjustada] = resultado.split('.')
  const inteiroNormalizado = (inteiroAjustado ?? '0').replace(/^0+(?=[0-9])/, '')
  return `${sinal}${inteiroNormalizado}${fracaoAjustada === undefined ? '' : `.${fracaoAjustada}`}`
}

function partesDecimalLocalizado(valor: string, locale: string): Intl.NumberFormatPart[] {
  const correspondencia = /^(-?)([0-9]+)(?:\.([0-9]+))?$/.exec(valor)
  if (!correspondencia) throw new Error('Valor decimal canônico inválido.')

  const sinal = correspondencia[1] ?? ''
  const inteiro = correspondencia[2] ?? '0'
  const fracao = correspondencia[3] ?? ''
  const formatador = new Intl.NumberFormat(locale, { useGrouping: true, maximumFractionDigits: 0 })
  const partesInteiro = formatador.formatToParts(BigInt(`${sinal}${inteiro}`))
  if (!fracao) return partesInteiro

  const decimal = separadorDecimalMonetario(locale)

  const ultimoInteiro = partesInteiro.reduce((ultimo, parte, indice) => parte.type === 'integer' ? indice : ultimo, -1)
  const antesDaFracao = partesInteiro.slice(0, ultimoInteiro + 1)
  const depoisDoInteiro = partesInteiro.slice(ultimoInteiro + 1)
  const partes: Intl.NumberFormatPart[] = [
    ...antesDaFracao,
    { type: 'decimal', value: decimal },
    { type: 'fraction', value: localizarDigitos(fracao, locale) },
    ...depoisDoInteiro,
  ]
  return partes
}

function localizarDigitos(digitos: string, locale: string): string {
  const formatador = new Intl.NumberFormat(locale, { useGrouping: false, maximumFractionDigits: 0 })
  const mapa = Array.from({ length: 10 }, (_, digito) => formatador.format(BigInt(digito)))
  return [...digitos].map((digito) => mapa[digito.charCodeAt(0) - 48] ?? digito).join('')
}

function normalizarDigitos(valor: string, locale: string): string {
  const formatador = new Intl.NumberFormat(locale, { useGrouping: false, maximumFractionDigits: 0 })
  const mapa = new Map(Array.from({ length: 10 }, (_, digito) => [formatador.format(BigInt(digito)), String(digito)]))
  return [...valor].map((caractere) => mapa.get(caractere) ?? caractere).join('')
}

function separadoresLocale(locale: string): { decimal: string; agrupamento: string | null; largurasGrupos: number[] } {
  const partes = new Intl.NumberFormat(locale, { useGrouping: true, maximumFractionDigits: 0 })
    .formatToParts(12345678901234567n)
  return {
    decimal: separadorDecimalMonetario(locale),
    agrupamento: partes.find((parte) => parte.type === 'group')?.value ?? null,
    largurasGrupos: partes.filter((parte) => parte.type === 'integer').map((parte) => [...parte.value].length),
  }
}

function agrupamentoValido(grupos: string[], largurasEsperadas: number[]): boolean {
  if (grupos.length < 2 || grupos.length > largurasEsperadas.length) return false
  return grupos.every((grupo, indice) => {
    const posicaoEsperada = largurasEsperadas.length - grupos.length + indice
    const largura = largurasEsperadas[posicaoEsperada]
    return largura !== undefined && (indice === 0 ? grupo.length > 0 && grupo.length <= largura : grupo.length === largura)
  })
}
