#!/usr/bin/env python3
"""
Builds the free lists used by Simple TV's "Chaînes gratuites" option.

Sources, all published for free use:
  * Free-TV/IPTV  - channels officially provided for free (kept first when a channel appears twice)
  * iptv-org      - publicly available streams; NSFW, closed and blocklisted (DMCA) channels are removed
  * Internet Archive "feature_films" - public-domain films, in French or Arabic

Only French- and Arabic-language channels are kept. Output (in the folder given as argument):
  live.m3u     channels grouped as "Français · Info", "Arabe · Sport", …
  movies.json  public-domain films with poster, summary and video address
  report.txt   counts per category, to check each run

Usage: free_lists.py OUT_DIR FREE_TV_DIR IPTV_ORG_DIR IPTV_DB_DIR
"""
import csv
import html
import json
import re
import sys
import urllib.parse
import urllib.request
from collections import OrderedDict, defaultdict
from pathlib import Path

out_dir, freetv_dir, iptv_dir, db_dir = (Path(p) for p in sys.argv[1:5])

LANGS = {"fra": "Français", "ara": "Arabe"}

# Free-TV playlists and the language their channels are in when iptv-org does not say.
FREETV_FILES = {
    "playlist_france.m3u8": "fra",
    "playlist_belgium.m3u8": None,
    "playlist_switzerland.m3u8": None,
    "playlist_canada.m3u8": None,
    "playlist_luxembourg.m3u8": None,
    "playlist_monaco.m3u8": "fra",
    "playlist_chad.m3u8": None,
    "playlist_egypt.m3u8": "ara",
    "playlist_iraq.m3u8": "ara",
    "playlist_lebanon.m3u8": "ara",
    "playlist_qatar.m3u8": "ara",
    "playlist_saudi_arabia.m3u8": "ara",
    "playlist_united_arab_emirates.m3u8": "ara",
    "playlist_zz_news_ar.m3u8": "ara",
    "playlist_zz_documentaries_ar.m3u8": "ara",
}

# iptv-org categories folded into a few simple groups, in display order.
GROUPS = [
    ("Généralistes", {"general", "public"}),
    ("Info", {"news", "business", "weather", "legislative"}),
    ("Sport", {"sports", "outdoor", "auto"}),
    ("Films & séries", {"movies", "series", "classic", "comedy"}),
    ("Jeunesse", {"kids", "animation", "family"}),
    ("Découverte", {"documentary", "science", "education", "culture", "travel"}),
    ("Divertissement", {"entertainment", "lifestyle", "cooking", "relax", "interactive", "woman"}),
    ("Musique", {"music"}),
    ("Religion", {"religious"}),
]
EXCLUDED_CATEGORIES = {"xxx", "shop"}
# Pay channels: a free stream of these is never an authorised one, so they are always left out.
PAY_CHANNELS = re.compile(
    r"paris premi|canal\s*\+|canal plus|bein|rmc sport|\bocs\b|cin[ée]\s*\+|eurosport|ssc\s*sport|"
    r"\bosn\b|shahid|starz|dazn|l'?[ée]quipe live|multisports|golf\s*\+|infosport",
    re.I,
)
OTHER = "Autres"
GROUP_ORDER = [g for g, _ in GROUPS] + [OTHER]


def group_for(categories):
    for name, cats in GROUPS:
        if cats & categories:
            return name
    return OTHER


# ---- iptv-org database --------------------------------------------------------------------

def read_csv(name):
    with open(db_dir / "data" / name, encoding="utf-8") as f:
        return list(csv.DictReader(f))


channels = {c["id"]: c for c in read_csv("channels.csv")}
blocked = {b["channel"] for b in read_csv("blocklist.csv")}
feeds = defaultdict(dict)  # channel -> feed id -> languages
main_feed = {}
for f in read_csv("feeds.csv"):
    langs = set(filter(None, f["languages"].split(";")))
    feeds[f["channel"]][f["id"]] = langs
    if f["is_main"].upper() == "TRUE":
        main_feed[f["channel"]] = f["id"]
logos = {}
for row in read_csv("logos.csv"):
    if row["channel"] not in logos and not row.get("feed"):
        logos[row["channel"]] = row["url"]


def channel_info(tvg_id):
    """Returns (channel id, languages, categories, usable) for an iptv-org id such as 'TF1.fr@SD'."""
    if not tvg_id:
        return None, set(), set(), True
    cid, _, feed = tvg_id.partition("@")
    c = channels.get(cid)
    if c is None:
        return None, set(), set(), True
    langs = feeds.get(cid, {}).get(feed or main_feed.get(cid, ""), set())
    if not langs:
        langs = set().union(*feeds.get(cid, {}).values()) if feeds.get(cid) else set()
    cats = set(filter(None, c["categories"].split(";")))
    usable = (
        c["is_nsfw"].upper() != "TRUE"
        and not c["closed"]
        and cid not in blocked
        and not (cats & EXCLUDED_CATEGORIES)
    )
    return cid, langs, cats, usable


