import urllib.request
import json
import os
from dotenv import load_dotenv

load_dotenv()

key = os.getenv("GOOGLE_API_KEY")

url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key={key}"
payload = json.dumps({
    "model": "models/gemini-embedding-001",
    "content": {"parts": [{"text": "hello world"}]}
}).encode()

req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})

try:
    with urllib.request.urlopen(req) as r:
        data = json.loads(r.read())
        print("SUCCESS")
        print("Embedding dimension:", len(data["embedding"]["values"]))
except urllib.error.HTTPError as e:
    print("HTTP Error:", e.code, e.reason)
    print("Response:", e.read().decode())
except Exception as e:
    print("ERROR:", e)