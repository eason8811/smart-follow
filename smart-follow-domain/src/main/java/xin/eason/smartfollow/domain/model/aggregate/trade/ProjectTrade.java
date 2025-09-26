package xin.eason.smartfollow.domain.model.aggregate.trade;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import xin.eason.smartfollow.domain.model.vo.project.ProjectKey;
import xin.eason.smartfollow.domain.model.vo.trade.*;
import xin.eason.smartfollow.types.enums.OrderType;
import xin.eason.smartfollow.types.enums.TradeSide;
import xin.eason.smartfollow.types.enums.TradeStatus;
import xin.eason.smartfollow.types.exceptions.AppException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 单笔成交记录聚合根 (事实记录)
 */
@Getter
@ToString
public class ProjectTrade {

    // 业务身份
    /**
     * 成交标识符, 优先使用交易所提供的成交ID. 若无, 则基于关键交易信息合成一个唯一的ID, 以确保在系统内该成交记录的唯一性.
     * 合成ID主要用于避免因外部ID缺失导致的数据重复插入问题.
     */
    private final String tradeId;          // 优先使用交易所成交ID, 没有则生成合成ID
    private final ProjectKey projectKey;

    // 基本属性
    private final ItemId item; // 标的 (instType + symbol) 
    private final TradeSide side;          // BUY/SELL 或 LONG/SHORT
    private final OrderType ordType;       // MARKET/LIMIT/IOC/FOK/POST_ONLY
    private final BigDecimal leverage;     // 可空 (合约/杠杆才有) 
    private final Quantity qty;            // > 0
    private final BigDecimal entryPrice;   // > 0 (若给) 
    private final BigDecimal exitPrice;    // > 0 (若给) 
    private final Money fee;               // 手续费 (可负) 
    private final Money pnl;               // 实现盈亏 (可负) 
    private final Instant tsOpen;          // 开仓/触发时间
    private final Instant tsFilled;        // 成交完成时间 (可空) 
    private final Instant tsClose;         // 平仓时间 (可空) 
    private final TradeStatus status;      // OPEN/PARTIALLY_CLOSED/CLOSED/CANCELED

    // 来源与审计
    private final String source;           // OKX / BINANCE / REPLAY / IMPORT ...
    private final String externalTradeId;  // 交易所侧成交ID (可空) 
    private final String externalOrderId;  // 交易所侧订单ID (可空) 
    private final String sourcePayloadHash;// 原文SHA-256 (幂等与回放) 

    @Builder
    private ProjectTrade(
            String tradeId,
            ProjectKey projectKey,
            ItemId item,
            TradeSide side,
            OrderType ordType,
            BigDecimal leverage,
            Quantity qty,
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            Money fee,
            Money pnl,
            Instant tsOpen,
            Instant tsFilled,
            Instant tsClose,
            TradeStatus status,
            String source,
            String externalTradeId,
            String externalOrderId,
            String sourcePayloadHash
    ) {
        // 必填校验
        require(projectKey != null, "projectKey 不能为空");
        require(item != null, "instrument 不能为空");
        require(side != null, "side 不能为空");
        require(qty != null && qty.amount() != null && qty.amount().signum() > 0, "qty 必须 > 0");
        require(tsOpen != null, "tsOpen 不能为空");
        require(status != null, "status 不能为空");
        require(source != null && !source.isBlank(), "source 不能为空");
        // 合法性校验
        if (entryPrice != null && entryPrice.signum() <= 0) throw AppException.of("entryPrice 必须 > 0");
        if (exitPrice != null && exitPrice.signum() <= 0) throw AppException.of("exitPrice 必须 > 0");

        this.projectKey = projectKey;
        this.item = item;
        this.side = side;
        this.ordType = ordType;
        this.leverage = leverage;
        this.qty = qty;
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.fee = fee;
        this.pnl = pnl;
        this.tsOpen = ensureMillis(tsOpen);
        this.tsFilled = tsFilled == null ? null : ensureMillis(tsFilled);
        this.tsClose = tsClose == null ? null : ensureMillis(tsClose);
        this.status = status;
        this.source = source;
        this.externalTradeId = nullIfBlank(externalTradeId);
        this.externalOrderId = nullIfBlank(externalOrderId);
        this.sourcePayloadHash = ensureHash(sourcePayloadHash);

        // tradeId 生成：优先用外部ID，否则用合成键哈希
        this.tradeId = (tradeId != null && !tradeId.isBlank())
                ? tradeId
                : (this.externalTradeId != null
                ? this.externalTradeId
                : synthesizeId(projectKey, item, side, this.tsOpen, qty.amount(), entryPrice, source));
    }

    // —— 帮助方法 —— //
    private static void require(boolean ok, String msg) {
        if (!ok) throw AppException.of(msg);
    }

    private static Instant ensureMillis(Instant t) {
        return Instant.ofEpochMilli(t.toEpochMilli());
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String ensureHash(String h) {
        if (h == null || h.length() != 64) throw AppException.of("sourcePayloadHash 必须为 64 位十六进制");
        return h.toLowerCase();
    }

    /**
     * 合成 tradeId：当外部成交ID不存在时，用领域关键信息生成稳定ID (避免重复插入)
     */
    public static String synthesizeId(ProjectKey key, ItemId inst, TradeSide side,
                                      Instant openTs, BigDecimal qty, BigDecimal price, String source) {
        try {
            String s = key.asString() + "|" + inst + "|" + side.name() + "|" +
                    openTs.toEpochMilli() + "|" + (qty == null ? "" : qty.stripTrailingZeros().toPlainString()) + "|" +
                    (price == null ? "" : price.stripTrailingZeros().toPlainString()) + "|" + source;
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw AppException.of("生成合成 tradeId 失败: " + e.getMessage());
        }
    }
}
