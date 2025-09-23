package xin.eason.smartfollow.domain.model.vo.project;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import xin.eason.smartfollow.types.enums.Exchange;

import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotBlank;
import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotNull;

/**
 * 项目 ID 值对象
 * 用于统一内部主键, 避免频繁字符串拼接
 */
@Getter
@ToString
@EqualsAndHashCode
public final class ProjectKey {
    /**
     * @see Exchange
     */
    private final Exchange exchange; // OKX / BINANCE
    /**
     * 外部 ID
     */
    private final String externalId;     // 唯一码（OKX: uniqueCode）

    /**
     * 构造函数
     *
     * @param exchange   {@link  Exchange}
     * @param externalId {@link #externalId}
     */
    private ProjectKey(Exchange exchange, String externalId) {
        requireNotNull(exchange, "exchange 不能为空");
        requireNotBlank(externalId, "externalId 不能为空");
        this.exchange = exchange;
        this.externalId = externalId;
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
