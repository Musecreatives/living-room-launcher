"""Inline prototype assets into one HTML file (artifact pages cannot load remote images)."""
import base64, mimetypes, re
from pathlib import Path

here = Path(__file__).parent
src = (here / "index.src.html").read_text(encoding="utf-8")


def data_uri(match):
    f = here / "assets" / match.group(1)
    mime = mimetypes.guess_type(f.name)[0] or "application/octet-stream"
    return f"data:{mime};base64,{base64.b64encode(f.read_bytes()).decode()}"


(here / "index.html").write_text(re.sub(r"\{\{([\w.-]+)\}\}", data_uri, src), encoding="utf-8")
print("index.html", round((here / "index.html").stat().st_size / 1024), "KB")
