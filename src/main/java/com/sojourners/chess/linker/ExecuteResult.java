package com.sojourners.chess.linker;

/**
 * 走棋执行结果
 */
public enum ExecuteResult {
    SUCCESS,
    RETRY_FAILED_PROMOTED,
    SCREENSHOT_INVALID,
    FAILED
}