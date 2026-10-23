"""Convert EcoLift SVG icons into Android vector drawables.

Usage:
    python docs/web-port/svg_to_vector.py SRC_DIR DST_DIR
    python docs/web-port/svg_to_vector.py --check SRC_DIR DST_DIR

A second run overwrites the same files. Identical bytes are left untouched.
The script stops when an SVG uses an element or a paint it does not handle.
"""

from __future__ import annotations

import math
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path


class SvgError(Exception):
    """The SVG uses something this converter will not guess at."""


_NUM = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
_NAMED = {
    "black": (0, 0, 0, 255),
    "white": (255, 255, 255, 255),
    "red": (255, 0, 0, 255),
    "green": (0, 128, 0, 255),
    "blue": (0, 0, 255, 255),
    "transparent": None,
    "none": None,
    "currentcolor": (0, 0, 0, 255),
}


def fmt(number: float) -> str:
    if abs(number) < 5e-7:
        return "0"
    text = f"{number:.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def mul(left, right):
    a1, b1, c1, d1, e1, f1 = left
    a2, b2, c2, d2, e2, f2 = right
    return (
        a1 * a2 + c1 * b2,
        b1 * a2 + d1 * b2,
        a1 * c2 + c1 * d2,
        b1 * c2 + d1 * d2,
        a1 * e2 + c1 * f2 + e1,
        b1 * e2 + d1 * f2 + f1,
    )


IDENT = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def is_ident(matrix) -> bool:
    return all(abs(a - b) < 1e-9 for a, b in zip(matrix, IDENT))


def apply_point(matrix, x, y):
    a, b, c, d, e, f = matrix
    return (a * x + c * y + e, b * x + d * y + f)


def scale_of(matrix) -> tuple[float, float]:
    a, b, c, d, _, _ = matrix
    return math.hypot(a, b), math.hypot(c, d)


def parse_transform(text: str | None):
    if not text or not text.strip():
        return IDENT
    result = IDENT
    for match in re.finditer(r"([a-zA-Z]+)\s*\(([^)]*)\)", text):
        name = match.group(1)
        nums = [float(item) for item in _NUM.findall(match.group(2))]
        if name == "matrix" and len(nums) == 6:
            part = tuple(nums)
        elif name == "translate" and len(nums) in (1, 2):
            tx = nums[0]
            ty = nums[1] if len(nums) == 2 else 0.0
            part = (1, 0, 0, 1, tx, ty)
        elif name == "scale" and len(nums) in (1, 2):
            sx = nums[0]
            sy = nums[1] if len(nums) == 2 else sx
            part = (sx, 0, 0, sy, 0, 0)
        elif name == "rotate" and len(nums) in (1, 3):
            angle = math.radians(nums[0])
            cos, sin = math.cos(angle), math.sin(angle)
            rotation = (cos, sin, -sin, cos, 0, 0)
            if len(nums) == 3:
                cx, cy = nums[1], nums[2]
                part = mul(mul((1, 0, 0, 1, cx, cy), rotation), (1, 0, 0, 1, -cx, -cy))
            else:
                part = rotation
        elif name == "skewX" and len(nums) == 1:
            part = (1, 0, math.tan(math.radians(nums[0])), 1, 0, 0)
        elif name == "skewY" and len(nums) == 1:
            part = (1, math.tan(math.radians(nums[0])), 0, 1, 0, 0)
        else:
            raise SvgError(f"unsupported transform {match.group(0)}")
        result = mul(result, part)
    return result


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def style_map(text: str | None) -> dict[str, str]:
    found = {}
    if not text:
        return found
    for piece in text.split(";"):
        if ":" not in piece:
            continue
        key, value = piece.split(":", 1)
        found[key.strip()] = value.strip()
    return found


def presentation(elem) -> dict[str, str]:
    keys = {
        "fill",
        "stroke",
        "stroke-width",
        "stroke-linecap",
        "stroke-linejoin",
        "stroke-miterlimit",
        "stroke-opacity",
        "fill-opacity",
        "fill-rule",
        "opacity",
        "display",
    }
    values = {key: elem.attrib[key] for key in keys if key in elem.attrib}
    values.update({key: value for key, value in style_map(elem.attrib.get("style")).items() if key in keys})
    return values


def parse_color(text: str):
    raw = text.strip()
    named = _NAMED.get(raw.lower())
    if raw.lower() in _NAMED:
        return named
    hex_text = raw[1:] if raw.startswith("#") else None
    if hex_text is None:
        rgb = re.fullmatch(r"rgba?\(([^)]+)\)", raw)
        if not rgb:
            raise SvgError(f"unsupported color {text!r}")
        parts = [float(item) for item in re.findall(r"[+-]?\d*\.?\d+", rgb.group(1))]
        if len(parts) not in (3, 4):
            raise SvgError(f"unsupported color {text!r}")
        alpha = int(round((parts[3] if len(parts) == 4 else 1.0) * 255))
        return tuple(int(round(channel)) for channel in parts[:3]) + (alpha,)
    if len(hex_text) == 3:
        hex_text = "".join(ch * 2 for ch in hex_text)
    if len(hex_text) == 6:
        hex_text = "FF" + hex_text
    if len(hex_text) != 8:
        raise SvgError(f"unsupported color {text!r}")
    value = int(hex_text, 16)
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, (value >> 24) & 255)


