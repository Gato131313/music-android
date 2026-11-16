#!/usr/bin/env python3
"""
Genera una biblioteca de prueba realista (tipo Snaptube/YouTube, nombres sucios) para el emulador.
~1 120 canciones + ~660 videos. Requiere ffmpeg. Solo usa la biblioteca estándar de Python.

Uso: python3 tools/make_testlib.py <carpeta_salida>
Resultado: <salida>/Music, <salida>/Download, <salida>/Snaptube, <salida>/Movies ... (para `adb push` a /sdcard/)
"""
import os
import random
import shutil
import struct
import subprocess
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "tools/testlib"
TPL = os.path.join(OUT, "_tpl")
random.seed(42)

ARTISTS = [
    "Guns N' Roses", "Mon Laferte", "EMJAY", "Bad Bunny", "BTS", "BLACKPINK", "Karol G", "Shakira", "Taylor Swift",
    "The Weeknd", "Queen", "Coldplay", "Maná", "Soda Stereo", "Natalia Lafourcade", "Peso Pluma", "Rosalía", "Dua Lipa",
    "Billie Eilish", "Harry Styles", "Olivia Rodrigo", "Ed Sheeran", "Bruno Mars", "Adele", "Rihanna", "Beyoncé",
    "Daddy Yankee", "J Balvin", "Ozuna", "Feid", "Rauw Alejandro", "Myke Towers", "Anuel AA", "Sebastián Yatra",
    "Camilo", "Morat", "Reik", "Jesse & Joy", "Ha*Ash", "Carin León", "Grupo Frontera", "Junior H", "Natanael Cano",
    "Fuerza Regida", "Eslabon Armado", "Los Ángeles Azules", "Luis Miguel", "Juan Gabriel", "José José", "Rocío Dúrcal",
    "Vicente Fernández", "Alejandro Fernández", "Café Tacvba", "Caifanes", "Zoé", "Enjambre", "Siddhartha", "Kenia OS",
    "Danna Paola", "Belinda", "Thalía", "Paulina Rubio", "Gloria Trevi", "Selena", "Selena Gomez", "Ariana Grande",
    "Lady Gaga", "Katy Perry", "Miley Cyrus", "Sia", "Imagine Dragons", "Linkin Park", "Metallica", "AC/DC",
    "Nirvana", "Red Hot Chili Peppers", "Arctic Monkeys", "The Strokes", "Gorillaz", "Daft Punk", "Calvin Harris",
    "David Guetta", "Avicii", "Marshmello", "Twice", "Stray Kids", "NewJeans", "IVE", "SEVENTEEN", "aespa",
    "LE SSERAFIM", "TXT", "ENHYPEN", "(G)I-DLE", "Red Velvet", "EXO", "BIGBANG", "PSY", "Mon Laferte",
    "Tini", "María Becerra", "Nicki Nicole", "Bizarrap", "Duki", "Paulo Londra", "Wisin & Yandel", "Don Omar",
    "Romeo Santos", "Aventura", "Prince Royce", "Marc Anthony", "Celia Cruz", "Héctor Lavoe", "Juanes", "Carlos Vives",
    "Simon & Garfunkel", "The Beatles", "Michael Jackson", "Madonna", "Whitney Houston", "Mariah Carey", "Eminem",
]

WORDS_ES = ["amor", "noche", "corazón", "luna", "fuego", "cielo", "besos", "tú", "mía", "vida", "baila", "loca", "otra vez",
            "sin ti", "contigo", "para siempre", "veneno", "ojos", "rosa", "mar", "despacio", "perdón", "tequila", "reina",
            "verano", "lluvia", "secreto", "mentiras", "adiós", "volver", "frío", "calor", "estrella", "sueños"]
WORDS_EN = ["love", "night", "heart", "fire", "sky", "dance", "crazy", "forever", "dream", "light", "gold", "rain",
            "midnight", "summer", "lonely", "wild", "stars", "home", "magic", "baby", "tonight", "falling", "ghost", "money"]

def song_title(i):
    r = random.random()
    if r < 0.45:
        w = random.sample(WORDS_ES, random.choice([1, 2, 2, 3]))
    elif r < 0.9:
        w = random.sample(WORDS_EN, random.choice([1, 2, 2, 3]))
    else:
        w = random.sample(WORDS_ES + WORDS_EN, 2)
    t = " ".join(w).title()
    if random.random() < 0.08:
        t += " (feat. " + random.choice(ARTISTS) + ")"
    if random.random() < 0.04:
        t += " (Remix)"
    return t

