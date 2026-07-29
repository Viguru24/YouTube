import urllib.request
import json

endpoints = [
    "https://pipedapi.adminforge.de/search?q=Doug+In+Exile&filter=all",
    "https://pipedapi.astral.cool/search?q=Doug+In+Exile&filter=all",
]

for url in endpoints:
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        res = urllib.request.urlopen(req, timeout=5).read().decode('utf-8')
        data = json.loads(res)
        items = data.get('items', data) if isinstance(data, dict) else data
        print(f"=== {url} ===")
        print(f"Total items: {len(items)}")
        if items:
            item = items[0]
            print(f"ALL KEYS: {list(item.keys())}")
            print(f"FULL FIRST ITEM: {json.dumps(item, indent=2)}")
        break
    except Exception as e:
        print(f"FAIL {url}: {e}")
