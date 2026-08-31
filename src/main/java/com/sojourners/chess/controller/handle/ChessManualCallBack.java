package com.sojourners.chess.controller.handle;

import java.util.List;

public interface ChessManualCallBack {
    void browseChessRecord(String fenCode, List<String> moveList, boolean redGo, List<String> nextList);
    void setNextList(List<String> nextList);
    void newChessBoardFromManual(String fenCode);
    void turnOnAnalysisMode();
    void turnOffAnalysisMode();
    void refreshLineChart();

    /**
     * 棋谱侧栏可见性切换后回调，触发棋盘自适应尺寸重算。
     */
    default void onNotationPaneVisibilityChanged(boolean visible) {}
}