def variants(a):
    """Formas sucias de escribir un artista (para probar la unión)."""
    glued = "".join(ch for ch in a if ch.isalnum())
    return [a, a, a, a.lower(), a + " - Topic", glued + "VEVO", a.upper() if len(a) < 12 else a]

def run(cmd):
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

DURS = [62, 95, 128, 151, 167, 183, 197, 212, 226, 241, 258, 274, 296]

def make_templates():
    os.makedirs(TPL, exist_ok=True)
    for d in DURS:
        src = ["-f", "lavfi", "-i", f"sine=frequency={110 + d % 7 * 55}:beep_factor=6:duration={d}"]
        run(["ffmpeg", "-y", *src, "-ac", "1", "-ar", "16000", "-c:a", "libmp3lame", "-b:a", "8k", "-map_metadata", "-1", "-id3v2_version", "0", f"{TPL}/a{d}.mp3"])
        run(["ffmpeg", "-y", *src, "-ac", "1", "-ar", "16000", "-c:a", "aac", "-b:a", "12k", f"{TPL}/a{d}.m4a"])
        run(["ffmpeg", "-y", "-f", "lavfi", "-i", f"testsrc2=size=160x90:rate=2:duration={d}",
             "-f", "lavfi", "-i", f"sine=frequency=330:beep_factor=6:duration={d}",
             "-c:v", "libx264", "-preset", "veryfast", "-b:v", "20k", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "10k", "-ac", "1", "-ar", "16000",
             "-shortest", f"{TPL}/v{d}.mp4"])
    for d in [95, 183]:
        src = ["-f", "lavfi", "-i", f"sine=frequency=440:beep_factor=6:duration={d}"]
        run(["ffmpeg", "-y", *src, "-ac", "1", "-ar", "8000", "-c:a", "flac", f"{TPL}/a{d}.flac"])
        run(["ffmpeg", "-y", *src, "-ac", "1", "-ar", "16000", "-c:a", "libvorbis", "-q:a", "0", f"{TPL}/a{d}.ogg"])
    run(["ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:beep_factor=6:duration=62", "-ac", "1", "-ar", "8000", f"{TPL}/a62.wav"])
    # Portadas: degradados de colores
    hues = [0, 20, 40, 120, 160, 200, 230, 260, 290, 320, 340, 60]
    for i, h in enumerate(hues):
        run(["ffmpeg", "-y", "-f", "lavfi", "-i", f"gradients=size=300x300:c0=0x{hsv(h, .7, .9)}:c1=0x{hsv((h + 50) % 360, .8, .35)}:duration=1",
             "-frames:v", "1", "-q:v", "6", f"{TPL}/cover{i}.jpg"])

def hsv(h, s, v):
    import colorsys
    r, g, b = colorsys.hsv_to_rgb(h / 360, s, v)
    return "%02x%02x%02x" % (int(r * 255), int(g * 255), int(b * 255))

def syncsafe(n):
    return bytes([(n >> 21) & 0x7F, (n >> 14) & 0x7F, (n >> 7) & 0x7F, n & 0x7F])

def frame(fid, payload):
    return fid.encode() + syncsafe(len(payload)) + b"\x00\x00" + payload

def text_frame(fid, s):
    return frame(fid, b"\x03" + s.encode("utf-8"))

def id3(title=None, artist=None, album=None, genre=None, cover=None):
    fr = b""
    if title: fr += text_frame("TIT2", title)
    if artist: fr += text_frame("TPE1", artist)
    if album: fr += text_frame("TALB", album)
    if genre: fr += text_frame("TCON", genre)
    if cover:
        with open(cover, "rb") as f:
            img = f.read()
        fr += frame("APIC", b"\x03" + b"image/jpeg\x00" + b"\x03" + b"\x00" + img)
    if not fr:
        return b""
    return b"ID3\x04\x00\x00" + syncsafe(len(fr)) + fr

def write_mp3(dst, dur, **tags):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(f"{TPL}/a{dur}.mp3", "rb") as f:
        body = f.read()
    with open(dst, "wb") as f:
        f.write(id3(**tags) + body)

def copy(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)

def safe(name):
    return name.replace("/", "_").replace("?", "").replace(":", " ").replace("*", "").replace('"', "'")

