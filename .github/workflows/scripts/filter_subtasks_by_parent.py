#!/usr/bin/env python3
"""Filter a JSON list of type:subtask issues down to the ones that belong
to a given parent user-story number, by checking each subtask's own body
for its "### Parent user story" reference — not by trusting the parent's
own checklist to be perfectly in sync (it's kept up to date, but this is
the robust direction: each subtask independently declares its parent).

Usage: python3 filter_subtasks_by_parent.py <parent_number> < all_subtasks.json > matching_subtasks.json
"""
import json
import re
import sys

def main():
    if len(sys.argv) != 2:
        print("usage: filter_subtasks_by_parent.py <parent_number>", file=sys.stderr)
        sys.exit(1)

    parent_number = sys.argv[1]
    issues = json.load(sys.stdin)

    # Matches "### Parent user story" (or similar heading text) followed,
    # within a short distance, by "#<parent_number>" as a whole number
    # (not a prefix of a longer number).
    pattern = re.compile(
        r"parent\s+user\s+story.{0,30}#" + re.escape(parent_number) + r"\b",
        re.IGNORECASE | re.DOTALL,
    )

    matching = [
        issue for issue in issues
        if issue.get("body") and pattern.search(issue["body"])
    ]

    json.dump(matching, sys.stdout)

if __name__ == "__main__":
    main()
