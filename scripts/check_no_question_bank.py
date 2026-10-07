from pathlib import Path
blocked = ["内部资料，请勿网上转发", "2026年部分练习题", "行政处罚法"]
exts = {".html", ".json", ".csv", ".txt", ".md", ".java", ".gradle", ".yml", ".yaml"}
for p in Path(".").rglob("*"):
    if p.is_file() and p.suffix.lower() in exts and ".git" not in p.parts:
        try: text = p.read_text(encoding="utf-8")
        except Exception: continue
        for s in blocked:
            if s in text:
                raise SystemExit(f"Blocked internal-data marker found in {p}: {s}")
print("Privacy check passed: no known internal-question-bank markers found.")
