package com.polymarket.bot;

import com.polymarket.bot.service.TelegramNotifier;
import com.polymarket.bot.service.TradeExecutor;
import com.polymarket.bot.service.WhaleWatcher;
import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

/**
 * Main Entry Point.
 * 主入口点。
 */
public class Main {
    public static void main(String[] args) {
        // 1. Load Environment Variables / 加载环境变量
        // Handle case where .env file is missing (e.g. Docker/Production)
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
        if (botToken == null)
            botToken = System.getenv("TELEGRAM_BOT_TOKEN");

        String chatId = dotenv.get("TELEGRAM_CHAT_ID");
        if (chatId == null)
            chatId = System.getenv("TELEGRAM_CHAT_ID");

        if (botToken == null || chatId == null) {
            System.err.println("Fatal Error: Missing Configuration");
            System.err.println("TELEGRAM_BOT_TOKEN: " + (botToken == null ? "[MISSING]" : "[SET]"));
            System.err.println("TELEGRAM_CHAT_ID: " + (chatId == null ? "[MISSING]" : "[SET]"));
            System.err.println(
                    "Please set these environment variables in your cloud provider (Render/Fly) or .env file.");
            System.exit(1);
        }

        try {
            // 2. Initialize Telegram Bot with Proxy Support / 初始化带代理支持的 Telegram 机器人
            DefaultBotOptions botOptions = new DefaultBotOptions();

            String proxyHost = dotenv.get("HTTP_PROXY_HOST");
            if (proxyHost == null)
                proxyHost = System.getenv("HTTP_PROXY_HOST");

            String proxyPort = dotenv.get("HTTP_PROXY_PORT");
            if (proxyPort == null)
                proxyPort = System.getenv("HTTP_PROXY_PORT");

            if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null && !proxyPort.isEmpty()) {
                botOptions.setProxyType(DefaultBotOptions.ProxyType.HTTP);
                botOptions.setProxyHost(proxyHost);
                botOptions.setProxyPort(Integer.parseInt(proxyPort));
                System.out.println(
                        "Using Proxy: " + proxyHost + ":" + proxyPort + " / 使用代理：" + proxyHost + ":" + proxyPort);
            }

            TelegramNotifier bot = new TelegramNotifier(botOptions, botToken, chatId);
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("Telegram Bot started successfully. / Telegram 机器人启动成功。");

            bot.sendAlert("🤖 Polymarket Bot Started! Monitoring whales... \n🤖 Polymarket 机器人已启动！正在监控巨鲸...");

            // 3. Initialize Whale Watcher / 初始化巨鲸观察者
            TradeExecutor executor = new TradeExecutor(bot);
            WhaleWatcher watcher = new WhaleWatcher(bot, executor);

            // Send a test alert immediately / 立即发送测试警报
            // watcher.sendTestAlert(); // Disabled to prevent spam / 已禁用以防止刷屏

            // Verify Connection / 验证连接
            if (!watcher.testConnection()) {
                System.err.println(
                        "⚠️ WARNING: Goldsky Connection Failed. Please check PROXY_GUIDE.md. / 警告：Goldsky 连接失败。请检查 PROXY_GUIDE.md。");
            }

            // 5. Start Keep-Alive Server (For Render/Fly Health Checks) / 启动保活服务器（用于
            // Render/Fly 健康检查）
            try {
                int port = Integer.parseInt(dotenv.get("PORT", "8080"));
                HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/", exchange -> {
                    String response = "Polymarket Bot is Running. / Polymarket 机器人正在运行。";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    try (var os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                });
                server.start();
                System.out.println("Keep-Alive HTTP Server started on port " + port);
            } catch (Exception e) {
                System.err.println("Failed to start HTTP Server: " + e.getMessage());
            }

            // 4. Schedule Polling (e.g., every 5 seconds) / 调度轮询（例如，每 5 秒）
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(watcher::poll, 0, 5, TimeUnit.SECONDS);
            System.out.println("Whale polling scheduled. / 巨鲸轮询已调度。");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Fatal Error during startup. Exiting. / 启动期间发生致命错误。正在退出。");
            System.exit(1);
        }
    }
}
