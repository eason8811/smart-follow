package xin.eason.smartfollow.types.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import xin.eason.smartfollow.types.exceptions.AppException;
import xin.eason.smartfollow.types.exceptions.IllegalParamException;

import java.util.Objects;

/**
 * 字段验证工具类, 当字段不符合要求时抛出 {@link IllegalParamException} 异常
 */
public final class FieldValidateUtils {
    /**
     * 确保给定的对象不为空, 如果对象为空则抛出 {@link IllegalParamException} 异常
     *
     * @param object 待检查的对象
     * @param msg    当 <code>object</code> 为空时, 抛出异常的信息
     * @throws IllegalParamException 如果 <code>object</code> 为 <code>null</code>
     */
    @Contract(value = "null, !null -> fail", pure = true)
    public static void requireNotNull(Object object, @Nls String msg) {
        if (Objects.isNull(object))
            throw IllegalParamException.of(msg);
    }

    /**
     * 确保给定的字符串既不为 <code>null</code> 也不为空白, 如果字符串不符合要求则抛出 {@link IllegalParamException} 异常
     *
     * @param string 待检查的字符串
     * @param msg    当 <code>string</code> 为 <code>null</code> 或空白时, 抛出异常的信息
     * @throws IllegalParamException 如果 <code>string</code> 为 <code>null</code> 或仅包含空白字符
     */
    @Contract(value = "null, !null -> fail", pure = true)
    public static void requireNotBlank(String string, @Nls String msg) {
        if (string == null || string.isBlank())
            throw IllegalParamException.of(msg);
    }

    /**
     * 确保给定的条件为 <code>true</code>, 如果条件不满足则抛出 {@link IllegalParamException} 异常
     *
     * @param ok  用于检查的布尔值, 应该是某个条件的结果
     * @param msg 当 <code>ok</code> 为 <code>false</code> 时, 抛出异常的信息
     * @throws IllegalParamException 如果 <code>ok</code> 为 <code>false</code>
     */
    @Contract(value = "false, !null -> fail", pure = true)
    public static void require(boolean ok, @Nls String msg) {
        if (!ok)
            throw IllegalParamException.of(msg);
    }
}
