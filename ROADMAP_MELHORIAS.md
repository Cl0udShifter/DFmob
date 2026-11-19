# DFmob: Plano de Melhorias e Roadmap de Funcionalidades

Documento local de acompanhamento das melhorias recomendadas para o DFmob. Cada item possui status de conclusão com suporte a marcação `[ ]` (pendente) e `[OK]` (concluído).

---

## 1. Tela Principal (`HomeScreen`)

- [OK] **1.1 Próxima partida no card favorito**
  * Exibir diretamente no card da linha favorita na tela inicial um chip com a previsão do próximo ônibus para a parada mais próxima da localização atual do usuário, sem precisar abrir a tela do mapa.
  * Considerar o sentido específico salvo para a linha (ou neutro se a linha for circular).

- [OK] **1.2 Alertas operacionais da SEMOB**
  * Adicionar ícone de alerta à esquerda da engrenagem de configurações no cabeçalho superior.
  * Ícone possui indicador visual (ponto âmbar de destaque) quando há alertas operacionais não visualizados pelo usuário.
  * Ao ser clicado, navega para a tela de Avisos seguindo o padrão visual do app (título com ponto verde "Avisos.").
  * Exibe cards individuais para cada comunicado da SEMOB com texto explicativo e badges destacados contendo o número e a cor oficial de cada linha afetada, permitindo abrir diretamente a linha no mapa ao tocar no badge.
  * Escuta periódica em segundo plano mesmo com app fechado via WorkManager, disparando notificação local do sistema com navegação direta para a tela de Avisos ao tocar.

---

## 2. Tela de Mapa e Paradas (`StopsScreen`)

- [OK] **2.1 Filtro de busca dentro do painel da parada**
  * Barra de pesquisa rápida e minimalista no topo do painel de chegadas (`FloatingArrivalsDeck`) para filtrar linhas instantaneamente em paradas com muitas opções (ex: EPNB, EPTG, W3, Rodoviária).
  * Exibição padrão, limpa e discreta em todas as paradas, sem caixa envolvente cinza.

- [ ] **2.2 Tabela horária completa do dia na parada**
  * Ícone discreto abaixo do horário principal de chegada, alinhado aos chips das próximas partidas ("Seguintes").
  * Ao tocar, expandir instantaneamente o painel para a altura de 75% da tela, listando todas as chegadas futuras do dia para aquela linha naquela parada específica.
  * Suporte a telemetria GPS ao vivo e atualização contínua com o passar do tempo.
  * Retorno à visualização anterior da parada ao puxar o painel para baixo ou acionar o botão/gesto de voltar do Android.

- [ ] **2.3 Tipo de veículo e acessibilidade**
  * Exibir no card da linha ícones compactos e sem texto indicando a categoria do ônibus a caminho: Padron, Articulado (BRT) ou Mini.
  * Exibir ícone discreto indicando se o veículo possui elevador/rampa de acessibilidade ativa.

- [ ] **2.4 Botão de ativar aviso de aproximação**
  * Ícone de sino em cada card de linha no painel da parada.
  * Quando ativado, disparar notificações locais do Android quando o veículo estiver a 15, 10, 5 e 2 minutos de distância da parada, mesmo com o app fechado ou em segundo plano.
  * Linhas com aviso ativo sobem para o topo do painel da parada, ignorando a ordem padrão.
  * Ao tocar na notificação, abrir o app diretamente no mapa da parada com a linha selecionada.
  * Cancelamento automático após a passagem pelo status "Já", retornando o card à sua posição original.
  * Tratamento para ativação tardia (quando o ônibus já estiver a menos de 15, 10, 5 ou 2 minutos).

---

## 3. Tela de Linhas (`LinesScreen`)

- [ ] **3.1 Histórico de linhas recentes**
  * Seção retrátil localizada logo abaixo do seletor Ônibus / Metrô-DF contendo as últimas 3 linhas consultadas pelo passageiro.

- [ ] **3.2 Etiqueta de integração tarifária**
  * Ícones gráficos minimalistas sem texto identificando linhas que possuem integração com o Metrô-DF ou aceitam a tarifa máxima de integração de R$ 5,50 com o Cartão Mobilidade.

---

## 4. Tela de Planejar Viagem (`TripPlannerScreen`)

- [ ] **4.1 Correção do bug ao selecionar horário de chegada**
  * Eliminar a mensagem passageira "Falha ao calcular rotas: StandaloneCoroutine was cancelled" que surge ao alterar o horário de chegada desejado.
  * Ignorar `CancellationException` durante o debounce de recálculo de rotas no ViewModel, mantendo a interface estável.

- [ ] **4.2 Cálculo da tarifa total da viagem integrada**
  * Exibir o valor total estimado da viagem aplicando as regras de integração tarifária do Distrito Federal (máximo de R$ 5,50 no Bilhete Único no intervalo de integração, em vez de somar o valor integral das passagens).

- [ ] **4.3 Salvar trajeto completo como favorito**
  * Permitir favoritar o par Origem e Destino com um nome personalizado (ex: "Faculdade", "Trabalho", "Academia").
  * Exibir os trajetos salvos dentro do módulo de favoritos na tela inicial.

---

## 5. Detalhes do Trajeto e Navegação ao Vivo (`TripItineraryDetailView`)

- [ ] **5.1 Alarme de aproximação para desembarque**
  * Disparar notificação local com vibração e alerta sonoro quando o usuário estiver a 2 paradas (ou 400 metros) do ponto de descida programado.

- [ ] **5.2 Alternância rápida de câmera**
  * Botão flutuante na tela de navegação para alternar o foco da câmera entre a localização atual do usuário e o ônibus monitorado em aproximação.

- [ ] **5.3 Remoção do botão verde inferior de avanço manual**
  * Remover o botão verde de avançar etapa situado na parte inferior da tela, concentrando a navegação nas setas de avanço e retorno do cabeçalho superior.

---

## 6. Tela de Configurações (`SettingsScreen`)

- [ ] **6.1 Configuração de alertas e notificações**
  * Seletor de antecedência para o alarme de desembarque (1, 2 ou 3 paradas de antecedência).
  * Chaves individuais (toggles) para habilitar ou desabilitar cada faixa de notificação de chegada de ônibus na parada (15 min, 10 min, 5 min e 2 min).
