#!/usr/bin/env python3
"""
Парсер каналов Rutube для tv-app.

Запускается через GitHub Actions по расписанию (раз в неделю).
Все запросы к Rutube API идут С СЕРВЕРОВ GITHUB, не с IP пользователя.

Что делает:
1. Дёргает autowidget/2 — список всех каналов
2. Для каждого канала дёргает play/options/{id}
3. Собирает JSON со всем необходимым для приложения
4. Публикует в ветку gh-pages

Особенности:
- Умный retry: 404/403 не повторяет, 5xx/timeout повторяет
- Не фильтрует каналы с 404 (гео-блок GitHub) — оставляет для fallback в приложении
- Фильтрует только явно заблокированные (blocking_rule/player_stub)

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

MAX_RETRIES = 3
CHANNEL_DELAY = 0.15
RETRY_DELAY_BASE = 0.5

# Постоянные коды — не retry
PERMANENT_ERRORS = {400, 401, 403, 404, 410, 422}

# Счётчики для статистики
STATS = {"ok": 0, "404": 0, "403": 0, "5xx": 0, "timeout": 0, "other": 0}


# ---------- HTTP ----------

def http_get(url, params=None, timeout=15):
    """
    Умный retry:
    - 404/403/400 и т.п. → сразу raise (постоянные ошибки)
    - 5xx / timeout / network → retry до MAX_RETRIES
    """
    last_ex = None
    for attempt in range(MAX_RETRIES):
        try:
            r = requests.get(url, params=params, headers=HEADERS, timeout=timeout)
            if r.status_code in PERMANENT_ERRORS:
                raise requests.HTTPError(
                    f"{r.status_code} {r.reason}",
                    response=r
                )
            r.raise_for_status()
            return r
        except requests.HTTPError as e:
            status = e.response.status_code if e.response is not None else 0
            if status in PERMANENT_ERRORS:
                raise
            last_ex = e
            if attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_DELAY_BASE * (attempt + 1))
        except (requests.Timeout, requests.ConnectionError) as e:
            last_ex = e
            if attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_DELAY_BASE * (attempt + 1))
        except Exception as e:
            last_ex = e
            break
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
            "unavailable": False,
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

def enrich_channel(ch):
    try:
        url = f"{BASE}/api/play/options/{ch['id']}/"
        r = http_get(url)
        resp = r.json()
        STATS["ok"] += 1
    except requests.HTTPError as e:
        status = e.response.status_code if e.response is not None else 0
        if status == 404 or status == 410:
            STATS["404"] += 1
            ch["unavailable"] = True
        elif status == 403:
            STATS["403"] += 1
            ch["unavailable"] = True
        elif 500 <= status < 600:
            STATS["5xx"] += 1
        else:
            STATS["other"] += 1
        return
    except (requests.Timeout, requests.ConnectionError):
        STATS["timeout"] += 1
        return
    except Exception:
        STATS["other"] += 1
        return

    if is_blocked(resp):
        ch["blocked"] = True
        ch["blocked_reason"] = blocked_reason(resp)
        return

    author = resp.get("author")
    if isinstance(author, dict):
        ch["avatar"] = author.get("avatar_url") or ""

    live = resp.get("live_streams") or {}
    if isinstance(live, dict):
        hls = live.get("hls") or []
        if hls and isinstance(hls, list):
            first = hls[0]
            if isinstance(first, dict):
                ch["stream_url"] = first.get("url")


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
    print(f"→ Estimated time: ~{int(len(channels) * CHANNEL_DELAY)} sec + network\n")

    if not channels:
        print("FATAL: no channels parsed")
        sys.exit(1)

    t_start = time.time()
    for i, ch in enumerate(channels, 1):
        enrich_channel(ch)
        if i % 20 == 0 or i == len(channels):
            elapsed = time.time() - t_start
            eta = (elapsed / i) * (len(channels) - i)
            print(f"[{i}/{len(channels)}] elapsed={int(elapsed)}s eta={int(eta)}s "
                  f"| 404={STATS['404']} ok={STATS['ok']}")
        time.sleep(CHANNEL_DELAY)

    blocked_count = sum(1 for c in channels if c.get("blocked"))
    with_url_count = sum(1 for c in channels if c.get("stream_url"))
    unavailable_count = sum(1 for c in channels if c.get("unavailable"))
    no_url_count = len(channels) - blocked_count - with_url_count - unavailable_count

    print("\n" + "=" * 60)
    print("RESULT")
    print("=" * 60)
    print(f"Total channels:      {len(channels)}")
    print(f"✅ With stream_url:  {with_url_count}")
    print(f"🚫 Blocked:          {blocked_count}")
    print(f"❌ Unavailable(404): {unavailable_count}")
    print(f"⚠️  No URL:           {no_url_count}")
    print(f"⏱️  Total time:       {int(time.time() - t_start)}s")
    print()
    print("HTTP stats:")
    print(f"  ok      = {STATS['ok']}")
    print(f"  404     = {STATS['404']}")
    print(f"  403     = {STATS['403']}")
    print(f"  5xx     = {STATS['5xx']}")
    print(f"  timeout = {STATS['timeout']}")
    print(f"  other   = {STATS['other']}")

    # Оставляем ВСЕ каналы, кроме явно blocked (blocking_rule/player_stub).
    # 404 от GitHub IP — это гео-блок федеральных каналов для США.
    # Твой RU IP получит URL через fallback в приложении.
    final_channels = [
        c for c in channels
        if not c.get("blocked")
    ]

    print(f"\n→ Final channels in JSON: {len(final_channels)}")

    payload = {
        "updated": datetime.now(timezone.utc).isoformat(),
        "total": len(final_channels),
        "with_url": sum(1 for c in final_channels if c.get("stream_url")),
        "no_url": sum(1 for c in final_channels if not c.get("stream_url")),
        "channels": final_channels,
    }

    with open("channels.json", "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print("\n→ Saved channels.json")
    print(f"→ Done at {datetime.now(timezone.utc).isoformat()}")


if __name__ == "__main__":
    main()
