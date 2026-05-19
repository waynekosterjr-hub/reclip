#!/usr/bin/env python3
"""Render the README PDF source into GitHub-friendly images and README markup."""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "docs" / "readme" / "ReClip_Serverless_Media_Engine.pdf"
DEFAULT_OUTPUT = ROOT / "docs" / "screenshots" / "serverless-readme"
DEFAULT_README = ROOT / "README.md"
README_TITLE = "ReClip Serverless Media Engine"
README_WIDTH = 900
IMAGE_WIDTH = 1100
JPEG_QUALITY = 88


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Regenerate README page images from the canonical README PDF."
    )
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--readme", type=Path, default=DEFAULT_README)
    parser.add_argument("--title", default=README_TITLE)
    parser.add_argument("--image-width", type=int, default=IMAGE_WIDTH)
    parser.add_argument("--readme-width", type=int, default=README_WIDTH)
    parser.add_argument("--quality", type=int, default=JPEG_QUALITY)
    return parser.parse_args()


def require_pdf_renderer():
    try:
        import pypdfium2 as pdfium  # type: ignore
    except ImportError as exc:
        raise SystemExit(
            "Missing dependency: pypdfium2. Install it with "
            "`python -m pip install pypdfium2 pillow`."
        ) from exc
    return pdfium


def reset_output_dir(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for old_page in output_dir.glob("page-*.jpg"):
        old_page.unlink()


def render_pdf_pages(pdf_path: Path, output_dir: Path, image_width: int, quality: int) -> list[Path]:
    pdfium = require_pdf_renderer()
    document = pdfium.PdfDocument(str(pdf_path))
    page_paths: list[Path] = []

    reset_output_dir(output_dir)
    try:
        for index in range(len(document)):
            page = document[index]
            width, _height = page.get_size()
            scale = image_width / width
            bitmap = page.render(scale=scale)
            image = bitmap.to_pil().convert("RGB")

            page_path = output_dir / f"page-{index + 1:02d}.jpg"
            image.save(page_path, "JPEG", quality=quality, optimize=True, progressive=True)
            page_paths.append(page_path)
            bitmap.close()
            page.close()
    finally:
        document.close()

    return page_paths


def write_readme(readme_path: Path, pdf_path: Path, page_paths: list[Path], title: str, width: int) -> None:
    rel_pdf = pdf_path.relative_to(ROOT).as_posix()
    lines = [
        f"# {title}",
        "",
        f"This README is generated from `{rel_pdf}`. To refresh it, run:",
        "",
        "```powershell",
        "py -3 -m pip install pypdfium2 pillow",
        "py -3 scripts/update_readme_images.py",
        "```",
        "",
    ]

    for index, page_path in enumerate(page_paths, start=1):
        rel_page = page_path.relative_to(ROOT).as_posix()
        lines.append(
            f'<p align="center"><img src="{rel_page}" width="{width}" '
            f'alt="{title} page {index}" /></p>'
        )

    readme_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def copy_source_if_requested(source: Path, target: Path) -> Path:
    source = source.resolve()
    target = target.resolve()
    if source == target:
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    return target


def main() -> int:
    args = parse_args()
    source = args.source.resolve()
    if not source.exists():
        print(f"README source PDF not found: {source}", file=sys.stderr)
        return 1

    canonical_source = copy_source_if_requested(source, DEFAULT_SOURCE)
    pages = render_pdf_pages(canonical_source, args.output_dir.resolve(), args.image_width, args.quality)
    write_readme(args.readme.resolve(), canonical_source, pages, args.title, args.readme_width)
    print(f"Rendered {len(pages)} README page image(s) from {canonical_source}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
