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
        "viewbox": VIEWBOX, "bounded": 1, "addressdetails": 1})
    for tent in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                time.sleep(1.1)
                return json.load(r)
        except Exception as ex:
            print("  erro", ex); time.sleep(5)
    return []

def bom(r):
    a = r.get("address") or {}
    if (a.get("city") or a.get("town") or a.get("municipality")) != "São Paulo":
        return False  # Osasco, Guarulhos etc.
    return r.get("type") not in VAGO and r.get("addresstype") not in VAGO and r.get("category") != "boundary"

def sem_acento(t):
    import unicodedata
    return "".join(c for c in unicodedata.normalize("NFD", t.lower()) if unicodedata.category(c) != "Mn")

def main():
    d = json.load(open("eventos.json"))
    cache = {}
    ok = falha = 0
    for e in d.get("eventos", []):
        if e.get("lat") is not None and e.get("lng") is not None:
            e.pop("sem_local", None); ok += 1; continue
        end, loc = e.get("endereco", ""), e.get("local", "")
        partes = [p.strip() for p in end.split(",") if p.strip()]
        bairros = [sem_acento(p) for p in partes if not re.search(r"\d", p) and p != "São Paulo"][1:]
        tentativas = []  # (busca, só pelo nome do lugar?)
        if re.search(r"\d", end):
            tentativas.append((end, False))
            # sem o bairro, às vezes o OSM acha melhor
            if len(partes) >= 2: tentativas.append((f"{partes[0]}, {partes[1]}, São Paulo", False))
        if loc: tentativas.append((f"{loc}, São Paulo", True))
        achou = None
        for q, so_nome in tentativas:
            if q not in cache: cache[q] = [r for r in busca(q) if bom(r)]
            for r in cache[q]:
                if so_nome:
                    # pelo nome: tem que ser o estabelecimento (não uma rua com o mesmo nome)
                    # e no bairro informado, se houver
                    if r.get("category") == "highway": continue
                    if bairros and not any(b in sem_acento(r.get("display_name", "")) for b in bairros): continue
                achou = r; break
            if achou: break
        if achou:
            e["lat"], e["lng"] = round(float(achou["lat"]), 6), round(float(achou["lon"]), 6)
            e.pop("sem_local", None); ok += 1
            if achou.get("category") == "highway": e["local_aproximado"] = True  # só a rua, sem o número
            else: e.pop("local_aproximado", None)
            print("OK   ", e["nome"][:40], "|", achou.get("display_name", "")[:70])
        else:
            e["sem_local"] = True; falha += 1
            print("FALHA", e["nome"][:40], "|", end)
    d["geocodificacao"] = {"com_local": ok, "sem_local": falha}
    json.dump(d, open("eventos.json", "w"), ensure_ascii=False, indent=1)
    print(f"com local: {ok}  sem local: {falha}")

main()
