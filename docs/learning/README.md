# Learn Android development through Fuel & Expense Tracker

An **80-page, 29-chapter** learning guide built around the actual application: **16,040 words of core lessons**, with annotated source excerpts, a file atlas, matched screenshots and **12 answered practical labs**. The complete book contains approximately **19,125 words**, including appendices and code.

Learning edition: **21 October 2026 (Malaysia)**. App source: **1.5.4 / code 11**, pinned to [`1f13482`](https://github.com/Piccol1001/ChatgptFuelConsumption/tree/1f13482bfc550da77e5c950e48075f62de12620f). This is a documentation delivery; application code, data, signing identity and version are unchanged.

## Download

- [Read the PDF](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/learning-materials/Fuel_Expense_Tracker_Learning_Guide.pdf)
- [Download everything as one ZIP](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/learning-materials/Fuel_Expense_Tracker_Complete_Learning_Pack.zip)
- [Editable Word document](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/learning-materials/Fuel_Expense_Tracker_Learning_Guide.docx)
- [Offline HTML edition](../../learning-materials/Fuel_Expense_Tracker_Learning_Guide.html) — download and open in a browser.
- [Markdown edition](BOOK.md)
- [Source companion ZIP](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/learning-materials/Source_Companion.zip)
- [Offline source browser](../../learning-materials/Source_Browser.html) — download and open locally; search file paths and expand code.

The complete ZIP includes PDF, DOCX, HTML, Markdown, screenshot assets, the offline source browser and a source archive. The archive contains **212 exact source/configuration/test/resource/documentation files**, including **all 63 production Kotlin files**, with a checksum manifest. It excludes APKs, personal records, signing keys and build caches. The archive is a reading/build-source snapshot, not a full Git clone or a bundle of downloaded SDK dependencies.

## What you will learn

1. Why Kotlin, Compose, Room, DataStore and the other technologies were chosen.
2. Programming fundamentals, data types, functions, state, lifecycle, coroutines and architectural data flow.
3. Exact money, cents-first entry, categories, payments, refunds and conflict-aware Undo.
4. Fuel physics and assumptions, weighted calculations, journey estimates and actual costs.
5. Monthly income, commitments, allocations, precise rounding and recurring-work idempotency.
6. Compose layouts, themes, accessibility, gestures and the measured Journey layout investigation.
7. Offline OCR, receipt parsing, local categorization, duplicate detection and safe auto-save.
8. Encryption, keys, app lock, encrypted backups and crash recovery across storage systems.
9. Analytics, charts, exports, widgets and performance measurement.
10. Testing, common mistakes, real regression cases, Git/GitHub, Codex collaboration and compatible APK releases.

The book distinguishes current implementation from historical requests, estimates from measured facts, and historical test results from verification performed for this document. It does not claim that reading alone replaces practical work or every Android API reference.

## Editable sources and rebuilding

Edit the six files under [chapters](chapters/). `BOOK.md`, `APPENDICES.md`, screenshots, source manifests and distributable editions are generated. The generator reads app source from the pinned Git checkpoint, so later application changes cannot silently change the learning examples.

Requirements: Python 3, Pandoc (built with 3.1.11.1), the [documentation Python packages](tools/requirements.txt), Open Sans and Liberation Mono TTF fonts under `/usr/share/fonts/truetype/`. Browser validation uses `/usr/bin/chromium`. These tools are development-time documentation dependencies, not app dependencies.

From the repository root:

```sh
python docs/learning/tools/build_book.py
python docs/learning/tools/verify_book.py
```

Review [build statistics](BUILD_STATS.json), [validation](VALIDATION.md), [machine-readable checks](VALIDATION.json) and [artifact checksums](../../learning-materials/SHA256SUMS). Visually inspect representative PDF pages after changing layout. The DOCX contents field may need updating in a word processor after edits; the PDF has resolved clickable page numbers.

When publishing a revised learning edition, regenerate all formats together, verify the complete ZIP and public download hashes, then record the new edition. Do not increment the Android release version for documentation work.
