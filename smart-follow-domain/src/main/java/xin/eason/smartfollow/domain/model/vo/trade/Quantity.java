package xin.eason.smartfollow.domain.model.vo.trade;

import java.math.BigDecimal;

import xin.eason.smartfollow.types.exceptions.IllegalParamException;

/**
 * 仓位数量, 恒大于 0
 * <p>单位可以为 张 / 币 / 合约, 默认为 币</p>
 *
 * @param amount 仓位数量
 * @param unit   仓位数量单位
 */
public record Quantity(BigDecimal amount, String unit) {
    /**
     * 创建一个表示仓位数量的对象, 该数量必须大于 0
     * <p>单位可以为 张 / 币 / 合约, 默认为 币</p>
     *
     * @param amount 仓位的数量, 必须是一个大于 0 的数值
     * @param unit   数量的单位, 可以是 张, 币, 或 合约, 如果未指定或为空白, 则默认设置为 COIN
     * @throws IllegalParamException 如果 <code>amount</code> 小于等于 0
     */
    public Quantity(BigDecimal amount, String unit) {
        if (amount == null || amount.signum() <= 0)
            throw IllegalParamException.of("数量必须 > 0");
        this.amount = amount.stripTrailingZeros();
        this.unit = (unit == null || unit.isBlank()) ? "COIN" : unit.toUpperCase();
    }
}
