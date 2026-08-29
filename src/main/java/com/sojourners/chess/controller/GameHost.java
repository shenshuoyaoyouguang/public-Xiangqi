package com.sojourners.chess.controller;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.controller.handle.ManualController;
import com.sojourners.chess.linker.AbstractGraphLinker;
import com.sojourners.chess.model.ThinkData;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/**
 * 对局域跨域服务接口（IT-7.5）：GameSession 经此访问协作域与视图，由 Controller 实现。
 */
public interface GameHost {

    EngineController getEngineController();

    AbstractGraphLinker getGraphLinker();

    ManualController getManualController();

    com.sojourners.chess.board.ChessBoard getBoard();

    boolean isRedGo();

    void setRedGo(boolean redGo);

    Canvas getCanvas();

    ListView<ThinkData> getThinkListView();

    Label getInfoShowLabel();

    void reverseBoard();

    /**
     * 新棋盘创建后回写 Controller 的兼容别名引用
     */
    void onBoardCreated(ChessBoard board);
}
