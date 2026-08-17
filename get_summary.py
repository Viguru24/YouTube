import urllib.request
import json
import re
import html

videoId = 'pFZOgx8TqQA'
url = f'https://www.youtube.com/watch?v={videoId}&hl=en&gl=US'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Accept-Language': 'en-US,en;q=0.9',
    'Cookie': 'PREF=hl=en&gl=US; SOCS=CAI'
}

req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    page_html = resp.read().decode('utf-8')

m = re.search(r'"captionTracks":\s*(\[.*?\])', page_html)
if m:
    tracks = json.loads(m.group(1))
    for t in tracks:
        base = t.get('baseUrl')
        cap_req = urllib.request.Request(base, headers=headers)
        try:
            with urllib.request.urlopen(cap_req) as cap_resp:
                raw = cap_resp.read().decode('utf-8')
                texts = re.findall(r'<text[^>]*>(.*?)</text>', raw)
                clean_texts = [html.unescape(t) for t in texts]
                full_text = ' '.join(clean_texts)
                print(f'Extracted transcript length: {len(full_text)} characters')
                with open('video_transcript.txt', 'w', encoding='utf-8') as f:
                    f.write(full_text)
        except Exception as e:
            print('Track error:', e)
else:
    print('No captionTracks found')
