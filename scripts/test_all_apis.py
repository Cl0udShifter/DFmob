#!/usr/bin/env python3
"""
DFmob API Health Check and Diagnostic Suite
Tests all external transit, telemetry, tile and geocoding services used by DFmob.
"""

import time
import json
import struct
import urllib.request
import urllib.parse
import urllib.error
import sys

TIMEOUT_SECONDS = 10

def print_header(title):
    print("=" * 70)
    print(f" {title}")
    print("=" * 70)

def print_result(name, ok, code, elapsed_ms, details):
    status_str = "[OK]  " if ok else "[FAIL]"
    print(f"{status_str} | {name:<30} | HTTP {code:<3} | {elapsed_ms:>6.1f} ms | {details}")

def test_moovit_stops_arrivals():
    url = "https://app5.moovitapp.com/services-app/services/V4/StopsArrivals"
    api_key = "moovit_2751703405"
    user_key = "2BF18CA7E497970E2F02F4EAA0BE8461"
    metro_id = "1702"
    stop_id = 6342602 # Rodoviária do Plano Piloto

    now_ms = int(time.time() * 1000)
    buf = bytearray()
    buf += struct.pack(">BhBi", 15, 1, 8, 1)
    buf += struct.pack(">i", stop_id)
    buf += struct.pack(">Bh", 12, 2)
    buf += struct.pack(">Bhq", 10, 1, now_ms)
    buf += struct.pack(">Bhb", 2, 2, 1)
    buf += struct.pack(">Bhb", 2, 3, 0)
    buf += struct.pack(">Bhb", 2, 4, 1)
    buf += struct.pack(">Bhb", 2, 5, 0)
    buf += struct.pack(">Bhb", 2, 6, 1)
    buf += b"\x00\x00"

    req = urllib.request.Request(url, data=bytes(buf), headers={
        "Host": "app5.moovitapp.com",
        "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 14; 2412DPC0AG Build/UKQ1.231003.002)",
        "CLIENT_VERSION": "5.204.1.1814",
        "PHONE_TYPE": "1",
        "API_KEY": api_key,
        "USER_KEY": user_key,
        "Metro-Revision-Metro-Id": metro_id,
        "Content-Type": "application/octet",
        "Accept": "application/json",
    })

    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            rev = resp.headers.get("Metro-Revision-Number", "N/A")
            raw = resp.read().decode("utf-8")
            data = json.loads(raw)
            arr_lst = data.get("3", {}).get("lst", [])
            lines_count = max(0, len(arr_lst) - 2) if len(arr_lst) > 2 else 0
            print_result("Moovit: StopsArrivals", True, resp.status, elapsed, f"Rev: {rev}, Linhas com previsão: {lines_count}")
            return True
    except urllib.error.HTTPError as e:
        elapsed = (time.perf_counter() - t0) * 1000
        rev = e.headers.get("Metro-Revision-Number", "N/A")
        print_result("Moovit: StopsArrivals", False, e.code, elapsed, f"Falha (Rev serv: {rev})")
        return False
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("Moovit: StopsArrivals", False, 0, elapsed, f"Erro: {e}")
        return False

def test_moovit_line_arrivals():
    url = "https://app5.moovitapp.com/services-app/services/V4/LineArrivals"
    api_key = "moovit_2751703405"
    user_key = "2BF18CA7E497970E2F02F4EAA0BE8461"
    metro_id = "1702"
    stop_id = 6342602
    line_id = 7202957

    buf = bytearray()
    buf += struct.pack(">BhBi", 15, 1, 12, 1)
    buf += struct.pack(">Bhi", 8, 1, stop_id)
    buf += struct.pack(">Bhi", 8, 2, line_id)
    buf += b"\x00"
    buf += struct.pack(">Bh", 12, 2)
    buf += struct.pack(">Bhq", 10, 1, int(time.time() * 1000))
    buf += struct.pack(">Bhb", 2, 2, 1)
    buf += struct.pack(">Bhb", 2, 3, 0)
    buf += struct.pack(">Bhb", 2, 4, 1)
    buf += struct.pack(">Bhb", 2, 5, 0)
    buf += struct.pack(">Bhb", 2, 6, 1)
    buf += b"\x00\x00"

    req = urllib.request.Request(url, data=bytes(buf), headers={
        "Host": "app5.moovitapp.com",
        "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 14; 2412DPC0AG Build/UKQ1.231003.002)",
        "CLIENT_VERSION": "5.204.1.1814",
        "PHONE_TYPE": "1",
        "API_KEY": api_key,
        "USER_KEY": user_key,
        "Metro-Revision-Metro-Id": metro_id,
        "Content-Type": "application/octet",
        "Accept": "application/json",
    })

    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            rev = resp.headers.get("Metro-Revision-Number", "N/A")
            raw = resp.read().decode("utf-8")
            print_result("Moovit: LineArrivals", True, resp.status, elapsed, f"Rev: {rev}, Resposta: {len(raw)} bytes")
            return True
    except urllib.error.HTTPError as e:
        elapsed = (time.perf_counter() - t0) * 1000
        rev = e.headers.get("Metro-Revision-Number", "N/A")
        print_result("Moovit: LineArrivals", False, e.code, elapsed, f"Falha (Rev serv: {rev})")
        return False
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("Moovit: LineArrivals", False, 0, elapsed, f"Erro: {e}")
        return False

