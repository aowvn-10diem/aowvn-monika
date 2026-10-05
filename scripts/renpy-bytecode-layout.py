#!/usr/bin/env python3
"""Keep official bytecode intact, relocate orphan PEP3147 caches for sourceless import."""
import argparse
from pathlib import Path
import re


def normalize(root: Path, tag: str = "cpython-312") -> int:
    moves = []
    destinations = set()
    for source in sorted(root.rglob("__pycache__/*.pyc")):
        match = re.fullmatch(r"(.+)\.(cpython-[0-9]+)(?:\.opt-[0-9]+)?\.pyc", source.name)
        if not match or match.group(2) != tag:
            raise RuntimeError("Unexpected bytecode tag")
        module = match.group(1)
        directory = source.parent.parent
        if (directory / (module + ".py")).is_file():
            continue
        destination = directory / (module + ".pyc")
        if destination.exists() or destination in destinations:
            raise RuntimeError("Ambiguous sourceless module destination")
        destinations.add(destination)
        moves.append((source, destination))
    # Validate all destinations before moving anything.
    for source, destination in moves:
        source.rename(destination)
    for directory in sorted(root.rglob("__pycache__"), reverse=True):
        if directory.is_dir() and not any(directory.iterdir()): directory.rmdir()
    return len(moves)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    args = parser.parse_args()
    print("Relocated orphan bytecode modules:", normalize(args.root))
