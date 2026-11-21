# DFmob: Especificação e Roadmap de Paridade com Moovit

Documento de especificação técnica das novas funcionalidades identificadas na engenharia reversa do aplicativo Moovit, detalhando a arquitetura de implementação, o nível de paridade com o Moovit e a metodologia de testes empíricos para validação em Brasília (DF).

---

## Sumário de Funcionalidades e Nível de Paridade

| Funcionalidade | Identificador Moovit | Nível de Paridade | Fonte dos Dados |
| :--- | :--- | :--- | :--- |
| 1. Nível de Lotação por Horário | `CONGESTION_LEVEL` / `CongestionSource` | 1:1 quando disponível na API; Aproximação Preditiva DF quando ausente | Moovit Thrift (`MVArrival.vehicleAttributes`) + Motor Heurístico DF |
| 2. Dicas Inteligentes de Rota | `TRIP_INSIGHTS` | 1:1 de formato e regras contextuais | Análise de legs de `TripPlanItinerary` + regras operacionais |
| 3. Compare Rotas no Mapa | `COMPARE_ON_MAP` | 1:1 total | Polylines simultâneas de `searchTripPlan` |
| 4. Que Horas Devo Sair? | `ADVANCED_TIME_PICKER` | 1:1 total | Parâmetros Thrift `timeType` e `timeUtc` da API Moovit |
| 5. Preferências de Rota | `TRIP_PLAN_ADVANCED_ROUTE` | 1:1 total | Mapeamento direto de enums Thrift `MVTripPlanPref` |
| 6. Defina Sua Tela Inicial | `DEFAULT_TAB` | 1:1 funcional | SharedPreferences / DataStore local |
| 7. Ver Trânsito no Mapa | `TRAFFIC_ON_MAP` | Descartado | Descartado para manter o mapa do DFmob limpo, focado em transporte público e sem poluição visual externa |

---

## 1. Nível de Lotação por Horário (`CONGESTION_LEVEL`)

### 1.1 Descrição e Comportamento no Moovit
No código descompilado do Moovit:
* Protocolo Thrift `MVArrival` (campo 14: `vehicleAttributes`):
  * `level`: `EMPTY` (0), `FEW` (1), `LOW` (2), `MED` (3), `HIGH` (4), `FULL` (5), `PACKED` (6).
  * `source`: `SENSOR` (1), `USER` (2), `PREDICTION` (3).
* Quando `source == PREDICTION`, a interface exibe textos probabilísticos por horário:
  * "Geralmente tem assentos disponíveis" (níveis 0 a 2).
  * "Geralmente apenas em pé" (níveis 3 e 4).
  * "Geralmente lotado" (níveis 5 e 6).
* Quando `source == SENSOR` ou `USER`, exibe a informação direta de tempo real ("Assentos disponíveis", "Apenas em pé", "Lotado").

### 1.2 Arquitetura de Implementação no DFmob
1. **Camada de Rede (`MoovitTransitClient.kt`):**
   * Expandir o parser de chegadas (`fetchArrivalsForStop` e `fetchLiveVehiclesForLine`) para decodificar o campo 14 (`vehicleAttributes`), extraindo `level` e `source`.
2. **Motor de Inferência Preditiva de Brasília (`CrowdingPredictionEngine.kt`):**
   * Em Brasília, a SEMOB-DF não fornece dados de lotação/catraca no feed de telemetria, e a adesão de usuários locais ao relato manual no Moovit é baixa.
   * Portanto, quando a API do Moovit não enviar o campo 14 (ausente em consultas locais), o DFmob ativa o motor preditivo do DF:
     * Sentido Regiões Administrativas -> Plano Piloto (dias úteis das 06:00 às 08:45): Alta ocupação ("Geralmente lotado").
     * Sentido Plano Piloto -> Regiões Administrativas (dias úteis das 17:00 às 19:30): Alta ocupação ("Geralmente lotado").
     * Eixos principais e troncais (ex: EPTG, EPNB, Estrutural, W3 Sul/Norte) em horários de pico.
     * Entre-pico (10:00 às 15:30) e horários noturnos (após 20:30): Baixa ocupação ("Geralmente tem assentos disponíveis").

