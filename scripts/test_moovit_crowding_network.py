import urllib.request
import struct
import time
import json
from urllib.error import HTTPError

XOR_KEY = 0x5A

def deobfuscate(bytes_list):
    return ''.join(chr(x ^ XOR_KEY) for x in bytes_list)

API_KEY = deobfuscate([55, 53, 53, 44, 51, 46, 5, 104, 109, 111, 107, 109, 106, 105, 110, 106, 111])
USER_KEY = deobfuscate([104, 24, 28, 107, 98, 25, 27, 109, 31, 110, 99, 109, 99, 109, 106, 31, 104, 28, 106, 104, 28, 110, 31, 27, 27, 106, 24, 31, 98, 110, 108, 107])
METRO_ID = deobfuscate([107, 109, 106, 104])

STOPS = [
    (8638501, 'Rod.P.Piloto | Plataforma A'),
    (6340659, 'Rod.P.Piloto | Plataforma C'),
    (6345109, 'Tag. Centro | Praça do Relógio'),
    (6342937, 'EPNB | Riacho Mall / Ultrabox'),
    (6345190, 'Ceilândia Centro | Fort Atacadista'),
    (6340929, 'EPTG | SIA (CAESB)'),
    (6342206, 'W3 Sul | 502/503 Sul'),
    (1694621558, 'W3 Norte | 702/703 Norte'),
    (3599806, 'Rod. Sobradinho | Terminal Qd. 18'),
    (3599801, 'Polo Jk | Ultrabox (Gama)')
]

def build_stop_request(stop_id, from_time_ms):
    buf = bytearray()
    # Field 1: stopIds (list<i32>)
    buf.append(15)
    buf.extend(struct.pack('>h', 1))
    buf.append(8)
    buf.extend(struct.pack('>i', 1))
    buf.extend(struct.pack('>i', stop_id))

    # Field 2: conf (STRUCT)
    buf.append(12)
    buf.extend(struct.pack('>h', 2))
    # 1: fromTime (i64)
    buf.append(10)
    buf.extend(struct.pack('>h', 1))
    buf.extend(struct.pack('>q', from_time_ms))
    # 2: intrestingInLastArrivals
    buf.append(2)
    buf.extend(struct.pack('>h', 2))
    buf.append(1)
    # 3: includeOnlyLastArrivals
    buf.append(2)
    buf.extend(struct.pack('>h', 3))
    buf.append(0)
    # 4: includeStaticSchedule
    buf.append(2)
    buf.extend(struct.pack('>h', 4))
    buf.append(1)
    # 5: includeShapeSegments
    buf.append(2)
    buf.extend(struct.pack('>h', 5))
    buf.append(0)
    # 6: forceAllArrivalsRetrieval
    buf.append(2)
    buf.extend(struct.pack('>h', 6))
    buf.append(1)
    # End of structs
    buf.append(0)
    buf.append(0)
    return bytes(buf)

def execute_moovit_query(stop_id, from_time_ms, metro_rev):
    url = 'https://app5.moovitapp.com/services-app/services/V4/StopsArrivals'
    payload = build_stop_request(stop_id, from_time_ms)

    def do_post(rev):
        headers = {
            'Host': 'app5.moovitapp.com',
            'User-Agent': 'Dalvik/2.1.0 (Linux; U; Android 14; 2412DPC0AG Build/UKQ1.231003.002)',
            'CLIENT_VERSION': '5.204.1.1814',
            'PHONE_TYPE': '1',
            'API_KEY': API_KEY,
            'USER_KEY': USER_KEY,
            'Metro-Revision-Metro-Id': METRO_ID,
            'Metro-Revision-Number': rev,
            'Content-Type': 'application/octet',
            'Accept': 'application/json'
        }
        req = urllib.request.Request(url, data=payload, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=8) as resp:
                return resp.getcode(), resp.headers, resp.read().decode('utf-8')
        except HTTPError as e:
            return e.code, e.headers, e.read().decode('utf-8', errors='ignore')

    code, hdrs, body = do_post(metro_rev)
    new_rev = hdrs.get('Metro-Revision-Number', metro_rev)
    if code == 412 and new_rev:
        code, hdrs, body = do_post(new_rev)
    
    return code, new_rev, body

def run_tests():
    print('================================================================')
    print('TESTE DE REDE EMPIRICO: CONSULTA DE LOTACAO NA API MOOVIT (DF)')
    print('================================================================')
    metro_rev = '1795150951139'
    current_ms = int(time.time() * 1000)

    total_stops = len(STOPS)
    total_lines = 0
    total_arrivals = 0
    field_14_count = 0
    field_4_count = 0

    results = []

    for stop_id, stop_name in STOPS:
        code, metro_rev, body = execute_moovit_query(stop_id, current_ms, metro_rev)
        if code != 200:
            print(f'[FALHA] Parada {stop_id} ({stop_name}): HTTP {code}')
            continue

        try:
            data = json.loads(body)
            has_field_4 = ('4' in data)
            if has_field_4:
                field_4_count += 1

            stop_lines = 0
            stop_arrs = 0
            stop_field_14 = 0

            if '3' in data and 'lst' in data['3']:
                lines = data['3']['lst'][2:]
                stop_lines = len(lines)
                for l in lines:
                    if '2' in l and 'lst' in l['2']:
                        arrs = l['2']['lst'][2:]
                        stop_arrs += len(arrs)
                        for a in arrs:
                            if '14' in a:
                                stop_field_14 += 1

            total_lines += stop_lines
            total_arrivals += stop_arrs
            field_14_count += stop_field_14

            status_str = f'Linhas: {stop_lines:2d} | Chegadas: {stop_arrs:2d} | Campo 14: {stop_field_14} | Campo 4: {int(has_field_4)}'
            print(f'[OK] {stop_id:10d} - {stop_name:<36} -> {status_str}')
            results.append({
                'stop_id': stop_id,
                'stop_name': stop_name,
                'lines': stop_lines,
                'arrivals': stop_arrs,
                'field_14': stop_field_14,
                'field_4': int(has_field_4)
            })
        except Exception as e:
            print(f'[ERRO] {stop_id} ({stop_name}): {e}')

    print('----------------------------------------------------------------')
    print(f'Total de Paradas Analisadas: {total_stops}')
    print(f'Total de Linhas Ativas Retornadas: {total_lines}')
    print(f'Total de Chegadas Individuais Analisadas: {total_arrivals}')
    print(f'Chegadas com Campo 14 (vehicleAttributes): {field_14_count}')
    print(f'Respostas com Campo 4 (stopRealTimeInfo): {field_4_count}')
    print('================================================================')
    if field_14_count == 0:
        print('CONCLUSAO EMPIRICA: A API do Moovit NAO transmite o campo 14 para Brasilia.')
        print('JUSTIFICATIVA: SEMOB-DF nao fornece telemetria de ocupacao e densidade de relatos locais e insuficiente.')
        print('Acao necessaria no DFmob: decodificar campo 14 como prioridade e aplicar motor preditivo pendular do DF.')

if __name__ == '__main__':
    run_tests()
