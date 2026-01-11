package com.polymarket.bot.service;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Handles trade execution (Simulation & Real).
 * 处理交易执行（模拟和真实）。
 */
public class TradeExecutor {

    private final TelegramNotifier notifier;
    private final double copyAmount;
    private final boolean isSimulation;

    public TradeExecutor(TelegramNotifier notifier) {
        this.notifier = notifier;
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String amountStr = dotenv.get("COPY_TRADE_AMOUNT");
        if (amountStr == null)
            amountStr = System.getenv("COPY_TRADE_AMOUNT");
        this.copyAmount = (amountStr != null) ? Double.parseDouble(amountStr) : 10.0;

        String mode = dotenv.get("TRADE_MODE");
        if (mode == null)
            mode = System.getenv("TRADE_MODE");
        this.isSimulation = !"REAL".equalsIgnoreCase(mode);

        System.out.println("TradeExecutor initialized. Mode: " + (isSimulation ? "SIMULATION" : "REAL") + ", Amount: $"
                + copyAmount);
    }

    /**
     * Executes a copy trade.
     * 执行跟单交易。
     * 
     * @param whaleAddress The address we are copying
     * @param marketTitle  The market name
     * @param outcome      The outcome (Yes/No)
     * @param type         The action (Buy/Sell)
     */
    public void executeCopyTrade(String whaleAddress, String marketTitle, String outcome, String type) {
        // In simulation mode, we just log and notify.
        // 在模拟模式下，我们只记录和通知。

        String logMsg = String.format(
                "[%s] Copying Trade!\nWhale: %s\nMarket: %s\nOutcome: %s\nAction: %s\nAmount: $%.2f",
                isSimulation ? "SIMULATION" : "REAL",
                whaleAddress, marketTitle, outcome, type, copyAmount);

        System.out.println(logMsg);
        notifier.sendAlert("📋 " + logMsg);

        if (!isSimulation) {
            // Real execution logic will go here in Phase 3.5
            // 真实的执行逻辑将在第 3.5 阶段放在这里
            System.err.println("Real trading not yet implemented in Java CLOB client.");
        }
    }
}
