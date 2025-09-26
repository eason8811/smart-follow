package xin.eason.smartfollow.domain.model.vo.project;

import xin.eason.smartfollow.types.enums.Exchange;

import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotBlank;
import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotNull;

/**
 * 项目 ID 值对象
 * 用于统一内部主键, 避免频繁字符串拼接
 *
 * @param exchange   OKX / BINANCE
 * @param externalId 外部 ID
 *                   唯一码（OKX: uniqueCode）
 */
public record ProjectKey(Exchange exchange, String externalId) {
    /**
     * 构造函数
     *
     * @param exchange   {@link  Exchange}
     * @param externalId {@link #externalId}
     */
    public ProjectKey {
        requireNotNull(exchange, "exchange 不能为空");
        requireNotBlank(externalId, "externalId 不能为空");
    }

    /**
     * 静态构造函数
     *
     * @param exchange   {@link  Exchange}
     * @param externalId {@link #externalId}
     * @return 项目 ID 值对象
     */
    public static ProjectKey of(Exchange exchange, String externalId) {
        return new ProjectKey(exchange, externalId);
    }

    /**
     * 将项目 ID 值对象转换为字符串
     *
     * @return 项目 ID 字符串, 格式为 <code>{@link Exchange}:{@link #externalId}</code>
     */
    public String asString() {
        return exchange.name() + ":" + externalId;
    }
}
