#!/usr/bin/env python3
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app" / "src" / "main" / "assets" / "words.json"
words = json.loads(path.read_text(encoding="utf-8"))
assert len(words) == 50, f"Expected 50 words, found {len(words)}"
ids = [w["id"] for w in words]
assert ids == list(range(1, 51)), "IDs must be 1..50"
for w in words:
    compact = "".join(w["word"].replace("\u200c", "").split())
    assert w["firstSound"], f"Missing first sound for {w['word']}"
    assert w["lastSound"], f"Missing last sound for {w['word']}"
    assert w["firstSound"] == compact[0], f"First sound mismatch for {w['word']}"
    assert w["lastSound"] == compact[-1], f"Last sound mismatch for {w['word']}"
print(f"OK: {len(words)} words validated")
