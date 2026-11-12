#!/usr/bin/env python3
"""Generates the client's GUI sprites: anti-aliased rounded rectangles and a soft shadow.

Every sprite is white and gets tinted at draw time. They are nine-slice sprites, so one
sprite per corner radius covers every rectangle size.

  round/rN.png  rounded rect with an N-pixel corner radius, size (2N+1)^2, border N
  shadow.png    blurred rounded rect for drop shadows

Run from the repo root: python3 tools/gen_sprites.py
"""
import json
import os

import numpy as np
from PIL import Image, ImageFilter

OUT = "src/main/resources/assets/nyx/textures/gui/sprites"
MAX_RADIUS = 48
SUPERSAMPLE = 8


def rounded_alpha(size, radius, ss=SUPERSAMPLE):
    """Coverage (0..1) of a size x size square with rounded corners of the given radius."""
    n = size * ss
    c = (np.arange(n) + 0.5) / ss  # sample centres in pixel units
    x, y = np.meshgrid(c, c)
    # Distance from each sample to the nearest corner circle centre (only inside the corner boxes).
    cx = np.clip(x, radius, size - radius)
    cy = np.clip(y, radius, size - radius)
    inside = (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2
    cover = inside.reshape(size, ss, size, ss).mean(axis=(1, 3))
    return cover


def save(path, alpha, meta):
    img = Image.new("RGBA", (alpha.shape[1], alpha.shape[0]), (255, 255, 255, 0))
    a = Image.fromarray(np.round(alpha * 255).astype(np.uint8), "L")
    img.putalpha(a)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    with open(path + ".mcmeta", "w") as f:
        json.dump(meta, f, separators=(",", ":"))


def nine_slice(size, border):
    return {"gui": {"scaling": {"type": "nine_slice", "width": size, "height": size,
                                "border": border, "stretch_inner": True}}}


def main():
    for r in range(1, MAX_RADIUS + 1):
        size = 2 * r + 1
        save(f"{OUT}/round/r{r}.png", rounded_alpha(size, r), nine_slice(size, r))

    # Shadow: a 64x64 rounded rect (radius 12) inset by 48px, Gaussian-blurred.
    # Drawn so the shape edge lines up with the element's edge; border 64 keeps the blur in the corners.
    pad, inner, radius = 48, 64, 12
    size = inner + 2 * pad
    shape = np.zeros((size, size))
    shape[pad:pad + inner, pad:pad + inner] = rounded_alpha(inner, radius)
    img = Image.fromarray(np.round(shape * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(16))
    alpha = np.asarray(img).astype(np.float64) / 255.0
    save(f"{OUT}/shadow.png", alpha, nine_slice(size, 64))
    print(f"wrote {MAX_RADIUS} round sprites and shadow.png to {OUT}")


if __name__ == "__main__":
    main()
