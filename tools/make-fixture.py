"""Deterministic local test tone, not a human or an official reference recording."""
import math
import pathlib
import struct
import wave

path = pathlib.Path(__file__).resolve().parents[1] / "evidence" / "fixtures" / "Koenami-test.wav"
path.parent.mkdir(parents=True, exist_ok=True)
rate = 16000
with wave.open(str(path), "wb") as output:
    output.setnchannels(1)
    output.setsampwidth(2)
    output.setframerate(rate)
    samples = []
    for i in range(rate * 5):
        t = i / rate
        envelope = min(1, t * 12, (5 - t) * 12)
        fundamental = 160
        value = sum(math.sin(2 * math.pi * fundamental * n * t) / n for n in range(1, 19))
        samples.append(struct.pack('<h', int(value * envelope * 8000)))
    output.writeframes(b''.join(samples))
print(path)