def color_hex(color) -> str:
    red, green, blue, alpha = color
    return f"#{alpha:02X}{red:02X}{green:02X}{blue:02X}"


DEFAULT_PAINT = {
    "fill": (0, 0, 0, 255),
    "stroke": None,
    "stroke_width": 1.0,
    "linecap": "butt",
    "linejoin": "miter",
    "miterlimit": 4.0,
    "fill_opacity": 1.0,
    "stroke_opacity": 1.0,
    "opacity": 1.0,
    "fill_rule": "nonzero",
}


def child_paint(parent: dict, elem) -> dict:
    paint = dict(parent)
    attrs = presentation(elem)
    if "fill" in attrs:
        paint["fill"] = parse_color(attrs["fill"])
    if "stroke" in attrs:
        paint["stroke"] = parse_color(attrs["stroke"])
    if "stroke-width" in attrs:
        paint["stroke_width"] = float(attrs["stroke-width"])
    if "stroke-linecap" in attrs:
        paint["linecap"] = attrs["stroke-linecap"]
    if "stroke-linejoin" in attrs:
        paint["linejoin"] = attrs["stroke-linejoin"]
    if "stroke-miterlimit" in attrs:
        paint["miterlimit"] = float(attrs["stroke-miterlimit"])
    if "fill-opacity" in attrs:
        paint["fill_opacity"] = float(attrs["fill-opacity"])
    if "stroke-opacity" in attrs:
        paint["stroke_opacity"] = float(attrs["stroke-opacity"])
    if "opacity" in attrs:
        paint["opacity"] = parent["opacity"] * float(attrs["opacity"])
    if "fill-rule" in attrs:
        paint["fill_rule"] = attrs["fill-rule"]
    return paint


def length_of(text: str) -> float:
    value = text.strip()
    for suffix in ("px", "dp", "dip", "pt"):
        if value.endswith(suffix):
            value = value[: -len(suffix)]
    if value.endswith("%"):
        raise SvgError("percentage lengths are not converted")
    return float(value)


def tokenize(path_data: str):
    index = 0
    size = len(path_data)
    while index < size:
        char = path_data[index]
        if char.isspace() or char == ",":
            index += 1
            continue
        if char.isalpha():
            yield ("cmd", char)
            index += 1
            continue
        match = _NUM.match(path_data, index)
        if not match:
            raise SvgError(f"bad path data near {path_data[index:index + 16]!r}")
        yield ("num", float(match.group()))
        index = match.end()


def parse_path(path_data: str):
    tokens = list(tokenize(path_data))
    index = 0
    commands = []
    cx = cy = sx = sy = 0.0
    prev = ""
    ctrl = (0.0, 0.0)

    def take(count):
        nonlocal index
        nums = []
        for _ in range(count):
            if index >= len(tokens) or tokens[index][0] != "num":
                raise SvgError(f"path ended early in {path_data!r}")
            nums.append(tokens[index][1])
            index += 1
        return nums

    def has_num():
        return index < len(tokens) and tokens[index][0] == "num"

    while index < len(tokens):
        kind, value = tokens[index]
        if kind == "cmd":
            command = value
            index += 1
        elif prev:
            command = "L" if prev == "M" else "l" if prev == "m" else prev
        else:
            raise SvgError("path data does not start with a command")
        relative = command.islower()
        verb = command.upper()
        if verb == "Z":
            commands.append(("Z",))
            cx, cy = sx, sy
            prev = command
            continue
        while True:
            if verb == "M":
                x, y = take(2)
                if relative:
                    x, y = cx + x, cy + y
                commands.append(("M", x, y))
                cx, cy = sx, sy = x, y
                verb = "L"
                relative = command.islower()
            elif verb == "L":
                x, y = take(2)
                if relative:
                    x, y = cx + x, cy + y
                commands.append(("L", x, y))
                cx, cy = x, y
            elif verb == "H":
                x = take(1)[0]
                x = cx + x if relative else x
                commands.append(("L", x, cy))
                cx = x
            elif verb == "V":
                y = take(1)[0]
                y = cy + y if relative else y
                commands.append(("L", cx, y))
                cy = y
            elif verb == "C":
                x1, y1, x2, y2, x, y = take(6)
                if relative:
                    x1, y1, x2, y2, x, y = cx + x1, cy + y1, cx + x2, cy + y2, cx + x, cy + y
                commands.append(("C", x1, y1, x2, y2, x, y))
                ctrl = (x2, y2)
                cx, cy = x, y
            elif verb == "S":
                x2, y2, x, y = take(4)
                if relative:
                    x2, y2, x, y = cx + x2, cy + y2, cx + x, cy + y
                if prev.upper() in ("C", "S"):
                    x1, y1 = 2 * cx - ctrl[0], 2 * cy - ctrl[1]
                else:
                    x1, y1 = cx, cy
                commands.append(("C", x1, y1, x2, y2, x, y))
                ctrl = (x2, y2)
                cx, cy = x, y
            elif verb == "Q":
                x1, y1, x, y = take(4)
                if relative:
                    x1, y1, x, y = cx + x1, cy + y1, cx + x, cy + y
                commands.append(("Q", x1, y1, x, y))
                ctrl = (x1, y1)
                cx, cy = x, y
            elif verb == "T":
                x, y = take(2)
                if relative:
                    x, y = cx + x, cy + y
                if prev.upper() in ("Q", "T"):
                    x1, y1 = 2 * cx - ctrl[0], 2 * cy - ctrl[1]
                else:
                    x1, y1 = cx, cy
                commands.append(("Q", x1, y1, x, y))
                ctrl = (x1, y1)
                cx, cy = x, y
            elif verb == "A":
                rx, ry, phi, large, sweep, x, y = take(7)
                if relative:
                    x, y = cx + x, cy + y
                commands.append(("A", rx, ry, phi, large, sweep, x, y, cx, cy))
                cx, cy = x, y
            else:
                raise SvgError(f"unsupported path command {command}")
            prev = command if verb not in ("H", "V") else command
            if verb in ("H", "V"):
                prev = "l" if command.islower() else "L"
            if not has_num():
                break
    if index != len(tokens):
        raise SvgError("path data has trailing junk")
    return commands


