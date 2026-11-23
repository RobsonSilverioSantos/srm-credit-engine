import { useRef, useState, type FormEvent } from 'react'
import { Box, Button, Chip, Stack, Typography } from '@mui/material'
import type { CatalogoVisual } from '../aplicacao/catalogoVisual'
import { CampoFormulario } from '../tema/componentes/CampoFormulario'
import { Mensagem } from '../tema/componentes/Feedback'
import { Painel } from '../tema/componentes/Painel'
import { TabelaDados, type EstadoTabela } from '../tema/componentes/TabelaDados'
import { LayoutAdministrativo } from './LayoutAdministrativo'

const secoes = [
  { id: 'formulario', rotulo: 'Formulário' },
  { id: 'tabela', rotulo: 'Tabela e estados' },
  { id: 'mensagens', rotulo: 'Mensagens' },
]

type Linha = CatalogoVisual['recebiveis'][number]
type ExemploEstado = 'dados' | 'vazio' | 'carregando' | 'erro'

const exemplos = [
  { valor: 'dados', rotulo: 'Com dados' },
  { valor: 'vazio', rotulo: 'Vazio' },
  { valor: 'carregando', rotulo: 'Carregando' },
  { valor: 'erro', rotulo: 'Erro' },
] as const satisfies readonly { valor: ExemploEstado; rotulo: string }[]

function FormularioDemonstrativo() {
  const [codigo, setCodigo] = useState('')
  const [validado, setValidado] = useState(false)
  const campoCodigo = useRef<HTMLInputElement>(null)
  const erro = validado && codigo.trim() === '' ? 'Informe uma referência fictícia.' : undefined

  function validar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setValidado(true)
    if (codigo.trim() === '') campoCodigo.current?.focus()
  }

  return (
    <Stack component="form" noValidate onSubmit={validar} spacing={2}>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
        <CampoFormulario
          required inputRef={campoCodigo} label="Referência" placeholder="Ex.: DEMO-001"
          value={codigo} erro={erro} helperText="Não informe dados reais."
          onChange={(evento) => { setCodigo(evento.target.value); setValidado(false) }}
        />
        <CampoFormulario
          label="Modalidade" defaultValue="duplicata"
          opcoes={[{ valor: 'duplicata', rotulo: 'Duplicata' }, { valor: 'cheque', rotulo: 'Cheque' }]}
          onChange={() => setValidado(false)}
        />
      </Box>
      <Box><Button variant="contained" type="submit">Validar exemplo</Button></Box>
      {validado && !erro && <Mensagem severity="success">Exemplo validado. Nenhum dado foi enviado ou salvo.</Mensagem>}
    </Stack>
  )
}

export function PaginaCatalogo({ catalogo }: { catalogo: CatalogoVisual }) {
  const [exemplo, setExemplo] = useState<ExemploEstado>('dados')
  const estados: Record<ExemploEstado, EstadoTabela<Linha>> = {
    dados: { tipo: 'pronto', linhas: catalogo.recebiveis },
    vazio: { tipo: 'pronto', linhas: [] },
    carregando: { tipo: 'carregando' },
    erro: { tipo: 'erro', mensagem: 'Falha fictícia ao carregar registros.' },
  }

  return (
    <LayoutAdministrativo secoes={secoes}>
      <Box component="header">
        <Typography variant="overline" color="secondary.main" fontWeight={700}>SRM · F00</Typography>
        <Typography variant="h1" gutterBottom>Catálogo visual</Typography>
        <Typography color="text.secondary">Componentes e estados da interface administrativa de recebíveis.</Typography>
      </Box>
      <Mensagem><strong>Dados fictícios.</strong> Este catálogo não usa contas reais, não acessa o BFF e não executa operações financeiras.</Mensagem>

      <Painel id="formulario" titulo="Formulário" descricao="Campos com instruções, seleção e validação local de exemplo.">
        <FormularioDemonstrativo />
      </Painel>

      <Painel id="tabela" titulo="Tabela e estados" descricao="Alterne o estado da tabela para conferir o feedback da interface.">
        <Box sx={{ maxWidth: 280 }}>
          <CampoFormulario
            label="Estado da tabela" value={exemplo} opcoes={exemplos}
            onChange={(evento) => {
              const opcao = exemplos.find(({ valor }) => valor === evento.target.value)
              if (opcao) setExemplo(opcao.valor)
            }}
          />
        </Box>
        <TabelaDados
          legenda="Recebíveis fictícios"
          chaveLinha={(linha) => linha.id}
          estado={estados[exemplo]}
          colunas={[
            { id: 'referencia', rotulo: 'Referência', cabecalhoLinha: true, renderizar: (linha) => linha.id },
            { id: 'cedente', rotulo: 'Cedente', renderizar: (linha) => linha.cedente },
            { id: 'modalidade', rotulo: 'Modalidade', renderizar: (linha) => linha.modalidade },
            { id: 'valor', rotulo: 'Valor de face', alinhar: 'right', renderizar: (linha) => linha.valorExibicao },
            {
              id: 'situacao', rotulo: 'Situação',
              renderizar: (linha) => <Chip size="small" variant="outlined" color={linha.situacao === 'Disponível' ? 'success' : 'default'} label={linha.situacao} />,
            },
          ]}
        />
      </Painel>

      <Painel id="mensagens" titulo="Mensagens" descricao="Exemplos estáticos de feedback; nenhum corresponde a uma operação real.">
        <Stack spacing={1.5}>
          <Mensagem severity="success">Sucesso: os campos do exemplo estão preenchidos.</Mensagem>
          <Mensagem severity="warning">Atenção: confira a referência antes de continuar.</Mensagem>
          <Mensagem severity="info">Informação: o catálogo não persiste dados.</Mensagem>
        </Stack>
      </Painel>
    </LayoutAdministrativo>
  )
}
