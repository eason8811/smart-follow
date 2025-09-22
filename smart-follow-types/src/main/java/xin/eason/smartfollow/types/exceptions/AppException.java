package xin.eason.smartfollow.types.exceptions;

/**
 * 系统内通用业务异常/校验异常。
 * 建议在控制层做统一捕获并转换为标准响应。
 */
public class AppException extends RuntimeException {
    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 便捷工厂
     */
    public static AppException of(String message) {
        return new AppException(message);
    }
}
