"""
stream_mic_serial.py
------------------------------------------------------------------
Le o stream PCM do ESP32 (sketch stream_mic_serial.ino) via serial
a 2.000.000 baud e faz uma destas duas coisas:

  python stream_mic_serial.py print   /dev/ttyUSB0
      -> mostra estatisticas do stream em tempo real
         (bytes, amostras, nivel de pico RMS) so pra confirmar
         que o microfone esta captando

  python stream_mic_serial.py save    /dev/ttyUSB0  saida.raw
      -> grava o PCM cru em 'saida.raw'. Depois abre no Audacity:
         Import -> Raw Data -> 16-bit PCM, 48000 Hz, Mono

Dependencias:
  pip install pyserial
------------------------------------------------------------------
"""

import sys
import time
import struct
import serial

BAUDRATE   = 921_600
SAMPLE_RATE = 16_000
BYTES_PER_SAMPLE = 2
BLOCK_BYTES = 512


def open_serial(port: str) -> serial.Serial:
    """Abre a porta serial com timeout generoso. Retorna o objeto Serial."""
    return serial.Serial(
        port=port,
        baudrate=BAUDRATE,
        bytesize=serial.EIGHTBITS,
        parity=serial.PARITY_NONE,
        stopbits=serial.STOPBITS_ONE,
        timeout=2.0,
    )


def print_stream(port: str, duration_s: float = 30.0) -> None:
    """
    Le 'duration_s' segundos de audio e mostra estatisticas no terminal.
    Nao tenta imprimir os bytes crus (seriam lixo visual). Em vez disso,
    calcula o RMS do bloco - um numero que indica volume - e imprime
    uma barrinha de nivel tipo VU-meter.
    """
    ser = open_serial(port)
    print(f"[print] lendo de {port} @ {BAUDRATE} baud por {duration_s:.0f}s")
    print(f"[print] formato: {SAMPLE_RATE} Hz, 16-bit, mono, blocos de {BLOCK_BYTES} bytes")
    print("[print] CTRL+C para parar\n")

    total_bytes = 0
    start = time.time()
    try:
        while time.time() - start < duration_s:
            bloco = ser.read(BLOCK_BYTES)
            if len(bloco) != BLOCK_BYTES:
                print(f"[warn] leu so {len(bloco)} bytes - cabo desconectado ou baud errado?")
                continue

            amostras = struct.unpack(f"<{BLOCK_BYTES // 2}h", bloco)
            rms = (sum(s * s for s in amostras) / len(amostras)) ** 0.5
            rms_norm = min(rms / 32768.0, 1.0)
            barras = int(rms_norm * 40)
            print(f"[{time.time() - start:6.2f}s] "
                  f"{'#' * barras:<40} "
                  f"RMS={rms:6.0f}")

            total_bytes += len(bloco)
    except KeyboardInterrupt:
        print("\n[print] interrompido pelo usuario")

    elapsed = time.time() - start
    print(f"\n[print] total: {total_bytes} bytes em {elapsed:.2f}s")
    print(f"[print] taxa media: {total_bytes / elapsed:.0f} bytes/s "
          f"(esperado ~{SAMPLE_RATE * BYTES_PER_SAMPLE} bytes/s)")
    ser.close()


def save_raw(port: str, filename: str, duration_s: float = 10.0) -> None:
    """
    Le 'duration_s' segundos de audio e grava PCM cru em 'filename'.
    O arquivo pode ser aberto no Audacity ou em qualquer player que
    aceite raw PCM 16-bit little-endian, 48 kHz, mono.
    """
    ser = open_serial(port)
    print(f"[save] lendo de {port} @ {BAUDRATE} baud")
    print(f"[save] gravando em '{filename}' por {duration_s:.0f}s")

    total = 0
    start = time.time()
    try:
        with open(filename, "wb") as f:
            while time.time() - start < duration_s:
                bloco = ser.read(BLOCK_BYTES)
                if len(bloco) != BLOCK_BYTES:
                    print(f"[warn] leu so {len(bloco)} bytes")
                    continue
                f.write(bloco)
                total += len(bloco)
    except KeyboardInterrupt:
        print("\n[save] interrompido pelo usuario")

    elapsed = time.time() - start
    ser.close()
    print(f"\n[save] gravados {total} bytes em {elapsed:.2f}s")
    print(f"[save] arquivo '{filename}' pronto.")
    print(f"[save] abre no Audacity: Import -> Raw Data ->")
    print(f"        16-bit PCM, Little-endian, {SAMPLE_RATE} Hz, Mono")


def main() -> None:
    args = sys.argv[1:]
    if len(args) < 2 or args[0] not in ("print", "save"):
        print(__doc__)
        sys.exit(1)

    modo = args[0]
    porta = args[1]

    if modo == "print":
        print_stream(porta)
    elif modo == "save":
        if len(args) < 3:
            print("uso: python stream_mic_serial.py save /dev/ttyUSB0 saida.raw")
            sys.exit(1)
        arquivo = args[2]
        duracao = float(args[3]) if len(args) > 3 else 10.0
        save_raw(porta, arquivo, duracao)


if __name__ == "__main__":
    main()
