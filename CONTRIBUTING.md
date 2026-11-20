# Contributing

This is a personal app, written for one phone and one pair of hands. It is public because
it may as well be, not because it is looking for maintainers. That shapes what is useful to
send.

## What is genuinely wanted

**Corrections to a caution.** This is the part that can hurt somebody if it is wrong. A
caution that is missing, a medicine an herb interacts with that is not listed, a line that
says more than the evidence does: those are worth your time and mine. Open a **Bug report**
issue and tick "A caution is missing, wrong or hard to find".

Please say where you are getting it from. Every herb in the book is written from a source it
names, and a correction needs the same footing as the thing it corrects. The book only takes
text that is in the public domain or openly licensed.

**Bugs.** Open a **Bug report** issue.

## What is probably not

**Feature pull requests.** They are unlikely to be merged, and it is fairer to say so than
to let you write one. If you want the app to do something it does not, open an issue and say
what you were trying to do.

**Anything that claims an herb cures or treats something.** The book says what an herb is
traditionally used for and what the research says, and nothing stronger. This will not
change.

## Security

Do not open a public issue. Use
[private vulnerability reporting](https://github.com/wanderwildwood/yakuso/security/advisories/new).

## If you do send code

```sh
./gradlew :app:testDebugUnitTest      # what CI runs
python3 tools/check-cautions.py       # and this, on every push
```

Commit messages here are plain sentences saying what changed and why, in the present tense.
Have a look at `git log` before writing one.

## License

GPL-3.0-only. By contributing you agree your contribution is licensed under it.
