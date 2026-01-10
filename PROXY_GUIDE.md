# Why "Global Mode" (全局模式) is not enough for Java

Even if you enable **Global Mode** in Clash/VPN, Java applications (like this bot) often **ignore** the system proxy settings and try to connect directly to the internet. 

Because `api.telegram.org` is blocked in your region, direct connections fail with `ConnectException` or `Timeout`.

即使您在 Clash/VPN 中启用了 **全局模式**，Java 应用程序（如本机器人）通常也会 **忽略** 系统代理设置，并尝试直接连接到互联网。

由于 `api.telegram.org` 在您所在的地区被阻止，直接连接会因 `ConnectException` 或 `Timeout` 而失败。

## Implementation / 解决方案

We have updated the code to explicitly read proxy settings from `.env` and force Java to use them.
我们已更新代码，以从 `.env` 显式读取代理设置并强制 Java 使用它们。

### Step 1: Find your Proxy Port / 第一步：找到您的代理端口
*   **Clash**: Usually `7890` (Mixed/HTTP)
*   **V2Ray / U / N**: Usually `10809` (HTTP)

### Step 2: Edit `.env` file / 第二步：编辑 `.env` 文件

Open your `.env` file and add/uncomment these lines:
打开您的 `.env` 文件并添加/取消注释以下行：

```properties
HTTP_PROXY_HOST=127.0.0.1
HTTP_PROXY_PORT=7890
```
(Replace `7890` with your actual port if different)
（如果有不同，请将 `7890` 替换为您的实际端口）

### Step 3: Restart Bot / 第三步：重启机器人

```bash
mvn clean compile exec:java
```

Now the bot will say:
现在机器人会显示：
> `Using Proxy: 127.0.0.1:7890`
