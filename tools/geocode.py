"""Coloca latitude/longitude em cada evento de eventos.json (OpenStreetMap / Nominatim).
Só aceita resultado específico (rua+número ou o próprio estabelecimento) dentro da cidade de SP.
Evento que não dá para localizar com precisão recebe "sem_local": true e não vai para o mapa."""
import json, re, time, urllib.parse, urllib.request

UA = {"User-Agent": "RadarCorridas-eventos/1.0 (github.com/efreetales/radar-corridas)"}
VIEWBOX = "-46.83,-23.36,-46.36,-23.80"  # cidade de São Paulo
VAGO = {"suburb", "neighbourhood", "quarter", "city_district", "city", "town", "municipality",
        "state", "county", "administrative", "postcode", "region", "country", "borough"}

def busca(q):
    url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode({
        "q": q, "format": "jsonv2", "limit": 3, "countrycodes": "br",
        "viewbox": VIEWBOX, "bounded": 1, "addressdetails": 0})
    for tent in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                time.sleep(1.1)
                return json.load(r)
        except Exception as ex:
            print("  erro", ex); time.sleep(5)
    return []

def bom(r):
    return r.get("type") not in VAGO and r.get("addresstype") not in VAGO and r.get("category") != "boundary"

def main():
    d = json.load(open("eventos.json"))
    cache = {}
    ok = falha = 0
    for e in d.get("eventos", []):
        if e.get("lat") is not None and e.get("lng") is not None:
            e.pop("sem_local", None); ok += 1; continue
        end, loc = e.get("endereco", ""), e.get("local", "")
        tentativas = []
        if re.search(r"\d", end):
            tentativas.append(end)
            # sem o bairro, às vezes o OSM acha melhor
            partes = [p.strip() for p in end.split(",")]
            if len(partes) >= 2: tentativas.append(f"{partes[0]}, {partes[1]}, São Paulo")
        if loc: tentativas.append(f"{loc}, São Paulo")
        achou = None
        for q in tentativas:
            if q not in cache: cache[q] = [r for r in busca(q) if bom(r)]
            if cache[q]: achou = cache[q][0]; break
        if achou:
            e["lat"], e["lng"] = round(float(achou["lat"]), 6), round(float(achou["lon"]), 6)
            e.pop("sem_local", None); ok += 1
            print("OK   ", e["nome"][:40], "|", achou.get("display_name", "")[:70])
        else:
            e["sem_local"] = True; falha += 1
            print("FALHA", e["nome"][:40], "|", end)
    d["geocodificacao"] = {"com_local": ok, "sem_local": falha}
    json.dump(d, open("eventos.json", "w"), ensure_ascii=False, indent=1)
    print(f"com local: {ok}  sem local: {falha}")

main()
