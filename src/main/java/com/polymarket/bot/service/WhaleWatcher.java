package com.polymarket.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Monitors the Goldsky Subgraph for whale activity.
 * 监控 Goldsky 子图中的巨鲸活动。
 */
public class WhaleWatcher {

    private final TelegramNotifier notifier;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    // In Phase 1, we hardcode or fetch a small list.
    // For simplicity in this demo step, let's keep a hardcoded list of "known
    // whales" or just fetch top 5.
    // 在第一阶段，我们硬编码或获取一个小列表。
    // 为了简化这个演示步骤，我们保留一个硬编码的“已知巨鲸”列表或仅获取前 5 名。
    private final Set<String> watchedAddresses = new HashSet<>();

    // Goldsky Subgraph URLs (Example public endpoints, may need specific project
    // IDs in production)
    // Goldsky 子图 URL（示例公共端点，生产环境可能需要特定项目 ID）
    // NOTE: Using a placeholders. Ideally investigate specific live URL.
    // Using a generic structure for now based on research.
    private static final String PNL_SUBGRAPH_URL = "https://api.goldsky.com/api/public/project_cl6mb8i9h0003e201j6li0diw/subgraphs/pnl-subgraph/0.0.14/gn";
    private static final String ACTIVITY_SUBGRAPH_URL = "https://api.goldsky.com/api/public/project_cl6mb8i9h0003e201j6li0diw/subgraphs/activity-subgraph/0.0.4/gn";

    private long lastCheckedTimestamp = System.currentTimeMillis() / 1000;

    public WhaleWatcher(TelegramNotifier notifier) {
        this.notifier = notifier;
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();

        // Load manual watchlist from env / 从环境变量加载手动观察列表
        Dotenv dotenv = Dotenv.load();
        String manualList = dotenv.get("MANUAL_WATCHLIST");
        if (manualList != null && !manualList.isEmpty()) {
            String[] addresses = manualList.split(",");
            for (String addr : addresses) {
                String cleanAddr = addr.trim();
                if (!cleanAddr.isEmpty()) {
                    watchedAddresses.add(cleanAddr);
                    System.out.println("Added manual watch address: " + cleanAddr + " / 已添加手动观察地址：" + cleanAddr);
                }
            }
        }

        // Initial "Scout": Fetch Top Traders (Simplification: Monitoring a dummy
        // address if fetch fails)
        // 初始“侦察”：获取顶级交易者（简化：如果获取失败，则监控一个虚拟地址）
        // Implementation TODO: Add full GraphQL Query for top users.
        // 实现待办：添加获取顶级用户的完整 GraphQL 查询。
    }

    /**
     * Polls for new trades.
     * 轮询新交易。
     */
    public void poll() {
        try {
            // 1. Refresh "Whales" list occasionally (e.g. if empty) / 偶尔刷新“巨鲸”列表（例如，如果为空）
            if (watchedAddresses.isEmpty()) {
                fetchTopTraders();
            }

            System.out.println("Polling for whale activity... / 正在轮询巨鲸活动...");

            // 2. Query recent activity (Transactions/Trades) / 查询最近活动（交易）
            // Schema guess: multifillOrders or fpmmTrade (Fixed Product Market Maker Trade)
            // Using a query for `fpmmTrades` which is common for Prediction Markets
            // (Gnosis/Polymarket)
            // schema 猜测：multifillOrders 或 fpmmTrade (固定产品做市商交易)
            // 使用 `fpmmTrades` 查询，这对预测市场 (Gnosis/Polymarket) 来说很常见
            String query = String.format(
                    "{ \"query\": \"{ fpmmTrades(first: 20, orderBy: creationTimestamp, orderDirection: desc, where: { creationTimestamp_gt: \\\"%d\\\" }) { id creationTimestamp title outcomeIndex type amount collateralAmount creator { id } } }\" }",
                    lastCheckedTimestamp);

            Request request = new Request.Builder()
                    .url(ACTIVITY_SUBGRAPH_URL)
                    .post(RequestBody.create(query, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonNode root = mapper.readTree(response.body().string());
                    JsonNode trades = root.path("data").path("fpmmTrades");

                    if (trades.isArray()) {
                        long maxTimestamp = lastCheckedTimestamp;

                        for (JsonNode trade : trades) {
                            String creator = trade.path("creator").path("id").asText().toLowerCase();
                            long timestamp = trade.path("creationTimestamp").asLong();

                            // Keep track of latest time
                            if (timestamp > maxTimestamp) {
                                maxTimestamp = timestamp;
                            }

                            // Check if this is one of our watched whales
                            // 检查这是否是我们关注的巨鲸之一
                            if (watchedAddresses.contains(creator)) {
                                String title = trade.path("title").asText();
                                String type = trade.path("type").asText(); // Buy/Sell
                                String amount = trade.path("collateralAmount").asText(); // USDC Amount
                                String outcome = trade.path("outcomeIndex").asText(); // Yes/No index

                                String msg = String.format(
                                        "🚨 *Whale Alert!* 巨鲸警报!\n\nUser: `%s`\nAction: %s\nMarket: %s\nAmount: $%s USDC\nOutcome: %s",
                                        creator, type, title, amount, outcome);

                                notifier.sendAlert(msg);
                                System.out.println("Alert sent for: " + creator);
                            }
                        }
                        // Update last checked time to avoid duplicates
                        // 更新上次检查时间以避免重复
                        lastCheckedTimestamp = maxTimestamp;
                    }
                } else {
                    System.err.println("Failed to poll activity: " + response.code() + " " + response.message());
                }
            } catch (Exception e) {
                System.err.println("Network error polling activity: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Error in poll loop: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void fetchTopTraders() {
        // Construct GraphQL query for users sorted by profit
        // 构建按利润排序的用户的 GraphQL 查询
        String query = "{ \"query\": \"{ users(first: 5, orderBy: profit, orderDirection: desc) { id profit } }\" }";

        Request request = new Request.Builder()
                .url(PNL_SUBGRAPH_URL)
                .post(RequestBody.create(query, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode root = mapper.readTree(responseBody);
                JsonNode users = root.path("data").path("users");

                if (users.isArray()) {
                    System.out.println("---- Top Whales (Profit) / 顶级巨鲸 (利润) ----");
                    for (JsonNode user : users) {
                        String address = user.path("id").asText();
                        double profit = user.path("profit").asDouble();
                        watchedAddresses.add(address);
                        System.out.printf("Whale: %s | Profit: $%.2f%n", address, profit);
                    }
                    if (users.size() > 0) {
                        notifier.sendAlert("🐳 Found " + users.size() + " Top Whales on startup! / 启动时发现了 "
                                + users.size() + " 名顶级巨鲸！");
                    }
                }
            } else {
                System.err.println("Failed to fetch whales: " + response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
