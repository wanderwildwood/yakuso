#!/usr/bin/env python3
"""Which lines of the herb book may go into a CC BY-NC-ND work (the game Wander).

    tools/game-safe.py [path/to/herbs.json] [--summary]

A line may go in when every source it came from is public domain, CC BY (3.0 or 4.0) or "own" (text
by the app's author, wander wildwood, which needs no credit beyond his name); a CC BY source must then be credited, and the credit is printed beside the line. A line may not go in when
any of its sources is CC BY-SA (its share-alike terms can't be met under NC-ND) or "none" (not
tied to a source and not his own).

Default path: app/src/main/assets/herbs.json, next to this folder. --summary prints only the counts.
"""
import json
import os
import sys
from collections import Counter

ALLOWED = {"public-domain", "cc-by-3.0", "cc-by-4.0", "own"}
NEEDS_CREDIT = {"cc-by-3.0", "cc-by-4.0"}
LABEL = {"cc-by-3.0": "CC BY 3.0", "cc-by-4.0": "CC BY 4.0", "cc-by-sa-4.0": "CC BY-SA 4.0",
         "public-domain": "public domain", "own": "wander wildwood's own text", "none": "not tied to a source"}


def lines(herb):
    """(what, text, source ids) for every text line of an entry, in page order."""
    if herb.get("dontUseReason"):
        yield "don't-use reason", herb["dontUseReason"], herb.get("dontUseReasonSrc")
    for c in herb.get("cautions") or []:
        yield f"caution ({c['severity']})", c["text"], c.get("src")
    if herb.get("interactions"):
        yield "interactions", ", ".join(herb["interactions"]), herb.get("interactionsSrc")
    for u in herb.get("uses") or []:
        yield "use", f"{u['for']} ({u.get('evidence')})", u.get("src")
    if herb.get("research"):
        yield "research", herb["research"], herb.get("researchSrc")
    p = herb.get("preparation")
    if p:
        text = f"{p['method']}: {p['amount']}, {p['tempF']} °F, {p['steepMin']}-{p['steepMax']} min" + (f"; {p['note']}" if p.get("note") else "")
        yield "preparation", text, p.get("src")
    if herb.get("parts"):
        yield "parts", herb["parts"], herb.get("partsSrc")
    if herb.get("taste"):
        yield "taste", herb["taste"], herb.get("tasteSrc")
    if herb.get("alsoCalled"):
        yield "also called", ", ".join(herb["alsoCalled"]), herb.get("alsoCalledSrc")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    summary_only = "--summary" in sys.argv
    here = os.path.dirname(os.path.abspath(__file__))
    path = args[0] if args else os.path.join(here, "..", "app", "src", "main", "assets", "herbs.json")
    book = json.load(open(path, encoding="utf-8"))
    shared = book.get("sharedSources") or {}

    count = Counter()
    blocked_by = Counter()
    herbs_all_safe = 0
    credits = {}
    for herb in book["herbs"]:
        sources = {s["id"]: s for s in herb.get("sources") or []}
        sources.update({k: dict(v, id=k) for k, v in shared.items()})
        may, may_not = [], []
        for what, text, src in lines(herb):
            src = src or []
            lics = [sources[i].get("license", "none") for i in src if i in sources] or ["none"]
            count["lines"] += 1
            if all(l in ALLOWED for l in lics):
                credit = [sources[i] for i in src if sources[i].get("license") in NEEDS_CREDIT]
                for s in credit:
                    credits[s["url"]] = s
                count["may"] += 1
                if credit:
                    count["may, with credit"] += 1
                if "own" in lics:
                    count["may, own text"] += 1
                may.append((what, text, credit))
            else:
                count["may not"] += 1
                for l in sorted(set(lics) - ALLOWED):
                    blocked_by[l] += 1
                why = ", ".join(f"{i} ({LABEL.get(sources[i].get('license'), '?')})" for i in src
                                if i in sources and sources[i].get("license") not in ALLOWED) or "no source"
                may_not.append((what, text, why))
        if not may_not:
            herbs_all_safe += 1
        if summary_only:
            continue
        print(f"== {herb['name']} ({herb['id']}): {len(may)} may, {len(may_not)} may not")
        for what, text, credit in may:
            c = "; ".join(f"credit: {s['title']}, {s['url']} ({LABEL[s['license']]})" for s in credit)
            print(f"  MAY      {what}: {text}" + (f"\n             {c}" if c else ""))
        for what, text, why in may_not:
            print(f"  MAY NOT  {what}: {text}\n             because of: {why}")
        print()

    print("Summary")
    print(f"  herbs: {len(book['herbs'])}, of which every line may go in: {herbs_all_safe}")
    print(f"  text lines: {count['lines']}")
    print(f"  may go in: {count['may']} ({count['may, with credit']} of them need a CC BY credit; "
          f"{count['may, own text']} are wander wildwood's own text)")
    print(f"  may not: {count['may not']}")
    print(f"    with a CC BY-SA source (Wikipedia): {blocked_by['cc-by-sa-4.0']}")
    print(f"    not tied to a source: {blocked_by['none']}")
    print(f"  CC BY works to credit: {len(credits)}")
    for s in credits.values():
        print(f"    {s['title']}, {s['url']} ({LABEL[s['license']]})")


if __name__ == "__main__":
    main()
