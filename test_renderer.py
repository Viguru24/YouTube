import urllib.request
import re

url = 'https://www.youtube.com/results?search_query=technology'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Accept-Language': 'en-US,en;q=0.9', 'Cookie': 'SOCS=CAI'})
html = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')

# Extract videoRenderer objects
renderers = html.split('"videoRenderer":{')
print(f"Total videoRenderers found: {len(renderers)-1}")

for idx, block in enumerate(renderers[1:6], 1):
    id_m = re.search(r'"videoId":"([a-zA-Z0-9_-]{11})"', block)
    pub_m = re.search(r'"publishedTimeText":\s*\{\s*"simpleText":\s*"([^"]+)"', block)
    title_m = re.search(r'"title":\s*\{\s*"runs":\s*\[\s*\{\s*"text":\s*"([^"]+)"', block)
    
    vid = id_m.group(1) if id_m else "N/A"
    pub = pub_m.group(1) if pub_m else "N/A"
    title = title_m.group(1) if title_m else "N/A"
    
    print(f"[{idx}] ID: {vid} | Time: {pub} | Title: {title[:40]}")
