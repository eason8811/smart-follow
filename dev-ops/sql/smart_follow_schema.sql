-- 1. exchange_project —— 项目主表 / 每个项目的身份证
CREATE TABLE `exchange_project`
(
    `id`                 BIGINT                              NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `exchange`           ENUM ('OKX','BINANCE')              NOT NULL COMMENT '交易所标识, 取值：OKX/BINANCE',
    `leader_external_id` VARCHAR(191)                        NOT NULL COMMENT '平台侧项目/领航员稳定外部ID (逻辑唯一) ',
    `name`               VARCHAR(255)                        NULL COMMENT '项目/领航员名称',
    `status`             VARCHAR(64)                         NULL COMMENT '运行状态 (如RUNNING/CLOSED/PAUSED等, 文本) ',
    `first_seen`         TIMESTAMP(3)                        NOT NULL COMMENT '首次被系统发现时间',
    `last_seen`          TIMESTAMP(3)                        NOT NULL COMMENT '最近一次被看到时间',
    `last_visibility`    ENUM ('VISIBLE','MISSING','HIDDEN') NOT NULL DEFAULT 'VISIBLE' COMMENT '最近可见性：VISIBLE/MISSING/HIDDEN',
    `min_copy_cost`      DECIMAL(36, 18)                     NULL COMMENT '最小可复制保证金/成本 (USDT口径) ',
    `base_currency`      VARCHAR(16)                         NULL     DEFAULT 'USDT' COMMENT '计价币 (默认USDT) ',
    `data_quality_score` FLOAT                               NULL     DEFAULT 1.0 COMMENT '数据质量分 (0~1) ',
    `extra`              JSON                                NOT NULL COMMENT '平台特有原始字段 (JSON) ',
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
    `visibility`     ENUM ('VISIBLE','MISSING','HIDDEN') NOT NULL COMMENT '可见性 (VISIBLE/MISSING/HIDDEN) ',
    `equity`         DECIMAL(36, 18)                     NULL COMMENT '权益/AUM (USDT口径) ',
    `followers`      INT                                 NULL COMMENT '跟随人数',
    `positions_open` INT                                 NULL COMMENT '持仓笔数',
    `fees_daily`     DECIMAL(36, 18)                     NULL COMMENT '当日费用',
    `pnl_daily`      DECIMAL(36, 18)                     NULL COMMENT '当日PnL',
    `raw`            JSON                                NOT NULL COMMENT '原始快照JSON',
    `aum_usd`        DECIMAL(36, 18) GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(`raw`, '$.aum')) AS DECIMAL(36, 18))) STORED COMMENT '生成列：从raw($.aum)提取的AUM(USDT)数值, 便于筛选/排序',
    PRIMARY KEY (`id`),
    KEY `idx_proj_ts` (`project_id`, `ts`) USING BTREE,
    KEY `idx_snapshot_aum` (`aum_usd`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='每次采集的时序状态, 亦用于检测消失 (missing) 与幸存者偏差修正'
    PARTITION BY RANGE COLUMNS (`ts`) (
        PARTITION p2025_08 VALUES LESS THAN ('2025-09-01'),
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );

-- 3. exchange_project_trade —— 项目交易/持仓明细[按月分区] 记录每个项目中的交易明细
CREATE TABLE `exchange_project_trade`
(
    `id`           BIGINT                             NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`   BIGINT                             NOT NULL COMMENT '逻辑外键, 指向 exchange_project.id',
    `symbol`       VARCHAR(64)                        NOT NULL COMMENT '交易标的 (如BTC-USDT或合约代码) ',
    `side`         ENUM ('LONG','SHORT','BUY','SELL') NOT NULL COMMENT '方向：LONG/SHORT (合约方向) 或BUY/SELL (现货方向) ',
    `ts_open`      TIMESTAMP(3)                       NOT NULL COMMENT '开仓时间',
    `ts_close`     TIMESTAMP(3)                       NULL COMMENT '平仓时间 (可为空) ',
    `entry_price`  DECIMAL(36, 18)                    NULL COMMENT '入场价格',
    `exit_price`   DECIMAL(36, 18)                    NULL COMMENT '出场价格',
    `qty`          DECIMAL(36, 18)                    NULL COMMENT '数量/张数',
    `leverage`     DECIMAL(18, 8)                     NULL COMMENT '杠杆 (若适用) ',
    `pnl`          DECIMAL(36, 18)                    NULL COMMENT '实际PnL (展示口径) ',
    `mae`          DECIMAL(36, 18)                    NULL COMMENT '最大不利变动 (复制口径估算) ',
    `mfe`          DECIMAL(36, 18)                    NULL COMMENT '最大有利变动',
    `fees`         DECIMAL(36, 18)                    NULL COMMENT '手续费等成本',
    `duration_sec` INT                                NULL COMMENT '持仓时长 (秒) ',
    PRIMARY KEY (`id`),
    KEY `idx_proj_open` (`project_id`, `ts_open`) USING BTREE,
    KEY `idx_proj_close` (`project_id`, `ts_close`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='风格识别与可复制收益计算的主数据'
    PARTITION BY RANGE COLUMNS (`ts_open`) (
        PARTITION p2025_08 VALUES LESS THAN ('2025-09-01'),
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );

-- 4. tombstone —— 项目消失事件 (幸存者偏差的黑匣子)
CREATE TABLE `tombstone`
(
    `project_id`       BIGINT       NOT NULL COMMENT '逻辑外键, 指向 exchange_project.id',
    `disappeared_at`   TIMESTAMP(3) NOT NULL COMMENT '第一次从可见转为缺失/隐藏的时间',
    `last_snapshot_id` BIGINT       NULL COMMENT '消失前最后一次快照ID (逻辑外键) ',
    `reason`           VARCHAR(255) NULL COMMENT '可选原因 (如下架/清退/改名等) ',
    PRIMARY KEY (`project_id`, `disappeared_at`),
    KEY `idx_last_snap` (`last_snapshot_id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='记录可见性由visible→missing/hidden的事件';

-- 5. crawl_log —— 抓取调用日志 (观测与去重)
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
    COMMENT ='采集日志(不可变事实), 支持304/短路、审计与统计';


-- 6. crawl_task —— 抓取任务, 做意图级 (任务级) 去重, 避免单一任务时间超长重入,
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
    /* 便于查询过期锁：生成列 */
    `locked_until` TIMESTAMP(3) GENERATED ALWAYS AS
        (IF(`locked_at` IS NULL OR `lock_ttl_sec` IS NULL, NULL,
            TIMESTAMPADD(SECOND, `lock_ttl_sec`, `locked_at`))) STORED COMMENT '锁过期时间=locked_at+ttl',
    `created_at`   TIMESTAMP(3)           NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   TIMESTAMP(3)           NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),

    /* 幂等唯一键：同一时间窗口内，同一意图只创建一次 */
    UNIQUE KEY `uk_task_intent` (`exchange`, `api_name`, `params_hash`, `window_key`),

    /* 调度常用索引：根据状态和锁过期时间快速扫描可抢占任务 */
    KEY `idx_status_lockeduntil` (`status`, `locked_until`),
    KEY `idx_exchange_api_status` (`exchange`, `api_name`, `status`),

    /* 健壮性检查（MySQL 8.0.16+才强制执行；旧版仅作为文档） */
    CHECK (`next_page` >= 1),
    CHECK (`total_page` IS NULL OR `total_page` >= 0),
    CHECK (`lock_ttl_sec` IS NULL OR `lock_ttl_sec` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='爬取任务聚合：意图级去重/锁/状态机/分页';

-- 新增 2025-09 月分区 (< '2025-10-01') 示例：exchange_project_snapshot (列 ts) 
ALTER TABLE `exchange_project_snapshot`
    REORGANIZE PARTITION pMAX INTO (
        PARTITION p2025_09 VALUES LESS THAN ('2025-10-01'),
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );

-- 新增 2025-09 月分区 (< '2025-10-01') 示例：exchange_project_trade (列 ts_open) 
ALTER TABLE `exchange_project_trade`
    REORGANIZE PARTITION pMAX INTO (
        PARTITION p2025_09 VALUES LESS THAN ('2025-10-01'),
        PARTITION pMAX VALUES LESS THAN (MAXVALUE)
        );