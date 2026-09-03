package com.sojourners.chess.linker;

/**
 * 走棋执行器抽象：执行走棋并验证，返回执行结果
 */
public interface IMoveExecutor {

    /**
     * Executes a move action by clicking on the target platform, then verifies the move via screenshot recognition.
     *
     * @param action the move action to execute (contains source and destination coordinates)
     * @param ctx    execution context providing board position, recognition, screenshot, and click callbacks
     * @return the result of the execution (SUCCESS, FAILED, SCREENSHOT_INVALID, or RETRY_FAILED_PROMOTED)
     */
    ExecuteResult execute(Action action, ExecContext ctx);
}