"""
Extrai a lista oficial de pontos de fiscalização eletrônica da CET (painel Power BI público)
e grava radares_cet.tsv para o app Radar Corridas.
Formato de saída: lat, lng, limite, sentido(vazio), descrição  (igual ao radares.tsv do app)
"""
import csv, json, os, re, sys, urllib.request

KEY = "d8b9566c-25e1-4f66-a54b-ae3b02bda6e7"
VIEW = "https://app.powerbi.com/view?r=eyJrIjoiZDhiOTU2NmMtMjVlMS00ZjY2LWE1NGItYWUzYjAyYmRhNmU3IiwidCI6ImIwODI2Nzg2LTZmNjktNGVjZC1iZmEyLTYyMmRhYTJiMTlhZCJ9"
ENTITY = "tblFiscalizacaoEletronica"
COLS = ["ID", "LATITUDE", "LONGITUDE", "VELOCIDADE", "DESCRIÇÃO DO LOCAL", "ENQUADRAMENTO", "ENQUADRAMENTOS",
        "TIPO", "DESATIVAÇÃO", "ATIVAÇÃO", "FAIXAS", "CÓDIGO LOCAL", "REDUTOR"]
os.makedirs("out", exist_ok=True)
H = {"User-Agent": "Mozilla/5.0", "X-PowerBI-ResourceKey": KEY}


