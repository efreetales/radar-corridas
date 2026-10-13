"""
Radares das rodovias estaduais (DER-SP, dados abertos, licença CC BY 4.0) na Grande São Paulo.
A planilha do DER traz a coordenada mas não o limite: o limite vem da via do OpenStreetMap no ponto (maxspeed).
Saída out/radares_der.tsv no mesmo formato da CET: lat, lng, limite, sentido, descrição, eixo da via.
"""
import io, json, math, os, re, time, urllib.parse, urllib.request
import openpyxl

URL = "https://dersp.der.sp.gov.br/WebSite/Arquivos/DadosAbertos/AtivosRodoviarios/Radar/radares.xlsx"
BOX = (-24.15, -23.05, -47.30, -45.90)   # Grande SP com folga
OVERPASS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"]
TIPOS = {"CEV": "radar", "CEC": "radar", "REV": "lombada eletrônica", "REC": "lombada eletrônica"}
os.makedirs("out", exist_ok=True)

req = urllib.request.Request(URL, headers={"User-Agent": "Mozilla/5.0"})
wb = openpyxl.load_workbook(io.BytesIO(urllib.request.urlopen(req, timeout=120).read()), read_only=True)
rows = list(wb.worksheets[0].iter_rows(values_only=True))
head = [str(h or "").strip() for h in rows[0]]
ix = {h: i for i, h in enumerate(head)}
cands = []
for r in rows[1:]:
    if r[ix["Cancelamento"]]:
        continue
    m = re.match(r"\s*(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)", str(r[ix["Coordenadas"]] or ""))
    if not m:
        continue
    lat, lng = float(m.group(1)), float(m.group(2))
    if not (BOX[0] < lat < BOX[1] and BOX[2] < lng < BOX[3]):
        continue
    km = r[ix["Km"]]
    desc = f"{r[ix['Rodovia']]} km {str(km).replace('.', ',')} ({TIPOS.get(str(r[ix['Tipo']] or ''), 'radar')}, DER)"
    cands.append((lat, lng, desc))
print("DER na Grande SP:", len(cands))


def overpass_ways(points):
    ways = {}
    for i in range(0, len(points), 40):
        chunk = points[i:i + 40]
        q = "[out:json][timeout:300];(" + "".join(
            f'way(around:40,{la:.6f},{lo:.6f})["highway"~"^(motorway|trunk|primary|secondary|tertiary|motorway_link|trunk_link)$"];'
            for la, lo in chunk) + ");out tags geom;"
        for ep in OVERPASS * 2:
            try:
                rq = urllib.request.Request(ep, data=("data=" + urllib.parse.quote(q)).encode(),
                                            headers={"User-Agent": "RadarCorridas/1.0 (github actions)"})
                with urllib.request.urlopen(rq, timeout=320) as resp:
                    for el in json.loads(resp.read()).get("elements", []):
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


def limit(tags):
    m = re.search(r"\d+", tags.get("maxspeed", "") or "")
    n = int(m.group()) if m else None
    return n if n and 10 <= n <= 130 else None


segs = []
for w in (overpass_ways([(la, lo) for la, lo, _ in cands]) if os.environ.get("COM_MAPA") else []):  # o app completa pelo mapa
    g, tags = w["geometry"], w.get("tags", {})
    ow = tags.get("oneway", "")
    oneway = -1 if ow == "-1" else 1 if ow in ("yes", "true", "1") or tags.get("highway", "").startswith("motorway") else 0
    for i in range(len(g) - 1):
        segs.append(((g[i]["lat"], g[i]["lon"]), (g[i + 1]["lat"], g[i + 1]["lon"]), oneway, limit(tags)))

com_limite = 0
with open("out/radares_der.tsv", "w", encoding="utf-8") as f:
    for lat, lng, desc in cands:
        best, bd = None, 40.0
        for a, b, ow, lim in segs:
            if abs(a[0] - lat) > 0.002 or abs(a[1] - lng) > 0.002:
                continue
            d = dist_seg(lat, lng, a, b) - (5 if lim else 0)   # prefere a via com limite
            if d < bd:
                bd, best = d, (a, b, ow, lim)
        lim = bear = axis = ""
        if best:
            a, b, ow, l = best
            ax = bearing(a[0], a[1], b[0], b[1])
            axis = f"{ax:.0f}"
            bear = f"{ax:.0f}" if ow == 1 else f"{(ax + 180) % 360:.0f}" if ow == -1 else ""
            lim = l or ""
            if l:
                com_limite += 1
        f.write(f"{lat:.6f}\t{lng:.6f}\t{lim}\t{bear}\t{desc}\t{axis}\n")
print("com limite do mapa:", com_limite, "de", len(cands))
