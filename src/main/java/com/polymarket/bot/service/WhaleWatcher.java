package com.polymarket.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

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
    private final Set<String> watchedAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> manualWatchlist = ConcurrentHashMap.newKeySet();
    private boolean initialDiscoveryDone = false;

    // Goldsky Subgraph URLs (Example public endpoints, may need specific project
    // IDs in production)
    // Goldsky 子图 URL（示例公共端点，生产环境可能需要特定项目 ID）
    // NOTE: Using a placeholders. Ideally investigate specific live URL.
    // Using a generic structure for now based on research.
    private static final String PNL_SUBGRAPH_URL = "https://api.goldsky.com/api/public/project_cl6mb8i9h0003e201j6li0diw/subgraphs/pnl-subgraph/0.0.14/gn";
    private static final String ACTIVITY_SUBGRAPH_URL = "https://api.goldsky.com/api/public/project_cl6mb8i9h0003e201j6li0diw/subgraphs/activity-subgraph/0.0.4/gn";

    private long lastCheckedTimestamp = System.currentTimeMillis() / 1000;

    private final int maxDailyTrades;
    private final double minWinRate;

    private final TradeExecutor tradeExecutor;

    public WhaleWatcher(TelegramNotifier notifier, TradeExecutor tradeExecutor) {
        this.notifier = notifier;
        this.tradeExecutor = tradeExecutor;
        this.mapper = new ObjectMapper();

        // Load dotenv first needed for Proxy config
        Dotenv dotenv = Dotenv.load();

        // Configure Proxy for OkHttp (Goldsky API)
        String proxyHost = dotenv.get("HTTP_PROXY_HOST");
        String proxyPort = dotenv.get("HTTP_PROXY_PORT");

        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null && !proxyPort.isEmpty()) {
            java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                    new java.net.InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .proxy(proxy)
                    .build();
            System.out.println("WhaleWatcher using HTTP Proxy: " + proxyHost + ":" + proxyPort);
        } else {
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }

        // Load manual watchlist from env / 从环境变量加载手动观察列表
        String manualList = dotenv.get("MANUAL_WATCHLIST");
        if (manualList != null && !manualList.isEmpty()) {
            String[] addresses = manualList.split(",");
            for (String addr : addresses) {
                String cleanAddr = addr.trim();
                if (!cleanAddr.isEmpty()) {
                    watchedAddresses.add(cleanAddr);
                    manualWatchlist.add(cleanAddr);
                    System.out.println("Added manual watch address: " + cleanAddr + " / 已添加手动观察地址：" + cleanAddr);
                }
            }
        }

        // Load filter config / 加载过滤配置
        String maxTradesStr = dotenv.get("MAX_DAILY_TRADES");
        this.maxDailyTrades = (maxTradesStr != null) ? Integer.parseInt(maxTradesStr) : 50;

        String minWinRateStr = dotenv.get("MIN_WIN_RATE");
        this.minWinRate = (minWinRateStr != null) ? Double.parseDouble(minWinRateStr) : 0.60;

        System.out.println("Bot Filter: Max Daily Trades = " + maxDailyTrades + ", Min Win Rate = " + minWinRate);

        // Initial "Scout": Fetch Top Traders (Simplification: Monitoring a dummy
        // address if fetch fails)
        // 初始“侦察”：获取顶级交易者（简化：如果获取失败，则监控一个虚拟地址）
        // Implementation TODO: Add full GraphQL Query for top users.
        // 实现待办：添加获取顶级用户的完整 GraphQL 查询。
    }

    /**
     * Sends a fake alert for testing purposes.
     * 发送用于测试目的的伪造警报。
     */
    public void sendTestAlert() {
        String fakeUser = "0x1234567890abcdef1234567890abcdef12345678";
        String title = "Trump vs Harris 2024 Election Winner";
        String outcome = "Yes";
        String type = "Buy";
        String amount = "1000.00";

        String msg = "🧪 *TEST ALERT / 测试警报*\n\nUser: `" + fakeUser + "`\nAction: " + type + "\nMarket: " + title
                + "\nAmount: $" + amount + " USDC\nOutcome: " + outcome;

        notifier.sendAlert(msg);
        System.out.println("Sent TEST alert.");

        // Also simulate execution
        tradeExecutor.executeCopyTrade(fakeUser, title, outcome, type);
    }

    /**
     * Tests connection to Goldsky by fetching 1 global trade.
     * 通过获取 1 笔全球交易来测试与 Goldsky 的连接。
     */
    public boolean testConnection() {
        String query = "{ \"query\": \"{ fpmmTrades(first: 1, orderBy: creationTimestamp, orderDirection: desc) { creationTimestamp } }\" }";
        Request request = new Request.Builder()
                .url(ACTIVITY_SUBGRAPH_URL)
                .post(RequestBody.create(query, MediaType.parse("application/json")))
                .build();

        System.out.println("Testing Goldsky Connection... / 正在测试 Goldsky 连接...");
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                System.out.println("✅ Connection Successful! (Status: " + response.code() + ") / 连接成功！（状态："
                        + response.code() + ")");
                return true;
            } else {
                System.err.println("❌ Connection Failed. Status: " + response.code());
                System.err.println("❌ Data: " + (response.body() != null ? response.body().string() : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ Connection Error (Network/Proxy problem?): " + e.getMessage());
            System.err.println("❌ 连接错误（网络/代理问题？）：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Polls for new trades.
     * 轮询新交易。
     */
    public void poll() {
        try {
            // 1. Refresh "Whales" list on startup (Auto-Discovery)
            // 启动时刷新“巨鲸”列表（自动发现）
            if (!initialDiscoveryDone) {
                fetchTopTraders();
                initialDiscoveryDone = true;
            }

            System.out.println("Polling activity for " + watchedAddresses.size() + " whales... / 正在为 "
                    + watchedAddresses.size() + " 个巨鲸轮询活动...");

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

                                // Check type of whale / 检查巨鲸类型
                                boolean isManual = manualWatchlist.contains(creator);
                                String alertTitle = isManual ? "🚨 *Whale Alert!* 巨鲸警报!"
                                        : "🔍 *Smart Money Alert* 聪明钱警报";

                                String msg = String.format(
                                        "%s\n\nUser: `%s`\nAction: %s\nMarket: %s\nAmount: $%s USDC\nOutcome: %s",
                                        alertTitle, creator, type, title, amount, outcome);

                                notifier.sendAlert(msg);
                                System.out.println("Alert sent for: " + creator);

                                // Execute Copy Trade ONLY for manual list / 仅为手动列表执行跟单交易
                                if (isManual) {
                                    tradeExecutor.executeCopyTrade(creator, title, outcome, type);
                                } else {
                                    System.out.println("Observation only (Smart Money): " + creator);
                                }
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

    /**
     * Checks if an address behaves like a bot (high frequency).
     * 检查地址是否像机器人一样行为（高频）。
     * 
     * @param address The address to check / 要检查的地址
     * @return true if bot / 如果是机器人则返回 true
     */
    private boolean isPotentialBot(String address) {
        // Query trades in last 24 hours / 查询过去 24 小时的交易
        long oneDayAgo = (System.currentTimeMillis() / 1000) - 86400;

        // We ask for (maxDailyTrades + 1) items. If we get that many, it's a bot.
        // 我们请求 (maxDailyTrades + 1) 个条目。如果得到那么多，那就是机器人。
        String query = String.format(
                "{ \"query\": \"{ fpmmTrades(first: %d, where: { creator: \\\"%s\\\", creationTimestamp_gt: \\\"%d\\\" }) { id } }\" }",
                maxDailyTrades + 1, address, oneDayAgo);

        Request request = new Request.Builder()
                .url(ACTIVITY_SUBGRAPH_URL)
                .post(RequestBody.create(query, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonNode root = mapper.readTree(response.body().string());
                JsonNode trades = root.path("data").path("fpmmTrades");
                if (trades.isArray()) {
                    int count = trades.size();
                    if (count > maxDailyTrades) {
                        System.out.println("⚠️ Detected Bot: " + address + " (" + count + " trades/24h) / 检测到机器人："
                                + address + " (" + count + " 笔交易/24小时)");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking bot status for " + address + ": " + e.getMessage());
        }
        return false;
    }

    private boolean checkWinRate(String address, double profit) {
        // Since we don't have a guaranteed 'winRate' field in the public schema without
        // verifying,
        // We will currently use a heuristic: Must be Profitable.
        // 由于在未验证的情况下公共 Schema 中没有保证的 'winRate' 字段，
        // 我们目前使用启发式方法：必须盈利。

        // In a real production app, we would query: { user(id: "...") { stats { winRate
        // } } }
        // 在真实的生产应用中，我们会查询：{ user(id: "...") { stats { winRate } } }

        // For now, if Profit is very high, we assume they are "Winning".
        // 目前，如果利润很高，我们假设他们是“赢家”。
        if (profit <= 0) {
            System.out.println("Skipping Low Profit user: " + address);
            return false;
        }

        // TODO: Implement actual field query when Schema is available.
        // 待办：可用时实施实际字段查询。
        return true;
    }

    private void fetchTopTraders() {
        // Construct GraphQL query for users sorted by profit
        // 构建按利润排序的用户的 GraphQL 查询
        // Fetching top 50 to filter down to top 20 humans
        // 获取前 50 名以过滤出前 20 名真人
        String query = "{ \"query\": \"{ users(first: 50, orderBy: profit, orderDirection: desc) { id profit } }\" }";

        Request request = new Request.Builder()
                .url(PNL_SUBGRAPH_URL)
                .post(RequestBody.create(query, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode root = mapper.readTree(responseBody);
                JsonNode usersNode = root.path("data").path("users");

                if (usersNode.isArray()) {
                    System.out.println("---- Top Whales Analysis (Profit) [Parallel] / 顶级巨鲸分析 (利润) [并行] ----");

                    // Convert JsonNode to List for parallel streaming
                    List<JsonNode> candidates = new ArrayList<>();
                    usersNode.forEach(candidates::add);

                    // Parallel Stream to check Bot Status & Win Rate concurrently
                    // 并行流以并发检查机器人状态和胜率
                    List<String> validWhales = candidates.parallelStream()
                            .filter(user -> {
                                String address = user.path("id").asText();
                                double profit = user.path("profit").asDouble();

                                // Check for Bot / 检查机器人
                                if (isPotentialBot(address)) {
                                    return false;
                                }
                                // Check Win Rate / 检查胜率
                                if (!checkWinRate(address, profit)) {
                                    return false;
                                }
                                return true;
                            })
                            .map(user -> user.path("id").asText())
                            .limit(20) // Take top 20 valid ones
                            .toList();

                    // Add to watched list
                    validWhales.forEach(addr -> {
                        watchedAddresses.add(addr);
                        System.out.println("✅ Smart Money Added: " + addr);
                    });

                    if (!validWhales.isEmpty()) {
                        String msg = "🐳 Found " + validWhales.size()
                                + " Smart Money Whales (Parallel Scan)! / 并行扫描发现了 " + validWhales.size() + " 名聪明钱巨鲸！";
                        notifier.sendAlert(msg);
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