def stringify(commands) -> str:
    parts = []
    for command in commands:
        verb = command[0]
        if verb == "Z":
            parts.append("Z")
        elif verb == "A":
            _, rx, ry, phi, large, sweep, x, y, _, _ = command
            parts.append(
                f"A{fmt(rx)} {fmt(ry)} {fmt(phi)} {int(large)} {int(sweep)} {fmt(x)} {fmt(y)}"
            )
        else:
            parts.append(verb + " ".join(fmt(number) for number in command[1:]))
    return "".join(parts)


def arc_to_cubics(x1, y1, rx, ry, phi_deg, large, sweep, x2, y2):
    if math.hypot(x2 - x1, y2 - y1) < 1e-9:
        return []
    if abs(rx) < 1e-9 or abs(ry) < 1e-9:
        return [((x1, y1), (x2, y2), (x2, y2))]
    phi = math.radians(phi_deg)
    cos, sin = math.cos(phi), math.sin(phi)
    rx, ry = abs(rx), abs(ry)
    dx, dy = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cos * dx + sin * dy
    y1p = -sin * dx + cos * dy
    radius_scale = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if radius_scale > 1:
        grow = math.sqrt(radius_scale)
        rx *= grow
        ry *= grow
    num = (rx * rx) * (ry * ry) - (rx * rx) * (y1p * y1p) - (ry * ry) * (x1p * x1p)
    den = (rx * rx) * (y1p * y1p) + (ry * ry) * (x1p * x1p)
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if int(large) == int(sweep):
        coef = -coef
    cxp = coef * (rx * y1p / ry)
    cyp = coef * (-ry * x1p / rx)
    cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0
    cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0

    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        lengths = math.hypot(ux, uy) * math.hypot(vx, vy)
        amount = math.acos(max(-1.0, min(1.0, dot / lengths))) if lengths else 0.0
        if ux * vy - uy * vx < 0:
            amount = -amount
        return amount

    theta = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if int(sweep) == 0 and delta > 0:
        delta -= 2 * math.pi
    elif int(sweep) == 1 and delta < 0:
        delta += 2 * math.pi
    segments = max(1, math.ceil(abs(delta) / (math.pi / 2)))
    step = delta / segments
    cubics = []
    for piece in range(segments):
        start = theta + piece * step
        end = start + step
        alpha = math.sin(step) * (math.sqrt(4 + 3 * math.tan(step / 2) ** 2) - 1) / 3

        def point(theta_value):
            return (
                cx + rx * math.cos(theta_value) * cos - ry * math.sin(theta_value) * sin,
                cy + rx * math.cos(theta_value) * sin + ry * math.sin(theta_value) * cos,
            )

        def derivative(theta_value):
            return (
                -rx * math.sin(theta_value) * cos - ry * math.cos(theta_value) * sin,
                -rx * math.sin(theta_value) * sin + ry * math.cos(theta_value) * cos,
            )

        p1 = point(start)
        p2 = point(end)
        d1 = derivative(start)
        d2 = derivative(end)
        cubics.append(
            (
                (p1[0] + alpha * d1[0], p1[1] + alpha * d1[1]),
                (p2[0] - alpha * d2[0], p2[1] - alpha * d2[1]),
                p2,
            )
        )
    return cubics


def transform_commands(commands, matrix):
    moved = []
    current = (0.0, 0.0)
    for command in commands:
        verb = command[0]
        if verb == "M":
            current = apply_point(matrix, command[1], command[2])
            moved.append(("M", current[0], current[1]))
        elif verb == "L":
            current = apply_point(matrix, command[1], command[2])
            moved.append(("L", current[0], current[1]))
        elif verb == "C":
            c1 = apply_point(matrix, command[1], command[2])
            c2 = apply_point(matrix, command[3], command[4])
            current = apply_point(matrix, command[5], command[6])
            moved.append(("C", c1[0], c1[1], c2[0], c2[1], current[0], current[1]))
        elif verb == "Q":
            c1 = apply_point(matrix, command[1], command[2])
            current = apply_point(matrix, command[3], command[4])
            moved.append(("Q", c1[0], c1[1], current[0], current[1]))
        elif verb == "A":
            _, rx, ry, phi, large, sweep, x, y, x0, y0 = command
            start = apply_point(matrix, x0, y0)
            cubics = arc_to_cubics(x0, y0, rx, ry, phi, large, sweep, x, y)
            for c1, c2, end in cubics:
                c1 = apply_point(matrix, c1[0], c1[1])
                c2 = apply_point(matrix, c2[0], c2[1])
                end = apply_point(matrix, end[0], end[1])
                moved.append(("C", c1[0], c1[1], c2[0], c2[1], end[0], end[1]))
            current = apply_point(matrix, x, y)
            if not cubics:
                moved.append(("L", current[0], current[1]))
        elif verb == "Z":
            moved.append(("Z",))
        else:
            raise SvgError(f"cannot transform {verb}")
    return moved


