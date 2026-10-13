#!/usr/bin/env python3
"""Read and update CHANGELOG.md for releases.

  changelog.py notes              print the Unreleased lines
  changelog.py release NAME FILE  move the lines in FILE from Unreleased into
                                  a new "## NAME" section below it
"""
import pathlib
import sys

PATH = pathlib.Path(__file__).resolve().parent.parent / 'CHANGELOG.md'
HEADING = '## Unreleased'


def split(text):
    """The file as (before Unreleased's lines, its lines, everything after)."""
    lines = text.splitlines()
    start = lines.index(HEADING) + 1
    end = next((i for i in range(start, len(lines)) if lines[i].startswith('## ')), len(lines))
    return lines[:start], [l for l in lines[start:end] if l.strip()], lines[end:]


def main(args):
    head, unreleased, rest = split(PATH.read_text())
    if args == ['notes']:
        # Print nothing at all when Unreleased is empty: the release workflow
        # treats any output as notes to publish.
        if unreleased:
            print('\n'.join(unreleased))
    elif len(args) == 3 and args[0] == 'release':
        released = [l for l in pathlib.Path(args[2]).read_text().splitlines() if l.strip()]
        # Lines merged while the release was building stay Unreleased.
        remaining = [l for l in unreleased if l not in released]
        section = ['', f'## {args[1]}', ''] + released
        out = head + ([''] + remaining if remaining else []) + section + [''] + rest
        PATH.write_text('\n'.join(out).rstrip('\n') + '\n')
    else:
        sys.exit(__doc__)


main(sys.argv[1:])
