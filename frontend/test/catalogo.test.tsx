import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test } from 'vitest'
import { consultarCatalogoVisual } from '../src/aplicacao/catalogoVisual'
import { catalogoEmMemoria } from '../src/infraestrutura/catalogoEmMemoria'
import { PaginaCatalogo } from '../src/apresentacao/PaginaCatalogo'
import { tema } from '../src/tema/tema'

function renderizarCatalogo() {
  const user = userEvent.setup()
  render(
    <ThemeProvider theme={tema}>
      <PaginaCatalogo catalogo={consultarCatalogoVisual(catalogoEmMemoria)} />
    </ThemeProvider>,
  )
  return user
}

test('identifica os dados fictícios e expõe cabeçalhos e valores na tabela', () => {
  renderizarCatalogo()
  expect(screen.getByText('Dados fictícios.')).toBeVisible()
  expect(screen.getByText('Acesso demonstrativo')).toBeVisible()
  const tabela = screen.getByRole('table', { name: 'Recebíveis fictícios' })
  expect(within(tabela).getByRole('columnheader', { name: 'Valor de face' })).toBeVisible()
  const linha = within(tabela).getByRole('rowheader', { name: 'DEMO-001' }).closest('tr')!
  expect(within(linha).getByRole('cell', { name: 'R$ 18.500,00' })).toBeVisible()
  expect(screen.getByRole('region', { name: 'Recebíveis fictícios' })).toHaveAttribute('tabindex', '0')
})

test('associa o erro ao campo, foca a referência e limpa o feedback ao editar', async () => {
  const user = renderizarCatalogo()
  const referencia = screen.getByRole('textbox', { name: /referência/i })
  const validar = screen.getByRole('button', { name: 'Validar exemplo' })
  await user.click(validar)
  expect(referencia).toHaveFocus()
  expect(referencia).toBeInvalid()
  expect(referencia).toHaveAccessibleDescription('Informe uma referência fictícia.')
  await user.type(referencia, 'DEMO-004')
  expect(referencia).toBeValid()
  await user.click(validar)
  expect(screen.getByText('Exemplo validado. Nenhum dado foi enviado ou salvo.')).toBeVisible()
  await user.type(referencia, 'A')
  expect(screen.queryByText(/Exemplo validado/)).not.toBeInTheDocument()
})

test('alterna entre vazio, carregando, erro e dados sem manter uma tabela obsoleta', async () => {
  const user = renderizarCatalogo()
  const seletor = screen.getByRole('combobox', { name: 'Estado da tabela' })
  const painel = within(screen.getByRole('region', { name: 'Tabela e estados' }))

  async function selecionar(nome: string) {
    await user.click(seletor)
    await user.click(screen.getByRole('option', { name: nome }))
  }

  await selecionar('Vazio')
  expect(painel.getByRole('status')).toHaveTextContent('Nenhum registro')
  expect(painel.queryByRole('table')).not.toBeInTheDocument()
  await selecionar('Carregando')
  expect(painel.getByRole('status')).toHaveTextContent('Carregando registros…')
  await selecionar('Erro')
  expect(painel.getByRole('alert')).toHaveTextContent('Falha fictícia')
  await selecionar('Com dados')
  expect(painel.getByRole('table')).toBeVisible()
  expect(painel.queryByRole('alert')).not.toBeInTheDocument()
})

test('abre o menu compacto e o Escape restaura o foco no acionador', async () => {
  const user = renderizarCatalogo()
  expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveAttribute('href', '#conteudo')
  const menu = screen.getByRole('button', { name: 'Menu' })
  await user.click(menu)
  const navegacao = screen.getByRole('navigation', { name: 'Seções do catálogo' })
  expect(within(navegacao).getByRole('link', { name: 'Formulário' })).toHaveAttribute('href', '#formulario')
  await user.keyboard('{Escape}')
  expect(menu).toHaveFocus()
  expect(menu).toHaveAttribute('aria-expanded', 'false')
})