def http(url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method="POST" if data else "GET",
                                 headers={**H, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


html = http(VIEW).decode("utf-8", "ignore")
cluster = re.findall(r"https://wabi-[a-z0-9-]+\.analysis\.windows\.net", html)[0]
api = cluster.replace("-redirect", "-api")
expl = json.loads(http(f"{api}/public/reports/{KEY}/modelsAndExploration?preferReadOnlySession=true"))
model = expl["models"][0]
report_id = expl["exploration"]["report"]["objectId"]

select = [{"Column": {"Expression": {"SourceRef": {"Source": "t"}}, "Property": c}, "Name": f"t.{c}"} for c in COLS]
query = {
    "version": "1.0.0",
    "queries": [{
        "Query": {"Commands": [{"SemanticQueryDataShapeCommand": {
            "Query": {"Version": 2, "From": [{"Name": "t", "Entity": ENTITY, "Type": 0}], "Select": select},
            "Binding": {"Primary": {"Groupings": [{"Projections": list(range(len(COLS)))}]},
                        "DataReduction": {"DataVolume": 4, "Primary": {"Window": {"Count": 30000}}}, "Version": 1},
            "ExecutionMetricsKind": 1}}]},
        "QueryId": "",
        "ApplicationContext": {"DatasetId": model["dbName"], "Sources": [{"ReportId": report_id, "VisualId": "radar"}]}
    }],
    "cancelQueries": [],
    "modelId": model["id"],
}
raw = http(f"{api}/public/reports/querydata?synchronous=true", query)
open("out/raw.json", "wb").write(raw)
res = json.loads(raw)
dsr = res["results"][0]["result"]["data"]["dsr"]
ds = dsr["DS"][0]
dicts = ds.get("ValueDicts", {})
rows = ds["PH"][0]["DM0"]
schema = rows[0]["S"]
out = []
prev = [None] * len(schema)
for row in rows:
    rep = row.get("R", 0)
    nul = row.get("Ø", 0)
    vals = iter(row.get("C", []))
    cur = []
    for i, col in enumerate(schema):
        if rep & (1 << i):
            v = prev[i]
        elif nul & (1 << i):
            v = None
        else:
            v = next(vals, None)
            if "DN" in col and isinstance(v, int):
                v = dicts[col["DN"]][v]
        cur.append(v)
    prev = cur
    out.append(dict(zip(COLS, cur)))

print("linhas:", len(out), "completo:", ds.get("IC"))
with open("out/cet_bruto.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=COLS, delimiter=";")
    w.writeheader()
    w.writerows(out)


def num(v):
    try:
        return float(str(v).replace(",", "."))
    except Exception:
        return None


def nice(desc):
    """'AVENIDA JAGUARE (BAIRRO/CENTRO) X PRACA ...' -> 'Av. Jaguare (bairro/centro)'"""
    d = desc.split(" X ")[0].strip().title()
    for a, b in [("Avenida ", "Av. "), ("Rua ", "R. "), ("Estrada ", "Estr. "), ("Viaduto ", "Vd. "), ("Praca ", "Pç. "),
                 (" De ", " de "), (" Da ", " da "), (" Do ", " do "), (" Dos ", " dos "), (" Das ", " das "), (" E ", " e ")]:
        d = d.replace(a, b)
    d = re.sub(r"\(([^)]*)\)", lambda m: "(" + m.group(1).lower() + ")", d)
    return d


ROADS = ("^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|"
         "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$")
OVERPASS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"]
import math, time, urllib.parse


def overpass_ways(points):
    """Vias do mapa a até 35 m de cada ponto (em lotes)."""
    ways = {}
    for i in range(0, len(points), 150):
        chunk = points[i:i + 150]
        q = "[out:json][timeout:300];(" + "".join(
            f'way(around:35,{la:.6f},{lo:.6f})["highway"~"{ROADS}"];' for la, lo in chunk) + ");out tags geom;"
        for ep in OVERPASS:
            try:
                req = urllib.request.Request(ep, data=("data=" + urllib.parse.quote(q)).encode(),
                                             headers={"User-Agent": "RadarCorridas/1.0 (github actions)"})
                with urllib.request.urlopen(req, timeout=320) as r:
                    for el in json.loads(r.read()).get("elements", []):
                        if el.get("type") == "way" and el.get("geometry"):
                            ways[el["id"]] = el
                break
            except Exception as ex:
                print("overpass falhou", ep, ex)
                time.sleep(5)
        time.sleep(2)
    return list(ways.values())


def bearing(a_lat, a_lng, b_lat, b_lng):
    c = math.cos(math.radians(a_lat))
    return (math.degrees(math.atan2((b_lng - a_lng) * c, b_lat - a_lat)) + 360) % 360


def dist_seg(lat, lng, a, b):
    k = 111320.0
    c = math.cos(math.radians(lat))
    ax, ay = (a[1] - lng) * k * c, (a[0] - lat) * k
    bx, by = (b[1] - lng) * k * c, (b[0] - lat) * k
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0 if l2 == 0 else max(0, min(1, -(ax * dx + ay * dy) / l2))
    return math.hypot(ax + t * dx, ay + t * dy)


def norm(txt):
    import unicodedata
    t = unicodedata.normalize("NFD", txt or "").encode("ascii", "ignore").decode().lower()
    for w in ["avenida ", "av. ", "av ", "rua ", "r. ", "estrada ", "estr. ", "alameda ", "al. ", "viaduto ", "ponte "]:
        t = t.replace(w, "")
    return re.sub(r"[^a-z0-9 ]", "", t).strip()


cands = []
for r in out:
    lat, lng = num(r["LATITUDE"]), num(r["LONGITUDE"])
    if lat is None or lng is None or not (-24.2 < lat < -23.2 and -47.2 < lng < -46.2):
        continue
    if r["DESATIVAÇÃO"]:
        continue
    codes = [c.strip() for c in str(r["ENQUADRAMENTOS"] or "").split(",")]
    if "V" not in codes:
        continue
    cands.append((r, lat, lng))

ways = overpass_ways([(la, lo) for _, la, lo in cands])
print("vias do mapa:", len(ways))
segs = []
for w in ways:
    g = w["geometry"]
    tags = w.get("tags", {})
    ow = tags.get("oneway", "")
    hw = tags.get("highway", "")
    oneway = -1 if ow == "-1" else 1 if ow in ("yes", "true", "1") or tags.get("junction") == "roundabout" or hw.startswith("motorway") else 0
    for i in range(len(g) - 1):
        segs.append(((g[i]["lat"], g[i]["lon"]), (g[i + 1]["lat"], g[i + 1]["lon"]), oneway, tags.get("name", ""), hw))

kept = 0
matched_name = 0
with open("out/radares_cet.tsv", "w", encoding="utf-8") as f:
    for r, lat, lng in cands:
        m = re.match(r"\s*(\d+)", str(r["VELOCIDADE"] or ""))
        lim = int(m.group(1)) if m else ""
        desc = nice(str(r["DESCRIÇÃO DO LOCAL"] or "")).replace("\t", " ").replace("\n", " ")
        street = norm(str(r["DESCRIÇÃO DO LOCAL"] or "").split(" X ")[0].split("(")[0])
        # Via do mapa mais próxima; prefere a que tem o mesmo nome da descrição da CET
        best, best_score = None, 1e9
        for a, b, ow, name, hw in segs:
            if abs(a[0] - lat) > 0.001 or abs(a[1] - lng) > 0.001:
                continue
            d = dist_seg(lat, lng, a, b)
            if d > 35:
                continue
            same = street and norm(name) and (norm(name) in street or street in norm(name))
            score = d - (25 if same else 0)
            if score < best_score:
                best_score, best = score, (a, b, ow, name, same)
        axis = bear = ""
        if best:
            a, b, ow, name, same = best
            ax = bearing(a[0], a[1], b[0], b[1])
            axis = f"{ax:.0f}"
            if ow == 1:
                bear = f"{ax:.0f}"
            elif ow == -1:
                bear = f"{(ax + 180) % 360:.0f}"
            if same:
                matched_name += 1
        f.write(f"{lat:.6f}\t{lng:.6f}\t{lim}\t{bear}\t{desc}\t{axis}\n")
        kept += 1
print("radares de velocidade ativos:", kept, "| com via do mesmo nome:", matched_name)
