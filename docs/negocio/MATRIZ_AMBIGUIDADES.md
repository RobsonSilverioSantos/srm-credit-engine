# Matriz de premissas não homologadas — novo MVP

| Tema | Premissa demonstrativa | Risco / confirmação necessária |
|---|---|---|
| Prazo | `prazoMeses` informado, inteiro positivo; vencimento informativo | Definir convenção por dias úteis/corridos e arredondamentos do negócio |
| Taxa base | 1,00% a.m. inicial e versionada | Fonte e vigência oficial ainda não homologadas |
| Spread | Duplicata 1,50%; cheque 2,50% a.m. | Políticas comerciais podem evoluir |
| Câmbio | BRL por unidade USD, cotação intradiária mockada e válida até 30 min | Negócio deve definir PTAX, horário e taxa negociada |
| Elegibilidade | Recebível disponível e da organização autenticada | Regras documentais da cessão podem exigir outros controles |
| Segurança | Autenticação local para demonstração com organização no principal | Não equivale a SSO, MFA nem governança produtiva |
| Liquidação | Antecipação ao cedente, sem transferência real | Diferenciar pagamento bancário, estorno e conciliação futura |
