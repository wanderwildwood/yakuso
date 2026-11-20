#!/usr/bin/env python3
"""Checks the herb book before it goes in a build.

    tools/check-cautions.py [path/to/herbs.json]

Exits 1 and says what is wrong when any herb:
  - has no source, or a source without an https address
  - has no caution, or a caution with an unknown severity
  - uses the words "cure" or "treat(s)" anywhere (the book says "traditionally used for")
  - is a "don't use" entry with a preparation, or without its reason
  - is an ordinary entry without a preparation
  - mentions pregnancy or nursing in a caution without saying to ask a midwife or doctor
  - repeats an id
  - tags a use with a need the Finder doesn't have, or a "don't use" entry with any need
  - is tagged "pregnancy" (or "nursing") while a caution says to avoid it then: an "avoid" caution
    that names it, or a caution that names it beside "don't", "never", "avoid", "unsafe" and the like
  - is tagged "pregnancy" (or "nursing") without a caution that names it and says to ask a
    midwife or doctor

Default path: app/src/main/assets/herbs.json, next to this folder.
"""
import json
import os
import re
import sys

SEVERITIES = {"avoid", "caution", "note"}
FORBIDDEN = re.compile(r"\b(cure[sd]?|curing|treats?|treated)\b", re.IGNORECASE)
PREGNANCY = re.compile(r"\b(pregnan\w*|nursing|breastfeed\w*)\b", re.IGNORECASE)
ASK = re.compile(r"\bmidwife\b|\bdoctor\b", re.IGNORECASE)
NEEDS = {"sleep", "calm", "digestion", "colds", "sore-throat", "nausea", "headache", "period-cramps", "pregnancy", "nursing"}
STATES = {
    "pregnancy": re.compile(r"\bpregnan\w*", re.IGNORECASE),
    "nursing": re.compile(r"\bnursing\b|\bbreastfeed\w*|\bbreast ?milk\b", re.IGNORECASE),
}
AVOID_WORDS = re.compile(
    r"\b(don'?t|do not|never|avoid\w*|unsafe|should not|must not|not be used|not safe|stop)\b|\bdon’t\b",
    re.IGNORECASE)


def strings(value):
    """Every string inside a JSON value, so the word check sees the whole entry."""
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for v in value.values():
            yield from strings(v)
    elif isinstance(value, list):
        for v in value:
            yield from strings(v)


def problems(book):
    out = []
    seen = set()
    herbs = book.get("herbs")
    if not isinstance(herbs, list) or not herbs:
        return ["no herbs in the file"]
    for i, herb in enumerate(herbs):
        hid = herb.get("id") or f"entry {i + 1}"
        if hid in seen:
            out.append(f"{hid}: the id is used twice")
        seen.add(hid)
        if not herb.get("name"):
            out.append(f"{hid}: no name")

        sources = herb.get("sources") or []
        if not sources:
            out.append(f"{hid}: no source")
        for s in sources:
            if not str(s.get("url", "")).startswith("https://"):
                out.append(f"{hid}: a source has no https address ({s.get('title')})")

        cautions = herb.get("cautions") or []
        if not cautions:
            out.append(f"{hid}: no caution")
        for c in cautions:
            if c.get("severity") not in SEVERITIES:
                out.append(f"{hid}: unknown severity {c.get('severity')!r}")
            text = c.get("text") or ""
            if not text.strip():
                out.append(f"{hid}: an empty caution")
            if PREGNANCY.search(text) and not ASK.search(text):
                out.append(f"{hid}: a pregnancy or nursing caution that does not say to ask a midwife or doctor")

        for text in strings(herb):
            m = FORBIDDEN.search(text)
            if m:
                out.append(f"{hid}: says {m.group(0)!r} (\"{text[:60]}\")")

        needs = set()
        for use in herb.get("uses") or []:
            for n in use.get("needs") or []:
                if n not in NEEDS:
                    out.append(f"{hid}: unknown need {n!r} on \"{use.get('for')}\"")
                needs.add(n)
        if herb.get("dontUse") and needs:
            out.append(f"{hid}: a don't-use entry is tagged for the Finder ({', '.join(sorted(needs))})")
        for state, word in STATES.items():
            if state not in needs:
                continue
            naming = [c for c in cautions if word.search(c.get("text") or "")]
            for c in naming:
                text = c.get("text") or ""
                if c.get("severity") == "avoid" or AVOID_WORDS.search(text):
                    out.append(f"{hid}: tagged {state!r} but a caution says to avoid it then (\"{text[:60]}\")")
            if not any(ASK.search(c.get("text") or "") for c in naming):
                out.append(f"{hid}: tagged {state!r} without a caution that names it and says to ask a midwife or doctor")

        if herb.get("dontUse"):
            if herb.get("preparation"):
                out.append(f"{hid}: a don't-use entry has a preparation")
            if not (herb.get("dontUseReason") or "").strip():
                out.append(f"{hid}: a don't-use entry has no reason")
        elif not herb.get("preparation"):
            out.append(f"{hid}: no preparation")
    return out


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "app", "src", "main", "assets", "herbs.json")
    with open(path, encoding="utf-8") as f:
        book = json.load(f)
    found = problems(book)
    if found:
        for p in found:
            print("FAIL", p)
        print(f"{len(found)} problem(s) in {path}")
        sys.exit(1)
    print(f"OK: {len(book['herbs'])} herbs, every one sourced and cautioned ({path})")


if __name__ == "__main__":
    main()
