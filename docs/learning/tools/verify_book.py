#!/usr/bin/env python3
"""Check generated learning artifacts; these are documentation checks, not app tests."""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import zipfile
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import fitz
from docx import Document
from playwright.sync_api import sync_playwright

from build_book import ROOT, DOCS, OUT, BASE, SNAPSHOT, DESCRIPTIONS


def main():
    result = {"edition": "2026-10-21", "source_commit": SNAPSHOT, "checks": {}}
    checks = result["checks"]
    stats = json.loads((DOCS / "BUILD_STATS.json").read_text())
    source = (DOCS / "BOOK.md").read_text()
    pdf = fitz.open(OUT / f"{BASE}.pdf")
    assert len(pdf) == stats["pdf_pages"]
    text = " ".join(page.get_text() for page in pdf)
    normalized = re.sub(r"\s+", " ", text)
    headings = re.findall(r"^# ((?:\d+\.|Appendix [A-Z]\.).+)$", source, flags=re.M)
    for heading in headings:
        assert heading in normalized, heading
    assert all(len(page.get_text().strip()) > 50 for page in pdf)
    overflow = [(i + 1, b[:4]) for i, page in enumerate(pdf) for b in page.get_text("blocks")
                if b[0] < -1 or b[1] < -1 or b[2] > page.rect.width + 1 or b[3] > page.rect.height + 1]
    assert not overflow, overflow
    assert len([item for item in pdf.get_toc() if item[0] == 1]) == len(headings)
    assert sum(len(page.get_links()) for page in pdf) > 150
    checks["pdf"] = {"pages": len(pdf), "top_level_sections": len(headings), "blank_pages": 0,
                     "out_of_page_blocks": 0, "bookmarks_and_links": "pass"}

    paths = set(subprocess.check_output(["git", "ls-tree", "-r", "--name-only", SNAPSHOT], cwd=ROOT, text=True).splitlines())
    links = re.findall(r"https://github.com/Piccol1001/ChatgptFuelConsumption/blob/" + SNAPSHOT + r"/([^\s)]+)", source)
    for link in links:
        path, _, line = link.partition("#L")
        assert path in paths, path
        if line:
            content = subprocess.check_output(["git", "show", f"{SNAPSHOT}:{path}"], cwd=ROOT)
            assert 1 <= int(line) <= len(content.splitlines())
    checks["pinned_source_links"] = {"verified": len(links), "broken": 0}

    with zipfile.ZipFile(OUT / "Source_Companion.zip") as archive:
        assert archive.testzip() is None
        manifest = json.loads(archive.read("SOURCE_MANIFEST.json"))
        assert len(manifest["files"]) == stats["source_files"]
        for item in manifest["files"]:
            data = archive.read("source/" + item["path"])
            assert len(data) == item["bytes"]
            assert hashlib.sha256(data).hexdigest() == item["sha256"]
            expected = subprocess.check_output(["git", "show", f'{SNAPSHOT}:{item["path"]}'], cwd=ROOT)
            assert data == expected
            assert b"-----BEGIN PRIVATE KEY-----" not in data
            assert b"-----BEGIN RSA PRIVATE KEY-----" not in data
        assert not any(re.search(r"(?i)\.(?:apk|jks|keystore|p12|db)$", n) for n in archive.namelist())
    checks["source_archive"] = {"exact_git_files": len(manifest["files"]),
                                "production_kotlin_files": len(DESCRIPTIONS), "integrity": "pass"}

    with zipfile.ZipFile(OUT / "Fuel_Expense_Tracker_Complete_Learning_Pack.zip") as archive:
        assert archive.testzip() is None
        assert len([x for x in archive.namelist() if x.startswith("assets/")]) == 5
        for ext in ("pdf", "docx", "html", "md"):
            assert archive.read(f"{BASE}.{ext}") == (OUT / f"{BASE}.{ext}").read_bytes()
    for line in (OUT / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split("  ", 1)
        assert hashlib.sha256((OUT / name).read_bytes()).hexdigest() == digest
    checks["package_and_checksums"] = "pass"

    docx = Document(OUT / f"{BASE}.docx")
    assert len([p for p in docx.paragraphs if p.style.name == "Heading 1"]) == len(headings)
    assert len(docx.inline_shapes) == 5
    assert all(s.width <= docx.sections[0].page_width - docx.sections[0].left_margin - docx.sections[0].right_margin
               for s in docx.inline_shapes)
    checks["editable_docx"] = {"top_level_sections": len(headings), "embedded_images": 5,
                                "structure": "pass", "word_processor_visual_check": "not performed"}

    # Independent worked-answer arithmetic, not a reimplementation of app coverage.
    assert Decimal(600) / 50 == 12
    assert Decimal(350) / 25 == 14
    litres = Decimal(220) * Decimal("7.5") / 100
    assert (litres * Decimal("2.05")).quantize(Decimal(".01"), rounding=ROUND_HALF_UP) + 8 + 6 + 5 == Decimal("52.83")
    assert litres * Decimal("2.10") + 8 + 6 + 5 == Decimal("53.65")
    assert sum([20, 30, 15, 36]) == 101
    assert [Decimal(101) * x / 100 for x in [20, 30, 15, 35]] == [Decimal("20.2"), Decimal("30.3"), Decimal("15.15"), Decimal("35.35")]
    assert 400000 - 250000 - 70000 == 80000
    assert 125050 / Decimal(100) == Decimal("1250.50")
    checks["worked_answers"] = "fuel, journey, allocation, allowance and sen examples pass"

    browser_checks = []
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path="/usr/bin/chromium", headless=True,
                                    args=["--no-sandbox", "--disable-dev-shm-usage"])
        for width, height in [(390, 844), (1280, 900)]:
            page = browser.new_page(viewport={"width": width, "height": height})
            errors, requests = [], []
            page.on("pageerror", lambda e: errors.append(str(e)))
            def deny_external(route):
                requests.append(route.request.url)
                route.abort()
            page.route("http://**/*", deny_external)
            page.route("https://**/*", deny_external)
            # The managed browser blocks file://. Render exact self-contained bytes instead.
            page.set_content((OUT / f"{BASE}.html").read_text(), wait_until="load")
            assert page.locator("#TOC a").count() > 100
            assert page.locator("img").count() == 5
            assert page.locator("img").evaluate_all("xs => xs.every(x => x.complete && x.naturalWidth > 0)")
            broken = page.locator('a[href^="#"]').evaluate_all("xs => xs.filter(a => !document.getElementById(decodeURIComponent(a.hash.slice(1)))).map(a => a.hash)")
            assert not broken, broken
            assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), "guide overflow"
            page.screenshot(path=f"/tmp/learning-html-{width}.png", full_page=False)
            page.set_content((OUT / "Source_Browser.html").read_text(), wait_until="load")
            page.locator("#q").fill("MonthlyPlanner")
            visible = page.locator("details:not([hidden])")
            assert visible.count() >= 2
            assert visible.evaluate_all("xs => xs.every(x => x.dataset.path.includes('monthlyplanner'))")
            visible.first.locator("summary").click()
            assert visible.first.get_attribute("open") is not None
            assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), "source browser overflow"
            assert not errors, errors
            assert not requests, requests
            browser_checks.append({"viewport": f"{width}x{height}", "offline_images_and_toc": "pass",
                                   "source_filter_and_expand": "pass", "external_resource_requests": 0,
                                   "javascript_errors": 0, "horizontal_document_overflow": False,
                                   "harness": "exact HTML via set_content; managed browser blocks file://"})
            page.close()
        browser.close()
    checks["offline_browser"] = browser_checks
    result["scope"] = "Documentation validation only. No Android app tests rerun; historical evidence is labelled in the book."
    (DOCS / "VALIDATION.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
