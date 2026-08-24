#!/usr/bin/env python3
"""Extract a subtask's parent user-story number from its own body, via its
"### Parent user story" self-reference. Reverse direction of
filter_subtasks_by_parent.py (which goes parent -> matching subtasks);
this goes one subtask -> its parent. Same regex, so the two stay in sync.

Usage: python3 find_parent_story.py < subtask_body.txt
Prints the parent issue number, or nothing if not found.
"""
import re
import sys


def main():
    body = sys.stdin.read()
    m = re.search(r"parent\s+user\s+story.{0,30}#(\d+)", body, re.IGNORECASE | re.DOTALL)
    if m:
        print(m.group(1))


if __name__ == "__main__":
    main()
