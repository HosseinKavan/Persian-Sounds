#!/usr/bin/env python3
from pathlib import Path
import json
import shutil
import torch
from transformers import Wav2Vec2ForCTC
from huggingface_hub import hf_hub_download
from onnxruntime.quantization import quantize_dynamic, QuantType

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "avasanj"
OUT.mkdir(parents=True, exist_ok=True)

repo_id = "Reza2kn/AvaSanj-v1"
print("Loading AvaSanj...")
model = Wav2Vec2ForCTC.from_pretrained(repo_id)
model.eval()

class Wrapper(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m
    def forward(self, input_values):
        return self.m(input_values).logits

wrapper = Wrapper(model)
dummy = torch.zeros(1, 16000, dtype=torch.float32)
fp32 = OUT / "avasanj_fp32.onnx"
int8 = OUT / "avasanj_int8.onnx"

print("Exporting ONNX...")
torch.onnx.export(
    wrapper,
    dummy,
    fp32,
    input_names=["input_values"],
    output_names=["logits"],
    dynamic_axes={
        "input_values": {1: "samples"},
        "logits": {1: "frames"},
    },
    opset_version=17,
    do_constant_folding=True,
)

print("Quantizing...")
quantize_dynamic(
    model_input=str(fp32),
    model_output=str(int8),
    weight_type=QuantType.QInt8,
    per_channel=True,
    reduce_range=False,
)

vocab_src = hf_hub_download(repo_id=repo_id, filename="vocabulary.json")
shutil.copyfile(vocab_src, OUT / "vocabulary.json")

size_mb = int8.stat().st_size / (1024*1024)
print(f"AvaSanj INT8 ONNX size: {size_mb:.1f} MB")
if size_mb < 50:
    raise RuntimeError("quantized model unexpectedly small")
fp32.unlink(missing_ok=True)
