package xin.eason.smartfollow.domain.model.vo.trade;

import java.math.BigDecimal;

/**
 * <p>金额, 包括金额数值和货币类型</p>
 * <p>主要用于标识手续费或收益</p>
 *
 * @param amount 金额数值
 * @param ccy    货币类型
 */
public record Money(BigDecimal amount, String ccy) {
    /**
     * 创建一个新的 <code>Money</code> 实例, 用于表示金额及其货币类型
     *
     * @param amount 金额数值 如果为 <code>null</code>, 则默认设置为 0 并去除尾部零
     * @param ccy    货币类型 如 USDT, USDC 等 如果为 <code>null</code> 或空白, 则默认设置为 USDT
     */
    public Money(BigDecimal amount, String ccy) {
        this.amount = amount == null ? BigDecimal.ZERO : amount.stripTrailingZeros();
        this.ccy = (ccy == null || ccy.isBlank()) ? "USDT" : ccy.toUpperCase();
    }
}
