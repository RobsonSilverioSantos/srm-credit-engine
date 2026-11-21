import type { TipoDocumento } from './operacoes'

// Apenas caixa ASCII: não converter Unicode para caracteres que o servidor aceitaria.
export function caixaAscii(valor: string): string {
  return valor.replace(/[a-z]/g, (letra) => letra.toUpperCase())
}

const separadores: Record<number, string> = { 2: '.', 5: '.', 8: '/', 12: '-' }

export function formatarCnpj(valor: string, colagem = false): string {
  const texto = caixaAscii(valor)
  // Uma colagem com pontuação só é formatada se for uma máscara completa válida.
  if (colagem && !/^[A-Z0-9]*(?![\s\S])/.test(texto)
    && !/^[A-Z0-9]{2}\.[A-Z0-9]{3}\.[A-Z0-9]{3}\/[A-Z0-9]{4}-[0-9]{2}(?![\s\S])/.test(texto)) return texto
  let numero = ''
  let ultimoSeparador = false
  for (const caractere of texto) {
    if (/^[A-Z0-9]$/.test(caractere)) {
      numero += caractere
      ultimoSeparador = false
    } else if (separadores[numero.length] === caractere && !ultimoSeparador) {
      ultimoSeparador = true
    } else {
      return texto
    }
  }
  if (numero.length > 14) return texto
  return [...numero].map((caractere, indice) => `${separadores[indice] ?? ''}${caractere}`).join('')
}

// Reconstrói a máscara a partir da edição, sem remover caracteres novos inválidos.
export function editarCnpj(
  anterior: string, entrada: string, posicao: number, tipoInput: string, colagem: boolean,
  selecaoAnterior: boolean, dadosInput?: string | null,
): { valor: string; cursor: number } {
  const texto = caixaAscii(entrada)
  const bruto = (valor: string) => valor.replace(/[./-]/g, '')
  const numeroAnterior = bruto(anterior)
  const anteriorFormatado = /^[A-Z0-9]{0,14}(?![\s\S])/.test(numeroAnterior)
    && formatarCnpj(numeroAnterior) === anterior

  if (colagem) {
    const valor = formatarCnpj(texto, true)
    return { valor, cursor: valor === texto ? posicao : formatarCnpj(texto.slice(0, posicao), true).length }
  }
  if (!anteriorFormatado) {
    const valor = /^[A-Z0-9]{0,14}(?![\s\S])/.test(texto) ? formatarCnpj(texto) : texto
    return { valor, cursor: valor === texto ? posicao : formatarCnpj(texto.slice(0, posicao)).length }
  }

  let inicio = 0
  // Limitar a comparação ao cursor evita mover a edição quando há caracteres repetidos.
  const tamanhoInserido = tipoInput.startsWith('insert')
    ? (dadosInput?.length ?? Math.max(0, texto.length - anterior.length)) : 0
  const limite = Math.max(0, posicao - tamanhoInserido)
  while (inicio < limite && inicio < anterior.length && inicio < texto.length && anterior[inicio] === texto[inicio]) inicio++
  let fimAnterior = anterior.length
  let fimTexto = texto.length
  while (fimAnterior > inicio && fimTexto > inicio && anterior[fimAnterior - 1] === texto[fimTexto - 1]) {
    fimAnterior--
    fimTexto--
  }
  const inserido = texto.slice(inicio, fimTexto)
  const removido = anterior.slice(inicio, fimAnterior)
  if (!/^[A-Z0-9]*(?![\s\S])/.test(inserido)) return { valor: texto, cursor: posicao }

  let esquerda = bruto(anterior.slice(0, inicio))
  let direita = bruto(anterior.slice(fimAnterior))
  if (!inserido && removido && !bruto(removido)) {
    // Seleção explícita de pontuação não deve apagar caracteres não selecionados.
    if (selecaoAnterior) return { valor: texto, cursor: posicao }
    if (tipoInput === 'deleteContentForward') direita = direita.slice(1)
    else esquerda = esquerda.slice(0, -1)
  }
  const numero = esquerda + inserido + direita
  if (numero.length > 14) return { valor: texto, cursor: posicao }
  const valor = formatarCnpj(numero)
  const cursor = formatarCnpj(esquerda + inserido).length
  return { valor, cursor }
}

export function erroTipoDocumento(pais: string, tipo: TipoDocumento): string {
  if (pais === 'BR' && tipo !== 'CNPJ') return 'Para BR, selecione CNPJ.'
  if (/^[A-Z]{2}(?![\s\S])/.test(pais) && pais !== 'BR' && tipo !== 'OUTRO') {
    return 'Para país estrangeiro, selecione Outro.'
  }
  return ''
}

export function erroDocumento(pais: string, tipo: TipoDocumento, documento: string): string {
  if (!/^[A-Z]{2}(?![\s\S])/.test(pais)) return 'Informe o código do país com duas letras ASCII.'
  const erroTipo = erroTipoDocumento(pais, tipo)
  if (erroTipo) return erroTipo
  if (tipo === 'CNPJ') {
    if (!/^(?:[A-Z0-9]{12}[0-9]{2}|[A-Z0-9]{2}\.[A-Z0-9]{3}\.[A-Z0-9]{3}\/[A-Z0-9]{4}-[0-9]{2})(?![\s\S])/.test(documento)) {
      return 'Informe um CNPJ com 12 letras ou números e dois dígitos finais.'
    }
  } else if (!/^[A-Z0-9]{1,32}(?![\s\S])/.test(documento)) {
    return 'Use de 1 a 32 letras ASCII ou números, sem espaços ou pontuação.'
  }
  return ''
}
