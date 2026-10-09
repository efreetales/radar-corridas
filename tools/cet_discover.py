"""Descobre a estrutura do painel Power BI público da CET (fiscalização eletrônica)."""
import json, os, urllib.request

KEY = "d8b9566c-25e1-4f66-a54b-ae3b02bda6e7"
TENANT = "b0826786-6f69-4ecd-bfa2-622daa2b19ad"
os.makedirs("out", exist_ok=True)

def get(url, headers=None):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", **(headers or {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()

def post(url, body, headers=None):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method="POST",
                                 headers={"User-Agent": "Mozilla/5.0", "Content-Type": "application/json", **(headers or {})})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()

import re
api = None
try:
    html = get("https://app.powerbi.com/view?r=eyJrIjoiZDhiOTU2NmMtMjVlMS00ZjY2LWE1NGItYWUzYjAyYmRhNmU3IiwidCI6ImIwODI2Nzg2LTZmNjktNGVjZC1iZmEyLTYyMmRhYTJiMTlhZCJ9").decode("utf-8", "ignore")
    found = sorted(set(re.findall(r"https://wabi-[a-z0-9-]+\.analysis\.windows\.net", html)))
    print("clusters no html:", found)
except Exception as ex:
    print("html falhou", ex)
    found = []
cands = []
for f in found:
    cands.append(f.replace("-redirect", "-api"))
for r in ["brazil-south-b-primary", "brazil-south-primary", "brazil-south-c-primary", "south-central-us", "us-east2-b-primary",
          "west-us", "north-europe", "west-europe", "us-north-central-b-primary", "us-east-b-primary"]:
    cands.append(f"https://wabi-{r}-api.analysis.windows.net")
for c in cands:
    try:
        get(f"{c}/public/reports/{KEY}/modelsAndExploration?preferReadOnlySession=true", {"X-PowerBI-ResourceKey": KEY})
        api = c
        print("OK", c)
        break
    except Exception as ex:
        print("nao", c, ex)
if api is None:
    raise SystemExit("nenhum cluster respondeu")
h = {"X-PowerBI-ResourceKey": KEY}
expl = get(f"{api}/public/reports/{KEY}/modelsAndExploration?preferReadOnlySession=true", h)
open("out/exploration.json", "wb").write(expl)
e = json.loads(expl)
model_id = e["models"][0]["id"]
print("model", model_id)
try:
    schema = post(f"{api}/public/reports/conceptualschema", {"modelIds": [model_id]}, h)
    open("out/schema.json", "wb").write(schema)
except Exception as ex:
    print("schema falhou", ex)
open("out/meta.json", "w").write(json.dumps({"api": api, "model": model_id, "dbName": e["models"][0].get("dbName")}))