def test_moovit_trip_planner():
    url = "https://app5.moovitapp.com/services-app/services/V4/TripPlanner2/Search"
    api_key = "moovit_2751703405"
    user_key = "2BF18CA7E497970E2F02F4EAA0BE8461"
    metro_id = "1702"
    now_ms = int(time.time() * 1000)

    def write_location(dos, lat_e6, lon_e6, caption):
        dos += struct.pack(">Bh", 12, 1)
        cb = caption.encode("utf-8")
        dos += struct.pack(">Bhi", 11, 1, len(cb))
        dos += cb
        dos += struct.pack(">Bh", 12, 3)
        dos += struct.pack(">Bhi", 8, 1, lat_e6)
        dos += struct.pack(">Bhi", 8, 2, lon_e6)
        dos += b"\x00"
        dos += struct.pack(">Bhi", 8, 4, 1)
        dos += b"\x00"
        dos += struct.pack(">Bhi", 8, 2, 1)
        dos += b"\x00"

    buf = bytearray()
    buf += struct.pack(">Bhi", 8, 1, 2)
    buf += struct.pack(">Bhq", 10, 2, now_ms)
    buf += struct.pack(">Bhi", 8, 3, 2)
    buf += struct.pack(">Bhb", 2, 4, 1)
    buf += struct.pack(">BhBi", 15, 5, 8, 2)
    buf += struct.pack(">ii", 3, 1)
    buf += struct.pack(">Bh", 12, 6)
    write_location(buf, -15793900, -47882800, "Rodoviaria")
    buf += struct.pack(">Bh", 12, 7)
    write_location(buf, -15833500, -48056900, "Taguatinga")
    buf += struct.pack(">Bhb", 2, 10, 1)
    buf += struct.pack(">Bhb", 2, 15, 1)
    buf += struct.pack(">Bhi", 8, 17, 1)
    buf += b"\x00"

    req = urllib.request.Request(url, data=bytes(buf), headers={
        "Host": "app5.moovitapp.com",
        "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 14; 2412DPC0AG Build/UKQ1.231003.002)",
        "CLIENT_VERSION": "5.204.1.1814",
        "PHONE_TYPE": "1",
        "API_KEY": api_key,
        "USER_KEY": user_key,
        "Metro-Revision-Metro-Id": metro_id,
        "Content-Type": "application/octet",
        "Accept": "application/json",
    })

    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            rev = resp.headers.get("Metro-Revision-Number", "N/A")
            raw = resp.read()
            print_result("Moovit: TripPlanner2", True, resp.status, elapsed, f"Rev: {rev}, Resposta: {len(raw)} bytes")
            return True
    except urllib.error.HTTPError as e:
        elapsed = (time.perf_counter() - t0) * 1000
        rev = e.headers.get("Metro-Revision-Number", "N/A")
        print_result("Moovit: TripPlanner2", False, e.code, elapsed, f"Falha (Rev serv: {rev})")
        return False
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("Moovit: TripPlanner2", False, 0, elapsed, f"Erro: {e}")
        return False

def test_semob_wfs_ultima_posicao():
    cql = urllib.parse.quote("cd_linha='0.886'")
    url = f"http://geoserver.semob.df.gov.br/geoserver/semob/wfs?service=WFS&version=1.0.0&request=GetFeature&typeName=semob:ultima_posicao&outputFormat=application/json&maxFeatures=10&cql_filter={cql}"
    req = urllib.request.Request(url, headers={"User-Agent": "DFmob-Android"})

    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            raw = resp.read().decode("utf-8")
            if raw.strip().startswith("{"):
                data = json.loads(raw)
                feat_count = len(data.get("features", []))
                print_result("Semob: ultima_posicao", True, resp.status, elapsed, f"Veículos retornados: {feat_count}")
                return True
            else:
                print_result("Semob: ultima_posicao", False, resp.status, elapsed, f"Resposta não-JSON (possível XML de erro WFS)")
                return False
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("Semob: ultima_posicao", False, 0, elapsed, f"Erro: {e}")
        return False

def test_semob_wfs_percurso():
    cql = urllib.parse.quote("cd_linha='0.886'")
    url = f"http://geoserver.semob.df.gov.br/geoserver/semob/wfs?service=WFS&version=1.0.0&request=GetFeature&typeName=semob:percuso%20das%20linhas&outputFormat=application/json&srsName=EPSG:4326&maxFeatures=5&cql_filter={cql}"
    req = urllib.request.Request(url, headers={"User-Agent": "DFmob-Android"})

    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            raw = resp.read().decode("utf-8")
            if raw.strip().startswith("{"):
                data = json.loads(raw)
                feat_count = len(data.get("features", []))
                print_result("Semob: percurso_linhas", True, resp.status, elapsed, f"Shapes retornados: {feat_count}")
                return True
            else:
                print_result("Semob: percurso_linhas", False, resp.status, elapsed, f"Resposta não-JSON")
                return False
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("Semob: percurso_linhas", False, 0, elapsed, f"Erro: {e}")
        return False

