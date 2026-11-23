import html
import json
import re
import sys
from pathlib import Path

BASE_URL = "https://kalorie-official.web.app"
LOCALES = {"sk": "sk_SK", "en": "en_US"}
ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "hosting" / "index.html"
STRINGS = ROOT / "landing" / "strings.json"


def replace_once(text, pattern, replacement, label):
    result, count = re.subn(pattern, replacement, text, count=1)
    if count != 1:
        sys.exit(f"build.py: {label} not found in {SOURCE.name}")
    return result


def build(source, lang, strings):
    page = source
    page = replace_once(page, r'<html lang="cs">', f'<html lang="{lang}">', "html lang")
    page = replace_once(page, r"<title>[^<]*</title>", f"<title>{html.escape(strings['title'])}</title>", "title")
    page = replace_once(page, r'(<meta name="description" content=")[^"]*', lambda m: m.group(1) + html.escape(strings["desc"]), "meta description")
    page = replace_once(page, r'(<meta property="og:description" content=")[^"]*', lambda m: m.group(1) + html.escape(strings["desc"]), "og:description")
    page = replace_once(page, r'(<meta property="og:locale" content=")[^"]*', lambda m: m.group(1) + LOCALES[lang], "og:locale")
    page = replace_once(page, r'(<meta property="og:url" content=")[^"]*', lambda m: m.group(1) + f"{BASE_URL}/{lang}/", "og:url")
    page = replace_once(page, r'(<link rel="canonical" href=")[^"]*', lambda m: m.group(1) + f"{BASE_URL}/{lang}/", "canonical")
    page = replace_once(page, r'("@type": "SoftwareApplication", "name": "Kalorie", "description": )"[^"]*"', lambda m: m.group(1) + json.dumps(strings["desc"], ensure_ascii=False), "structured data description")

    def text(match):
        key = match.group(2)
        if key not in strings:
            sys.exit(f"build.py: missing {lang} string '{key}'")
        return match.group(1) + html.escape(strings[key], quote=False) + "<"

    page, count = re.subn(r'(data-i18n="(\w+)"[^>]*>)[^<]*<', text, page)
    if count == 0:
        sys.exit("build.py: no data-i18n elements found")
    page = re.sub(r'aria-label="[^"]*"( data-i18n-aria="(\w+)")', lambda m: f'aria-label="{html.escape(strings[m.group(2)])}"{m.group(1)}', page)
    page = re.sub(r"/img/(ios|android)-cs\.webp", rf"/img/\1-{lang}.webp", page)
    page = re.sub(r'href="/(get|privacy|delete-account|contact)#cs"', rf'href="/\1#{lang}"', page)
    return page


def main():
    source = SOURCE.read_text(encoding="utf-8")
    translations = json.loads(STRINGS.read_text(encoding="utf-8"))
    for lang in LOCALES:
        target = ROOT / "hosting" / lang / "index.html"
        target.parent.mkdir(exist_ok=True)
        target.write_text(build(source, lang, translations[lang]), encoding="utf-8")


if __name__ == "__main__":
    main()
