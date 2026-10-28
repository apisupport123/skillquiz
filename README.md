# SkillQuiz — AI-powered employee skill test

Koi bhi technology type karo (Java, PHP, Python, GenAI, Spring Boot, SQL, AWS…) → AI us topic ke
MCQ questions banata hai → har employee ko **alag paper** milta hai (alag questions + options shuffle)
→ submit karte hi score, rank aur har answer ka explanation → sab result database mein save.

```
 Employees ──► GitHub Pages (docs/)  ──►  Render (Spring Boot, backend/)  ──►  Supabase Postgres
               free, static UI             free, sleeps after 15 min            free, results + question pool
                                                   │
                                                   └──► Google Gemini (free key) – sirf naye topic pe
```

## Kaise kaam karta hai (important design)

- **AI har user ke liye call nahi hota.** Topic + level ka ek *question pool* banta hai. Pehla banda
  "Java" start kare → AI ~15 questions likhta hai → DB mein save. Uske baad sabko us pool se random
  10 milte hain, aur background mein pool 45 tak badhta rehta hai. Isse free AI limit nahi tootti.
- **Baju wale ko alag paper:** random questions + har question ke options alag order mein. Retake
  pe pehle wo questions aate hain jo us employee ne pehle nahi dekhe.
- **Cheating se bachav:** correct answer browser ko submit ke *baad* hi bheja jaata hai. Score
  server pe calculate hota hai.
- **Galat AI answer:** Admin page → "Check AI questions" → galat question ko *Switch off* karo.
- Page refresh ho jaaye toh test wahin se resume hota hai; time khatam hone pe auto-submit.

## Folder structure

```
backend/   Spring Boot 3.5 (Java 21) API + Dockerfile
docs/      Frontend (index.html + config.js) – GitHub Pages yahi folder serve karega
render.yaml                     Render blueprint
.github/workflows/build.yml     Har push pe backend compile check
.github/workflows/keepalive.yml Supabase ko so jaane se rokta hai
```

---

## Setup — 4 steps (~30 minute)

### Step 1 — Gemini API key (free)
1. https://aistudio.google.com/apikey → Google account se login → **Create API key**.
2. Key copy karke safe rakho. **Isse kabhi GitHub pe commit mat karna.**

### Step 2 — Supabase database (free)
1. https://supabase.com → New project. Region: **Mumbai (ap-south-1)**. Strong DB password set karo.
2. Project ready hone ke baad upar **Connect** button → **Session pooler** tab (ye IPv4 pe chalta hai,
   Render ko yahi chahiye — "Direct connection" mat lena).
3. Wahan se 3 cheezein note karo:
   - Host: jaise `aws-0-ap-south-1.pooler.supabase.com`
   - User: jaise `postgres.abcdxyzproject`
   - Password: jo tumne project banate waqt diya
4. Inse JDBC URL banao:
   `jdbc:postgresql://<HOST>:5432/postgres?sslmode=require`

Tables khud ban jaayengi jab backend pehli baar start hoga.

### Step 3 — GitHub pe code daalo
```bash
cd skillquiz
git init && git add . && git commit -m "SkillQuiz first version"
git branch -M main
git remote add origin https://github.com/apisupport123/skillquiz.git
git push -u origin main
```
Push ke baad repo ke **Actions** tab mein "Backend build" green hona chahiye.

### Step 4 — Backend Render pe (free)
1. https://render.com → GitHub se login → **New → Blueprint** → apni `skillquiz` repo choose karo.
   (Render `render.yaml` padh ke Docker service bana dega.)
2. Ye values maangega, bharo:

| Variable | Value |
|---|---|
| `DB_URL` | Step 2 wala JDBC URL |
| `DB_USERNAME` | `postgres.xxxxx` (pooler user) |
| `DB_PASSWORD` | Supabase DB password |
| `GEMINI_API_KEY` | Step 1 wali key |
| `ADMIN_KEY` | Admin page ka password (khud banao, strong rakho) |
| `ALLOWED_ORIGINS` | `https://apisupport123.github.io` |

