package xin.eason.smartfollow.domain.model.aggregate.trade;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
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

import static xin.eason.smartfollow.types.utils.FieldValidateUtils.require;
import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotNull;

/**
 * 单笔成交记录聚合根 (事实记录)
 */
@Slf4j
@Getter
@ToString
public class ProjectTradeAggregate {

    // 业务身份
    /**
     * 仓位回合 ID 优先使用交易所成交ID, 没有则生成合成ID
     */
    private final String tradeId;
    /**
     * 带单项目 ID 由交易所和外部唯一 ID 组成 (如: OKX:123xxx)
     *
     * @see ProjectKey
     */
    private final ProjectKey projectKey;

    // 基本属性
    /**
     * 标的标识符 (包含了标的的 symbol 和类型)
     *
     * @see ItemId
     */
    private final ItemId item;
    /**
     * 交易方向
     *
     * @see TradeSide
     */
    private final TradeSide side;
    /**
     * 订单类型
     *
     * @see OrderType
     */
    private final OrderType ordType;
    /**
     * 杠杆倍数
     */
    private final BigDecimal leverage;
    /**
     * 仓位 (包含数值和单位)
     *
     * @see Quantity
     */
    private final Quantity qty;
    /**
     * 本仓位回合开仓价格
     */
    private final BigDecimal entryPrice;
    /**
     * 本仓位回合平仓价格
     */
    private final BigDecimal exitPrice;
    /**
     * 手续费 (包含数值和币种, 可为负)
     */
    private final Money fee;
    /**
     * 实现盈亏 (包含数值和币种, 可为负)
     */
    private final Money pnl;
    /**
     * 本回合开仓时间
     */
    private final Instant tsOpen;
    /**
     * 本回合成交完成时间
     */
    private final Instant tsFilled;
    /**
     * 本仓位回合平仓时间
     */
    private final Instant tsClose;
    /**
     * 本仓位回合状态
     */
    private final TradeStatus status;      // OPEN/PARTIALLY_CLOSED/CLOSED/CANCELED

    // 来源与审计
    /**
     * 数据来源
     */
    private final String source;           // OKX / BINANCE / REPLAY / IMPORT ...
    /**
     * 仓位回合外部 ID, 如果没有可为空
     */
    private final String externalTradeId;
    /**
     * 本仓位回合最早成交的订单的外部 ID (作为这个仓位回合在时间上的锚点)
     */
    private final String externalOrderId;
    /**
     * 本仓位回合原始数据哈希值 (SHA-256, 用于幂等与回放)
     */
    private final String sourcePayloadHash;

    /**
     * 构造一个 <code>ProjectTrade</code> 实例, 代表一个项目中的交易记录
     *
     * @param tradeId           交易 ID, 如果为 null 或空白, 则会根据其他参数生成
     * @param projectKey        项目键, 不能为空
     * @param item              标的标识符, 不能为空
     * @param side              交易方向, 不能为空
     * @param ordType           订单类型
     * @param leverage          杠杆倍数
     * @param qty               数量, 必须大于 0
     * @param entryPrice        入场价格, 如果提供, 必须大于 0
     * @param exitPrice         出场价格, 如果提供, 必须大于 0
     * @param fee               手续费
     * @param pnl               盈亏
     * @param tsOpen            开仓时间, 不能为空
     * @param tsFilled          成交时间
     * @param tsClose           平仓时间
     * @param status            交易状态, 不能为空
     * @param source            交易来源, 不能为空且非空白
     * @param externalTradeId   外部交易 ID, 可以为空或空白
     * @param externalOrderId   外部订单 ID, 可以为空或空白
     * @param sourcePayloadHash 源数据哈希, 如果为空或空白, 将被设置为 null
     */
    @Builder
    private ProjectTradeAggregate(
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
        // 参数校验
        requireNotNull(projectKey, "projectKey 不能为空");
        requireNotNull(item, "instrument 不能为空");
        requireNotNull(side, "side 不能为空");
        require(qty != null && qty.amount() != null && qty.amount().signum() > 0, "qty 必须 > 0");
        requireNotNull(tsOpen, "tsOpen 不能为空");
        requireNotNull(status, "status 不能为空");
        require(source != null && !source.isBlank(), "source 不能为空");
        require(entryPrice != null && entryPrice.signum() <= 0, "entryPrice 必须 > 0");
        require(exitPrice != null && exitPrice.signum() <= 0, "exitPrice 必须 > 0");

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

        // tradeId 生成, 优先用外部ID，否则用合成键哈希
        if (tradeId != null && !tradeId.isBlank())
            this.tradeId = tradeId;
        else if (this.externalTradeId != null)
            this.tradeId = this.externalTradeId;
        else
            this.tradeId = synthesizeId(projectKey, item, side, this.tsOpen, qty.amount(), entryPrice, source);
    }

    /* 内部工具方法 */
    private static Instant ensureMillis(Instant t) {
        return Instant.ofEpochMilli(t.toEpochMilli());
    }

    /**
     * 将给定的字符串 <code>s</code> 转换为 null, 如果它是空白或 null.
     * 空白定义为仅包含空格、制表符、换行符等不可见字符的字符串。
     *
     * @param s 需要检查是否为空白或 null 的字符串
     * @return 如果 <code>s</code> 为 null 或是空白, 返回 null; 否则返回原始字符串 <code>s</code>
     */
    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * 确保提供的哈希值符合要求, 即长度为 64 的十六进制字符串
     * 如果不符合要求, 将抛出 <code>AppException</code>
     *
     * @param hash 需要验证的哈希字符串
     * @return 转换为小写后的哈希字符串
     * @throws AppException 如果提供的哈希为空或其长度不是 64
     */
    private static String ensureHash(String hash) {
        if (hash == null || hash.length() != 64)
            throw AppException.of("sourcePayloadHash 必须为 64 位十六进制");
        return hash.toLowerCase();
    }

    /**
     * 生成一个合成的 tradeId. 该 ID 是基于提供的参数使用 SHA-256 算法进行哈希计算后得到的结果.
     *
     * @param key       项目键, 不能为空
     * @param inst      标的标识符, 不能为空
     * @param side      交易方向, 不能为空
     * @param openTs    开仓时间, 不能为空
     * @param qty       数量, 可以为空, 如果不为空则必须大于 0
     * @param price     价格, 可以为空, 如果不为空则必须大于 0
     * @param source    交易来源, 不能为空且非空白
     * @return 返回通过 SHA-256 哈希算法生成的字符串形式的 tradeId
     * @throws AppException 如果在生成过程中遇到任何异常, 将抛出此异常, 携带具体的错误信息
     */
    public static String synthesizeId(ProjectKey key, ItemId inst, TradeSide side,
                                      Instant openTs, BigDecimal qty, BigDecimal price, String source) {
        try {
            String str = key.asString() + "|" + inst + "|" + side.name() + "|" +
                    openTs.toEpochMilli() + "|" + (qty == null ? "" : qty.stripTrailingZeros().toPlainString()) + "|" +
                    (price == null ? "" : price.stripTrailingZeros().toPlainString()) + "|" + source;
            log.info("合成 tradeId: {}", str);
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(str.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw AppException.of("生成合成 tradeId 失败: " + e.getMessage());
        }
    }
}
