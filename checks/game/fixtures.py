"""Dữ liệu nhạc giả và cấu hình riêng dùng chung cho jar mới và jar đối chứng."""

import hashlib
import json
import sys
from pathlib import Path

directory = Path(sys.argv[1])
directory.mkdir(parents=True, exist_ok=True)
config = {
    "client": {"intro": False, "lyricsAutoFetch": False, "lyricsNext": False, "lyricsFollow": False, "lyricsBearing": 270, "lyricsElevation": 15},
    "music": {"enabled": True, "showLyrics": True, "source": "ANY", "lyricsStyle": "SKY", "autoPerf": False, "cosmetic": "cap_back"},
    "effects": {"sky": 1, "cosmeticsEnabled": True, "trail": 3, "aura": True, "deathFx": 3, "deathOthers": True},
}
(directory / "nosclient.json").write_text(json.dumps(config), encoding="utf-8")
(directory / "nosclient.properties").write_text("intro=false\nlyrics=true\nlyricsAutoFetch=false\nlyricsAurora=false\nsky=false\ndfps=false\n", encoding="utf-8")
data = {"synced": True, "source": "verification fixture", "duration": 3600000, "starts": [], "ends": [], "texts": [], "words": [], "wordStarts": [], "wordEnds": []}
lrc = []
for index in range(300):
    start = index * 4000
    text = "Nostalgia music under aurora" if index % 2 == 0 else "Âm nhạc Nostalgia dưới bầu trời"
    words = text.split()
    data["starts"].append(start)
    data["ends"].append(start + 3900)
    data["texts"].append(text)
    data["words"].append(words)
    data["wordStarts"].append([start + word * 3000 // len(words) for word in range(len(words))])
    data["wordEnds"].append([start + (word + 1) * 3000 // len(words) for word in range(len(words))])
    lrc.append(f"[{start // 60000:02}:{start // 1000 % 60:02}.00]{text}")
(directory / "words.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
(directory / "words.lrc").write_text("\n".join(lrc), encoding="utf-8")
print(hashlib.sha1(b"rick astley\0never gonna give you up|v6").hexdigest()[:20] + ".json")
print(hashlib.sha256(b"never gonna give you up\nrick astley\n3600").hexdigest() + ".lrc")