def test_osm_nominatim_search():
    encoded = urllib.parse.quote("Rodoviaria do Plano Piloto")
    url = f"https://nominatim.openstreetmap.org/search?q={encoded}&format=json&addressdetails=1&countrycodes=br&viewbox=-48.3,-15.4,-47.2,-16.1&bounded=1&limit=3"
    req = urllib.request.Request(url, headers={"User-Agent": "DFmob-Android/1.0 (contact: github.com/Cl0udShifter/DFmob)"})

    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            raw = resp.read().decode("utf-8")
            data = json.loads(raw)
            print_result("OSM: Nominatim Geocoding", True, resp.status, elapsed, f"Resultados encontrados: {len(data)}")
            return True
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("OSM: Nominatim Geocoding", False, 0, elapsed, f"Erro: {e}")
        return False

def test_cartodb_tiles():
    # Tile em Brasília (zoom 12) em alta resolucao Retina 512px (@2x)
    api_key = "cb1_5aim_1_0da00b46f9fe375dd500c522"
    url = f"https://a.basemaps.cartocdn.com/rastertiles/light_all/12/1503/2179@2x.png?key={api_key}"
    req = urllib.request.Request(url, headers={"User-Agent": "DFmob-Android"})

    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            raw = resp.read()
            print_result("CartoDB: Retina @2x (512px)", True, resp.status, elapsed, f"Tamanho da imagem: {len(raw)} bytes")
            return True
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("CartoDB: Retina @2x (512px)", False, 0, elapsed, f"Erro: {e}")
        return False

def test_stadia_maps():
    api_key = "d09566e4-2f4d-4a5c-8b32-adb43a4f44ad"
    tile_url = f"https://tiles.stadiamaps.com/tiles/alidade_smooth/12/1503/2179@2x.png?api_key={api_key}"
    search_url = f"https://api.stadiamaps.com/geocoding/v1/autocomplete?text=UnB&focus.point.lat=-15.7939&focus.point.lon=-47.8828&boundary.country=BRA&api_key={api_key}"

    t0 = time.perf_counter()
    try:
        req = urllib.request.Request(tile_url, headers={"User-Agent": "DFmob-Android"})
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            tile_bytes = len(resp.read())

        req2 = urllib.request.Request(search_url, headers={"User-Agent": "DFmob-Android"})
        with urllib.request.urlopen(req2, timeout=TIMEOUT_SECONDS) as resp2:
            search_data = json.loads(resp2.read().decode("utf-8"))
            features_count = len(search_data.get("features", []))

        elapsed = (time.perf_counter() - t0) * 1000
        print_result("Stadia: Tiles @2x & Autocomplete", True, 200, elapsed, f"Tile: {tile_bytes} bytes, Busca UnB: {features_count} locais")
        return True
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("Stadia: Tiles @2x & Autocomplete", False, 0, elapsed, f"Erro: {e}")
        return False

def test_github_updater():
    url = "https://api.github.com/repos/Cl0udShifter/DFmob/releases/latest"
    req = urllib.request.Request(url, headers={"User-Agent": "DFmob-Updater"})

    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            raw = resp.read().decode("utf-8")
            data = json.loads(raw)
            tag = data.get("tag_name", "sem tag")
            print_result("GitHub: App Releases API", True, resp.status, elapsed, f"Última versão: {tag}")
            return True
    except urllib.error.HTTPError as e:
        elapsed = (time.perf_counter() - t0) * 1000
        if e.code == 404:
            print_result("GitHub: App Releases API", True, e.code, elapsed, "404 esperado se repo ainda não tiver release publicado")
            return True
        print_result("GitHub: App Releases API", False, e.code, elapsed, f"HTTP {e.code}")
        return False
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        print_result("GitHub: App Releases API", False, 0, elapsed, f"Erro: {e}")
        return False

def main():
    print_header("DFmob - Teste e Diagnóstico de Serviços e APIs")
    tests = [
        test_moovit_stops_arrivals,
        test_moovit_line_arrivals,
        test_moovit_trip_planner,
        test_semob_wfs_ultima_posicao,
        test_semob_wfs_percurso,
        test_osm_nominatim_search,
        test_stadia_maps,
        test_cartodb_tiles,
        test_github_updater,
    ]

    results = []
    for t in tests:
        results.append(t())

    passed = sum(1 for r in results if r)
    total = len(results)
    print("=" * 70)
    print(f" Resumo: {passed}/{total} serviços operando normalmente.")
    print("=" * 70)
    return 0 if passed == total else 1

if __name__ == "__main__":
    sys.exit(main())
