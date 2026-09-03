package com.sojourners.chess.linker;

/**
 * 走棋执行器抽象：执行走棋并验证，返回执行结果
 */
public interface IMoveExecutor {

    ExecuteResult execute(Action action, ExecContext ctx);
}