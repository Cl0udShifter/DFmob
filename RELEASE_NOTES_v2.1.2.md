## Correções da Versão 2.1.2

Esta atualização preserva o traçado oficial da SEMOB-DF como rota verdadeira (como o percurso da linha `0.341` pelo Núcleo Bandeirante, Guará e EPTG) e reconstrói automaticamente as paradas corretas ao longo de toda a rota quando o itinerário estático em banco estiver desatualizado ou incompleto.

### O que foi corrigido

- **Preservação da rota oficial da SEMOB**: o traçado retornado pelo GeoServer da SEMOB-DF volta a ser mantido integralmente no mapa sem descarte.
- **Reconstrução automática de paradas no traçado oficial (`reconstructStopsAlongShape`)**: quando a sequência de paradas em banco diverge do percurso oficial da SEMOB, o aplicativo mapeia automaticamente todas as paradas reais ao longo do traçado oficial em ordem de deslocamento, filtrando paradas do sentido oposto, estações de metrô e duplicatas em terminais.
- **Cobertura de testes**: testes unitários em `StopsRepositoryShapeTest` atualizados para validar a preservação do traçado SEMOB, detecção de divergência e reconstrução ordenada de paradas no sentido da via.
