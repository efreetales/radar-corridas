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


kept = 0
with open("out/radares_cet.tsv", "w", encoding="utf-8") as f:
    for r in out:
        lat, lng = num(r["LATITUDE"]), num(r["LONGITUDE"])
        if lat is None or lng is None or not (-24.2 < lat < -23.2 and -47.2 < lng < -46.2):
            continue
        if r["DESATIVAÇÃO"]:
            continue  # desativado
        lim = num(r["VELOCIDADE"])
        lim = int(lim) if lim and 10 <= lim <= 130 else ""
        desc = str(r["DESCRIÇÃO DO LOCAL"] or "").replace("\t", " ").replace("\n", " ").strip()
        f.write(f"{lat:.6f}\t{lng:.6f}\t{lim}\t\t{desc}\n")
        kept += 1
print("ativos com coordenadas:", kept)