3. Deploy hone do (pehli baar 5–8 min). URL milega jaise `https://skillquiz-api.onrender.com`.
4. Check: browser mein `https://skillquiz-api.onrender.com/api/health` kholo → `{"status":"ok",...}`

### Step 5 — Frontend GitHub Pages pe
1. `docs/config.js` kholo, `apiBase` mein apna Render URL daalo, commit + push.
2. GitHub repo → **Settings → Pages** → Source: *Deploy from a branch* → Branch `main`, folder `/docs` → Save.
3. 1–2 minute mein link milega: `https://apisupport123.github.io/skillquiz/` — yahi link employees ko bhejo.

### Step 6 — Database ko jagaye rakho
Repo → **Settings → Secrets and variables → Actions → Variables** → New variable
`BACKEND_URL` = `https://skillquiz-api.onrender.com`. Ab har 3 din mein GitHub khud ping karega.

---

## Free AI limit (important)
Google free tier mein limit **har model ki alag, per day** hoti hai (midnight Pacific = subah ~12:30 IST pe reset):
- `gemini-3.1-flash-lite`: ~500 requests/day (primary)
- `gemini-3.6-flash`: ~20 requests/day (backup)

Ek naya topic+level = lagbhag 1–2 AI calls. Jo topic pehle se bana hai, uspe **0 calls** — chahe 100 log dein.
Isliye: **test se ek din pehle Admin → "Prepare topics for test day"** mein topics daal ke prepare kar lo.

## Test wale din ki checklist
- Test se **5 minute pehle** site khud kholo. Header mein "Online" green aana chahiye
  (Render free 15 min idle pe so jaata hai, jaagne mein ~1 minute).
- Agar naya topic hai (jaise "Kafka"), pehle **khud ek baar start karo** taaki question pool pehle se
  ban jaaye. Phir 30 log ek saath start karein toh bhi wait nahi.
- Results: Admin → password → **Download all results (CSV)** → Excel mein khulega.

## Settings badalni ho (Render → Environment)
| Variable | Default | Matlab |
|---|---|---|
| `QUIZ_QUESTIONS_PER_TEST` | 10 | ek paper mein kitne questions |
| `QUIZ_TIME_LIMIT_MINUTES` | 15 | timer |
| `QUIZ_TARGET_POOL_SIZE` | 40 | background mein pool kitna bade |
| `QUIZ_MAX_AI_CALLS_PER_MINUTE` | 6 | free AI limit ke andar rehne ke liye |
| `GEMINI_MODELS` | gemini-3.1-flash-lite,gemini-3.6-flash | AI models, order mein try hote hain |

## Laptop pe chalana (bina Supabase / AI ke)
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Phir `docs/index.html` browser mein kholo. Ye in-memory DB aur **demo questions** use karta hai.
Admin password: `admin123`. Real AI ke liye: `GEMINI_API_KEY=xxx QUIZ_DEMO_MODE=false mvn spring-boot:run -Dspring-boot.run.profiles=local`

## Free tier ki limits (jaan ke chalo)
- **Render free:** 15 min idle pe sleep, jaagne mein ~1 min. Hamesha-on chahiye toh Starter plan.
- **Supabase free:** 500 MB (lakhon results ke liye kaafi). 7 din inactive = pause; keepalive workflow
  isse rokta hai. GitHub 60 din tak repo mein koi commit na ho toh scheduled workflows band kar deta hai —
  tab Actions tab mein jaake dobara enable karna.
- **Gemini free:** per-minute limit kam hai; pool design isliye hai. Bahut log ek saath *naye-naye*
  topics start karein toh "1 minute baad try karo" message aa sakta hai.
- **Privacy:** Public leaderboard pe sirf naam + team dikhta hai, employee ID nahi. "My results" mein
  koi bhi kisi ka employee ID daal ke history dekh sakta hai — internal use ke liye theek hai; strict
  chahiye toh company SSO/login add karna padega.
