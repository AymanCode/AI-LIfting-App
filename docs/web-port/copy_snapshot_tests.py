"""Copy web snapshot tests into the Android unit-test tree.

The bucket rules are the list. Run from any cwd.

    python docs/web-port/copy_snapshot_tests.py --dry-run
    python docs/web-port/copy_snapshot_tests.py --bucket foundation
    python docs/web-port/copy_snapshot_tests.py --bucket viewmodel

A second run overwrites the same relative paths and leaves every other file alone.
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

SNAPSHOT = Path(
    r"C:\Users\ayman\Documents\ecolift-web-snapshot-57eda2a\composeApp\src\wasmJsTest\kotlin"
)
DEST_ROOT = Path(__file__).resolve().parents[2] / "src" / "test" / "java"

UI_COUPLED = {
    "com/ayman/ecolift/YearErasTest.kt",
    "com/ayman/ecolift/data/E1rmConsumerParityTest.kt",
    "com/ayman/ecolift/data/Y2E1rmPerfTest.kt",
    "com/ayman/ecolift/data/SmartSuggestToggleTest.kt",
}
BROWSER = {
    "com/ayman/ecolift/web/AppModeTest.kt",
    "com/ayman/ecolift/web/DiagnosticsTest.kt",
    "com/ayman/ecolift/web/LogDeepLinkTest.kt",
}


def bucket(rel: str) -> str:
    if rel in UI_COUPLED or rel.startswith("com/ayman/ecolift/ui/"):
        return "ui"
    if rel.startswith(
        (
            "com/ayman/ecolift/web/auth/",
            "com/ayman/ecolift/web/offline/",
            "com/ayman/ecolift/web/supabase/",
        )
    ):
        return "scope"
    if rel in BROWSER:
        return "browser"
    if rel.startswith("com/ayman/ecolift/web/demo/"):
        return "demo"
    if rel == "com/ayman/ecolift/web/ai/EdgeAiTest.kt":
        return "edge"
    return "foundation"


def select(name: str) -> list[Path]:
    files = sorted(SNAPSHOT.rglob("*.kt"))
    chosen = []
    for path in files:
        rel = path.relative_to(SNAPSHOT).as_posix()
        kind = bucket(rel)
        if name == "foundation" and kind == "foundation":
            chosen.append(path)
        elif name == "viewmodel" and "/ui/viewmodel/" in rel:
            chosen.append(path)
        elif name == "ui-coupled" and rel in UI_COUPLED:
            chosen.append(path)
        elif name == "edge" and kind == "edge":
            chosen.append(path)
    return chosen


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--bucket",
        choices=("foundation", "viewmodel", "ui-coupled", "edge"),
        default="foundation",
    )
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    chosen = select(args.bucket)
    print(f"{args.bucket} {len(chosen)}")
    for src in chosen:
        rel = src.relative_to(SNAPSHOT)
        dest = DEST_ROOT / rel
        print(rel.as_posix())
        if args.dry_run:
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dest)
    if not args.dry_run:
        print(f"copied {len(chosen)} into {DEST_ROOT}")


if __name__ == "__main__":
    main()
