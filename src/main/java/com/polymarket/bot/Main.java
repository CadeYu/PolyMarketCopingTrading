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

/**
 * Main Entry Point.
 * 主入口点。
 */
public class Main {
    public static void main(String[] args) {
        // 1. Load Environment Variables / 加载环境变量
        Dotenv dotenv = Dotenv.load();
        String botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
        String chatId = dotenv.get("TELEGRAM_CHAT_ID");

        if (botToken == null || chatId == null) {
            System.err.println("Error: TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not set in .env");
            System.err.println("错误：.env 中未设置 TELEGRAM_BOT_TOKEN 或 TELEGRAM_CHAT_ID");
            return;
        }

        try {
            // 2. Initialize Telegram Bot with Proxy Support / 初始化带代理支持的 Telegram 机器人
            DefaultBotOptions botOptions = new DefaultBotOptions();

            String proxyHost = dotenv.get("HTTP_PROXY_HOST");
            String proxyPort = dotenv.get("HTTP_PROXY_PORT");

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
            watcher.sendTestAlert();

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