def points_of(text: str):
    nums = [float(item) for item in _NUM.findall(text)]
    if len(nums) % 2:
        raise SvgError(f"odd polyline points in {text!r}")
    return list(zip(nums[0::2], nums[1::2]))


def circle_path(cx, cy, rx, ry) -> str:
    return (
        f"M{fmt(cx - rx)} {fmt(cy)}"
        f"A{fmt(rx)} {fmt(ry)} 0 1 1 {fmt(cx + rx)} {fmt(cy)}"
        f"A{fmt(rx)} {fmt(ry)} 0 1 1 {fmt(cx - rx)} {fmt(cy)}Z"
    )


def rect_path(x, y, width, height, rx, ry) -> str:
    if width <= 0 or height <= 0:
        return ""
    rx = min(rx, width / 2)
    ry = min(ry, height / 2)
    if rx <= 0 and ry <= 0:
        return f"M{fmt(x)} {fmt(y)}H{fmt(x + width)}V{fmt(y + height)}H{fmt(x)}Z"
    right = x + width
    bottom = y + height
    return (
        f"M{fmt(x + rx)} {fmt(y)}H{fmt(right - rx)}"
        f"A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(right)} {fmt(y + ry)}"
        f"V{fmt(bottom - ry)}"
        f"A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(right - rx)} {fmt(bottom)}"
        f"H{fmt(x + rx)}"
        f"A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x)} {fmt(bottom - ry)}"
        f"V{fmt(y + ry)}"
        f"A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + rx)} {fmt(y)}Z"
    )


def line_path(x1, y1, x2, y2) -> str:
    return f"M{fmt(x1)} {fmt(y1)}L{fmt(x2)} {fmt(y2)}"


def poly_path(points, close: bool) -> str:
    if not points:
        return ""
    head = f"M{fmt(points[0][0])} {fmt(points[0][1])}"
    rest = "".join(f"L{fmt(x)} {fmt(y)}" for x, y in points[1:])
    return head + rest + ("Z" if close else "")


def reject_effects(elem):
    for name in ("clip-path", "mask", "filter"):
        value = elem.attrib.get(name)
        if value and value not in ("none", "inherit"):
            raise SvgError(f"unsupported {name}")


def walk(elem, paint, matrix, shapes):
    tag = local(elem.tag)
    if tag in ("title", "desc", "metadata", "defs", "style", "script", "clipPath", "mask", "filter", "linearGradient", "radialGradient", "symbol"):
        if tag == "defs":
            for child in list(elem):
                child_tag = local(child.tag)
                if child_tag not in ("linearGradient", "radialGradient", "clipPath", "filter", "style", "title", "desc"):
                    raise SvgError(f"defs contains drawable <{child_tag}>")
        return
    attrs = presentation(elem)
    if attrs.get("display") == "none":
        return
    reject_effects(elem)
    paint = child_paint(paint, elem)
    matrix = mul(matrix, parse_transform(elem.attrib.get("transform")))
    if tag in ("g", "a"):
        for child in list(elem):
            walk(child, paint, matrix, shapes)
        return
    number = lambda name, default=0.0: float(elem.attrib.get(name, default))
    if tag == "path":
        data = elem.attrib.get("d", "").strip()
        if not data:
            return
        shapes.append(("path", data, paint, matrix))
    elif tag == "circle":
        shapes.append(("geom", circle_path(number("cx"), number("cy"), number("r"), number("r")), paint, matrix))
    elif tag == "ellipse":
        shapes.append(("geom", circle_path(number("cx"), number("cy"), number("rx"), number("ry")), paint, matrix))
    elif tag == "rect":
        rx = number("rx") if "rx" in elem.attrib else number("ry")
        ry = number("ry") if "ry" in elem.attrib else rx
        data = rect_path(number("x"), number("y"), number("width"), number("height"), rx, ry)
        if data:
            shapes.append(("geom", data, paint, matrix))
    elif tag == "line":
        shapes.append(("geom", line_path(number("x1"), number("y1"), number("x2"), number("y2")), paint, matrix))
    elif tag in ("polyline", "polygon"):
        data = poly_path(points_of(elem.attrib.get("points", "")), tag == "polygon")
        if data:
            shapes.append(("geom", data, paint, matrix))
    else:
        raise SvgError(f"unsupported element <{tag}>")


def convert_tree(text: str):
    root = ET.fromstring(text)
    if local(root.tag) != "svg":
        raise SvgError("root is not <svg>")
    view_box = root.attrib.get("viewBox")
    if view_box:
        box = [float(item) for item in _NUM.findall(view_box)]
        if len(box) != 4:
            raise SvgError(f"bad viewBox {view_box!r}")
        min_x, min_y, vb_w, vb_h = box
    else:
        min_x = min_y = 0.0
        vb_w = length_of(root.attrib["width"]) if "width" in root.attrib else None
        vb_h = length_of(root.attrib["height"]) if "height" in root.attrib else None
    if not vb_w or not vb_h:
        raise SvgError("svg needs a viewBox or a width and height")
    width = length_of(root.attrib["width"]) if "width" in root.attrib else vb_w
    height = length_of(root.attrib["height"]) if "height" in root.attrib else vb_h
    matrix = IDENT if abs(min_x) < 1e-9 and abs(min_y) < 1e-9 else (1, 0, 0, 1, -min_x, -min_y)
    warnings: list[str] = []
    shapes = []
    paint = child_paint(DEFAULT_PAINT, root)
    for child in list(root):
        walk(child, paint, matrix, shapes)
    return (width, height, vb_w, vb_h), shapes, warnings


