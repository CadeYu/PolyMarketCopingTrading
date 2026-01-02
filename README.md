# Polymarket Copy Trading Bot / Polymarket 跟单交易机器人

## Overview / 概述
A High-Frequency Trading (HFT) bot designed to copy the trades of top-performing accounts ("whales") on Polymarket.
一个专为复制 Polymarket 上表现最佳账户（“巨鲸”）交易而设计的高频交易 (HFT) 机器人。

## Phases / 阶段
1.  **Phase 1: Monitor & Notify** (Current) - Track whales and send Telegram alerts.
    **第一阶段：监控与通知**（当前） - 追踪巨鲸并发送 Telegram 报警。
2.  **Phase 2: Analyst** - Filter bots vs humans.
    **第二阶段：分析师** - 过滤机器人与真人。
3.  **Phase 3: Execution** - Auto copy trading.
    **第三阶段：执行** - 自动跟单。

## Setup / 设置
1.  **Install Java 17+ & Maven**
    **安装 Java 17+ 和 Maven**
2.  **Clone Repo**
    **克隆仓库**
3.  **Configure `.env`**
    **配置 `.env`**
    ```bash
    cp .env.example .env
    # Edit .env with your Telegram Keys
    # 编辑 .env 填入你的 Telegram 密钥
    ```
4.  **Run / 运行**
    ```bash
    mvn clean compile exec:java
    ```

## Disclaimer / 免责声明
This software is for educational purposes only. Use at your own risk.
本软件仅供教育目的使用。使用风险自负。
