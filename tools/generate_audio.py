"""Generate original, seamless ambient textures and short UI tones. Requires numpy."""
from pathlib import Path
import wave
import numpy as np

OUT = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
OUT.mkdir(parents=True, exist_ok=True)
RATE = 22050
rng = np.random.default_rng(724)

def write(name, samples):
    samples = np.clip(samples, -1, 1)
    with wave.open(str(OUT / (name + '.wav')), 'wb') as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes((samples * 32767).astype('<i2').tobytes())

n = RATE * 12
t = np.arange(n) / RATE
freq = np.fft.rfftfreq(n, 1/RATE)

def texture(power):
    spectrum = (rng.normal(size=len(freq)) + 1j*rng.normal(size=len(freq)))
    spectrum *= np.maximum(freq, 40) ** power
    spectrum[0] = 0
    signal = np.fft.irfft(spectrum, n)
    return signal / np.std(signal)

write('rain', texture(-.25) * .1 + texture(-.8) * .08)
write('waves', texture(-.8) * (.10 + .075*np.cos(2*np.pi*t/6)))
write('noise', texture(0) * .075)
write('city', texture(-1) * .08 + texture(-.3) * .025 + .015*np.sin(2*np.pi*80*t))

for name, notes, length in [('tap', [480], .055), ('start', [330, 440, 660], .65), ('complete', [660, 880, 1100], 1.1)]:
    tone = np.zeros(int(RATE*length))
    for i, hz in enumerate(notes):
        start = int(i*RATE*length/(len(notes)+1))
        dt = np.arange(len(tone)-start)/RATE
        envelope = (1-np.exp(-dt*180))*np.exp(-dt*12)
        tone[start:] += np.sin(2*np.pi*hz*dt)*envelope*.16
    tone[-220:] *= np.linspace(1,0,220)
    write(name, tone)
print('Generated seven original audio assets in', OUT)
