# Learning material validation — 21 October 2026

The generated guide contains **80 PDF pages**, **29 chapters**, four appendices, 14 annotated source excerpts and 12 answered labs. Core lessons contain **16,040 words**; the combined Markdown contains approximately **19,125 words**, including code and appendices. Source is pinned to `1f13482bfc550da77e5c950e48075f62de12620f` (app 1.5.4 / code11).

## Checks performed

- All 33 top-level sections are present in the PDF, with resolved contents links, bookmarks and source references. All 80 pages contain content; extracted text/image block bounds stay within the page.
- Representative cover, contents, prose, code, file-atlas and screen pages were rendered and visually inspected. Screenshot headings were kept with their images after a pagination correction.
- All **134 pinned source links** resolve to files in the recorded Git snapshot; excerpt line references are within their files.
- All **212 archived files** match the original Git blobs and their SHA-256 entries. The archive includes every one of the **63 production Kotlin files**, tests, resources, build configuration and selected evidence. No APK, signing key, financial database or build cache is included.
- Complete-package ZIP integrity, included document bytes and the eight distributed-file checksums pass. Standalone Markdown has its five relative image assets; the complete ZIP includes them too.
- The DOCX has all 33 top-level sections, five embedded screenshots and images within the document width. It is structurally checked; pagination was not visually checked in Microsoft Word or LibreOffice. Its contents field may need updating in a word processor after editing.
- Worked answers for sen entry/conversion, fuel aggregation, journey rounding, allocations and allowance were independently checked with exact arithmetic where appropriate.
- Chromium rendered the exact HTML artifact bytes at **390×844** and **1280×900**. Both editions work with external resource requests blocked: five embedded guide images load, internal contents targets exist, source-file filtering/expansion works, no JavaScript errors occur and no document-wide horizontal overflow remains. Code panes retain their own horizontal scrolling. The managed browser disallows `file://` navigation, so the harness used `set_content` with the unmodified generated HTML rather than claiming a successful local-file navigation.

Machine-readable results: [VALIDATION.json](VALIDATION.json). Artifact statistics: [BUILD_STATS.json](BUILD_STATS.json). Checksums: [SHA256SUMS](../../learning-materials/SHA256SUMS).

## Scope and provenance

This is documentation validation. Android tests were not rerun and no APK was rebuilt for the learning-material request. The book labels existing app test/build/native results as historical evidence and keeps the full 1.5.3 native run separate from the four exact-1.5.4 follow-ups. Physical-device limitations remain explicit.

The book explains current every-fill consumption as an estimate with an equal-tank-level assumption, planned allowance separately from actual cash flow, the actual current pager order and press-feedback value, typed encrypted DataStore, limited crop/rotate behavior, conservative currency/duplicate heuristics and deferred supplied-logo replacement. It does not claim every earlier requested optional capability exists.

Application sources, Room schemas, records, build configuration, version information, existing APKs and signing material were not modified. Documentation and download indexes are the only repository changes for this task.

## Reproduce

```sh
python docs/learning/tools/build_book.py
python docs/learning/tools/verify_book.py
```

Use the documented Pandoc, font and Python requirements in [README.md](README.md). Inspect representative pages after any layout edit. Publishing verification is recorded separately after the files are available on GitHub.
