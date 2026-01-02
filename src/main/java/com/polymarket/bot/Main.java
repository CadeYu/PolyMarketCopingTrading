package com.polymarket.bot;

import com.polymarket.bot.service.TelegramNotifier;
import com.polymarket.bot.service.WhaleWatcher;
import io.github.cdimascio.dotenv.Dotenv;
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
            // 2. Initialize Telegram Bot / 初始化 Telegram 机器人
            TelegramNotifier bot = new TelegramNotifier(botToken, chatId);
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("Telegram Bot started successfully. / Telegram 机器人启动成功。");

            bot.sendAlert("🤖 Polymarket Bot Started! Monitoring whales... \n🤖 Polymarket 机器人已启动！正在监控巨鲸...");

            // 3. Initialize Whale Watcher / 初始化巨鲸观察者
            WhaleWatcher watcher = new WhaleWatcher(bot);

            // 4. Schedule Polling (e.g., every 30 seconds) / 调度轮询（例如，每 30 秒）
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(watcher::poll, 0, 30, TimeUnit.SECONDS);
            System.out.println("Whale polling scheduled. / 巨鲸轮询已调度。");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Fatal Error during startup. Exiting. / 启动期间发生致命错误。正在退出。");
            System.exit(1);
        }
    }
}
