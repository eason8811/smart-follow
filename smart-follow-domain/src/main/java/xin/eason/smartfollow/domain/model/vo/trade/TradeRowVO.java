package xin.eason.smartfollow.domain.model.vo.trade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 仓位回合读模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeRowVO {
    /**
     * 交易对
     */
    private String symbol;
    /**
     * 交易方向
     */
    private String side;
    /**
     * 开仓时间
     */
    private Instant tsOpen;
    /**
     * 平仓时间
     */
    private Instant tsClose;
    /**
     * 开仓价格
     */
    private BigDecimal entryPrice;
    /**
     * 平仓价格
     */
    private BigDecimal exitPrice;
    /**
     * 仓位数量
     */
    private BigDecimal qty;
    /**
     * 杠杆倍数
     */
    private BigDecimal leverage;
    /**
     * 收益
     */
    private BigDecimal pnl;
    /**
     * 手续费
     */
    private BigDecimal fees;
    /**
     * 持仓时间
     */
    private Integer durationSec;
    /**
     * 仓位回合状态
     */
    private String status;
    /**
     * 仓位回合来源
     */
    private String source;
}
