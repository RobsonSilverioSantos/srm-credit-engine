import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Stack, Typography } from '@mui/material'
import { alterarStatusUsuario, criarUsuario, ErroGestaoUsuarios, listarUsuarios, type PortaGestaoUsuarios } from '../aplicacao/gestaoUsuarios'
import { isPerfilCriavel, type MinhaConta, type PerfilCriavel, type UsuarioOrganizacional } from '../dominio/usuarios'
import { CampoFormulario } from '../tema/componentes/CampoFormulario'
import { Carregando, Mensagem } from '../tema/componentes/Feedback'
import { Painel } from '../tema/componentes/Painel'
import { TabelaDados, type EstadoTabela } from '../tema/componentes/TabelaDados'

type Props = {
  porta: PortaGestaoUsuarios
  token: string
  aoExpirar: () => void
}

const perfis: readonly { valor: PerfilCriavel; rotulo: string }[] = [
  { valor: 'ADMIN', rotulo: 'Administrador' },
  { valor: 'OPERADOR', rotulo: 'Operador' },
]

function mensagemFalha(falha: unknown): string {
  return falha instanceof Error ? falha.message : 'Falha inesperada. Tente novamente.'
}

export function PaginaAdministracaoUsuarios({ porta, token, aoExpirar }: Props) {
  const [conta, setConta] = useState<MinhaConta | null>(null)
  const [usuarios, setUsuarios] = useState<UsuarioOrganizacional[]>([])
  const [estadoLista, setEstadoLista] = useState<'carregando' | 'pronto' | 'erro'>('carregando')
  const [erroLista, setErroLista] = useState('')
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')
  const [erroEmail, setErroEmail] = useState('')
  const [erroSenha, setErroSenha] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [perfil, setPerfil] = useState<PerfilCriavel>('OPERADOR')
  const [ocupado, setOcupado] = useState(false)
  const [tentativa, setTentativa] = useState(0)
  const [pendente, setPendente] = useState<UsuarioOrganizacional | null>(null)
  const trava = useRef(false)

  function validarFormulario(): boolean {
    const emailNormalizado = email.trim()
    const bytesSenha = new TextEncoder().encode(senha).length
    const mensagemEmail = !emailNormalizado || emailNormalizado.length > 254
      || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailNormalizado)
      ? 'Informe um e-mail válido.'
      : ''
    const mensagemSenha = bytesSenha < 12 || bytesSenha > 72
      ? 'A senha deve ter de 12 a 72 bytes UTF-8.'
      : ''
    setErroEmail(mensagemEmail)
    setErroSenha(mensagemSenha)
    return !mensagemEmail && !mensagemSenha
  }

  useEffect(() => {
    const controlador = new AbortController()
    let ativa = true

    async function carregar() {
      try {
        const minhaConta = await porta.minhaConta(token, controlador.signal)
        if (!ativa) return
        setConta(minhaConta)
        if (!minhaConta.capacidades.podeListarUsuarios) {
          setEstadoLista('pronto')
          return
        }
        const lista = await listarUsuarios(porta, token, minhaConta, controlador.signal)
        if (!ativa) return
        setUsuarios(lista)
        setEstadoLista('pronto')
      } catch (falha) {
        if (!ativa) return
        if (falha instanceof ErroGestaoUsuarios && falha.status === 401) {
          aoExpirar()
          return
        }
        setErroLista(mensagemFalha(falha))
        setEstadoLista('erro')
      }
    }

    void carregar()
    return () => {
      ativa = false
      controlador.abort()
    }
  }, [aoExpirar, porta, tentativa, token])

  async function cadastrar(evento: FormEvent) {
    evento.preventDefault()
    if (!token || !conta || trava.current || !validarFormulario()) return
    trava.current = true
    setOcupado(true)
    setErro('')
    setSucesso('')
    try {
      const novo = await criarUsuario(porta, token, conta, { email: email.trim(), senha, perfil }, new AbortController().signal)
      setUsuarios((atuais) => [...atuais, novo])
      setEmail('')
      setPerfil(conta.capacidades.perfisCriaveis[0] ?? 'OPERADOR')
      setSucesso('Usuário criado e ativo.')
    } catch (falha) {
      if (falha instanceof ErroGestaoUsuarios && falha.status === 401) {
        aoExpirar()
        return
      }
      setErro(mensagemFalha(falha))
    } finally {
      setSenha('')
      trava.current = false
      setOcupado(false)
    }
  }

  async function alterarStatus(usuario: UsuarioOrganizacional, ativo: boolean) {
    if (!token || !conta || trava.current) return
    trava.current = true
    setOcupado(true)
    setErro('')
    setSucesso('')
    setPendente(null)
    try {
      const atualizado = await alterarStatusUsuario(porta, token, conta, usuario, ativo, new AbortController().signal)
      setUsuarios((atuais) => atuais.map((item) => item.id === atualizado.id ? atualizado : item))
      setSucesso(ativo ? 'Usuário ativado.' : 'Usuário inativado.')
    } catch (falha) {
      if (falha instanceof ErroGestaoUsuarios && falha.status === 401) {
        aoExpirar()
        return
      }
      setErro(mensagemFalha(falha))
    } finally {
      trava.current = false
      setOcupado(false)
    }
  }

  if (estadoLista === 'carregando') {
    return <Painel id="administracao-usuarios" titulo="Administração de usuários"><Carregando rotulo="Verificando permissões…" /></Painel>
  }
  if (conta && !conta.capacidades.podeListarUsuarios && estadoLista === 'pronto') return null

  const estadoTabela: EstadoTabela<UsuarioOrganizacional> = estadoLista === 'erro'
    ? { tipo: 'erro', mensagem: erroLista, acao: <Button color="inherit" onClick={() => {
      setErroLista('')
      setEstadoLista('carregando')
      setTentativa((valor) => valor + 1)
    }}>Tentar novamente</Button> }
    : { tipo: 'pronto', linhas: usuarios }

  return (
    <Painel id="administracao-usuarios" titulo="Administração de usuários" descricao={conta ? `Perfil da sessão: ${conta.perfil}.` : undefined}>
      {conta && <Box component="form" noValidate onSubmit={cadastrar}>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
          <CampoFormulario label="E-mail do usuário" type="email" autoComplete="email" required value={email} disabled={ocupado}
            erro={erroEmail || undefined}
            onChange={(evento) => { setEmail(evento.target.value); setErroEmail('') }} />
          <CampoFormulario label="Senha inicial" type="password" autoComplete="new-password" required value={senha} disabled={ocupado}
            helperText={erroSenha ? undefined : 'A senha deve ter de 12 a 72 bytes UTF-8.'}
            erro={erroSenha || undefined}
            onChange={(evento) => { setSenha(evento.target.value); setErroSenha('') }} />
          <CampoFormulario label="Perfil" value={perfil} disabled={ocupado} opcoes={perfis.filter(({ valor }) => conta.capacidades.perfisCriaveis.includes(valor))}
            onChange={(evento) => {
              const valor = evento.target.value
              if (valor === 'ADMIN' || valor === 'OPERADOR') setPerfil(valor)
            }} />
        </Box>
        <Button type="submit" variant="contained" disabled={ocupado || conta.capacidades.perfisCriaveis.length === 0} sx={{ mt: 2 }}>
          {ocupado ? 'Salvando…' : 'Criar usuário'}
        </Button>
      </Box>}
      {sucesso && <Mensagem severity="success">{sucesso}</Mensagem>}
      {erro && <Mensagem severity="error">{erro}</Mensagem>}
      {ocupado && <Carregando rotulo="Atualizando usuário…" />}
      <Stack spacing={1}>
        <Typography component="h3" variant="h2">Usuários da organização</Typography>
        <TabelaDados
          legenda="Usuários organizacionais"
          chaveLinha={(usuario) => usuario.id}
          estado={estadoTabela}
          vazio={{ titulo: 'Nenhum usuário administrável', descricao: 'Crie um usuário para disponibilizar o acesso.' }}
          colunas={[
            { id: 'email', rotulo: 'E-mail', cabecalhoLinha: true, renderizar: (usuario) => usuario.email },
            { id: 'perfil', rotulo: 'Perfil', renderizar: (usuario) => usuario.perfil },
            { id: 'status', rotulo: 'Status', renderizar: (usuario) => <Chip size="small" color={usuario.ativo ? 'success' : 'default'} label={usuario.ativo ? 'Ativo' : 'Inativo'} /> },
            {
              id: 'acao',
              rotulo: 'Ação',
              renderizar: (usuario) => conta?.capacidades.podeAlterarStatus
                && usuario.id !== conta.id
                && isPerfilCriavel(usuario.perfil)
                && conta.capacidades.perfisCriaveis.includes(usuario.perfil)
                ? usuario.ativo
                  ? <Button color="error" disabled={ocupado} onClick={() => setPendente(usuario)}>Inativar</Button>
                  : <Button disabled={ocupado} onClick={() => void alterarStatus(usuario, true)}>Ativar</Button>
                : '—',
            },
          ]}
        />
      </Stack>
      <Dialog open={Boolean(pendente)} onClose={() => setPendente(null)} aria-labelledby="confirmar-inativacao-titulo">
        <DialogTitle id="confirmar-inativacao-titulo">Inativar usuário?</DialogTitle>
        <DialogContent>
          <DialogContentText>{pendente?.email} perderá o acesso ao sistema. Essa ação pode ser revertida por um administrador autorizado.</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendente(null)}>Cancelar</Button>
          <Button color="error" disabled={ocupado} onClick={() => pendente && void alterarStatus(pendente, false)}>Inativar usuário</Button>
        </DialogActions>
      </Dialog>
    </Painel>
  )
}
