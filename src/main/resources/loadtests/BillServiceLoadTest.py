import requests
import time
import concurrent.futures

# --- CONFIGURAÇÕES ---
# 1. Substitua pelo seu token JWT real
TOKEN = "SEU_TOKEN_AQUI"

# 2. Ajuste a URL base se não estiver rodando na porta 8085 localmente
BASE_URL = "https://localhost:8085/api/bill"

# 3. Mês de teste (certifique-se de que há dados para este mês)
BILL_DATE = "Mar 2026"

HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json"
}

# As 4 chamadas que o frontend faz simultaneamente
ENDPOINTS = [
    f"{BASE_URL}/load-main-table-data?billDate={BILL_DATE}",
    f"{BASE_URL}/load-card-table-data?billDate={BILL_DATE}",
    f"{BASE_URL}/load-assets-table-data?billDate={BILL_DATE}",
    f"{BASE_URL}/load-payment-card-table-data?billDate={BILL_DATE}"
]

def fetch_endpoint(url):
    """Faz a requisição e calcula o tempo gasto."""
    start_time = time.time()
    response = requests.get(url, headers=HEADERS)
    elapsed = (time.time() - start_time) * 1000 # Converte para milissegundos
    return response.status_code, elapsed, url

def simulate_dashboard_load(run_number):
    """Simula o carregamento simultâneo dos 4 endpoints da Dashboard."""
    print(f"\n--- Simulação de Dashboard #{run_number} Iniciada ---")
    start_total = time.time()

    # Executa as 4 chamadas concorrentemente (exatamente como o Promise.all do React)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        futures = [executor.submit(fetch_endpoint, url) for url in ENDPOINTS]

        for future in concurrent.futures.as_completed(futures):
            status, elapsed, url = future.result()
            # Pega só o final da URL para o print ficar limpo
            endpoint_name = url.split('/')[-1].split('?')[0]
            print(f"[{status}] {endpoint_name.ljust(30)} -> {elapsed:.2f} ms")

    total_elapsed = (time.time() - start_total) * 1000
    print(f"Tempo Total Perceptível pelo Usuário (Carregamento da Dashboard): {total_elapsed:.2f} ms")
    return total_elapsed

if __name__ == "__main__":
    print("Iniciando Teste de Carga (Cache VS Banco de Dados)...\n")

    # 1ª Rodada: Banco de Dados a Frio (Sem Cache)
    print(">> 1. PRIMEIRA CHAMADA (Vai bater no banco de dados e preencher o Cache)")
    simulate_dashboard_load(1)

    # Pequena pausa para fingir que o usuário leu a tela
    time.sleep(2)

    # 2ª a 5ª Rodada: Dashboard trocando de abas (Lendo do Cache)
    print("\n>> 2. CHAMADAS SUBSEQUENTES (Testando a Velocidade do Cache)")
    for i in range(2, 6):
        simulate_dashboard_load(i)
        time.sleep(0.5) # Pausa rápida entre os cliques