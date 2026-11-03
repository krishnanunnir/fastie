# Renders Kino's TV banner (320x180dp at xhdpi = 640x360): amber bolt + "kino" wordmark on the ground colour.
import sys
from PIL import Image, ImageDraw, ImageFont
W, H = 640, 360
img = Image.new("RGB", (W, H), (15, 15, 17))
d = ImageDraw.Draw(img)
font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 132)
text = "kino"
tw = d.textlength(text, font=font)
bolt_h = 150; s = bolt_h / 20.0
bolt_w = 10 * s
gap = 26
x0 = (W - (bolt_w + gap + tw)) / 2
# Material "flash_on" path M7,2v11h3v9l7,-12h-4l4,-8z, offset so its box starts at (7,2).
pts = [(7,2),(7,13),(10,13),(10,22),(17,10),(13,10),(17,2)]
top = (H - bolt_h) / 2
d.polygon([(x0 + (px - 7) * s, top + (py - 2) * s) for px, py in pts], fill=(255, 182, 39))
asc, desc = font.getmetrics()
bbox = d.textbbox((0, 0), text, font=font)
d.text((x0 + bolt_w + gap, (H - (bbox[3] - bbox[1])) / 2 - bbox[1]), text, font=font, fill=(255, 182, 39))
img.save(sys.argv[1])
