package com.polymarket.bot.service;

import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Handles sending notifications to Telegram (Send-only mode).
 * 处理发送通知到 Telegram（仅发送模式）。
 */
public class TelegramNotifier extends DefaultAbsSender {

    private final String botToken;
    private final String chatId;

    public TelegramNotifier(DefaultBotOptions options, String botToken, String chatId) {
        super(options);
        this.botToken = botToken;
        this.chatId = chatId;
    }

    @Override
    public String getBotToken() {
        return botToken;
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
