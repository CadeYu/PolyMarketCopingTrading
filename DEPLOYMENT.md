# Deployment Guide / 部署指南

How to deploy the Polymarket Copy Trading Bot to a Linux Server (VPS).
如何将 Polymarket 跟单机器人部署到 Linux 服务器 (VPS)。


## Option 2: Free Cloud (Best Option: Render) / 选项 2：免费云（最佳选项：Render）

**Verdict / 结论**:
*   **Fly.io / Railway**: Now mostly paid or short trials. (现在大部分是付费或短期试用)
*   **Render**: Has a workable **Free Tier**, but you must use a trick to keep it awake. (有一个可用的**免费层**，但必须使用技巧保持唤醒)

### Step 1: Deploy to Render / 第一步：部署到 Render
1.  Push code to **GitHub**.
2.  Go to **Render Dashboard** -> New **Web Service**.
3.  Connect your repo.
4.  Runtime: **Docker**.
5.  **Environment Variables** (Add these in Render):
    *   `TELEGRAM_BOT_TOKEN`
    *   `TELEGRAM_CHAT_ID`
    *   `MANUAL_WATCHLIST`
    *   `TRADE_MODE` = `SIMULATION`
6.  **Deploy**.

### Step 2: Prevent Sleeping (Crucial!) / 第二步：防止休眠（关键！）
Render Free Web Services sleep after 15 mins of inactivity.
Render 免费 Web 服务在不活动 15 分钟后会休眠。

The bot now has a built-in Web Server. You verify it by visiting your Render URL (e.g., `https://my-bot.onrender.com`). It should say "Polymarket Bot is Running".
机器人现在有一个内置的 Web 服务器。您可以通过访问您的 Render URL（例如 `https://my-bot.onrender.com`）来验证它。它应该显示 "Polymarket Bot is Running"。

**To keep it awake 24/7:**
**为了让它全天候保持唤醒：**

1.  Sign up for **UptimeRobot** (Free).
2.  Create a **New Monitor**.
    *   Type: **HTTP(s)**
    *   URL: `Your Render App URL`
    *   Interval: **5 minutes**
3.  Start Monitor.

This will "ping" your bot every 5 minutes, preventing Render from putting it to sleep.
这将每 5 分钟“ping”一次您的机器人，防止 Render 将其置于休眠状态。

---

## 3. Traditional VPS (Linux Server)
## 3. 传统 VPS (Linux 服务器)

(See below for manual setup steps...)

### 1. Build the Application / 构建应用


## 4. Configuration (Important) / 配置（重要）

SSH into your server and edit `.env`:
SSH 登录到你的服务器并编辑 `.env`：

```bash
nano .env
```

*   **Proxy**: If your server is outside China (e.g., AWS US, DigitalOcean), **COMMENT OUT** the Proxy settings. You don't need them.
    **代理**：如果你的服务器在中国境外（如 AWS 美国，DigitalOcean），**注释掉**代理设置。你不需要它们。
    ```properties
    # HTTP_PROXY_HOST=... (Comment this out / 注释掉这个)
    ```

*   **Tokens**: Ensure `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are set.
    **令牌**：确保设置了 `TELEGRAM_BOT_TOKEN` 和 `TELEGRAM_CHAT_ID`。

## 5. Run in Background / 在后台运行

To keep the bot running after you disconnect, use `screen` or `systemd`.
为了在你断开连接后保持机器人运行，请使用 `screen` 或 `systemd`。

### Option A: Using Screen (Easiest) / 选项 A：使用 Screen（最简单）

```bash
# 1. Start a new screen session
screen -S polybot

# 2. Run the bot
java -jar bot.jar

# 3. Detach (Check logs, then press Ctrl+A, then D)
# 3. 分离（检查日志，然后按 Ctrl+A，再按 D）
```

To reconnect later: `screen -r polybot`
稍后重新连接：`screen -r polybot`

### Option B: Using Systemd (Auto-restart) / 选项 B：使用 Systemd（自动重启）

Create a service file:
创建服务文件：
`/etc/systemd/system/polybot.service`

```ini
[Unit]
Description=Polymarket Bot
After=network.target

[Service]
User=root
WorkingDirectory=/root
ExecStart=/usr/bin/java -jar /root/bot.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

Enable and start:
启用并启动：

```bash
sudo systemctl enable polybot
sudo systemctl start polybot
```
