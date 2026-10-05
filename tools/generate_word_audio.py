#!/usr/bin/env python3
import asyncio
import json
import sys
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parents[1]
WORDS = ROOT / "app" / "src" / "main" / "assets" / "words.json"
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"

VOICE = "fa-IR-DilaraNeural"
RATE = "-28%"
PITCH = "+0Hz"
VOLUME = "+0%"

# Short pronunciation overrides for words that neural TTS can otherwise rush.
WORD_SPEECH_OVERRIDES = {
    "آب": "آب.",
    "او": "او.",
    "ما": "ما.",
    "تو": "تو.",
    "پا": "پا.",
    "دو": "دو.",
}

async def synth(text: str, output: Path, rate: str = RATE):
    communicate = edge_tts.Communicate(
        text=text,
        voice=VOICE,
        rate=rate,
        pitch=PITCH,
        volume=VOLUME,
    )
    await communicate.save(str(output))
    if not output.exists() or output.stat().st_size < 800:
        raise RuntimeError(f"audio generation failed for: {text}")

async def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for pattern in (
        "word_*.mp3",
        "question_first_*.mp3",
        "question_last_*.mp3",
        "feedback_*.mp3",
        "prompt_*.mp3",
    ):
        for old in OUT.glob(pattern):
            old.unlink()

    words = json.loads(WORDS.read_text(encoding="utf-8"))

    await synth("آفرین! درست گفتی.", OUT / "feedback_correct.mp3", "-24%")
    await synth("این یکی درست نبود. دوباره آروم و واضح بگو.", OUT / "feedback_wrong.mp3", "-28%")
    await synth("صدات رو خوب نشنیدم. یک بار دیگه بگو.", OUT / "feedback_unclear.mp3", "-28%")
    await synth("عالی بود! هر دو صدا درست بود. بریم سراغ واژه‌ی بعدی.", OUT / "feedback_complete.mp3", "-25%")
    await synth("گوش می‌دم. حالا بگو.", OUT / "prompt_listening.mp3", "-30%")

    for item in words:
        idx = item["id"]
        word = item["word"]
        spoken_word = WORD_SPEECH_OVERRIDES.get(word, word)

        await synth(spoken_word, OUT / f"word_{idx:02d}.mp3", "-32%")
        await synth(
            f"صدای اولِ {word} چیه؟",
            OUT / f"question_first_{idx:02d}.mp3",
            "-30%",
        )
        await synth(
            f"صدای آخرِ {word} چیه؟",
            OUT / f"question_last_{idx:02d}.mp3",
            "-30%",
        )

    print(f"Generated {len(words)} words + spoken instructions/feedback with {VOICE}")

if __name__ == "__main__":
    asyncio.run(main())
