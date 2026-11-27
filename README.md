# Biblioteca Escolar Dom João Nery

Sistema de biblioteca para o TCC de Vitor Miguel, Kaua, João Bartel, Davi Leal e Felipe Toisso.

## O que o sistema faz

- Catálogo público com busca por título, autor, assunto ou código, filtro por assunto, detalhes e disponibilidade.
- Painel com login administrativo para criar, editar, inativar e, quando permitido pelo histórico, excluir cadastros.
- Código **LIV** para cada título e código **EX** para cada exemplar físico. Os códigos são gerados ao salvar e permanecem os mesmos depois de uma edição.
- Empréstimo por exemplar, devolução, histórico, filtros de atraso e relatório CSV.
- QR Code em PNG para o endereço do catálogo.
- Automação em Python para verificar a API, gerar o QR Code e exportar relatórios.

## Como usar na apresentação

1. Abra a URL pública da Vercel no computador e no celular. Pesquise por autor ou assunto e abra os detalhes de um livro.
2. Entre em **Área administrativa** com a conta inicial criada na implantação. Cadastre um autor e um assunto caso o banco esteja vazio.
3. Em **Livros e exemplares**, cadastre um título com um exemplar e anote os códigos LIV e EX. Cadastre um aluno fictício.
4. Em **Empréstimos e relatórios**, empreste o exemplar. O catálogo deve mostrar zero disponíveis e o painel deixa de oferecer esse título para novo empréstimo.
5. Registre a devolução. O estoque volta a um e o histórico continua disponível.
6. Abra **QR Code** no site publicado e baixe o PNG. Escaneie em outro aparelho para confirmar a abertura da página pública.

## Arquitetura

| Camada | Implementação |
| --- | --- |
| Interface | HTML, CSS e JavaScript do navegador em `frontend/public`, hospedados na Vercel |
| Encaminhamento | A função `frontend/api/proxy.mjs` envia requisições de `/api` para a API HTTPS, no mesmo domínio para o navegador |
| Regras de negócio | Java 21 com Spring Boot e JDBC em uma aplicação Docker no Render |
| Dados | PostgreSQL 16 no Render, com migrações Flyway em `src/main/resources/db/migration` |
| Automação | Python 3.10 ou superior com biblioteca padrão, em `automacao` |

O navegador envia pedidos à API e nunca recebe credenciais do PostgreSQL. A API usa sessão administrativa, cookie HttpOnly e Secure em HTTPS, validação de entrada e senhas com BCrypt. O frontend público recebe apenas os dados do catálogo. O índice único parcial impede dois empréstimos ativos para o mesmo exemplar e as chaves estrangeiras preservam o histórico. Desativar um título remove-o do catálogo, preservando seus registros e permitindo devolver cópias emprestadas.

## Implantação na nuvem

### GitHub e Render

1. Envie o código para um repositório GitHub. Use o arquivo `.gitignore` e exclua arquivos com senhas ou exportações de alunos do envio.
2. No Render, crie um **Blueprint** para o repositório. O arquivo `render.yaml` pede a criação de um serviço Docker e um PostgreSQL na mesma região. A variável `DATABASE_URL` é alimentada pelo endereço interno do banco.
3. Informe `ADMIN_EMAIL` e `ADMIN_PASSWORD` nas variáveis privadas do serviço Java. Use uma senha inicial com pelo menos 14 caracteres e no máximo 72 bytes em UTF-8. O aplicativo cria a conta quando o e-mail ainda não existe. Alterar a variável de ambiente de uma conta já criada não redefine a senha.
4. Se quiser os registros de demonstração, defina `DEMO_DATA=true` antes de iniciar o serviço. A primeira execução insere títulos de exemplo e alunos fictícios. Deixe `false` para usar um acervo vazio.
5. Depois que o serviço estiver online, confirme `GET /actuator/health` na sua URL `.onrender.com` e confira `GET /api/catalog`.

O Blueprint seleciona o plano gratuito para demonstração. O PostgreSQL gratuito expira 30 dias após a criação. Se for necessária hospedagem permanente ou se a apresentação estiver marcada para depois do prazo, ajuste o plano antes de criar o banco. [Limites do plano gratuito](https://render.com/docs/free)

### Vercel

1. Importe o repositório e selecione `frontend` em **Root Directory**. O projeto usa o preset **Other**, com a pasta pública definida em `vercel.json`.
2. Defina `BIBLIOTECA_API_ORIGIN` em **Environment Variables** no ambiente Production. O valor é a origem HTTPS da API Java, como `https://nome-do-servico.onrender.com`, sem `/api`.
3. Publique o projeto e abra o endereço estável `.vercel.app` em uma aba anônima. Confirme que o catálogo abre, o login funciona e as alterações reaparecem depois de atualizar a página.
4. Gere o QR Code com a URL pública da Vercel. Faça a última conferência no celular, de preferência por outra rede.

| Credencial | Onde obter ou configurar |
| --- | --- |
| Login administrativo | `ADMIN_EMAIL` e `ADMIN_PASSWORD` no serviço Java do Render |
| Usuário, senha, host e banco PostgreSQL | Painel do banco no Render, menu **Connect** |
| Endereço da API | Painel do serviço Java no Render |
| Endereço público do catálogo | Painel do projeto na Vercel, seção **Domains** |

A aplicação utiliza a rede interna do Render para acessar o PostgreSQL. A configuração padrão do Blueprint mantém o acesso externo ao banco desabilitado.

## Automação

Execute os comandos abaixo na pasta do projeto, substituindo o domínio pelo endereço de produção.

```powershell
python automacao/biblioteca.py --url https://seu-projeto.vercel.app verificar
python automacao/biblioteca.py --url https://seu-projeto.vercel.app qrcode --saida qrcode-acervo.png
python automacao/biblioteca.py --url https://seu-projeto.vercel.app relatorio --tipo atrasados --saida atrasados.csv
```

O relatório solicita o e-mail e a senha de forma interativa. Em automações, configure `BIBLIOTECA_ADMIN_EMAIL` e `BIBLIOTECA_ADMIN_PASSWORD` no ambiente de execução. O CSV tem os dados privados necessários para a equipe identificar os empréstimos e deve ficar com a equipe autorizada.

## Verificação técnica

`automacao/teste_integracao.py` verifica login, pesquisa, estoque, códigos, devolução, preservação do histórico, relatórios e QR Code. Execute-o em um banco separado de testes. O roteiro cria registros fictícios e simula duas retiradas concorrentes para o mesmo exemplar.

Para contribuir com o código, use Java 21 e Maven ou Docker. O arquivo `compose.yaml` é opcional e serve ao desenvolvimento. A entrega de produção usa o PostgreSQL do Render e o endereço público da Vercel.

## Privacidade na demonstração

Use registros fictícios na apresentação. Os dados reais de alunos exigem autorização da escola e revisão da política de acesso, retenção e backup. Os nomes creditados no rodapé identificam a equipe do projeto.
