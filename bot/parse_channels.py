#!/usr/bin/env python3
"""
Парсер каналов Rutube для tv-app.

Запускается через GitHub Actions по расписанию.
Все запросы к Rutube API идут С СЕРВЕРОВ GITHUB, не с IP пользователя.

Что делает:
1. Дёргает autowidget/2 — список всех каналов
2. Для каждого канала дёргает play/options/{id}
3. Второй проход для каналов без URL
4. Сохраняет JSON со всем необходимым для приложения

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

# Сколько попыток на один запрос
MAX_RETRIES = 5
# Пауза между каналами (сек)
CHANNEL_DELAY = 0.5
# Пауза между попытками (сек), умножается на номер попытки
RETRY_DELAY_BASE = 1.0


# ---------- HTTP ----------

def http_get(url, params=None, timeout=30):
    last_ex = None
    for attempt in range(MAX_RETRIES):
        try:
            r = requests.get(url, params=params, headers=HEADERS, timeout=timeout)
            r.raise_for_status()
            return r
        except Exception as e:
            last_ex = e
            if attempt < MAX_RETRIES - 1:
                delay = RETRY_DELAY_BASE * (attempt + 1)
                print(f"  retry {attempt + 1}/{MAX_RETRIES - 1}: {e} (wait {delay}s)")
                time.sleep(delay)
    raise last_ex


# ---------- Утилиты ----------

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


# ---------- Парсинг каналов ----------

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
    """
    Дополняет канал данными из play/options.
    Возвращает True если удалось получить stream_url или определить blocked.
    Изменяет ch in-place.
    """
    try:
        resp = fetch_play_options(ch["id"])
    except Exception as e:
        print(f"  ✗ {ch['id']}: {e}")
        return False

    if is_blocked(resp):
        ch["blocked"] = True
        ch["blocked_reason"] = blocked_reason(resp)
        return True

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
                return True

    # URL нет — канал существует, но live не отдаётся
    return False


# ---------- main ----------

def main():
    print("=" * 60)
    print("TV App — Rutube Channels Parser")
    print(f"Started: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 60)

    print("\n→ Fetching channel list...")
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

    print("\n--- First pass ---")
    for i, ch in enumerate(channels, 1):
        print(f"[{i}/{len(channels)}] {ch['id']} — {ch['title']}")
        enrich_channel(ch)
        time.sleep(CHANNEL_DELAY)

    # Второй проход — для каналов, где не удалось получить URL и не blocked
    retry_pool = [
        c for c in channels
        if not c.get("stream_url") and not c.get("blocked")
    ]

    if retry_pool:
        print(f"\n--- Second pass: {len(retry_pool)} channels without URL ---")
        time.sleep(5)  # даём Rutube передохнуть
        for i, ch in enumerate(retry_pool, 1):
            print(f"[retry {i}/{len(retry_pool)}] {ch['id']} — {ch['title']}")
            enrich_channel(ch)
            time.sleep(1.0)

    # Статистика
    blocked_count = sum(1 for c in channels if c.get("blocked"))
    with_url_count = sum(1 for c in channels if c.get("stream_url"))
    no_url_count = len(channels) - blocked_count - with_url_count

    print("\n" + "=" * 60)
    print("RESULT")
    print("=" * 60)
    print(f"Total channels:    {len(channels)}")
    print(f"✅ With stream_url: {with_url_count}")
    print(f"🚫 Blocked:        {blocked_count}")
    print(f"⚠️  No URL:         {no_url_count}")

    if no_url_count > 0:
        print(f"\nChannels without URL (первые 10):")
        for c in channels:
            if not c.get("stream_url") and not c.get("blocked"):
                print(f"  - {c['id']} — {c['title']}")
                no_url_count -= 1
                if no_url_count <= -10:
                    break

    print()

    payload = {
        "updated": datetime.now(timezone.utc).isoformat(),
        "total": len(channels),
        "with_url": with_url_count,
        "blocked": blocked_count,
        "no_url": len(channels) - blocked_count - with_url_count,
        "channels": channels,
    }

    with open("channels.json", "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print("→ Saved channels.json")
    print(f"→ Done at {datetime.now(timezone.utc).isoformat()}")


if __name__ == "__main__":
    main()
