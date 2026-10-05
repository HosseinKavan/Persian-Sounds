#!/usr/bin/env python3
from pathlib import Path
import json
import re
import torch
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM

ROOT = Path(__file__).resolve().parents[1]
WORDS = ROOT / "app" / "src" / "main" / "assets" / "words.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "phoneme_targets.json"

repo = "Reza2kn/negara-g2p-clean-v7.1"
tokenizer = AutoTokenizer.from_pretrained(repo)
model = AutoModelForSeq2SeqLM.from_pretrained(repo)
model.eval()

words = json.loads(WORDS.read_text(encoding="utf-8"))
result = {}

for item in words:
    text = item["word"]
    enc = tokenizer(text, return_tensors="pt")
    with torch.no_grad():
        ids = model.generate(**enc, max_new_tokens=64)
    phones = tokenizer.decode(ids[0], skip_special_tokens=True).strip()
    compact = re.sub(r"\s+", "", phones)
    if not compact:
        raise RuntimeError(f"No phonemes for {text}")
    result[str(item["id"])] = {
        "word": text,
        "phones": phones,
        "first": compact[0],
        "last": compact[-1],
    }
    print(text, "->", phones, compact[0], compact[-1])

OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Wrote {OUT}")
