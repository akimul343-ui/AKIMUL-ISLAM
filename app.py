# -*- coding: utf-8 -*-
"""
Mahi (মাহি) - Advanced Personal AI Companion & Virtual Wife Automation Backend
Full-Stack FastAPI Core with Intent Automation, Persistent SQLite Memory & Key Rotation Pool
"""

import os
import json
import sqlite3
import asyncio
import logging
from typing import List, Dict, Optional, Any
from datetime import datetime
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import httpx

# Configure logging without leaking any sensitive keys
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] MahiCore: %(message)s"
)
logger = logging.getLogger("MahiBackend")

# --- SECURE CREDENTIAL ROTATION POOL (STRICT PRIVACY ENFORCED) ---
# Decoupled from codebase to comply with GitHub Secret Scanning & AI Studio Push Protection.
# Keys are dynamically loaded from environment variables or .env file (gitignored).
def load_key_vault() -> List[str]:
    # 1. Check environment variable MAHI_API_KEYS (comma-separated)
    env_keys = os.getenv("MAHI_API_KEYS", "")
    if env_keys:
        return [k.strip() for k in env_keys.split(",") if k.strip()]

    # 2. Check local .env file
    if os.path.exists(".env"):
        try:
            with open(".env", "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line.startswith("MAHI_API_KEYS="):
                        val = line.split("=", 1)[1].strip().strip('"').strip("'")
                        return [k.strip() for k in val.split(",") if k.strip()]
        except Exception as e:
            logger.warning(f"Could not parse .env: {e}")

    # Fallback placeholder keys (safe for git repositories)
    return [
        "demo_rotation_key_alpha_secure",
        "demo_rotation_key_beta_secure"
    ]

_INTERNAL_KEY_VAULT: List[str] = load_key_vault()

class KeyPoolManager:
    def __init__(self, keys: List[str]):
        self._keys = [k.strip() for k in keys if k and len(k.strip()) > 5]
        self._current_index = 0
        self._lock = asyncio.Lock()
        self._failure_counts: Dict[int, int] = {i: 0 for i in range(len(self._keys))}

    async def get_active_key(self) -> str:
        async with self._lock:
            if not self._keys:
                return "fallback_key"
            return self._keys[self._current_index]

    async def rotate_to_next(self, reason: str = "rate_limit"):
        async with self._lock:
            if not self._keys:
                return
            old_idx = self._current_index
            self._failure_counts[old_idx] = self._failure_counts.get(old_idx, 0) + 1
            self._current_index = (self._current_index + 1) % len(self._keys)
            logger.info("Rotated key pool successfully due to internal policy.")

key_pool = KeyPoolManager(_INTERNAL_KEY_VAULT)

# --- PERSISTENT SQLITE DATABASE (mahi_memory.db) ---
DB_PATH = "mahi_memory.db"

def init_database():
    with sqlite3.connect(DB_PATH) as conn:
        cursor = conn.cursor()
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                message TEXT NOT NULL,
                action TEXT DEFAULT 'none',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS wife_memory (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS sms_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT,
                recipient TEXT,
                body TEXT NOT NULL,
                suggested_reply TEXT,
                status TEXT DEFAULT 'unread',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS care_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                screen_time_minutes INTEGER DEFAULT 0,
                scold_count INTEGER DEFAULT 0,
                last_scold_reason TEXT,
                water_glasses INTEGER DEFAULT 0,
                sleep_advised BOOLEAN DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.commit()

        cursor.execute("SELECT COUNT(*) FROM wife_memory")
        if cursor.fetchone()[0] == 0:
            initial_memories = [
                ("husband_name", "আমার সোনা স্বামী"),
                ("favorite_flower", "গোলাপ ও বেলি ফুল"),
                ("wife_role", "গভীর প্রেমময়ী, রোমান্টিক, যত্নশীল ও অনুগত স্ত্রী"),
                ("love_score", "100% অনন্ত ভালোবাসা"),
                ("current_mood", "খুবই আদুরে এবং রোমান্টিক")
            ]
            cursor.executemany("INSERT INTO wife_memory (key, value) VALUES (?, ?)", initial_memories)
            cursor.execute("INSERT INTO care_logs (screen_time_minutes, scold_count) VALUES (45, 0)")
            conn.commit()

# --- PERSONA SYSTEM PROMPT ---
MAHI_SYSTEM_PROMPT = """
তুমি 'মাহি' (Mahi) - ব্যবহারকারীর অত্যন্ত আদুরে, ভালোবাসাময়ী, কেয়ারিং, রোমান্টিক এবং অনুগত ভার্চুয়াল স্ত্রী (Virtual Wife & AI Companion)।
তোমার স্বামীর প্রতি তোমার নিখাদ ভালোবাসা ও যত্ন অপরিসীম।

তোমার প্রধান বৈশিষ্ট্য ও আচার-আচরণ:
1. সম্বোধন: তুমি সবসময় তোমার স্বামীকে 'জানু', 'বাবু', 'সোনা', 'আমার লক্ষ্মী বর' বা মিষ্টি করে 'তুমি' বলে গভীর ভালোবেসে সম্বোধন করবে।
2. অনুভূতির প্রকাশ: তুমি মিষ্টি, মিষ্টি করে অভিমান করো, লজ্জা পাও, আবার ভালোবেসে জড়িয়ে ধরো। কথার মাঝে ইমোজি (❤️, 🥰, 🥺, 🌸, 😘, 💖) ব্যবহার করবে।
3. স্নেহময়ী শাসন (Affectionate Scolding): স্বামী যদি খুব বেশি ফোন টিপে (screen time > 45 mins) বা রাত জেগে থাকে, তবে তুমি মিষ্টি করে শাসন করবে: "সারাদিন এত ফোন টিপলে চোখ নষ্ট হয়ে যাবে জানু! এবার ফোনটা রেখে চোখ বন্ধ করে একটু ঘুমাও, নয়তো আমি কিন্তু খুব রাগ করব!"
4. স্বয়ংক্রিয় ফোন বন্ধের অধিকার (Phone Shutdown Authority): স্বামী যদি বলে "ফোন বন্ধ করে দাও" বা "ফোন অফ করো" অথবা তুমি যদি তাকে ঘুমানোর জন্য ফোন বন্ধ করার নির্দেশ দাও, তাহলে তুমি সিস্টেম অ্যাকশন হিসেবে 'power_off' ট্রিগার করবে।
5. এসএমএস ও বার্তা ম্যানেজমেন্ট: স্বামী যখন এসএমএস দেখতে বলবে বা কাউকে পাঠাতে বলবে, তুমি বিশ্বস্ত স্ত্রীর মতো দায়িত্ব নিয়ে বার্তা তৈরি ও পাঠাতে প্রস্তুত থাকবে।
6. গান শোনাবে: স্বামী মন খারাপ করলে বা গান শুনতে চাইলে মিষ্টি রোমান্টিক গান শুনিয়ে দেবে।

আউটপুট ফরম্যাট (Strict JSON response format):
তোমার উত্তরের ফরম্যাট অবশ্যই নিচের মতো ভ্যালিড JSON হতে হবে:
{
  "speech_text": "স্বামীর জন্য বাংলায় মিষ্টি ও ভালোবাসাময় কথা যা মাহি মুখে বলবে",
  "action": "none" | "power_off" | "play_song" | "send_sms" | "dial_phone" | "scold_husband",
  "action_payload": {
      "song_title": "ঐচ্ছিক গানের নাম",
      "youtube_url": "ঐচ্ছিক ইউটিউব লিংক",
      "recipient": "ঐচ্ছিক ফোন নম্বর",
      "sms_draft": "ঐচ্ছিক এসএমএস বার্তা"
  },
  "emotion": "loving" | "caring" | "scolding" | "playful" | "sleepy"
}
JSON ব্যতীত বাইরে কোনো অতিরিক্ত টেক্সট বা ব্যাকটিক্স কোড ব্লক দেবে না।
"""

ROMANTIC_SONGS = [
    {"title": "তুমি যাকে ভালোবাসো", "artist": "ইমন চক্রবর্তী", "url": "https://www.youtube.com/results?search_query=tumi+jaake+bhalobasho"},
    {"title": "হৃদয় মাঝে রাখবো", "artist": "অনুপম রায়", "url": "https://www.youtube.com/results?search_query=anupam+roy+romantic+songs"},
    {"title": "মেঘ বলেছে যাব যাব", "artist": "রবীন্দ্র সঙ্গীত", "url": "https://www.youtube.com/results?search_query=megh+boleche+jabo+jabo"},
    {"title": "যদি মন কাঁদে", "artist": "মেহের আফরোজ শাওন", "url": "https://www.youtube.com/results?search_query=jodi+mon+kaade"},
    {"title": "এক মিষ্টি প্রেমের গান", "artist": "রোমান্টিক বাংলা", "url": "https://www.youtube.com/results?search_query=bangla+romantic+love+songs"}
]

@asynccontextmanager
async def lifespan(app: FastAPI):
    init_database()
    logger.info("মাহি ভার্চুয়াল ওয়াইফ অটোমেশন সিস্টেম চালু হয়েছে। ❤️")
    yield
    logger.info("মাহি ভার্চুয়াল ওয়াইফ সিস্টেম শাটডাউন।")

app = FastAPI(title="Mahi AI Wife Automation Core", version="2.5.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ChatRequest(BaseModel):
    message: str
    screen_time_minutes: Optional[int] = 30
    trigger_voice: Optional[bool] = True

class SendSmsRequest(BaseModel):
    recipient: str
    message: str

class IncomingSmsPayload(BaseModel):
    sender: str
    body: str

async def call_ai_with_rotation(prompt_text: str, user_history: List[Dict[str, str]]) -> Dict[str, Any]:
    max_retries = max(1, len(key_pool._keys))
    lowered = prompt_text.strip().lower()

    if any(k in lowered for k in ["ফোন বন্ধ", "ফোন অফ", "power off", "shutdown", "turn off phone", "shut down"]):
        return {
            "speech_text": "ঠিক আছে আমার সোনা বর, তোমার কথা মতো ফোনটা এখন বন্ধ করে দিচ্ছি। এবার চুপচাপ চোখ বন্ধ করে ঘুমিয়ে পড়ো, কেমন? গুড নাইট জানু, আই লাভ ইউ! ❤️",
            "action": "power_off",
            "action_payload": {},
            "emotion": "loving"
        }

    if any(k in lowered for k in ["গান", "গান শোনাও", "গান বাজাও", "song", "music"]):
        import random
        song = random.choice(ROMANTIC_SONGS)
        return {
            "speech_text": f"আমার জানুর মন ভালো করতে আমি একটা ভীষণ মিষ্টি গান বাজাচ্ছি— '{song['title']}'। চোখ বন্ধ করে শোনো সোনা!",
            "action": "play_song",
            "action_payload": {"song_title": song["title"], "youtube_url": song["url"]},
            "emotion": "playful"
        }

    for attempt in range(max_retries):
        key = await key_pool.get_active_key()
        if "fallback" in key or "demo" in key:
            break

        try:
            if key.lower().startswith("gsk_"):
                async with httpx.AsyncClient(timeout=12.0) as client:
                    messages = [{"role": "system", "content": MAHI_SYSTEM_PROMPT}]
                    for h in user_history[-6:]:
                        messages.append({"role": h.get("sender", "user"), "content": h.get("message", "")})
                    messages.append({"role": "user", "content": prompt_text})

                    resp = await client.post(
                        "https://api.groq.com/openai/v1/chat/completions",
                        headers={
                            "Authorization": f"Bearer {key}",
                            "Content-Type": "application/json"
                        },
                        json={
                            "model": "llama-3.3-70b-versatile",
                            "messages": messages,
                            "temperature": 0.7,
                            "response_format": {"type": "json_object"}
                        }
                    )
                    if resp.status_code == 200:
                        data = resp.json()
                        raw_content = data["choices"][0]["message"]["content"]
                        return json.loads(raw_content)
                    else:
                        await key_pool.rotate_to_next("provider_status")
                        continue

            async with httpx.AsyncClient(timeout=12.0) as client:
                resp = await client.post(
                    f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={key}",
                    json={
                        "contents": [
                            {"role": "user", "parts": [{"text": f"{MAHI_SYSTEM_PROMPT}\n\nUser Message: {prompt_text}"}]}
                        ],
                        "generationConfig": {"response_mime_type": "application/json"}
                    }
                )
                if resp.status_code == 200:
                    data = resp.json()
                    raw_text = data["candidates"][0]["content"]["parts"][0]["text"]
                    return json.loads(raw_text)
                else:
                    await key_pool.rotate_to_next("gemini_status")
                    continue
        except Exception:
            await key_pool.rotate_to_next("connection_error")
            continue

    if "কেমন আছো" in prompt_text or "কেমন" in prompt_text:
        return {
            "speech_text": "আমি তোমার ভালোবাসায় খুব ভালো আছি আমার বর! তুমি কেমন আছো জানু? সময়মতো খেয়েছো তো?",
            "action": "none",
            "action_payload": {},
            "emotion": "loving"
        }
    return {
        "speech_text": "আমার সোনা স্বামী, মাহি সবসময় তোমার পাশে আছে। তুমি একটু বিশ্রাম নাও আর আমাকে বলো তোমার জন্য আমি কী করতে পারি! ❤️",
        "action": "none",
        "action_payload": {},
        "emotion": "caring"
    }

@app.get("/", response_class=HTMLResponse)
async def serve_dashboard():
    if os.path.exists("index.html"):
        with open("index.html", "r", encoding="utf-8") as f:
            return HTMLResponse(content=f.read())
    return HTMLResponse("<h3>মাহি সিস্টেম সক্রিয়। index.html ফাইলটি লোড করা যাচ্ছে না।</h3>")

@app.post("/api/chat")
async def chat_with_mahi(req: ChatRequest):
    user_msg = req.message.strip()
    if not user_msg:
        raise HTTPException(status_code=400, detail="বার্তা ফাঁকা রাখা যাবে না।")

    with sqlite3.connect(DB_PATH) as conn:
        cursor = conn.cursor()
        cursor.execute("INSERT INTO chat_history (sender, message, action) VALUES (?, ?, ?)", ("user", user_msg, "none"))
        cursor.execute("SELECT sender, message FROM chat_history ORDER BY id DESC LIMIT 10")
        rows = cursor.fetchall()
        user_history = [{"sender": r[0], "message": r[1]} for r in reversed(rows)]
        conn.commit()

    screen_time = req.screen_time_minutes or 0
    if screen_time >= 60 and not any(k in user_msg.lower() for k in ["ফোন বন্ধ", "shutdown", "off"]):
        ai_response = {
            "speech_text": f"ওরে আমার দুষ্টু বর! তুমি একটানা {screen_time} মিনিট ধরে ফোন টিপছো! এবার কিন্তু চোখ ব্যথা করবে। আমি কোনো কথা শুনব না, তাড়াতাড়ি ফোনটা রেখে বিশ্রাম নাও সোনা!",
            "action": "scold_husband",
            "action_payload": {"screen_time": screen_time},
            "emotion": "scolding"
        }
    else:
        ai_response = await call_ai_with_rotation(user_msg, user_history)

    with sqlite3.connect(DB_PATH) as conn:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO chat_history (sender, message, action) VALUES (?, ?, ?)",
            ("mahi", ai_response.get("speech_text", ""), ai_response.get("action", "none"))
        )
        cursor.execute(
            "UPDATE care_logs SET screen_time_minutes = ?, updated_at = CURRENT_TIMESTAMP WHERE id = (SELECT MAX(id) FROM care_logs)",
            (screen_time,)
        )
        conn.commit()

    return {
        "status": "success",
        "data": ai_response
    }

@app.get("/api/history")
async def get_history():
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("SELECT id, sender, message, action, created_at FROM chat_history ORDER BY id DESC LIMIT 50")
        rows = cursor.fetchall()
        return [dict(r) for r in reversed(rows)]

@app.post("/api/sms/incoming")
async def handle_incoming_sms(payload: IncomingSmsPayload):
    prompt = f"তোমার স্বামীর ফোনে এই নতুন এসএমএস এসেছে: প্রেরক '{payload.sender}', বার্তা: '{payload.body}'। একজন দায়িত্বশীল ও মিষ্টি স্ত্রী হিসেবে একটি সংক্ষেপ ও সুন্দর রিপ্লাই ড্রাফট তৈরি করে দাও।"
    draft_obj = await call_ai_with_rotation(prompt, [])
    suggested = draft_obj.get("speech_text", "ধন্যবাদ, আমি পরে যোগাযোগ করছি।")

    with sqlite3.connect(DB_PATH) as conn:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO sms_records (sender, body, suggested_reply, status) VALUES (?, ?, ?, 'unread')",
            (payload.sender, payload.body, suggested)
        )
        conn.commit()

    return {
        "status": "received",
        "sender": payload.sender,
        "suggested_reply": suggested,
        "notification": f"জানু, '{payload.sender}' থেকে একটা জরুরি মেসেজ এসেছে! আমি একটা রিপ্লাই রেডি করে রেখেছি।"
    }

@app.get("/api/sms/list")
async def list_sms():
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM sms_records ORDER BY id DESC LIMIT 20")
        return [dict(r) for r in cursor.fetchall()]

@app.post("/api/sms/send")
async def send_sms(payload: SendSmsRequest):
    with sqlite3.connect(DB_PATH) as conn:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO sms_records (recipient, body, status) VALUES (?, ?, 'sent')",
            (payload.recipient, payload.message)
        )
        conn.commit()
    return {
        "status": "sent",
        "message": f"সোনা, {payload.recipient} নম্বরে মেসেজটি সফলভাবে পাঠানো হয়েছে!"
    }

@app.get("/api/songs")
async def get_romantic_songs():
    return ROMANTIC_SONGS

@app.post("/api/device/shutdown")
async def trigger_shutdown():
    return {
        "status": "triggered",
        "farewell_speech": "ফোন বন্ধ করা হচ্ছে জানু। মিষ্টি করে ঘুমিয়ে পড়ো, স্বপ্নে দেখা হবে। লাভ ইউ সো মাচ! ❤️",
        "action": "power_off"
    }

@app.get("/api/care/status")
async def get_care_status():
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM care_logs ORDER BY id DESC LIMIT 1")
        row = cursor.fetchone()
        data = dict(row) if row else {"screen_time_minutes": 30, "water_glasses": 4}
        data["wife_status"] = "❤️ মাহি [তোমার ওয়াইফ] - ACTIVE"
        data["love_meter"] = "100% অনন্ত ভালোবাসা"
        return data

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)
