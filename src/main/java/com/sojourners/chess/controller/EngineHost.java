package com.sojourners.chess.controller;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.controller.handle.ManualController;
import com.sojourners.chess.linker.AbstractGraphLinker;

/**
 * 引擎域跨域服务接口（IT-7.2）：EngineController 经此访问对局状态、棋谱与连线服务，
 * 由 Controller 实现；跨域事件路由在 PR5 事件聚合层收编。
 */
public interface EngineHost {

    ChessBoard getBoard();

    boolean isRedGo();

    GameSession getSession();

    ManualController getChessManualHandle();

    AbstractGraphLinker getGraphLinker();

    void setThinking(boolean thinking);

    /**
     * 连线会话令牌：每次开启连线递增，autoClickTactic 据此丢弃过期代点请求
     */
    long getLinkSession();

    /**
     * 引擎 bestmove 的走子应用链（更新棋盘/棋谱/切换行棋方/再驱动），由 Controller 壳实现
     */
    void onEngineBestMove(String first, String second);

    /**
     * 走子已应用后的联动（记录棋谱/趋势图/切换行棋方/驱动引擎或库招）
     */
    void onMoveApplied(String move);
}
