package com.yupi.yuaicodemother.exception;

public class ThrowUtils {

    /**
     * 如果条件为真，则抛出异常
     * @param condition
     * @param runtimeException
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }

    }

    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throwIf(condition, new BusinessException(errorCode));

        }

    }

    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        if (condition) {
            throwIf(condition, new BusinessException(errorCode, message));

        }

    }
}
