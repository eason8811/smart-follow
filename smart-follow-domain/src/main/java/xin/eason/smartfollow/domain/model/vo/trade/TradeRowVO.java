package xin.eason.smartfollow.domain.model.vo.trade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 读模型（表格/导出）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeRowVO {
    private String symbol;
    private String side;
    private Instant tsOpen;
    private Instant tsClose;
    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private BigDecimal qty;
    private BigDecimal leverage;
    private BigDecimal pnl;
    private BigDecimal fees;
    private Integer durationSec;
    private String status;
    private String source;
}
