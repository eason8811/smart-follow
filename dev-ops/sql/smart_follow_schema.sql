-- 1. exchange_project —— 项目主表 / 每个项目的身份证
CREATE TABLE `exchange_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `exchange` ENUM('OKX','BINANCE') NOT NULL COMMENT '交易所标识, 取值：OKX/BINANCE',
  `leader_external_id` VARCHAR(191) NOT NULL COMMENT '平台侧项目/领航员稳定外部ID (逻辑唯一) ',
  `name` VARCHAR(255) NULL COMMENT '项目/领航员名称',
  `status` VARCHAR(64) NULL COMMENT '运行状态 (如RUNNING/CLOSED/PAUSED等, 文本) ',
  `first_seen` TIMESTAMP(3) NOT NULL COMMENT '首次被系统发现时间',
  `last_seen` TIMESTAMP(3) NOT NULL COMMENT '最近一次被看到时间',
  `last_visibility` ENUM('VISIBLE','MISSING','HIDDEN') NOT NULL DEFAULT 'VISIBLE' COMMENT '最近可见性：VISIBLE/MISSING/HIDDEN',
  `min_copy_cost` DECIMAL(36,18) NULL COMMENT '最小可复制保证金/成本 (USDT口径) ',
  `base_currency` VARCHAR(16) NULL DEFAULT 'USDT' COMMENT '计价币 (默认USDT) ',
  `data_quality_score` FLOAT NULL DEFAULT 1.0 COMMENT '数据质量分 (0~1) ',
  `extra` JSON NOT NULL COMMENT '平台特有原始字段 (JSON) ',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_leader` (`exchange`, `leader_external_id`),
  KEY `idx_exchange` (`exchange`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目主档, 供快照/交易/墓碑等表引用 (逻辑外键) ';

-- 2. exchange_project_snapshot —— 项目时序快照 (含可见性) [按月分区] 记录项目数据变化, 包括收益, 持仓, 跟随人数, 可见性等
CREATE TABLE `exchange_project_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` BIGINT NOT NULL COMMENT '逻辑外键, 指向 exchange_project.id',
  `ts` TIMESTAMP(3) NOT NULL COMMENT '快照时间',
  `visibility` ENUM('VISIBLE','MISSING','HIDDEN') NOT NULL COMMENT '可见性 (VISIBLE/MISSING/HIDDEN) ',
  `equity` DECIMAL(36,18) NULL COMMENT '权益/AUM (USDT口径) ',
  `followers` INT NULL COMMENT '跟随人数',
  `positions_open` INT NULL COMMENT '持仓笔数',
  `fees_daily` DECIMAL(36,18) NULL COMMENT '当日费用',
  `pnl_daily` DECIMAL(36,18) NULL COMMENT '当日PnL',
  `raw` JSON NOT NULL COMMENT '原始快照JSON',
  `aum_usd` DECIMAL(36,18) GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(`raw`, '$.aum')) AS DECIMAL(36,18))) STORED COMMENT '生成列：从raw($.aum)提取的AUM(USDT)数值, 便于筛选/排序',
  PRIMARY KEY (`id`),
  KEY `idx_proj_ts` (`project_id`, `ts`) USING BTREE,
  KEY `idx_snapshot_aum` (`aum_usd`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每次采集的时序状态, 亦用于检测消失 (missing) 与幸存者偏差修正'
PARTITION BY RANGE COLUMNS(`ts`) (
  PARTITION p2025_08 VALUES LESS THAN ('2025-09-01'),
  PARTITION pMAX VALUES LESS THAN (MAXVALUE)
);

-- 3) exchange_project_trade —— 项目交易/持仓明细[按月分区] 记录每个项目中的交易明细
CREATE TABLE `exchange_project_trade` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` BIGINT NOT NULL COMMENT '逻辑外键, 指向 exchange_project.id',
  `symbol` VARCHAR(64) NOT NULL COMMENT '交易标的 (如BTC-USDT或合约代码) ',
  `side` ENUM('LONG','SHORT','BUY','SELL') NOT NULL COMMENT '方向：LONG/SHORT (合约方向) 或BUY/SELL (现货方向) ',
  `ts_open` TIMESTAMP(3) NOT NULL COMMENT '开仓时间',
  `ts_close` TIMESTAMP(3) NULL COMMENT '平仓时间 (可为空) ',
  `entry_price` DECIMAL(36,18) NULL COMMENT '入场价格',
  `exit_price` DECIMAL(36,18) NULL COMMENT '出场价格',
  `qty` DECIMAL(36,18) NULL COMMENT '数量/张数',
  `leverage` DECIMAL(18,8) NULL COMMENT '杠杆 (若适用) ',
  `pnl` DECIMAL(36,18) NULL COMMENT '实际PnL (展示口径) ',
  `mae` DECIMAL(36,18) NULL COMMENT '最大不利变动 (复制口径估算) ',
  `mfe` DECIMAL(36,18) NULL COMMENT '最大有利变动',
  `fees` DECIMAL(36,18) NULL COMMENT '手续费等成本',
  `duration_sec` INT NULL COMMENT '持仓时长 (秒) ',
  PRIMARY KEY (`id`),
  KEY `idx_proj_open` (`project_id`, `ts_open`) USING BTREE,
  KEY `idx_proj_close` (`project_id`, `ts_close`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风格识别与可复制收益计算的主数据'
PARTITION BY RANGE COLUMNS(`ts_open`) (
  PARTITION p2025_08 VALUES LESS THAN ('2025-09-01'),
  PARTITION pMAX VALUES LESS THAN (MAXVALUE)
);

-- 4) tombstone —— 项目消失事件 (幸存者偏差的黑匣子) 
CREATE TABLE `tombstone` (
  `project_id` BIGINT NOT NULL COMMENT '逻辑外键, 指向 exchange_project.id',
  `disappeared_at` TIMESTAMP(3) NOT NULL COMMENT '第一次从可见转为缺失/隐藏的时间',
  `last_snapshot_id` BIGINT NULL COMMENT '消失前最后一次快照ID (逻辑外键) ',
  `reason` VARCHAR(255) NULL COMMENT '可选原因 (如下架/清退/改名等) ',
  PRIMARY KEY (`project_id`, `disappeared_at`),
  KEY `idx_last_snap` (`last_snapshot_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录可见性由visible→missing/hidden的事件';

-- 5) crawl_log —— 抓取调用日志 (观测与去重) 
CREATE TABLE `crawl_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `exchange` ENUM('OKX','BINANCE') NOT NULL COMMENT '目标交易所 (OKX/BINANCE) ',
  `target` VARCHAR(255) NOT NULL COMMENT '抓取目标 (URL/资源标识) ',
  `ts` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '抓取时间',
  `status_code` INT NULL COMMENT 'HTTP 状态码',
  `success` BOOLEAN NULL COMMENT '是否成功',
  `duration_ms` INT NULL COMMENT '耗时 (毫秒) ',
  `bytes` INT NULL COMMENT '返回体大小 (字节) ',
  `etag` VARCHAR(255) NULL COMMENT 'ETag',
  `last_modified` VARCHAR(255) NULL COMMENT 'Last-Modified',
  `content_hash` VARCHAR(64) NULL COMMENT '内容摘要 (相同则可跳过解析/落库) ',
  `error_msg` TEXT NULL COMMENT '错误信息',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_ts` (`exchange`, `ts`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采集稳定性监控、重试与去重依据';

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