import urllib.request
import urllib.parse
import re
import json

query = 'National Geographic'
url = 'https://www.youtube.com/results?search_query=' + urllib.parse.quote(query) + '&hl=en&gl=US'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Accept-Language': 'en-US,en;q=0.9',
    'Cookie': 'PREF=hl=en&gl=US; SOCS=CAI'
}
req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8')
        print('HTML len:', len(html))
        m = re.search(r'var ytInitialData = ({.*?});</script>', html)
        if not m:
            m = re.search(r'ytInitialData = ({.*?});', html)
        if m:
            print('Found ytInitialData len:', len(m.group(1)))
            raw = m.group(1)
            videos = re.findall(r'"videoId":"([a-zA-Z0-9_-]{11})"', raw)
            print('Found videoIds count:', len(videos), videos[:5])
        else:
            print('No ytInitialData found!')
except Exception as e:
    print('Error:', e)
