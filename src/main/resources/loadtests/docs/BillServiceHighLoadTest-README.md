BillServiceHighLoadTest — README

Objetivo
--------
Documentação de uso do script de teste de carga `BillServiceHighLoadTest.py` localizado em `src/main/resources/loadtests`.

O script simula múltiplos "usuários" fazendo as mesmas chamadas de leitura ao backend das telas de contas (o mesmo comportamento que o frontend faz via Promise.all). Ele foi criado para testar capacidade e latências do serviço.

Atenção importante
------------------
- Este é um script de carga. Não execute contra ambientes de produção que você não administra ou sem autorização da equipe de infraestrutura.
- O script envia muitas requisições concorrentes e pode causar impacto (latência, uso de CPU/memória, timeouts) no target.
- Por padrão faz apenas requisições GET (leituras), mas ainda assim pode sobrecarregar o serviço.

Pré-requisitos
--------------
- Python 3.8+ instalado
- Biblioteca requests

Instalar dependências (PowerShell):

```powershell
python -m pip install --user requests
```

Arquivos relevantes
-------------------
- `src/main/resources/loadtests/BillServiceHighLoadTest.py` — script de teste de carga
- `src/main/resources/loadtests/docs/BillServiceHighLoadTest-README.md` — este documento

Parâmetros e opções
-------------------
O script aceita os seguintes argumentos de linha de comando:

--users: número total de usuários simultâneos a simular (padrão 1000)
--batches: divide os usuários em N batches para ramp-up (padrão 10)
--batch-delay: segundos de espera entre batches (padrão 1.0)
--token: um token Bearer (JWT) para ser usado em todas as requisições
--token-file: caminho para arquivo com 1 token por linha (útil para simular muitos usuários distintos)
--base-url: URL base do serviço (padrão `http://localhost:8085/api/bill`)
--bill-date: parâmetro `billDate` enviado nas URLs (padrão `Mar 2026`)
--no-warn: pula o prompt de confirmação

Formato do arquivo de tokens
---------------------------
Se for usar `--token-file`, coloque um token por linha, sem aspas. Exemplo (`tokens.txt`):

```
eyJhbGciOiJI...token1
eyJhbGciOiJI...token2
...
```

Exemplos de execução (PowerShell)
--------------------------------
1) Teste rápido com 10 usuários localmente:

```powershell
python .\src\main\resources\loadtests\BillServiceHighLoadTest.py --users 10 --batches 2 --batch-delay 0.5 --no-warn
```

2) Teste com token único (apontando para staging/produção):

```powershell
python .\src\main\resources\loadtests\BillServiceHighLoadTest.py --users 1000 --batches 20 --token "SEU_JWT_AQUI" --base-url "https://api.meudominio.com/api/bill" --no-warn
```

3) Teste com arquivo de tokens:

```powershell
python .\src\main\resources\loadtests\BillServiceHighLoadTest.py --users 1000 --batches 20 --token-file .\tokens.txt --base-url "https://api.meudominio.com/api/bill" --no-warn
```

O que o script faz
-------------------
- Para cada usuário simulado, dispara as 4 requisições:
  - `/load-main-table-data?billDate=...`
  - `/load-card-table-data?billDate=...`
  - `/load-assets-table-data?billDate=...`
  - `/load-payment-card-table-data?billDate=...`
- Mede latência (ms) por requisição e status HTTP
- No final exibe:
  - total de requisições tentadas
  - distribuição de status code
  - percentis de latência: p50, p90, p95, p99

Interpretação rápida do resultado
---------------------------------
- Distribuição de status:
  - 200/2xx: sucesso
  - 4xx/5xx ou 0: indicação de erro (autenticação, serviço indisponível, timeout)
- Percentis: se p95/p99 estiverem muito altos, o serviço pode estar sobrecarregado ou com problemas de escalabilidade.

Boas práticas antes de rodar um teste grande
-------------------------------------------
1. Rodar primeiro com 5-20 usuários para validar tokens e endpoints.
2. Conferir logs do target e coordenar com a equipe de infra.
3. Aumentar usuários gradualmente (ajuste `--batches` e `--batch-delay`).
4. Evitar horários críticos de produção.

Dicas de melhoria (opcional)
----------------------------
- Exportar resultados para CSV/JSON para análise posterior (podemos acrescentar essa opção).
- Usar ferramentas especializadas (k6, Gatling, JMeter) para testes mais avançados e visualizações.
- Adicionar opção de simular diferentes bill_date por usuário para maior variabilidade.

Problemas conhecidos e troubleshooting
------------------------------------
- Se aparecerem muitos status 0 (timeout/exception), aumente `--batch-delay`, diminua `--users` ou aumente o timeout no código (linha fetch_url).
- Em Windows, se o número de threads for muito alto, pode haver limitação de recursos; reduza `--users` ou `--batches`.

Precisa que eu adicione mais alguma coisa?
-----------------------------------------
Posso:
- adicionar opção para salvar JSON/CSV com todos os resultados;
- gerar uma versão que use processos (multiprocessing) em vez de threads para escalar melhor;
- criar um pipeline simples para disparar o teste e coletar resultados automaticamente.

---
Arquivo criado automaticamente em `src/main/resources/loadtests/docs/BillServiceHighLoadTest-README.md`.