def drawn(path_data: str) -> bool:
    # An `m` with extra pairs is an implicit line. The command letter alone is not enough.
    if not path_data or not path_data.strip():
        return False
    return any(command[0] != "M" for command in parse_path(path_data))


def emit_shapes(size, shapes, warnings: list[str]) -> str:
    width, height, vb_w, vb_h = size
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{fmt(width)}dp"',
        f'    android:height="{fmt(height)}dp"',
        f'    android:viewportWidth="{fmt(vb_w)}"',
        f'    android:viewportHeight="{fmt(vb_h)}">',
    ]
    emitted = 0
    for kind, data, paint, matrix in shapes:
        if not drawn(data):
            continue
        if is_ident(matrix) and kind == "path":
            path_data = data
            stroke_width = paint["stroke_width"]
        else:
            commands = parse_path(data)
            if not is_ident(matrix):
                sx, sy = scale_of(matrix)
                if sx > 1e-9 and abs(sx - sy) / max(sx, sy) > 0.02:
                    warnings.append(
                        f"non-uniform scale {sx:.3f} by {sy:.3f}; stroke width uses the average"
                    )
                commands = transform_commands(commands, matrix)
                stroke_width = paint["stroke_width"] * ((sx + sy) / 2.0)
            else:
                stroke_width = paint["stroke_width"]
            path_data = stringify(commands)
        if not drawn(path_data):
            continue
        attrs = [f'android:pathData="{path_data}"']
        fill_alpha = paint["opacity"] * paint["fill_opacity"]
        stroke_alpha = paint["opacity"] * paint["stroke_opacity"]
        if paint["fill"] is not None:
            attrs.append(f'android:fillColor="{color_hex(paint["fill"])}"')
            if abs(fill_alpha - 1) > 1e-4:
                attrs.append(f'android:fillAlpha="{fmt(fill_alpha)}"')
            if paint["fill_rule"] in ("evenodd", "evenOdd"):
                attrs.append('android:fillType="evenOdd"')
        elif paint["stroke"] is not None:
            # VectorDrawable fills black when fillColor is omitted.
            attrs.append('android:fillColor="#00000000"')
        if paint["stroke"] is not None:
            attrs.append(f'android:strokeColor="{color_hex(paint["stroke"])}"')
            if abs(stroke_alpha - 1) > 1e-4:
                attrs.append(f'android:strokeAlpha="{fmt(stroke_alpha)}"')
            attrs.append(f'android:strokeWidth="{fmt(stroke_width)}"')
            if paint["linecap"] != "butt":
                attrs.append(f'android:strokeLineCap="{paint["linecap"]}"')
            if paint["linejoin"] != "miter":
                attrs.append(f'android:strokeLineJoin="{paint["linejoin"]}"')
            if paint["linejoin"] == "miter" and abs(paint["miterlimit"] - 4) > 1e-4:
                attrs.append(f'android:strokeMiterLimit="{fmt(paint["miterlimit"])}"')
        if len(attrs) == 1:
            continue
        body = "\n        ".join(attrs)
        lines.append(f"    <path\n        {body} />")
        emitted += 1
    if emitted == 0:
        raise SvgError("conversion produced an empty vector")
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def convert_text(text: str) -> tuple[str, list[str]]:
    size, shapes, warnings = convert_tree(text)
    return emit_shapes(size, shapes, warnings), warnings


def write_if_changed(path: Path, text: str) -> bool:
    data = text.encode("utf-8")
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_bytes() == data:
        return False
    path.write_bytes(data)
    return True


def convert_dir(src: Path, dst: Path) -> list[str]:
    notes = []
    files = sorted(src.glob("*.svg"))
    if not files:
        raise SvgError(f"no svg files in {src}")
    for file in files:
        try:
            xml, warnings = convert_text(file.read_text(encoding="utf-8"))
        except SvgError as error:
            raise SvgError(f"{file.name}: {error}") from error
        write_if_changed(dst / f"{file.stem}.xml", xml)
        for warning in warnings:
            notes.append(f"{file.name}: {warning}")
    return notes


def end_point(path_data: str):
    commands = parse_path(path_data)
    point = (0.0, 0.0)
    for command in commands:
        if command[0] in ("M", "L"):
            point = (command[1], command[2])
        elif command[0] == "C":
            point = (command[5], command[6])
        elif command[0] == "Q":
            point = (command[3], command[4])
        elif command[0] == "A":
            point = (command[6], command[7])
    return point


ANDROID = "{http://schemas.android.com/apk/res/android}"


def attr_of(elem, name: str) -> str | None:
    return elem.attrib.get(ANDROID + name, elem.attrib.get("android:" + name))


def path_data_of(xml_text: str) -> list[str]:
    root = ET.fromstring(xml_text)
    return [attr_of(elem, "pathData") for elem in root if local(elem.tag) == "path"]


