#!/usr/bin/env python3
import json
import sys
import wave
from pathlib import Path

from piper import PiperVoice, SynthesisConfig

ROOT = Path(__file__).resolve().parents[1]
WORDS = ROOT / "app" / "src" / "main" / "assets" / "words.json"
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"

if len(sys.argv) != 2:
    raise SystemExit("usage: generate_word_audio.py /path/to/fa_IR-amir-medium.onnx")

model_path = Path(sys.argv[1])
if not model_path.exists():
    raise SystemExit(f"missing Piper model: {model_path}")

OUT.mkdir(parents=True, exist_ok=True)
for old in OUT.glob("word_*.wav"):
    old.unlink()

words = json.loads(WORDS.read_text(encoding="utf-8"))
voice = PiperVoice.load(model_path)
config = SynthesisConfig(
    length_scale=1.08,
    noise_scale=0.55,
    noise_w_scale=0.70,
    volume=1.05,
    normalize_audio=True,
)

for item in words:
    output = OUT / f"word_{item['id']:02d}.wav"
    with wave.open(str(output), "wb") as wav_file:
        voice.synthesize_wav(item["word"], wav_file, syn_config=config)
    if output.stat().st_size < 500:
        raise RuntimeError(f"audio generation failed for {item['word']}")

print(f"Generated {len(words)} Persian word recordings in {OUT}")