### 1.3 Metodologia de Teste Empírico e Validação
* **Teste de Rede:** Executar script automatizado consultando 10 paradas de grande movimento no DF (ex: Rodoviária do Plano Piloto, Taguatinga Centro, EPNB Riacho Fundo, Gama Central) em dois horários distintos (pico das 07:30 e entre-pico das 14:00) para monitorar se o backend do Moovit envia o campo 14.
* **Teste Comparativo:** Comparar a indicação mostrada no app oficial do Moovit com a inferência gerada pelo DFmob para as mesmas linhas e horários, verificando convergência superior a 90%.

---

## 2. Dicas Inteligentes de Rota (`TRIP_INSIGHTS`)

### 2.1 Descrição e Comportamento no Moovit
No arquivo descompilado `com.moovit.feature.tripplan.ui.content.misc.a`, o Moovit insere badges contextuais nos cards de resultado da busca de viagem:
* "As linhas estão lotadas nesta rota"
* "Menos caminhada"
* "Melhor rota sem baldeação"
* "Linha com partidas frequentes"

### 2.2 Arquitetura de Implementação no DFmob
* Criar o analisador `TripInsightsEvaluator.kt` que recebe a lista de `TripPlanItinerary` retornada pela API e injeta badges dinâmicos:
  * Se a soma da distância a pé for menor que 300m: badge "Pouca caminhada".
  * Se o número de transferências for 0: badge "Sem baldeação".
  * Se o trajeto contiver linhas que operam em horário de pico com alta lotação inferida: badge âmbar "Linhas cheias no horário".
  * Se o tempo total for o menor entre todas as opções: badge verde "Mais rápida".

### 2.3 Metodologia de Teste Empírico e Validação
* Realizar 15 buscas de trajetos típicos de Brasília (ex: Ceilândia -> Rodoviária, Guará -> UnB, Gama -> Esplanada) e verificar se as tags geradas pelo DFmob correspondem às mesmas recomendações exibidas no Moovit.

---

## 3. Compare Rotas no Mapa (`COMPARE_ON_MAP`)

### 3.1 Descrição e Comportamento no Moovit
* Implementado em `ItineraryCompareOnMapActivity.java`.
* Ao pesquisar um trajeto com múltiplos resultados, o usuário clica em "Comparar no Mapa".
* O mapa renderiza todas as rotas candidatas simultaneamente com traçados em cores distintas (ex: Rota 1 em azul, Rota 2 em roxo, Rota 3 em verde).
* Um carrossel ou indicador inferior permite alternar o foco entre as rotas, destacando o traçado selecionado com espessura maior e atenuando as demais.

### 3.2 Arquitetura de Implementação no DFmob
* Aproveitar os dados de geometria já recebidos no `TripPlanItinerary`: cada rota já possui seus pontos geográficos de caminhada e transporte público.
* Adicionar na tela `TripPlannerScreen.kt` o botão de ação "Comparar no Mapa".
* Criar a visualização `TripComparisonMapScreen.kt` usando MapLibre/Stadia Maps:
  * Linha 1: Azul `#1976D2`
  * Linha 2: Roxo `#7B1FA2`
  * Linha 3: Verde `#388E3C`
  * Painel inferior compacto mostrando duração, preço e número das linhas para cada alternativa.

### 3.3 Metodologia de Teste Empírico e Validação
* Comparar a renderização dos traçados de 5 rotas complexas entre o Moovit oficial e o DFmob, validando se os caminhos percorridos nas vias do DF coincidem ponto a ponto.

---

## 4. Que Horas Devo Sair? (`ADVANCED_TIME_PICKER`) [CONCLUÍDO]

### 4.1 Descrição e Comportamento no Moovit
* Implementado no pacote `TripPlanTimePickerPackage`.
* Permite ao usuário escolher:
  1. Partir agora (`DEPART_NOW`).
  2. Partir às (`DEPART_AT`): define dia e hora futura de saída.
  3. Chegar às (`ARRIVE_BY`): define dia e hora em que o usuário precisa estar no destino, calculando a hora máxima em que deve sair de casa.

### 4.2 Arquitetura de Implementação no DFmob
* O cliente de rede `MoovitTransitClient.kt` já tem suporte nos parâmetros de Thrift:
  * `timeType = 2` para partida.
  * `timeType = 1` para chegada.
  * `timeUtc` com o timestamp em milissegundos.
* Implementar no `TripPlannerScreen.kt` o seletor visual de data e horário integrado ao Material 3 TimePicker / DatePicker.

