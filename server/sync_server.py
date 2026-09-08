#!/usr/bin/env python3
"""
Vixz Cross-Device Sync Server
=============================
A lightweight, zero-external-dependency synchronization backend for Vixz (Windows Desktop & Android).
Synchronizes watched YouTube video IDs, watch progress, favorites, and algorithm preferences
so you never watch videos twice across your PC and Phone.

Runs on Python 3.8+ with standard library only (no pip dependencies required).
Supports optional API key token authentication.
"""

import sys
import os
import json
import sqlite3
import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from datetime import datetime, timezone

DB_PATH = os.environ.get("SYNC_DB_PATH", "sync.db")
DEFAULT_PORT = int(os.environ.get("PORT", 8088))
DEFAULT_TOKEN = os.environ.get("SYNC_TOKEN", "")

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    with get_db() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS watched_videos (
                video_id TEXT PRIMARY KEY,
                title TEXT,
                channel TEXT,
                duration TEXT,
                thumbnail TEXT,
                position_seconds INTEGER DEFAULT 0,
                watched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        # Safe column migrations if existing database has older schema
        try:
            conn.execute("ALTER TABLE watched_videos ADD COLUMN duration TEXT")
        except Exception:
            pass
        try:
            conn.execute("ALTER TABLE watched_videos ADD COLUMN thumbnail TEXT")
        except Exception:
            pass

        conn.execute("""
            CREATE TABLE IF NOT EXISTS favorites (
                video_id TEXT PRIMARY KEY,
                title TEXT,
                channel TEXT,
                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS disliked_videos (
                video_id TEXT PRIMARY KEY,
                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS disliked_channels (
                channel_name TEXT PRIMARY KEY,
                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS subscribed_channels (
                channel_name TEXT PRIMARY KEY,
                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS algorithm_settings (
                key TEXT PRIMARY KEY,
                value TEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.commit()

class SyncRequestHandler(BaseHTTPRequestHandler):
    token = DEFAULT_TOKEN

    def _set_headers(self, status=200, content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", f"{content_type}; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key")
        self.end_headers()

    def do_OPTIONS(self):
        self._set_headers(204)

    def _authenticate(self) -> bool:
        if not self.token:
            return True
        # Check X-API-Key header
        x_api_key = self.headers.get("X-API-Key", "").strip()
        if x_api_key and x_api_key == self.token.strip():
            return True
        # Check Authorization header (Bearer)
        auth = self.headers.get("Authorization", "")
        if auth.startswith("Bearer "):
            return auth[7:].strip() == self.token.strip()
        # Check URL query param
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        if "token" in qs and qs["token"][0] == self.token:
            return True
        return False

    def _build_sync_payload(self, conn):
        watched_rows = conn.execute("""
            SELECT video_id, title, channel, duration, thumbnail, position_seconds, watched_at 
            FROM watched_videos 
            ORDER BY watched_at DESC 
            LIMIT 500
        """).fetchall()
        fav_rows = conn.execute("SELECT video_id, title, channel, added_at FROM favorites ORDER BY added_at DESC").fetchall()
        disliked_rows = conn.execute("SELECT video_id FROM disliked_videos").fetchall()
        disliked_channel_rows = conn.execute("SELECT channel_name FROM disliked_channels").fetchall()

        watched_list = [
            {
                "video_id": r["video_id"],
                "id": r["video_id"],
                "videoId": r["video_id"],
                "title": r["title"] or "",
                "channel": r["channel"] or "",
                "duration": r["duration"] or "",
                "thumbnail": r["thumbnail"] or "",
                "position": r["position_seconds"] or 0,
                "positionSeconds": r["position_seconds"] or 0,
                "watched_at": r["watched_at"],
                "watchedAt": r["watched_at"]
            } for r in watched_rows
        ]

        favorites_list = [
            {
                "video_id": r["video_id"],
                "id": r["video_id"],
                "title": r["title"] or "",
                "channel": r["channel"] or "",
                "added_at": r["added_at"]
            } for r in fav_rows
        ]

        favorite_ids = [r["video_id"] for r in fav_rows]
        disliked_ids = [r["video_id"] for r in disliked_rows]
        disliked_channels = [r["channel_name"] for r in disliked_channel_rows]

        sub_rows = conn.execute("SELECT channel_name FROM subscribed_channels ORDER BY added_at DESC").fetchall()
        subscribed_list = [r["channel_name"] for r in sub_rows]

        return {
            "success": True,
            "watched": watched_list,
            "watchedVideos": watched_list,
            "watchedIds": [r["video_id"] for r in watched_rows],
            "favorites": favorite_ids,
            "favoriteDetails": favorites_list,
            "disliked_videos": disliked_ids,
            "dislikedIds": disliked_ids,
            "disliked_channels": disliked_channels,
            "dislikedChannels": disliked_channels,
            "subscribed_channels": subscribed_list,
            "subscribedChannels": subscribed_list,
            "syncedAt": datetime.now(timezone.utc).isoformat()
        }

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")

        if path == "" or path == "/health":
            with get_db() as conn:
                count_watched = conn.execute("SELECT COUNT(*) FROM watched_videos").fetchone()[0]
                count_fav = conn.execute("SELECT COUNT(*) FROM favorites").fetchone()[0]
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "status": "ok",
                "service": "Vixz Cross-Device Sync Server",
                "version": "1.1.0",
                "totalWatched": count_watched,
                "totalFavorites": count_fav,
                "serverTime": datetime.now(timezone.utc).isoformat()
            }).encode("utf-8"))
            return

        if not self._authenticate():
            self._set_headers(401)
            self.wfile.write(json.dumps({"error": "Unauthorized. Please provide a valid Bearer token or X-API-Key header."}).encode("utf-8"))
            return

        if path == "/api/v1/sync":
            with get_db() as conn:
                payload = self._build_sync_payload(conn)
            self._set_headers(200)
            self.wfile.write(json.dumps(payload).encode("utf-8"))
            return

        self._set_headers(404)
        self.wfile.write(json.dumps({"error": "Not Found"}).encode("utf-8"))

    def do_POST(self):
        if not self._authenticate():
            self._set_headers(401)
            self.wfile.write(json.dumps({"error": "Unauthorized"}).encode("utf-8"))
            return

        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")
        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8") if content_len > 0 else "{}"

        try:
            req_data = json.loads(body)
        except Exception:
            req_data = {}

        if path == "/api/v1/sync/watched":
            vid = (req_data.get("video_id") or req_data.get("id") or 
                   req_data.get("videoId") or req_data.get("youtubeId"))
            if not vid:
                self._set_headers(400)
                self.wfile.write(json.dumps({"error": "Missing video id"}).encode("utf-8"))
                return

            title = req_data.get("title", "")
            channel = req_data.get("channel", "") or req_data.get("channelTitle", "") or req_data.get("channelName", "")
            duration = req_data.get("duration", "") or req_data.get("durationText", "")
            thumbnail = req_data.get("thumbnail", "") or req_data.get("thumbnailUrl", "")
            pos = int(req_data.get("position", req_data.get("positionSeconds", 0)))

            with get_db() as conn:
                conn.execute("""
                    INSERT INTO watched_videos (video_id, title, channel, duration, thumbnail, position_seconds, watched_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(video_id) DO UPDATE SET
                        title = CASE WHEN excluded.title != '' THEN excluded.title ELSE watched_videos.title END,
                        channel = CASE WHEN excluded.channel != '' THEN excluded.channel ELSE watched_videos.channel END,
                        duration = CASE WHEN excluded.duration != '' THEN excluded.duration ELSE watched_videos.duration END,
                        thumbnail = CASE WHEN excluded.thumbnail != '' THEN excluded.thumbnail ELSE watched_videos.thumbnail END,
                        position_seconds = excluded.position_seconds,
                        watched_at = CURRENT_TIMESTAMP
                """, (str(vid), title, channel, duration, thumbnail, pos))
                conn.commit()

            self._set_headers(200)
            self.wfile.write(json.dumps({"success": True, "videoId": str(vid), "synced": True}).encode("utf-8"))
            return

        if path == "/api/v1/sync/full":
            client_watched = req_data.get("watched") or req_data.get("watchedVideos") or []
            client_disliked = req_data.get("disliked_videos") or req_data.get("dislikedVideos") or req_data.get("dislikedIds") or []
            client_favorites = req_data.get("favorites") or []
            client_disliked_channels = req_data.get("disliked_channels") or req_data.get("dislikedChannels") or []

            with get_db() as conn:
                for w in client_watched:
                    if isinstance(w, dict):
                        w_id = w.get("video_id") or w.get("id") or w.get("videoId") or w.get("youtubeId")
                        if w_id:
                            pos = int(w.get("position", w.get("positionSeconds", 0)))
                            title = w.get("title", "")
                            channel = w.get("channel", "") or w.get("channelTitle", "")
                            dur = w.get("duration", "") or w.get("durationText", "")
                            thumb = w.get("thumbnail", "") or w.get("thumbnailUrl", "")
                            conn.execute("""
                                INSERT INTO watched_videos (video_id, title, channel, duration, thumbnail, position_seconds, watched_at)
                                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                                ON CONFLICT(video_id) DO UPDATE SET
                                    position_seconds = CASE WHEN excluded.position_seconds > 0 THEN excluded.position_seconds ELSE watched_videos.position_seconds END,
                                    watched_at = CURRENT_TIMESTAMP
                            """, (str(w_id), title, channel, dur, thumb, pos))
                    elif isinstance(w, str) and w.strip():
                        conn.execute("""
                            INSERT INTO watched_videos (video_id, title, channel, duration, thumbnail, position_seconds, watched_at)
                            VALUES (?, '', '', '', '', 0, CURRENT_TIMESTAMP)
                            ON CONFLICT(video_id) DO NOTHING
                        """, (w.strip(),))

                for d_id in client_disliked:
                    val = d_id if isinstance(d_id, str) else (d_id.get("id") or d_id.get("videoId") if isinstance(d_id, dict) else "")
                    if val:
                        conn.execute("INSERT INTO disliked_videos (video_id) VALUES (?) ON CONFLICT(video_id) DO NOTHING", (str(val),))

                for ch in client_disliked_channels:
                    if isinstance(ch, str) and ch.strip():
                        conn.execute("INSERT INTO disliked_channels (channel_name) VALUES (?) ON CONFLICT(channel_name) DO NOTHING", (ch.strip(),))

                for f in client_favorites:
                    if isinstance(f, str) and f.strip():
                        conn.execute("INSERT INTO favorites (video_id, title, channel) VALUES (?, '', '') ON CONFLICT(video_id) DO NOTHING", (f.strip(),))
                    elif isinstance(f, dict):
                        f_id = f.get("video_id") or f.get("id") or f.get("videoId") or f.get("youtubeId")
                        if f_id:
                            conn.execute("""
                                INSERT INTO favorites (video_id, title, channel)
                                VALUES (?, ?, ?)
                                ON CONFLICT(video_id) DO NOTHING
                            """, (str(f_id), f.get("title", ""), f.get("channel", "")))

                client_subscribed = req_data.get("subscribed_channels") or req_data.get("subscribedChannels") or []
                for s in client_subscribed:
                    if isinstance(s, str) and s.strip():
                        conn.execute("INSERT INTO subscribed_channels (channel_name) VALUES (?) ON CONFLICT(channel_name) DO NOTHING", (s.strip(),))

                conn.commit()
                response_data = self._build_sync_payload(conn)

            self._set_headers(200)
            self.wfile.write(json.dumps(response_data).encode("utf-8"))
            return

        self._set_headers(404)
        self.wfile.write(json.dumps({"error": "Endpoint not found"}).encode("utf-8"))

def run():
    parser = argparse.ArgumentParser(description="Vixz Sync Server")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"Port to bind (default: {DEFAULT_PORT})")
    parser.add_argument("--token", type=str, default=DEFAULT_TOKEN, help="Optional auth token required for sync")
    args = parser.parse_args()

    init_db()
    SyncRequestHandler.token = args.token

    server_address = ("0.0.0.0", args.port)
    httpd = HTTPServer(server_address, SyncRequestHandler)
    print(f"[Vixz Sync] Server listening on http://0.0.0.0:{args.port}")
    if args.token:
        print("[Vixz Sync] Auth Token protection: ENABLED (Bearer token required)")
    else:
        print("[Vixz Sync] Auth Token: DISABLED (Open for local/trusted VPS network)")
    print(f"[Vixz Sync] Database: {os.path.abspath(DB_PATH)}")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
        httpd.server_close()

if __name__ == "__main__":
    run()
