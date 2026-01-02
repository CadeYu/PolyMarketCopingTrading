package com.polymarket.bot.service;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Handles sending notifications to Telegram.
 * 处理发送通知到 Telegram。
 */
public class TelegramNotifier extends TelegramLongPollingBot {

    private final String botToken;
    private final String chatId;

    public TelegramNotifier(String botToken, String chatId) {
        super(botToken);
        this.botToken = botToken;
        this.chatId = chatId;
    }

    @Override
    public String getBotUsername() {
        return "PolymarketCopyBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // We only send alerts, so we strictly ignore incoming messages for now.
        // 我们只发送警报，所以目前严格忽略传入的消息。
    }

    /**
     * Send a message to the configured chat.
     * 发送消息到配置的聊天。
     *
     * @param message Text to send / 要发送的文本
     */
    public void sendAlert(String message) {
        SendMessage sm = new SendMessage();
        sm.setChatId(chatId);
        sm.setText(message);

        try {
            execute(sm);
        } catch (TelegramApiException e) {
            System.err.println("Failed to send Telegram message: " + e.getMessage());
            System.err.println("发送 Telegram 消息失败：" + e.getMessage());
        }
    }
}
