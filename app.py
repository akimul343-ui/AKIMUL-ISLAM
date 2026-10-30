import os
import json
import sqlite3
import itertools
from http.server import HTTPServer, BaseHTTPRequestHandler
import google.generativeai as genai

# Read API Keys securely from Render Environment Variables
raw_keys = os.environ.get("GEMINI_API_KEYS", "")
API_KEYS = [k.strip() for k in raw_keys.split(",") if k.strip()]

if not API_KEYS:
    # Fallback placeholder if environment variable is not set yet
    API_KEYS = ["YOUR_API_KEY_HERE"]

api_key_cycle = itertools.cycle(API_KEYS)

def get_next_ai_response(prompt_text: str):
    """Rotates API keys and fetches dynamic intelligence directly from Gemini API."""
    for _ in range(len(API_KEYS)):
        try:
            current_key = next(api_key_cycle)
            genai.configure(api_key=current_key)
            model = genai.GenerativeModel('gemini-1.5-flash')
            
            system_instruction = (
                "তুমি হলে 'রাকিব' (Rakib). তুমি ইউজারের অত্যন্ত বিশ্বস্ত, বুদ্ধিমান এবং পারফেক্ট ব্যক্তিগত এআই অ্যাসিস্ট্যান্ট। "
                "তোমার কোনো ফালতু কথা বলার সময় নেই; তুমি পাইথন কোডিং, অটোমেশন এবং জটিল কাজের বেলায় একশ শতাংশ নিখুঁত। "
                "ইউজারের ফোন কন্ট্রোল, ক্যাপকাট ভিডিও এডিটিং, ছবি এডিট করা এবং যেকোনো জটিল সমস্যার সমাধান করতে তুমি পুরোপুরি সক্ষম। "
                "সবসময় সাবলীল এবং মার্জিত বাংলায় উত্তর দেবে।"
            )
            
            chat = model.start_chat(history=[])
            response = chat.send_message(f"{system_instruction}\n\nUser Command: {prompt_text}")
            if response and response.text:
                return response.text
        except Exception as e:
            continue
    return "দুঃখিত, বর্তমানে এআই সার্ভার কানেক্ট করতে একটু সমস্যা হচ্ছে। রেন্ডারে GEMINI_API_KEYS সঠিকভাবে সেট করা আছে কিনা চেক করুন।"

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

class RakibServerHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/" or self.path == "":
            if os.path.exists("index.html"):
                self.send_response(200)
                self.send_header("Content-type", "text/html; charset=utf-8")
                self.end_headers()
                with open("index.html", "rb") as f:
                    self.wfile.write(f.read())
            else:
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"Frontend index.html not found!")
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/api/chat":
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            try:
                data = json.loads(post_data.decode('utf-8'))
                user_msg = data.get("message", "").strip()
            except:
                user_msg = ""

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

            response_data = {
                "status": "success",
                "reply": ai_reply,
                "action": action_triggered
            }

            self.send_response(200)
            self.send_header("Content-type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(json.dumps(response_data, ensure_ascii=False).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

def run(server_class=HTTPServer, handler_class=RakibServerHandler, port=10000):
    server_address = ('0.0.0.0', port)
    httpd = server_class(server_address, handler_class)
    print(f"Rakib AI Core running on port {port}...")
    httpd.serve_forever()

if __name__ == "__main__":
    run()
