import { useId } from 'react'
import { MenuItem, TextField, type TextFieldProps } from '@mui/material'

type Props = Omit<TextFieldProps, 'label' | 'select' | 'children' | 'error'> & {
  label: string
  erro?: string
  opcoes?: readonly { valor: string; rotulo: string }[]
}

export function CampoFormulario({ id, erro, helperText, opcoes, ...props }: Props) {
  const idGerado = useId()
  return (
    <TextField
      {...props}
      id={id ?? idGerado}
      error={Boolean(erro)}
      helperText={erro ?? helperText}
      select={opcoes !== undefined}
    >
      {opcoes?.map(({ valor, rotulo }) => <MenuItem key={valor} value={valor}>{rotulo}</MenuItem>)}
    </TextField>
  )
}
