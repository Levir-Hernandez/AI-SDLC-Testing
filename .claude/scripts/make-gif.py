"""Stitch Playwright screenshot frames into an animated GIF, for behavior that only shows over time.

Usage:
  python .claude/scripts/make-gif.py <out.gif> "<frames glob>" [--ms 600] [--keep]

  Example: python .claude/scripts/make-gif.py .claude/context/evidence/P2-BUG-004.gif ".claude/context/evidence/P2-BUG-004-f*.png"
  Frames are taken in name order (use f01, f02, ...) and deleted after the GIF is written unless --keep is given.
  The glob is expanded here, so it works the same from Bash and PowerShell. Requires Pillow.
"""
import argparse
import glob
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = (ROOT / ".claude" / "context" / "evidence").resolve()


def inside_evidence(path):
    resolved = (path if path.is_absolute() else ROOT / path).resolve()
    if not resolved.is_relative_to(EVIDENCE):
        sys.exit(f"{path} is outside .claude/context/evidence/.")
    return resolved


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(usage=__doc__)
    parser.add_argument("out")
    parser.add_argument("frames")
    parser.add_argument("--ms", type=int, default=600, help="milliseconds per frame")
    parser.add_argument("--keep", action="store_true", help="keep the frame files")
    args = parser.parse_args(argv)

    try:
        from PIL import Image
    except ImportError:
        sys.exit("Pillow is not installed (pip install pillow). Cite the frames in order instead of a GIF.")

    out = inside_evidence(Path(args.out))
    pattern = args.frames if Path(args.frames).is_absolute() else str(ROOT / args.frames)
    frames = sorted(inside_evidence(Path(p)) for p in glob.glob(pattern))
    if len(frames) < 2:
        sys.exit(f"Need at least 2 frames, found {len(frames)} for {args.frames}.")

    images = [Image.open(f).convert("RGB") for f in frames]
    size = images[0].size
    images = [im if im.size == size else im.resize(size) for im in images]
    out.parent.mkdir(parents=True, exist_ok=True)
    images[0].save(out, save_all=True, append_images=images[1:], duration=args.ms, loop=0, optimize=True)
    for im in images:
        im.close()
    if not args.keep:
        for f in frames:
            f.unlink()
    print(f"{out.relative_to(ROOT).as_posix()}: {len(frames)} frames, {args.ms} ms each.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
