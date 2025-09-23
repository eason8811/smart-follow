package xin.eason.smartfollow.types.exceptions;

/**
 * 参数非法异常
 */
public class IllegalParamException extends RuntimeException {
    public IllegalParamException(String message) {
        super(message);
    }

    /**
     * 便捷工厂
     */
    public static IllegalParamException of(String message) {
        return new IllegalParamException(message);
    }
}
