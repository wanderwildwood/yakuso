# 薬草 yakusō — Herbal

A small offline book of herbs for tea, for the [Mudita Kompakt](https://mudita.com/products/kompakt/),
built for its E Ink screen.

Not a fork. Written from scratch in Kotlin and Jetpack Compose, using Mudita's own
[MMD](https://github.com/mudita/MMD) design system so it looks like the apps the phone
already ships with.

*Yakusō* is 薬草: a medicinal herb, a plant gathered for what it does rather than how it looks.

## Not medical advice

**This app is a reference book, not medical advice.** It cannot tell you that an herb is
safe for you. Herbs interact with medicines and can harm some people, in pregnancy and while
nursing especially. If you take any medicine, are pregnant or nursing, or are unwell, ask
your doctor, midwife or pharmacist first, and see a doctor for anything serious or lasting.

The book never says an herb cures or treats anything. It says what an herb is
*traditionally used for*, and then, in one line, what the research says about that.

## What it is

- **Herbs**, A to Z, with a search field. A row carries a ⚠ when the herb has a serious
  caution.
- **An herb's page**, always in the same order, cautions first: who should not take it and
  which medicines it may interact with; what it is traditionally used for and what the
  research says; how to prepare it (temperatures in °F, then °C); the parts used, the taste
  and other names; and its sources.
- **"Don't use" pages** for herbs that are not safe to take (comfrey, for one). They open
  with "Don't take this" and the reason, and have no preparation.

It has no account, no sync and no network: there is no internet permission, and it works in
airplane mode.

## Where the herb text comes from

The cautions and research lines are written from the US National Center for Complementary
and Integrative Health's [Herbs at a Glance](https://www.nccih.nih.gov/health/herbsataglance)
pages and, for comfrey, from [LiverTox](https://www.ncbi.nlm.nih.gov/books/NBK548370/)
(National Institute of Diabetes and Digestive and Kidney Diseases). Both are works of the US
government and in the public domain; the wording in the app is its own. Every herb in
`app/src/main/assets/herbs.json` lists its sources, and `tools/check-cautions.py` refuses an
herb with no source, no caution, a "don't use" page with a preparation, or the words "cure"
or "treats".

## Status

Early. The book holds five herbs, and their text is a **draft** that has not yet been read
and approved: each page says so at the top. The brew timer, a journal of what you brewed, a
cupboard of what you have and a finder by everyday need are still to come.

## Building

```sh
python3 tools/check-cautions.py
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

Builds are signed with a keystore in `signing/`, which is gitignored. There is no fallback:
without it the build produces an *unsigned* APK, which will not install anywhere. That is
deliberate: a signing key committed to a public repo is not a signing key, and a missing one
should stop you rather than quietly hand you something installable.

The debug build takes the same key when it is there. The first install of any variant fixes
the signer for good, so a debug build put on a phone is not a throwaway.

## License

GPL-3.0-only. See [LICENSE](LICENSE). In the app, About > Licenses shows the source address,
the full license text and everything the app is built with; About also carries Privacy, Terms
and the not-medical-advice note.

Copyright (C) 2026 wander wildwood.