# ---- M3U reading ---------------------------------------------------------------------------

ATTR = re.compile(r'([\w-]+)="([^"]*)"')
PLAYABLE = re.compile(r"\.(m3u8|ts|mp4)(\?|$)|/hls/|/live/|manifest", re.I)
NOT_STREAMS = ("youtube.com", "youtu.be", "twitch.tv", "dailymotion.com/video", "facebook.com")


def read_m3u(path):
    entry = None
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if line.startswith("#EXTINF"):
            attrs = {k.lower(): v for k, v in ATTR.findall(line)}
            # The name follows the first comma outside quotes.
            name, quoted = "", False
            for i, ch in enumerate(line):
                if ch == '"':
                    quoted = not quoted
                elif ch == "," and not quoted:
                    name = line[i + 1:].strip()
                    break
            entry = {"name": name, "attrs": attrs, "ua": attrs.get("user-agent"), "ref": attrs.get("http-referrer")}
        elif line.startswith("#EXTVLCOPT:") and entry is not None:
            key, _, value = line[11:].partition("=")
            if key.strip().lower() == "http-user-agent":
                entry["ua"] = value.strip()
            elif key.strip().lower() in ("http-referrer", "http-referer"):
                entry["ref"] = value.strip()
        elif line and not line.startswith("#") and entry is not None:
            entry["url"] = line
            yield entry
            entry = None


def clean_name(name):
    name = re.sub(r"\s*\(\d{3,4}[pi]\)", "", name)  # (720p)
    name = re.sub(r"\s*\[[^\]]*\]", "", name)  # [Geo-blocked]
    name = re.sub(r"[ⒼⓎⒹⓉⓈⓂ]", "", name)  # Free-TV markers
    return re.sub(r"\s+", " ", name).strip()


def key_of(name):
    k = name.lower()
    k = re.sub(r"\b(hd|sd|fhd|uhd|4k|tv|live|en direct)\b", "", k)
    return re.sub(r"[^a-z0-9؀-ۿ]+", "", k)


# ---- Merge ---------------------------------------------------------------------------------

merged = OrderedDict()  # (lang, key) -> channel


def add(entry, default_lang, source):
    url = entry["url"]
    if any(bad in url for bad in NOT_STREAMS) or not PLAYABLE.search(url):
        return
    cid, langs, cats, usable = channel_info(entry["attrs"].get("tvg-id", ""))
    if not usable:
        return
    chosen = [l for l in LANGS if l in langs] or ([default_lang] if default_lang and not langs else [])
    if not chosen:
        return
    lang = chosen[0]
    name = clean_name(entry["name"]) or clean_name(entry["attrs"].get("tvg-name", ""))
    if not name or PAY_CHANNELS.search(name):
        return
    key = (lang, cid or key_of(name))
    ch = merged.get(key)
    if ch is None:
        ch = merged[key] = {
            "name": name,
            "lang": lang,
            "group": group_for(cats),
            "logo": entry["attrs"].get("tvg-logo") or (logos.get(cid) if cid else None),
            "urls": [],
            "ua": entry.get("ua"),
            "ref": entry.get("ref"),
            "sources": set(),
        }
    if url not in ch["urls"]:
        ch["urls"].append(url)
    ch["sources"].add(source)


for file, lang in FREETV_FILES.items():
    p = freetv_dir / "playlists" / file
    if p.exists():
        for e in read_m3u(p):
            add(e, lang, "Free-TV")

for p in sorted((iptv_dir / "streams").glob("*.m3u")):
    for e in read_m3u(p):
        add(e, None, "iptv-org")

# ---- Write live.m3u ------------------------------------------------------------------------

out_dir.mkdir(parents=True, exist_ok=True)
order = list(LANGS)
channels_out = sorted(
    merged.values(),
    key=lambda c: (order.index(c["lang"]), GROUP_ORDER.index(c["group"]), c["name"].lower()),
)


def attr(v):
    return (v or "").replace('"', "'")


lines = ["#EXTM3U"]
counts = OrderedDict()
for c in channels_out:
    group = f'{LANGS[c["lang"]]} · {c["group"]}'
    counts[group] = counts.get(group, 0) + 1
    extra = ""
    if c["logo"]:
        extra += f' tvg-logo="{attr(c["logo"])}"'
    if len(c["urls"]) > 1:
        extra += f' backup-urls="{attr("|".join(c["urls"][1:4]))}"'
    if c["ua"]:
        extra += f' user-agent="{attr(c["ua"])}"'
    if c["ref"]:
        extra += f' http-referrer="{attr(c["ref"])}"'
    lines.append(f'#EXTINF:-1 group-title="{group}"{extra},{c["name"]}')
    lines.append(c["urls"][0])
