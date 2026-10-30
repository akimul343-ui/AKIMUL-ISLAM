import os
import sqlite3
import itertools
from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
import google.generativeai as genai

app = FastAPI(title="Rakib AI Companion & System Automation Core")

# Secure API Key Pool with Automatic Background Rotation
API_KEYS = [


api_key_cycle = itertools.cycle(API_KEYS)

def get_next_ai_response(prompt_text: str):
    """Rotates API keys and fetches dynamic intelligence directly from Gemini API."""
    for _ in range(len(API_KEYS)):
        try:
            current_key = next(api_key_cycle)
            genai.configure(api_key=current_key)
            model = genai.GenerativeModel('gemini-1.5-flash')
            
            system_instruction = (
                "তুমি হলে 'রাকিব' (Rakib)। তুমি ইউজারের অত্যন্ত বিশ্বস্ত, বুদ্ধিমান এবং পারফেক্ট ব্যক্তিগত এআই অ্যাসিস্ট্যান্ট। "
                "তোমার কোনো ফালতু কথা বলার সময় নেই; তুমি কাজের বেলায় একশ শতাংশ নিখুঁত। "
                "ইউজারের ফোন কন্ট্রোল, ক্যাপকাট ভিডিও এডিটিং, ছবি এডিট করা এবং যেকোনো জটিল সমস্যার সমাধান করতে তুমি পুরোপুরি সক্ষম। "
                "সবসময় সাবলীল এবং মার্জিত বাংলায় উত্তর দেবে।"
            )
            
            chat = model.start_chat(history=[])
            response = chat.send_message(f"{system_instruction}\n\nUser Command: {prompt_text}")
            if response and response.text:
                return response.text
        except Exception as e:
            continue
    return "দুঃখিত, বর্তমানে এআই সার্ভার কানেক্ট করতে একটু সমস্যা হচ্ছে। আবার চেষ্টা করুন।"

# Database Initialization
DB_PATH = "rakib_memory.db"

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS chat_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sender TEXT,
            message TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.commit()
    conn.close()

init_db()

class CommandRequest(BaseModel):
    message: str

@app.get("/", response_class=HTMLResponse)
async def serve_frontend():
    if os.path.exists("index.html"):
        with open("index.html", "r", encoding="utf-8") as f:
            return f.read()
    return "<h3>Frontend index.html not found!</h3>"

@app.post("/api/chat")
async def process_command(req: CommandRequest):
    user_msg = req.message.strip()
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("INSERT INTO chat_history (sender, message) VALUES (?, ?)", ("user", user_msg))
    conn.commit()
    
    ai_reply = get_next_ai_response(user_msg)
    
    action_triggered = "none"
    if "কল" in user_msg or "call" in user_msg.lower():
        action_triggered = "direct_call"
    elif "ফোন বন্ধ" in user_msg or "lock" in user_msg.lower():
        action_triggered = "device_shutdown_lock"
    elif "ক্যাপকাট" in user_msg or "capcut" in user_msg.lower() or "এডিট" in user_msg.lower():
        action_triggered = "capcut_video_edit"

    cursor.execute("INSERT INTO chat_history (sender, message) VALUES (?, ?)", ("rakib", ai_reply))
    conn.commit()
    conn.close()

    return {
        "status": "success",
        "reply": ai_reply,
        "action": action_triggered
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=10000, reload=True)
