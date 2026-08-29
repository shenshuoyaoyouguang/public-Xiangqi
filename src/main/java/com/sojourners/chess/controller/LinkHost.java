package com.sojourners.chess.controller;

import com.sojourners.chess.board.ChessBoard;

/**
 * 连线域跨域服务接口（IT-7.3）：LinkController 经此访问对局状态与服务，
 * 由 Controller 实现；跨域事件路由在 PR5 事件聚合层收编。
 */
public interface LinkHost {

    ChessBoard getBoard();

    GameSession getSession();

    boolean isThinking();

    boolean isRedGo();

    void newChessBoardForLink(String fenCode);

    void switchPlayer(boolean f);

    void reverseBoard();

    void applyLinkMode(String value);

    void onMoveApplied(String move);
}
