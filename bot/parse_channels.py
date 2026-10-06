#!/usr/bin/env python3
"""
Парсер каналов Rutube для tv-app.

Запускается через GitHub Actions по расписанию.
Результат — channels.json в ветке gh-pages, доступен через GitHub Pages.

Что делает:
1. Дёргает autowidget/2 — список всех каналов
2. Для каждого канала дёргает play/options/{id}
3. Собирает JSON со всем необходимым для приложения

Автор: gvineon550-coder
"""

import json
import time
import sys
from datetime import datetime, timezone

try:
    import requests
except ImportError:
    print("ERROR: requests not installed. Run: pip install requests")
    sys.exit(1)


UA = (
    "Mozilla/5.0 (Linux; Android TV) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Safari/537.36"
)
BASE = "https://rutube.ru"
HEADERS = {
    "User-Agent": UA,
    "Referer": "https://rutube.ru/",
    "Origin": "https://rutube.ru",
    "Accept": "*/*",
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
}

TITLE_PREFIX = "Прямой эфир"
CATEGORY_RENAMES = {
    "Все прямые эфиры": "Прямые эфиры",
    "Новости и СМИ": "Новости",
}


# ---------- HTTP ----------

def http_get(url, params=None, timeout=20):
    for attempt in range(3):
        try:
            r = requests.get(url, params=params, headers=HEADERS, timeout=timeout)
            r.raise_for_status()
            return r
        except Exception as e:
            if attempt < 2:
                print(f"  retry {attempt + 1}: {e}")
                time.sleep(0.5 * (attempt + 1))
            else:
                raise


# ---------- Парсинг каналов ----------

def clean_title(s):
    s = s.strip()
    if s.startswith(TITLE_PREFIX):
        rest = s[len(TITLE_PREFIX):].lstrip(" :;,-\u2013\u2014")
        if rest:
            return rest
    return s


def clean_category(s):
    return CATEGORY_RENAMES.get(s.strip(), s.strip())


def category_text(value):
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, dict):
        name = value.get("name")
        return str(name).strip() if name else ""
    return str(value).strip()


def description_text(value):
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    return str(value).strip()


def fetch_channels():
    url = f"{BASE}/api/feeds/autowidget/2"
    params = {
        "client": "wdp",
        "show_hidden_videos": "true",
        "show_user_hidden_videos": "true",
        "origin__type": "rst,rspa",
    }
    r = http_get(url, params=params)
    return r.json()


def parse_channels(data):
    out = []
    seen = set()

    def add(entry, group=None):
        if not entry:
            return
        cid = entry.get("id")
        if not cid or cid in seen:
            return
        title = (entry.get("title") or "").strip()
        if not title:
            return
        duration = entry.get("duration")
        if duration is not None and duration != 0:
            return
        if entry.get("origin_type") == "ifrm":
            return
        seen.add(cid)

        own_cat = category_text(entry.get("category")) or None
        base_group = own_cat or (group.strip() if group else None)
        final_group = clean_category(base_group) if base_group else None

        out.append({
            "id": cid,
            "title": clean_title(title),
            "description": description_text(entry.get("description")),
            "category": final_group,
            "avatar": "",
            "stream_url": None,
            "blocked": False,
            "blocked_reason": "",
        })

    results = data.get("results")
    if results:
        for e in results:
            childs = e.get("childs")
            if childs:
                for ch in childs:
                    add(ch, e.get("name"))
            else:
                add(e)
    else:
        feed = data.get("feed") or {}
        resources = feed.get("resources") or []
        if len(resources) > 1:
            items = resources[1].get("items") or []
            for it in items:
                add(it)

    return out


# ---------- Заблокированные ----------

def is_blocked(resp):
    t = resp.get("type")
    if t in ("blocking_rule", "player_stub"):
        return True
    detail = resp.get("detail")
    if isinstance(detail, dict):
        dt = detail.get("type")
        if dt in ("blocking_rule", "player_stub"):
            return True
        name = str(detail.get("name") or "")
        if name.startswith("blocking_rule") or name == "login_required":
            return True
    return False


def blocked_reason(resp):
    detail = resp.get("detail") or {}
    if not isinstance(detail, dict):
        detail = {}
    name = str(detail.get("name") or "")
    t = resp.get("type") or detail.get("type")
    if t == "blocking_rule" or name.startswith("blocking_rule"):
        return "правообладатель или VPN"
    if t == "player_stub" or name == "login_required":
        return "скрыто автором (нужен вход)"
    return "видео недоступно"


# ---------- play/options ----------

def fetch_play_options(channel_id):
    url = f"{BASE}/api/play/options/{channel_id}/"
    r = http_get(url)
    return r.json()


def enrich_channel(ch):
    try:
        resp = fetch_play_options(ch["id"])
    except Exception as e:
        print(f"  ✗ {ch['id']}: {e}")
        return ch

    if is_blocked(resp):
        ch["blocked"] = True
        ch["blocked_reason"] = blocked_reason(resp)
        return ch

    # avatar
    author = resp.get("author")
    if isinstance(author, dict):
        ch["avatar"] = author.get("avatar_url") or ""

    # master m3u8 URL — приложение само выберет качество
    live = resp.get("live_streams") or {}
    if isinstance(live, dict):
        hls = live.get("hls") or []
        if hls and isinstance(hls, list):
            first = hls[0]
            if isinstance(first, dict):
                ch["stream_url"] = first.get("url")

    return ch


# ---------- main ----------

def main():
    print("→ Fetching channel list...")
    try:
        data = fetch_channels()
    except Exception as e:
        print(f"FATAL: cannot fetch channels: {e}")
        sys.exit(1)

    channels = parse_channels(data)
    print(f"→ Got {len(channels)} channels")

    if not channels:
        print("FATAL: no channels parsed")
        sys.exit(1)

    enriched = []
    for i, ch in enumerate(channels, 1):
        print(f"[{i}/{len(channels)}] {ch['id']} — {ch['title']}")
        enriched.append(enrich_channel(ch))
        time.sleep(0.3)  # не флудим API

    # считаем статистику
    blocked = sum(1 for c in enriched if c.get("blocked"))
    with_url = sum(1 for c in enriched if c.get("stream_url"))
    print(f"→ Result: {len(enriched)} channels, "
          f"{blocked} blocked, {with_url} with stream_url")

    payload = {
        "updated": datetime.now(timezone.utc).isoformat(),
        "total": len(enriched),
        "channels": enriched,
    }

    with open("channels.json", "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print("→ Saved channels.json")


if __name__ == "__main__":
    main()