(out_dir / "live.m3u").write_text("\n".join(lines) + "\n", encoding="utf-8")

# ---- Public-domain films from the Internet Archive -----------------------------------------

ARCHIVE_LANGS = {
    "fra": "language:(French OR fre OR fra OR Français OR francais)",
    "ara": "language:(Arabic OR ara OR عربي OR العربية)",
}
VIDEO_FORMATS = ["h.264", "h.264 IA", "MPEG4", "512Kb MPEG4"]
# Only films whose page states a public-domain mark or a Creative Commons licence: the
# "feature_films" collection also holds uploads that are not free of rights.
FREE_LICENCE = re.compile(r"creativecommons\.org/(publicdomain|licenses)/", re.I)
ADULT = re.compile(r"erot|sex|porn|xxx|adult|nud|libertin|x-rated|perver|uncensored", re.I)



def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "SimpleTV-lists/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def strip_html(s):
    s = re.sub(r"<[^>]+>", " ", s or "")
    return re.sub(r"\s+", " ", html.unescape(s)).strip()


movies = []
archive_error = None


def first(v):
    return (v[0] if v else "") if isinstance(v, list) else (v or "")


def is_free(licence, year):
    """Public-domain marks are trusted for films made before 1930, Creative Commons licences for films
    made since 1990: in between, many uploads carry a free licence they do not actually have."""
    if not FREE_LICENCE.search(licence):
        return False
    if not year.isdigit():
        return False
    if "publicdomain" in licence.lower():
        return int(year) < 1930
    # Old classics are often re-uploaded with a Creative Commons tag they do not have;
    # recent films with such a licence are usually posted by their own makers.
    return int(year) >= 1990


try:
    for lang, lang_query in ARCHIVE_LANGS.items():
        q = f"collection:(feature_films) AND mediatype:(movies) AND {lang_query}"
        params = urllib.parse.urlencode(
            [("q", q), ("fl[]", "identifier"), ("fl[]", "title"), ("fl[]", "year"), ("fl[]", "licenseurl"),
             ("fl[]", "subject"), ("sort[]", "downloads desc"), ("rows", "1500"), ("output", "json")]
        )
        docs = get_json(f"https://archive.org/advancedsearch.php?{params}")["response"]["docs"]
        for d in docs:
            ident = d["identifier"]
            year = str(first(d.get("year")))[:4]
            text = f"{first(d.get('title'))} {' '.join(d.get('subject') or []) if isinstance(d.get('subject'), list) else d.get('subject', '')} {ident}"
            if not is_free(str(first(d.get("licenseurl"))), year) or ADULT.search(text):
                continue
            try:
                meta = get_json(f"https://archive.org/metadata/{urllib.parse.quote(ident)}")
            except Exception:
                continue
            files = meta.get("files", [])
            video = None
            for fmt in VIDEO_FORMATS:
                video = next((f for f in files if f.get("format") == fmt and f["name"].lower().endswith(".mp4")), None)
                if video:
                    break
            if not video:
                continue
            m = meta.get("metadata", {})
            licence = str(first(m.get("licenseurl")))
            if not is_free(licence, year) or ADULT.search(f"{first(m.get('title'))} {m.get('subject', '')}"):
                continue
            title = first(m.get("title")) or first(d.get("title")) or ident
            desc = m.get("description", "")
            if isinstance(desc, list):
                desc = " ".join(desc)
            runtime = first(m.get("runtime"))
            movies.append({
                "id": ident,
                "title": strip_html(str(title))[:120],
                "year": year,
                "plot": strip_html(desc)[:700],
                "duration": runtime if isinstance(runtime, str) else "",
                "poster": f"https://archive.org/services/img/{ident}",
                "url": f"https://archive.org/download/{urllib.parse.quote(ident)}/{urllib.parse.quote(video['name'])}",
                "category": f"{LANGS[lang]} · Films libres de droits",
                "licence": licence,
            })
except Exception as e:  # keep the live list even if the Archive is unreachable
    archive_error = f"{type(e).__name__}: {e}"

# A failed run keeps the previous films; a successful one replaces them, even with an empty list.
if archive_error is None or not (out_dir / "movies.json").exists():
    (out_dir / "movies.json").write_text(json.dumps(movies, ensure_ascii=False, indent=1), encoding="utf-8")

# ---- Report --------------------------------------------------------------------------------

report = [f"Chaînes : {len(channels_out)}"]
report += [f"  {g} : {n}" for g, n in counts.items()]
both = sum(1 for c in channels_out if len(c["sources"]) > 1)
report.append(f"  présentes dans les deux sources : {both}")
report.append(f"Films libres de droits : {len(movies)}")
for cat in sorted({m['category'] for m in movies}):
    report.append(f"  {cat} : {sum(1 for m in movies if m['category'] == cat)}")
if archive_error:
    report.append(f"Internet Archive indisponible : {archive_error}")
(out_dir / "report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
print("\n".join(report))