def self_test():
    down = (
        '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" '
        'fill="none" stroke="#000000" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round">'
        '<path d="m6 9 6 6 6-6"></path></svg>'
    )
    expected = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="m6 9 6 6 6-6"
        android:fillColor="#00000000"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.65"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
"""
    actual, _ = convert_text(down)
    if actual != expected:
        raise SystemExit("hand-written quiet_down vector does not match the converter\n" + actual)

    translated = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">'
        '<g transform="translate(10,0)"><path d="M0 0L1 0" fill="none" stroke="#000" stroke-width="1"/>'
        "</g></svg>"
    )
    xml, _ = convert_text(translated)
    start = parse_path(path_data_of(xml)[0])[0]
    if abs(start[1] - 10) > 1e-3 or abs(start[2]) > 1e-3:
        raise SystemExit(f"translate was not baked, got {path_data_of(xml)[0]}")
    if 'strokeWidth="1"' not in xml:
        raise SystemExit("translation changed the stroke width")

    scaled = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">'
        '<g transform="scale(2)"><path d="M1 1L2 1" fill="none" stroke="#000" stroke-width="1"/>'
        "</g></svg>"
    )
    xml, warnings = convert_text(scaled)
    commands = parse_path(path_data_of(xml)[0])
    if abs(commands[0][1] - 2) > 1e-3 or abs(commands[1][1] - 4) > 1e-3:
        raise SystemExit(f"scale was not baked, got {path_data_of(xml)[0]}")
    if 'strokeWidth="2"' not in xml:
        raise SystemExit("uniform scale did not double the stroke width")

    rotated = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">'
        '<path transform="rotate(90)" d="M1 0L1 0" fill="none" stroke="#000" stroke-width="1"/></svg>'
    )
    xml, _ = convert_text(rotated)
    point = parse_path(path_data_of(xml)[0])[0]
    if abs(point[1]) > 1e-3 or abs(point[2] - 1) > 1e-3:
        raise SystemExit(f"rotate(90) failed, got {path_data_of(xml)[0]}")

    faded = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">'
        '<g opacity="0.5"><line x1="0" y1="0" x2="4" y2="0" stroke="#000" stroke-width="1"/></g></svg>'
    )
    xml, _ = convert_text(faded)
    if 'strokeAlpha="0.5"' not in xml or "M0 0L4 0" not in xml:
        raise SystemExit("line or opacity conversion failed\n" + xml)

    poly = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10" width="10" height="10">'
        '<polyline points="1,1 2,3 4,3" fill="none" stroke="#000"/></svg>'
    )
    xml, _ = convert_text(poly)
    if "M1 1L2 3L4 3" not in xml:
        raise SystemExit("polyline conversion failed\n" + xml)

    arrow = parse_path("M5 12h14")
    if abs(arrow[-1][1] - 19) > 1e-6 or abs(arrow[-1][2] - 12) > 1e-6:
        raise SystemExit("horizontal path parse failed")
    chevron = end_point("m12 5 7 7-7 7")
    if abs(chevron[0] - 12) > 1e-6 or abs(chevron[1] - 19) > 1e-6:
        raise SystemExit("relative path parse failed")


def sample_arc(x1, y1, rx, ry, phi_deg, large, sweep, x2, y2, step: float):
    if math.hypot(x2 - x1, y2 - y1) < 1e-9 or abs(rx) < 1e-9 or abs(ry) < 1e-9:
        return [(x2, y2)]
    phi = math.radians(phi_deg)
    cos, sin = math.cos(phi), math.sin(phi)
    rx, ry = abs(rx), abs(ry)
    dx, dy = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cos * dx + sin * dy
    y1p = -sin * dx + cos * dy
    radius_scale = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if radius_scale > 1:
        grow = math.sqrt(radius_scale)
        rx *= grow
        ry *= grow
    num = (rx * rx) * (ry * ry) - (rx * rx) * (y1p * y1p) - (ry * ry) * (x1p * x1p)
    den = (rx * rx) * (y1p * y1p) + (ry * ry) * (x1p * x1p)
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if int(large) == int(sweep):
        coef = -coef
    cxp = coef * (rx * y1p / ry)
    cyp = coef * (-ry * x1p / rx)
    cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0
    cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0

    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        lengths = math.hypot(ux, uy) * math.hypot(vx, vy)
        amount = math.acos(max(-1.0, min(1.0, dot / lengths))) if lengths else 0.0
        if ux * vy - uy * vx < 0:
            amount = -amount
        return amount

    theta = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if int(sweep) == 0 and delta > 0:
        delta -= 2 * math.pi
    elif int(sweep) == 1 and delta < 0:
        delta += 2 * math.pi
    length = abs(delta) * max(rx, ry)
    count = max(2, math.ceil(length / step))
    points = []
    for index in range(1, count + 1):
        theta_value = theta + delta * index / count
        points.append(
            (
                cx + rx * math.cos(theta_value) * cos - ry * math.sin(theta_value) * sin,
                cy + rx * math.cos(theta_value) * sin + ry * math.sin(theta_value) * cos,
            )
        )
    return points


def flatten(path_data: str, step: float):
    commands = parse_path(path_data)
    points = []
    current = (0.0, 0.0)
    start = (0.0, 0.0)

    def add_line(x, y):
        nonlocal current
        dist = math.hypot(x - current[0], y - current[1])
        count = max(1, math.ceil(dist / step))
        for index in range(1, count + 1):
            t = index / count
            points.append((current[0] + (x - current[0]) * t, current[1] + (y - current[1]) * t))
        current = (x, y)

    def add_cubic(c1, c2, end):
        nonlocal current
        spans = 12
        origin = current
        for index in range(1, spans + 1):
            t = index / spans
            u = 1 - t
            x = u**3 * origin[0] + 3 * u**2 * t * c1[0] + 3 * u * t**2 * c2[0] + t**3 * end[0]
            y = u**3 * origin[1] + 3 * u**2 * t * c1[1] + 3 * u * t**2 * c2[1] + t**3 * end[1]
            points.append((x, y))
        current = end

    for command in commands:
        verb = command[0]
        if verb == "M":
            current = start = (command[1], command[2])
            points.append(current)
        elif verb == "L":
            add_line(command[1], command[2])
        elif verb == "C":
            add_cubic((command[1], command[2]), (command[3], command[4]), (command[5], command[6]))
        elif verb == "Q":
            c1, end = (command[1], command[2]), (command[3], command[4])
            add_cubic(
                (current[0] + 2 / 3 * (c1[0] - current[0]), current[1] + 2 / 3 * (c1[1] - current[1])),
                (end[0] + 2 / 3 * (c1[0] - end[0]), end[1] + 2 / 3 * (c1[1] - end[1])),
                end,
            )
        elif verb == "A":
            _, rx, ry, phi, large, sweep, x, y, x0, y0 = command
            points.extend(sample_arc(x0, y0, rx, ry, phi, large, sweep, x, y, step))
            current = (x, y)
        elif verb == "Z":
            add_line(start[0], start[1])
    return points


def stamp(mask, width, height, x, y, radius):
    reach = int(math.ceil(radius))
    ix, iy = int(round(x)), int(round(y))
    for yy in range(max(0, iy - reach), min(height, iy + reach + 1)):
        for xx in range(max(0, ix - reach), min(width, ix + reach + 1)):
            if (xx - x) ** 2 + (yy - y) ** 2 <= radius * radius:
                mask[yy * width + xx] = 1


def raster_paths(paths, scale: int):
    side = 24 * scale
    mask = bytearray(side * side)
    for data, stroke in paths:
        radius = max(stroke, 0.4) / 2 * scale
        for x, y in flatten(data, 0.35):
            stamp(mask, side, side, x * scale, y * scale, radius)
    return mask, side


def sample_circle(cx, cy, r, step=0.35):
    count = max(8, math.ceil(2 * math.pi * r / step))
    return [(cx + r * math.cos(2 * math.pi * i / count), cy + r * math.sin(2 * math.pi * i / count)) for i in range(count + 1)]


def sample_round_rect(x, y, w, h, rx, ry, step=0.35):
    rx = min(rx, w / 2)
    ry = min(ry, h / 2)
    if rx <= 0 and ry <= 0:
        return flatten(rect_path(x, y, w, h, 0, 0), step)
    points = []
    points += [(x + rx + t, y) for t in frange(0, w - 2 * rx, step)]
    points += sample_corner(x + w - rx, y + ry, rx, ry, -math.pi / 2, 0, step)
    points += [(x + w, y + ry + t) for t in frange(0, h - 2 * ry, step)]
    points += sample_corner(x + w - rx, y + h - ry, rx, ry, 0, math.pi / 2, step)
    points += [(x + w - rx - t, y + h) for t in frange(0, w - 2 * rx, step)]
    points += sample_corner(x + rx, y + h - ry, rx, ry, math.pi / 2, math.pi, step)
    points += [(x, y + h - ry - t) for t in frange(0, h - 2 * ry, step)]
    points += sample_corner(x + rx, y + ry, rx, ry, math.pi, 3 * math.pi / 2, step)
    return points


def frange(start, stop, step):
    values = []
    cursor = start
    while cursor < stop:
        values.append(cursor)
        cursor += step
    values.append(stop)
    return values


def sample_corner(cx, cy, rx, ry, a0, a1, step):
    length = abs(a1 - a0) * max(rx, ry)
    count = max(2, math.ceil(length / step))
    return [
        (cx + rx * math.cos(a0 + (a1 - a0) * i / count), cy + ry * math.sin(a0 + (a1 - a0) * i / count))
        for i in range(count + 1)
    ]


def raster_points(point_groups, scale: int):
    side = 24 * scale
    mask = bytearray(side * side)
    for points, stroke in point_groups:
        radius = max(stroke, 0.4) / 2 * scale
        for x, y in points:
            stamp(mask, side, side, x * scale, y * scale, radius)
    return mask


def overlap(left: bytearray, right: bytearray) -> tuple[float, float, int, int]:
    both = sum(1 for a, b in zip(left, right) if a and b)
    left_ink = sum(left)
    right_ink = sum(right)
    left_ratio = both / left_ink if left_ink else 0.0
    right_ratio = both / right_ink if right_ink else 0.0
    return left_ratio, right_ratio, left_ink, right_ink


def source_groups(text: str):
    root = ET.fromstring(text)
    paint = child_paint(DEFAULT_PAINT, root)
    groups = []

    def visit(elem, inherited):
        tag = local(elem.tag)
        if tag in ("defs", "title", "desc", "metadata"):
            return
        current = child_paint(inherited, elem)
        stroke = current["stroke_width"] if current["stroke"] is not None else 0.0
        if tag in ("g", "svg", "a"):
            for child in list(elem):
                visit(child, current)
            return
        if tag == "path":
            groups.append((flatten(elem.attrib.get("d", ""), 0.35), stroke))
        elif tag == "circle":
            groups.append((sample_circle(float(elem.attrib.get("cx", 0)), float(elem.attrib.get("cy", 0)), float(elem.attrib["r"])), stroke))
        elif tag == "rect":
            rx = float(elem.attrib["rx"]) if "rx" in elem.attrib else float(elem.attrib.get("ry", 0))
            ry = float(elem.attrib["ry"]) if "ry" in elem.attrib else rx
            groups.append(
                (
                    sample_round_rect(
                        float(elem.attrib.get("x", 0)),
                        float(elem.attrib.get("y", 0)),
                        float(elem.attrib.get("width", 0)),
                        float(elem.attrib.get("height", 0)),
                        rx,
                        ry,
                    ),
                    stroke,
                )
            )

    for child in list(root):
        visit(child, paint)
    return groups


def vector_paths(xml_text: str):
    root = ET.fromstring(xml_text)
    found = []
    for elem in root:
        if local(elem.tag) != "path":
            continue
        stroke = float(attr_of(elem, "strokeWidth") or "0")
        found.append((attr_of(elem, "pathData"), stroke))
    return found


def write_png(path: Path, width: int, height: int, rgb: bytes):
    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + rgb[row * width * 3:(row + 1) * width * 3] for row in range(height))
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


def compare_icons(src: Path, dst: Path, image_path: Path):
    names = ["quiet_search", "quiet_calendar", "quiet_dumbbell", "quiet_heart"]
    scale = 8
    side = 24 * scale
    gap = 8
    sheet_w = (side * 3 + gap * 2) 
    sheet_h = side * len(names) + gap * (len(names) - 1)
    rgb = bytearray([255, 255, 255] * sheet_w * sheet_h)
    report = []
    for row, name in enumerate(names):
        source = (src / f"{name}.svg").read_text(encoding="utf-8")
        xml = (dst / f"{name}.xml").read_text(encoding="utf-8")
        source_mask = raster_points(source_groups(source), scale)
        vector_mask, _ = raster_paths(vector_paths(xml), scale)
        left_ratio, right_ratio, left_ink, right_ink = overlap(source_mask, vector_mask)
        report.append(f"{name} overlap source={left_ratio:.3f} vector={right_ratio:.3f} ink={left_ink}/{right_ink}")
        if left_ink < 20 or right_ink < 20:
            raise SystemExit(f"{name} raster is empty")
        if left_ratio < 0.82 or right_ratio < 0.82:
            raise SystemExit(report[-1] + " is below 0.82")
        origin_y = row * (side + gap)
        for y in range(side):
            for x in range(side):
                for panel, mask in ((0, source_mask), (1, vector_mask)):
                    if mask[y * side + x]:
                        px = panel * (side + gap) + x
                        py = origin_y + y
                        i = (py * sheet_w + px) * 3
                        rgb[i:i + 3] = b"\x11\x11\x11"
                if source_mask[y * side + x] != vector_mask[y * side + x]:
                    px = 2 * (side + gap) + x
                    py = origin_y + y
                    i = (py * sheet_w + px) * 3
                    rgb[i:i + 3] = b"\xc0\x30\x30"
    image_path.parent.mkdir(parents=True, exist_ok=True)
    write_png(image_path, sheet_w, sheet_h, bytes(rgb))
    return report


def check_outputs(src: Path, dst: Path):
    empty = []
    kept = 0
    for svg in sorted(src.glob("*.svg")):
        xml_path = dst / f"{svg.stem}.xml"
        xml = xml_path.read_text(encoding="utf-8")
        datas = path_data_of(xml)
        if not datas or not any(drawn(data) for data in datas):
            empty.append(svg.name)
        root = ET.fromstring(svg.read_text(encoding="utf-8"))
        raw_paths = []

        def collect(elem):
            if local(elem.tag) == "path" and not elem.attrib.get("transform"):
                raw_paths.append(elem.attrib.get("d", "").strip())
            elif local(elem.tag) in ("g", "svg"):
                if elem.attrib.get("transform"):
                    return
                for child in list(elem):
                    collect(child)

        collect(root)
        for raw in raw_paths:
            if raw and raw not in datas:
                raise SystemExit(f"{svg.name} dropped or rewrote path {raw}")
            kept += 1
        end_point(datas[0])
    if empty:
        raise SystemExit("empty vectors: " + ", ".join(empty))
    return kept


def main(argv: list[str]) -> int:
    self_test()
    check = False
    args = argv[1:]
    if args and args[0] == "--check":
        check = True
        args = args[1:]
    if len(args) != 2:
        print(__doc__.strip())
        return 2
    src, dst = Path(args[0]), Path(args[1])
    notes = convert_dir(src, dst)
    print(f"converted {len(list(src.glob('*.svg')))} svg files into {dst}")
    for note in notes:
        print("warning:", note)
    if check:
        kept = check_outputs(src, dst)
        image = Path.home() / "AppData" / "Local" / "Temp" / "ecolift-pstack" / "parity-D" / "svg-compare.png"
        for line in compare_icons(src, dst, image):
            print(line)
        print(f"verbatim paths kept: {kept}")
        print(f"compare image: {image}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except SvgError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