### 4.3 Metodologia de Teste Empírico e Validação
* Configurar no Moovit e no DFmob o mesmo cenário: "Chegar na UnB às 08:00 de amanhã saindo de Taguatinga Centro".
* Comparar os horários de partida recomendados por ambos os apps, comprovando paridade total de cronograma fornecido pelo backend.

---

## 5. Preferências de Rota Mais Inteligentes (`TRIP_PLAN_ADVANCED_ROUTE`) [CONCLUÍDO]

### 5.1 Descrição e Comportamento no Moovit
* No Thrift do Moovit (`MVTripPlanPref.java`), o algoritmo de roteamento aceita:
  * `LEAST_WALKING` (Menos caminhada).
  * `LEAST_TRANSFERS` (Menos transferências).
  * `FASTEST` (Mais rápida).
* Permite também restringir modais de transporte:
  * Apenas ônibus.
  * Apenas metrô.
  * Ambos (multimodal).

### 5.2 Arquitetura de Implementação no DFmob
* Expor na interface de Planejar Viagem um menu ou bottom sheet de preferências avançadas.
* Conectar as opções diretamente aos valores numéricos do protocolo Thrift suportados pela API.

### 5.3 Metodologia de Teste Empírico e Validação
* Para um mesmo par Origem/Destino com opções mistas de metrô e ônibus (ex: Águas Claras -> Rodoviária do Plano Piloto), aplicar os filtros em ambos os aplicativos e validar se os resultados priorizados são idênticos.

---

## 6. Defina Sua Tela Inicial (`DEFAULT_TAB`) [CONCLUÍDO]

### 6.1 Descrição e Comportamento no Moovit
* Implementado em `DefaultTabActivity.java`.
* Configuração que permite ao usuário definir qual seção do app deve ser aberta imediatamente ao iniciar:
  1. Paradas e Mapa ao vivo.
  2. Planejar Viagem.
  3. Linhas e Favoritos.

### 6.2 Arquitetura de Implementação no DFmob
* Adicionar na tela de Configurações (`SettingsScreen.kt`) a opção "Tela Inicial Padrão".
* Persistir em `DataStore` ou `SharedPreferences`.
* No `MainActivity.kt`, ler a configuração no início e definir a `startDestination` do NavHost.

### 6.3 Metodologia de Teste Empírico e Validação
* Teste de ciclo de vida com fechamento forçado e reinício do aplicativo, garantindo que o destino inicial respeita a escolha do usuário sem engasgos ou telas intermediárias piscando.

---

## 7. Ver Trânsito no Mapa (`TRAFFIC_ON_MAP`) [DESCARTADO]

### 6.1 Descrição e Comportamento no Moovit
* O Moovit ativa a camada de tráfego sobre o Google Maps (`GoogleMap.setTrafficEnabled(true)`).
* As vias principais são coloridas de acordo com a velocidade do tráfego: verde (livre), amarelo (moderado), vermelho (lento) e bordô (congestionado).

### 7.2 Arquitetura de Implementação no DFmob
* Como o DFmob utiliza MapLibre / Stadia Maps para máxima eficiência, offline-first e ausência de cobranças abusivas de API proprietária:
  * Opção A: Adicionar camada vetorial/raster de tráfego em tempo real via tile provider de tráfego aberto.
  * Opção B: Renderizar a velocidade média dos ônibus da SEMOB monitorados ao vivo nos trechos das vias (congestionamento inferido pela velocidade dos veículos GPS da frota do DF em circulação).
* Adicionar um botão discreto de alternância de camada (toggle de trânsito) no mapa.

### 7.3 Metodologia de Teste Empírico e Validação
* Monitorar simultaneamente a EPTG e a EPNB em horário de pico (18:00) no Moovit e no DFmob, verificando se as áreas de lentidão identificadas coincidem.

---

## Matriz de Validação Empírica (Critérios de Aceite)

1. **Paridade 1:1 de Dados:** Qualquer dado presente na resposta Thrift da API do Moovit deve ser decodificado e refletido no DFmob sem perdas ou distorções.
2. **Robustez a Falhas e Ausência de Dados:** Quando o backend do Moovit não disponibilizar informações para uma linha específica de Brasília, o DFmob deve recorrer imediatamente a inferências preditivas calibradas para a realidade do Distrito Federal, sem apresentar campos vazios ou erros.
3. **Consistência de Testes:** Todos os testes de comparação devem ser registrados com origem, destino, timestamp de consulta e prints/logs de resposta da API para comprovação empírica.
