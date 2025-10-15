-- 1. exchange_project —— 项目主表 / 每个项目的身份证
CREATE TABLE `exchange_project`
(
    `id`                 BIGINT                              NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `exchange`           ENUM ('OKX','BINANCE')              NOT NULL COMMENT '交易所标识, 取值, OKX/BINANCE',
    `leader_external_id` VARCHAR(191)                        NOT NULL COMMENT '平台侧项目/领航员稳定外部ID (逻辑唯一)',
    `name`               VARCHAR(255)                        NULL COMMENT '项目/领航员名称',
    `status`             VARCHAR(64)                         NULL COMMENT '运行状态 (如RUNNING/CLOSED/PAUSED等, 文本)',
    `first_seen`         TIMESTAMP(3)                        NOT NULL COMMENT '首次被系统发现时间',
    `last_seen`          TIMESTAMP(3)                        NOT NULL COMMENT '最近一次被看到时间',
    `last_visibility`    ENUM ('VISIBLE','MISSING','HIDDEN') NOT NULL DEFAULT 'VISIBLE' COMMENT '最近可见性, VISIBLE/MISSING/HIDDEN',
    `min_copy_cost`      DECIMAL(36, 18)                     NULL COMMENT '最小可复制保证金/成本 (USDT口径)',
    `base_currency`      VARCHAR(16)                         NULL     DEFAULT 'USDT' COMMENT '计价币 (默认USDT)',
    `data_quality_score` FLOAT                               NULL     DEFAULT 1.0 COMMENT '数据质量分 (0~1)',
    `extra`              JSON                                NOT NULL COMMENT '平台特有原始字段 (JSON)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exchange_leader` (`exchange`, `leader_external_id`),
    KEY `idx_exchange` (`exchange`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='项目主档, 供快照/交易/墓碑等表引用 (逻辑外键) ';

-- 2. exchange_project_snapshot —— 项目时序快照 (含可见性) [按月分区] 记录项目数据变化, 包括收益, 持仓, 跟随人数, 可见性等
CREATE TABLE `exchange_project_snapshot`
(
    `id`             BIGINT                              NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`     BIGINT                              NOT NULL COMMENT '逻辑外键, 指向 exchange_project.id',
    `ts`             TIMESTAMP(3)                        NOT NULL COMMENT '快照时间',
    `data_ver`       CHAR(14)                            NULL COMMENT '来源数据版本号, 如 20231129213200 (OKX 榜单每10分钟一版, 仅保留最近5版)',
    `source`         VARCHAR(32)                         NOT NULL DEFAULT 'OKX_RANK' COMMENT '快照来源, OKX_RANK / OKX_DETAIL / COMPUTED 等, 区分不同来源的同一时刻快照',
    `visibility`     ENUM ('VISIBLE','MISSING','HIDDEN') NOT NULL COMMENT '可见性 (VISIBLE/MISSING/HIDDEN)',
    `equity`         DECIMAL(36, 18)                     NULL COMMENT '权益/AUM (USDT口径), 部分来源可能缺失',
    `followers`      INT                                 NULL COMMENT '跟随人数 (OKX 榜单 ranks[].copyTraderNum)',
    `positions_open` INT                                 NULL COMMENT '持仓笔数 (仅对包含持仓信息的来源有效)',
    `fees_daily`     DECIMAL(36, 18)                     NULL COMMENT '当日费用 (仅对包含费用信息的来源有效)',
    `pnl_daily`      DECIMAL(36, 18)                     NULL COMMENT '当日 PnL (仅对包含收益信息的来源有效)',
    `raw`            JSON                                NOT NULL COMMENT '原始快照 JSON; 按 source 存放对应来源的原文 (便于审计与回放)',

    -- 常用排序/筛选项做生成列, 避免每次查询解析 JSON
    `aum_usd`        DECIMAL(36, 18)
        GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(`raw`, '$.aum')) AS DECIMAL(36, 18))) STORED
        COMMENT '生成列, raw($.aum) → AUM(USDT), 便于筛选/排序',
    `win_ratio`      DECIMAL(18, 6)
        GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(`raw`, '$.winRatio')) AS DECIMAL(18, 6))) STORED
        COMMENT '生成列, raw($.winRatio) → 胜率 (0.1=10%)',
    `pnl_ratio_90d`  DECIMAL(18, 6)
        GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(`raw`, '$.pnlRatio')) AS DECIMAL(18, 6))) STORED
        COMMENT '生成列, raw($.pnlRatio) → 近90日收益率',
    `pnl_90d_usd`    DECIMAL(36, 18)
        GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(`raw`, '$.pnl')) AS DECIMAL(36, 18))) STORED
        COMMENT '生成列, raw($.pnl) → 近90日收益 (USDT)',

    PRIMARY KEY (`id`),

    -- 幂等唯一键, 同一项目 + 同一时间 + 同一来源 只允许一条快照
    UNIQUE KEY `uk_proj_ts_src` (`project_id`, `ts`, `source`),

    KEY `idx_proj_ts` (`project_id`, `ts`) USING BTREE,
    KEY `idx_snapshot_aum` (`aum_usd`) USING BTREE,
    KEY `idx_snapshot_win_ratio` (`win_ratio`) USING BTREE,
    KEY `idx_snapshot_pnl_ratio` (`pnl_ratio_90d`) USING BTREE
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '每次采集的项目时序快照 (按来源区分), 用于画像/排序与可见性监测'
    PARTITION BY RANGE COLUMNS (`ts`) (
        -- 已存在历史分区, 覆盖 2025-08
        PARTITION p2025_08 VALUES LESS THAN ('2025-09-01'),
        -- 预创建未来分区, 覆盖 2025-09, 2025-10
        PARTITION p2025_09 VALUES LESS THAN ('2025-10-01'),
        PARTITION p2025_10 VALUES LESS THAN ('2025-11-01'),
        -- 兜底最大分区, 所有更未来的时间写入此分区, 后续再 REORGANIZE 拆分
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );

-- 3. exchange_project_trade —— 项目开平仓回合明细[按月分区] 记录每个项目中的交易明细
CREATE TABLE `exchange_project_trade`
(
    -- 基础字段
    `id`                  BIGINT                                               NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`          BIGINT                                               NOT NULL COMMENT '逻辑外键, 指向 exchange_project.id',
    `trade_uid`           CHAR(64)                                             NULL COMMENT '系统内回合唯一ID, 无外部ID时为合成ID(SHA-256) ',
    `symbol`              VARCHAR(64)                                          NOT NULL COMMENT '交易标的 (如 BTC-USDT 或 BTC-USDT-SWAP)',
    `inst_type`           ENUM ('SPOT','SWAP','FUTURES','MARGIN')              NULL COMMENT '产品类型 (可为空)',
    `side`                ENUM ('LONG','SHORT','BUY','SELL')                   NOT NULL COMMENT '方向: LONG/SHORT(合约) 或 BUY/SELL(现货)',
    `ord_type`            ENUM ('MARKET','LIMIT','POST_ONLY','FOK','IOC')      NULL COMMENT '订单类型 (可为空)',
    `leverage`            DECIMAL(18, 8)                                       NULL COMMENT '杠杆 (若适用)',

    -- 开平仓时间
    `ts_open`             TIMESTAMP(3)                                         NOT NULL COMMENT '开仓/触发时间',
    `ts_filled`           TIMESTAMP(3)                                         NULL COMMENT '完全成交时间 (可空)',
    `ts_close`            TIMESTAMP(3)                                         NULL COMMENT '平仓时间 (可空)',

    -- 开平仓价格以及仓位
    `entry_price`         DECIMAL(36, 18)                                      NULL COMMENT '入场价格',
    `exit_price`          DECIMAL(36, 18)                                      NULL COMMENT '出场价格',
    `qty`                 DECIMAL(36, 18)                                      NOT NULL COMMENT '仓位 (恒正)',

    -- 手续费和收益
    `fees`                DECIMAL(36, 18)                                      NULL COMMENT '手续费等成本 (可为负)',
    `fee_ccy`             VARCHAR(16)                                          NULL     DEFAULT 'USDT' COMMENT '手续费币种',
    `pnl`                 DECIMAL(36, 18)                                      NULL COMMENT '实现盈亏 (可为负)',
    `pnl_ccy`             VARCHAR(16)                                          NULL     DEFAULT 'USDT' COMMENT 'PnL 计价币',

    -- 订单状态和来源
    `status`              ENUM ('OPEN','PARTIALLY_CLOSED','CLOSED','CANCELED') NOT NULL DEFAULT 'CLOSED' COMMENT '成交/持仓状态',
    `source`              VARCHAR(32)                                          NOT NULL DEFAULT 'OKX' COMMENT '来源, OKX/BINANCE/REPLAY/IMPORT 等',
    `external_trade_id`   VARCHAR(191)                                         NULL COMMENT '交易所侧成交ID (优先用作幂等键)',
    `external_order_id`   VARCHAR(191)                                         NULL COMMENT '交易所侧订单ID (一般锚定最早的开仓订单, 可空)',
    `source_payload_hash` CHAR(64)                                             NOT NULL COMMENT '来源原文的 SHA-256 (十六进制), 用于幂等与审计',

    -- 生成列, 自动计算持仓时长 (秒)
    `duration_sec`        INT GENERATED ALWAYS AS (
        IF(`ts_close` IS NULL, NULL, TIMESTAMPDIFF(SECOND, `ts_open`, `ts_close`))
        ) STORED COMMENT '持仓时长 (秒, 生成列)',

    PRIMARY KEY (`id`),

    -- 幂等唯一键 (两级) 优先用外部成交ID, 无外部ID时用自然键 (注意统一毫秒与小数规范)
    UNIQUE KEY `uk_trade_uid` (`project_id`, `trade_uid`),
    UNIQUE KEY `uk_proj_source_trade_id` (`project_id`, `source`, `external_trade_id`),
    UNIQUE KEY `uk_natural_composite` (`project_id`, `symbol`, `side`, `ts_open`, `entry_price`, `qty`, `source`),

    KEY `idx_proj_open` (`project_id`, `ts_open`) USING BTREE,
    KEY `idx_proj_close` (`project_id`, `ts_close`) USING BTREE,
    KEY `idx_proj_symbol_open` (`project_id`, `symbol`, `ts_open`) USING BTREE,
    KEY `idx_external_trade_id` (`external_trade_id`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '项目交易/持仓明细 (事实表) 驱动影子/实盘, 收益复算与报表'
    PARTITION BY RANGE COLUMNS (`ts_open`) (
        PARTITION p2025_08 VALUES LESS THAN ('2025-09-01'),
        PARTITION p2025_09 VALUES LESS THAN ('2025-10-01'),
        PARTITION p2025_10 VALUES LESS THAN ('2025-11-01'),
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );

-- 4. exchange_project_trade_metrics —— 项目交易指标派生表 [按月分区] 记录每个项目中的交易指标派生数据
CREATE TABLE `exchange_project_trade_metrics`
(
    `id`            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trade_id`      BIGINT          NOT NULL COMMENT '逻辑外键, 指向 exchange_project_trade.id (逻辑外键)',
    `project_id`    BIGINT          NOT NULL COMMENT '冗余以便按项目过滤/统计 (来自 trade.project_id)',
    `ts_open`       TIMESTAMP(3)    NOT NULL COMMENT '冗余以便与主表分区对齐 (来自 trade.ts_open)',

    `source`        VARCHAR(32)     NOT NULL DEFAULT 'COMPUTED' COMMENT '指标来源, COMPUTED/BACKFILL/IMPORT 等',
    `algo_ver`      VARCHAR(16)     NOT NULL DEFAULT 'v1' COMMENT '算法/口径版本, 用于多次重算的版本化管理',
    `bar_interval`  VARCHAR(8)      NULL COMMENT '行情粒度, 如 1s/1m/5m/15m/1h/1d',
    `price_source`  VARCHAR(16)     NULL COMMENT '价格口径, MID/LAST/BID/ASK',

    -- MAE/MFE, 以 entry_price 为基准的百分比 (多头, 最低→mae, 最高 → mfe, 空头相反), 以及线性换算的金额口径
    `mae_pct`       DECIMAL(18, 6)  NULL COMMENT '最大不利变动百分比 (多头, min, 空头, max, 通常为负)',
    `mfe_pct`       DECIMAL(18, 6)  NULL COMMENT '最大有利变动百分比 (多头, max, 空头, min, 通常为正)',
    `mae_usd`       DECIMAL(36, 18) NULL COMMENT '最大不利变动金额 (USDT 口径, 线性近似, mae_pct * entry_price * qty)',
    `mfe_usd`       DECIMAL(36, 18) NULL COMMENT '最大有利变动金额 (USDT 口径, 线性近似, mfe_pct * entry_price * qty)',
    `mae_ts`        TIMESTAMP(3)    NULL COMMENT '发生 MAE 的时间 (进入最大不利点的时刻)',
    `mfe_ts`        TIMESTAMP(3)    NULL COMMENT '发生 MFE 的时间 (进入最大有利点的时刻)',

    -- 扩展, 滑点, 回撤/回升, 后续若不需要可忽略写入
    `slippage_pct`  DECIMAL(18, 6)  NULL COMMENT '滑点百分比 (成交 vs 预期)',
    `slippage_usd`  DECIMAL(36, 18) NULL COMMENT '滑点金额 (USDT 口径)',
    `max_dd_pct`    DECIMAL(18, 6)  NULL COMMENT '区间最大回撤百分比 (策略内口径)',
    `max_ru_pct`    DECIMAL(18, 6)  NULL COMMENT '区间最大回升百分比 (策略内口径)',

    `quality_score` FLOAT           NULL     DEFAULT 1.0 COMMENT '数据质量分 (0~1), 低于阈值可标记无效',
    `extra`         JSON            NOT NULL COMMENT '计算上下文与参数原文 (如 bars 使用条数, 异常标记等)',

    PRIMARY KEY (`id`),

    -- 同一笔成交在同一算法版本 (可选还加 price_source/bar_interval) 只保留一份指标
    UNIQUE KEY `uk_trade_ver` (`trade_id`, `algo_ver`),

    KEY `idx_proj_open` (`project_id`, `ts_open`) USING BTREE,
    KEY `idx_mae_pct` (`mae_pct`) USING BTREE,
    KEY `idx_mfe_pct` (`mfe_pct`) USING BTREE
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '成交派生指标 (MAE/MFE/滑点/回撤等), 可多版本重算, 与事实表通过 trade_id 关联'
    PARTITION BY RANGE COLUMNS (`ts_open`) (
        PARTITION p2025_08 VALUES LESS THAN ('2025-09-01'),
        PARTITION p2025_09 VALUES LESS THAN ('2025-10-01'),
        PARTITION p2025_10 VALUES LESS THAN ('2025-11-01'),
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );

-- 5. tombstone —— 项目消失事件 (幸存者偏差的黑匣子)
CREATE TABLE `tombstone`
(
    `project_id`       BIGINT       NOT NULL COMMENT '逻辑外键, 指向 exchange_project.id',
    `fromTs`           TIMESTAMP(3) NOT NULL COMMENT '第一次从可见转为缺失/隐藏的时间',
    `toTs`             TIMESTAMP(3) NULL COMMENT '不可见状态结束时间 (为空则表示仍处于不可见)',
    `last_snapshot_id` BIGINT       NULL COMMENT '消失前最后一次快照ID (逻辑外键, 指向 exchange_project_snapshot.id)',
    `reason_code`      VARCHAR(16)  NULL COMMENT '原因代码 (如下架/清退/改名等)',
    `reason_msg`       VARCHAR(255) NULL COMMENT '原因信息 (如下架/清退/改名等)',
    `detector`         VARCHAR(128) NULL COMMENT '触发来源',
    `created_at`       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`project_id`, `fromTs`),
    KEY `idx_last_snap` (`last_snapshot_id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='记录可见性由visible→missing/hidden的事件';

-- 6. crawl_log —— 抓取调用日志 (观测与去重)
CREATE TABLE `crawl_log`
(
    `id`                  BIGINT                 NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`             VARCHAR(191)           NOT NULL COMMENT '所属任务ID/自然键串',
    `exchange`            ENUM ('OKX','BINANCE') NOT NULL COMMENT '交易所',
    `target`              VARCHAR(512)           NOT NULL COMMENT '规范化抓取目标(URL/资源标识)',
    `method`              VARCHAR(16)            NOT NULL COMMENT 'HTTP方法(GET/POST/...)',
    `request_params_json` JSON                   NULL COMMENT '规范化参数JSON',
    `params_hash`         CHAR(64)               NOT NULL COMMENT '规范化参数哈希(SHA-256等)',
    `started_at`          TIMESTAMP(3)           NOT NULL COMMENT '开始时间(UTC)',
    `finished_at`         TIMESTAMP(3)           NOT NULL COMMENT '结束时间(UTC)',
    `status_code`         INT                    NULL COMMENT 'HTTP状态码(失败也尽量记录)',
    `success`             BOOLEAN                NOT NULL COMMENT '是否成功(2xx或304)',
    `not_modified`        BOOLEAN                NOT NULL DEFAULT FALSE COMMENT '是否304/短路',
    `content_length`      BIGINT                 NULL COMMENT '响应字节数',
    `etag`                VARCHAR(255)           NULL COMMENT 'ETag',
    `last_modified_raw`   VARCHAR(255)           NULL COMMENT 'Last-Modified原始值',
    `last_modified_at`    TIMESTAMP(3)           NULL COMMENT 'Last-Modified解析(UTC)',
    `content_hash`        CHAR(64)               NULL COMMENT '内容哈希(SHA-256等)',
    `error_msg`           TEXT                   NULL COMMENT '错误信息',
    PRIMARY KEY (`id`),
    KEY `idx_task_time` (`task_id`, `started_at` DESC),
    KEY `idx_exchange_target_time` (`exchange`, `target`, `started_at` DESC),
    KEY `idx_target_notmod` (`exchange`, `target`, `not_modified`),
    KEY `idx_params_time` (`exchange`, `params_hash`, `started_at` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='采集日志(不可变事实), 支持304/短路, 审计与统计';


-- 7. crawl_task —— 抓取任务, 做意图级 (任务级) 去重, 避免单一任务时间超长重入,
DROP TABLE IF EXISTS `crawl_task`;
CREATE TABLE `crawl_task`
(
    `id`           BIGINT                 NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `exchange`     ENUM ('OKX','BINANCE') NOT NULL COMMENT '目标交易所',
    `api_name`     VARCHAR(96)            NOT NULL COMMENT 'API 名称',
    `params_hash`  CHAR(64)               NOT NULL COMMENT '规范化参数哈希(SHA-256等)',
    `params_json`  TEXT                   NULL COMMENT '规范化参数JSON(建议TEXT以兼容驱动/ORM)',
    `window_key`   VARCHAR(128)           NOT NULL COMMENT '时间窗口键',
    `total_page`   INT                    NULL COMMENT '总页数(允许NULL=未知)',
    `next_page`    INT                    NOT NULL DEFAULT 1 COMMENT '下一页(1-based)',
    `status`       ENUM ('PENDING','RUNNING','DONE','FAILED','EXPIRED','CANCELLED')
                                          NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    `attempts`     INT                    NOT NULL DEFAULT 0 COMMENT '错误累计次数',
    `last_error`   TEXT                   NULL COMMENT '最后一次错误信息',
    `locked_by`    VARCHAR(191)           NULL COMMENT '当前锁持有者',
    `locked_at`    TIMESTAMP(3)           NULL COMMENT '加锁时间(UTC)',
    `lock_ttl_sec` INT                    NULL COMMENT '锁TTL(秒)',
    /* 便于查询过期锁, 生成列 */
    `locked_until` TIMESTAMP(3) GENERATED ALWAYS AS
        (IF(`locked_at` IS NULL OR `lock_ttl_sec` IS NULL, NULL,
            TIMESTAMPADD(SECOND, `lock_ttl_sec`, `locked_at`))) STORED COMMENT '锁过期时间=locked_at+ttl',
    `created_at`   TIMESTAMP(3)           NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   TIMESTAMP(3)           NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),

    /* 幂等唯一键, 同一时间窗口内, 同一意图只创建一次 */
    UNIQUE KEY `uk_task_intent` (`exchange`, `api_name`, `params_hash`, `window_key`),

    /* 调度常用索引, 根据状态和锁过期时间快速扫描可抢占任务 */
    KEY `idx_status_lockeduntil` (`status`, `locked_until`),
    KEY `idx_exchange_api_status` (`exchange`, `api_name`, `status`),

    /* 健壮性检查 (MySQL 8.0.16+才强制执行; 旧版仅作为文档) */
    CHECK (`next_page` >= 1),
    CHECK (`total_page` IS NULL OR `total_page` >= 0),
    CHECK (`lock_ttl_sec` IS NULL OR `lock_ttl_sec` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='爬取任务聚合, 意图级去重/锁/状态机/分页';

-- 新增 2025-09 月分区 (< '2025-10-01') 示例, exchange_project_snapshot (列 ts) 
ALTER TABLE `exchange_project_snapshot`
    REORGANIZE PARTITION pMAX INTO (
        PARTITION p2025_09 VALUES LESS THAN ('2025-10-01'),
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );

-- 新增 2025-09 月分区 (< '2025-10-01') 示例, exchange_project_trade (列 ts_open) 
ALTER TABLE `exchange_project_trade`
    REORGANIZE PARTITION pMAX INTO (
        PARTITION p2025_09 VALUES LESS THAN ('2025-10-01'),
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );

-- 读模型, 项目日聚合 (用作30/90天窗口的增量砖块) 
CREATE TABLE `rm_project_day`
(
    `project_id`  BIGINT          NOT NULL COMMENT '项目ID (逻辑外键, 指向 exchange_project.id, 不建物理外键, 便于补数与容错)',
    `day`         DATE            NOT NULL COMMENT '统计自然日 (UTC), 形如 2025-10-14, 同一项目+同一天唯一',
    -- 来自 snapshot 的日度聚合
    `pnl_daily`   DECIMAL(36, 18) NULL COMMENT '当日快照累计净盈亏 (USDT口径), 来自 exchange_project_snapshot 当日最后一条快照 pnl_daily 值',
    `equity_avg`  DECIMAL(36, 18) NULL COMMENT '当日权益/AUM 的平均值, 用于 ROI 分母的稳健估计, 计算方式为 AUM 值的时间间隔加权平均',
    -- 来自 trade / metrics 的日度聚合(按 ts_open/ts_close 落在该日内的回合口径) 
    `trade_cnt`   INT             NOT NULL DEFAULT 0 COMMENT '当日回合数量 (开平仓回合条数)',
    `win_cnt`     INT             NOT NULL DEFAULT 0 COMMENT '当日盈利回合数量 (pnl>0 的回合数)',
    `pnl_sum`     DECIMAL(36, 18) NULL COMMENT '当日回合盈亏合计 (USDT口径), 来自 exchange_project_trade.pnl 的求和',
    `mae_p50`     DECIMAL(18, 6)  NULL COMMENT '当日回合 MAE 百分比的中位数 (多头负为不利, 空头同口径对称, 后端计算后回写)',
    `mae_p95`     DECIMAL(18, 6)  NULL COMMENT '当日回合 MAE 百分比的95分位 (极端不利偏移的稳健上界)',
    `maxdd_p95`   DECIMAL(18, 6)  NULL COMMENT '当日区间最大回撤百分比的95分位 (路径层面跌幅风险的高分位)',
    `dur_p50_sec` INT             NULL COMMENT '当日回合持仓时长 (秒) 的中位数 (衡量典型持仓风格)',
    `quality_avg` FLOAT           NULL COMMENT '当日回合指标的平均质量分 (0~1, 来自 metrics.quality_score 的均值)',
    `updated_at`  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '该行最后更新时间 (由写入Job维护)',
    PRIMARY KEY (`project_id`, `day`),
    KEY `idx_day` (`day`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='读模型, 项目日聚合, 把快照与回合/指标按自然日打平, 作为窗口滚动聚合的基础 日砖块';

-- 读模型, 窗口KPI物化 (列表/筛选/排序直连) 
CREATE TABLE `rm_project_kpi_window`
(
    `project_id`  BIGINT          NOT NULL COMMENT '项目ID (逻辑外键, 指向 exchange_project.id, 不建物理外键)',
    `window_days` INT             NOT NULL COMMENT '滚动窗口长度 (单位: 天), 如 30/90 等, 作为度量口径的一部分',
    `as_of_ts`    TIMESTAMP(3)    NOT NULL COMMENT '该窗口聚合的"统计时点"时间戳 (Job写入时的时间), 用于观测新旧数据交替',
    -- KPI: 由 rm_project_day 在后端滚动聚合而来
    `roi`         DECIMAL(18, 6)  NULL COMMENT '窗口ROI = Σ(pnl_daily)/AVG(equity_avg), 对空分母做 NULL 保护',
    `pnl_sum`     DECIMAL(36, 18) NULL COMMENT '窗口净盈亏合计 (USDT口径)',
    `trade_cnt`   INT             NOT NULL DEFAULT 0 COMMENT '窗口内回合数量',
    `win_rate`    DECIMAL(18, 6)  NULL COMMENT '窗口胜率 = Σ(win_cnt)/Σ(trade_cnt), 为空或无交易时为 NULL',
    `mae_p95`     DECIMAL(18, 6)  NULL COMMENT '窗口 MAE 百分比95分位 (越接近0越不扛单)',
    `maxdd_p95`   DECIMAL(18, 6)  NULL COMMENT '窗口区间最大回撤百分比的95分位 (路径回撤风险的高分位)',
    `dur_p50_sec` INT             NULL COMMENT '窗口内持仓时长 (秒) 的中位数 (风格节奏)',
    `quality_avg` FLOAT           NULL COMMENT '窗口内质量分平均值 (0~1), 低质量样本可在Job侧剔除或降权',
    `score`       DECIMAL(18, 6)  NULL COMMENT '综合评分 (0~1或任意归一口径), 由后端评分器根据 ROI/胜率/非扛单/回撤/质量加权计算',
    `flags`       VARCHAR(128)    NULL COMMENT '风险/提示标签 (如 DATA_POOR, POSSIBLE_BAGHOLDING 等, 逗号分隔或短码)',
    `algo_ver`    VARCHAR(16)     NOT NULL DEFAULT 'v1' COMMENT '统计/评分算法版本 (口径变更用新版本并行回写, 便于灰度与回溯)',
    PRIMARY KEY (`project_id`, `window_days`, `algo_ver`),
    KEY `idx_score` (`window_days`, `score` DESC),
    KEY `idx_updated` (`as_of_ts`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='读模型 · 窗口KPI物化, 列表/排行榜直接读取, 算法变更用 algo_ver 版本化, 便于灰度与回滚';