def main():
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    make_templates()
    genres = ["Pop", "Rock", "Reggaeton", "K-Pop", "Regional Mexicano", "Indie", "Electrónica", "Salsa", "Balada", "Hip Hop"]
    songs = []
    n = 0
    used = set()
    while len(songs) < 1120:
        a = random.choice(ARTISTS)
        t = song_title(n)
        if (a, t) in used:
            continue
        used.add((a, t))
        songs.append((a, t, random.choice(DURS)))
        n += 1

    counts = {"mp3": 0, "m4a": 0, "flac": 0, "ogg": 0, "wav": 0}
    for i, (a, t, d) in enumerate(songs):
        v = random.choice(variants(a))
        p = i % 10
        cover = f"{TPL}/cover{i % 12}.jpg" if random.random() < 0.45 else None
        genre = random.choice(genres) if random.random() < 0.5 else None
        album = (a + " - Grandes Éxitos") if random.random() < 0.15 else None
        if p == 0:
            write_mp3(f"{OUT}/Snaptube/Audio/{safe(v)} - {safe(t)} (Official Video).mp3", d, cover=cover)
        elif p == 1:
            write_mp3(f"{OUT}/Download/{safe(t)} (Lyrics).mp3", d, title=f"{t} (Lyrics)", artist=f"{a} - Topic", cover=cover, genre=genre)
        elif p == 2:
            write_mp3(f"{OUT}/Download/{safe(v).replace(' ', '_')}_-_{safe(t).replace(' ', '_')}_[MV].mp3", d)
        elif p == 3:
            write_mp3(f"{OUT}/Music/{safe(a)}/{i % 20 + 1:02d} {safe(t)}.mp3", d, title=t, artist=a, album=album or f"{a} Vol. {i % 3 + 1}", genre=genre, cover=cover)
            counts["mp3"] += 1
            continue
        elif p == 4 and counts["m4a"] < 140:
            copy(f"{TPL}/a{d}.m4a", f"{OUT}/Snaptube/Audio/{safe(v)} - {safe(t)} [HD].m4a"); counts["m4a"] += 1; continue
        elif p == 5:
            glued = "".join(ch for ch in a if ch.isalnum()) + "VEVO"
            write_mp3(f"{OUT}/Download/{safe(a)} - {safe(t)} (Video Oficial).mp3", d, title=f"{a} - {t} (Video Oficial)", artist=glued, cover=cover)
        elif p == 6:
            write_mp3(f"{OUT}/Download/y2mate.com - {safe(v)} - {safe(t)} (Letra)_Snaptube.mp3", d, genre=genre)
        elif p == 7 and counts["flac"] < 12:
            dd = 95 if d < 150 else 183
            copy(f"{TPL}/a{dd}.flac", f"{OUT}/Music/FLAC/{safe(a)} - {safe(t)}.flac"); counts["flac"] += 1; continue
        elif p == 8 and counts["ogg"] < 12:
            dd = 95 if d < 150 else 183
            copy(f"{TPL}/a{dd}.ogg", f"{OUT}/Music/OGG/{safe(a)} - {safe(t)}.ogg"); counts["ogg"] += 1; continue
        elif p == 9 and counts["wav"] < 4:
            copy(f"{TPL}/a62.wav", f"{OUT}/Music/WAV/{safe(a)} - {safe(t)}.wav"); counts["wav"] += 1; continue
        else:
            write_mp3(f"{OUT}/Snaptube/Audio/{safe(v)} - {safe(t)} (Official Music Video) HD.mp3", d, title=f"{v} - {t} (Official Music Video) HD", artist=v, cover=cover, album=album, genre=genre)
        counts["mp3"] += 1

    # Videos: 230 parejas de canciones + sueltos
    vids = 0
    for (a, t, d) in random.sample(songs, 230):
        v = random.choice(variants(a))
        copy(f"{TPL}/v{d}.mp4", f"{OUT}/Snaptube/Video/{safe(v)} - {safe(t)} (Official Video).mp4"); vids += 1
    for i in range(300):
        a = random.choice(ARTISTS); t = song_title(9000 + i); d = random.choice(DURS)
        copy(f"{TPL}/v{d}.mp4", f"{OUT}/Snaptube/Video/{safe(a)} - {safe(t)} [Official Music Video].mp4"); vids += 1
    for i in range(130):
        d = random.choice(DURS[:4])
        copy(f"{TPL}/v{d}.mp4", f"{OUT}/DCIM/Camera/VID_2025{(i % 12) + 1:02d}{(i % 27) + 1:02d}_{100000 + i * 37}.mp4"); vids += 1
    # Notas de voz (deben ignorarse)
    for i in range(6):
        copy(f"{TPL}/a62.m4a", f"{OUT}/Recordings/Voz {i}.m4a")
    shutil.rmtree(TPL)
    total = sum(len(fs) for _, _, fs in os.walk(OUT))
    print(f"canciones={len(songs)} videos={vids} archivos={total} formatos={counts}")

if __name__ == "__main__":
    main()
