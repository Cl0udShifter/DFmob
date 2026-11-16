# DFmob

Aplicativo open source de mobilidade urbana para o Distrito Federal (ônibus e metrô), focado em altíssimo desempenho, consumo mínimo de memória RAM e dados móveis, cold start instantâneo e funcionamento offline-first.

## Pilares de Engenharia
- **Zero custo de API**: 100% baseado em dados abertos (GTFS Semob-DF, API DF no Ponto, Metrô-DF e OpenStreetMap).
- **Arquitetura inspirada na engenharia reversa do Moovit**:
  - Banco local SQLite com FTS5 para busca instantânea de linhas e paradas sem rede.
  - Coordenadas geográficas compactadas em inteiros de 32 bits (`graus * 10^6`).
  - Cache delta por revisões GTFS para economia de dados móveis.
  - Ausência total de SDKs de telemetria, rastreamento ou anúncios.
- **Stack nativa Android**:
  - Kotlin + Jetpack Compose com Baseline Profiles.
  - MapLibre Native Android SDK para renderização vetorial de mapas via hardware.
