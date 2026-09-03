package com.sojourners.chess.controller;

import com.sojourners.chess.linker.AbstractGraphLinker;
import com.sojourners.chess.linker.LinuxGraphLinker;
import com.sojourners.chess.linker.LinkerCallBack;
import com.sojourners.chess.linker.MacosGraphLinker;
import com.sojourners.chess.linker.WindowsGraphLinker;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.XiangqiUtils;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;

/**
 * 连线域（IT-7.3）：连线器生命周期与 LinkerCallBack 回调实现。
 * 识别差异容错（#64）与走子拒绝诊断日志随域迁入。
 */
public class LinkController implements LinkerCallBack {

    private static final System.Logger log = System.getLogger(LinkController.class.getName());

    private final LinkHost host;

    private final ComboBox<String> linkComboBox;

    private AbstractGraphLinker graphLinker;

    public LinkController(LinkHost host, ComboBox<String> linkComboBox) {
        this.host = host;
        this.linkComboBox = linkComboBox;
    }

    public void initGraphLinker() {
        try {
            this.graphLinker = com.sun.jna.Platform.isWindows() ?
                    new WindowsGraphLinker(this) : (com.sun.jna.Platform.isLinux() ?
                    new LinuxGraphLinker(this) : new MacosGraphLinker(this));
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "初始化连线器失败", e);
        }

        linkComboBox.getItems().addAll("自动走棋", "观战模式");
        linkComboBox.setValue("自动走棋");
    }

    public AbstractGraphLinker getGraphLinker() {
        return graphLinker;
    }

    @Override
    public void linkerInitChessBoard(String fenCode, boolean isReverse) {
        Platform.runLater(() -> {
            host.newChessBoardForLink(fenCode);
            if (isReverse) {
                host.reverseBoard();
            }
            host.applyLinkMode(linkComboBox.getValue());
        });
    }

    @Override
    public char[][] getEngineBoard() {
        return host.getBoard().getBoard();
    }

    @Override
    public boolean isThinking() {
        return host.isThinking();
    }

    @Override
    public boolean isWatchMode() {
        return "观战模式".equals(linkComboBox.getValue());
    }

    @Override
    public void linkerMove(int x1, int y1, int x2, int y2) {
        Platform.runLater(() -> {
            String move = host.getBoard().move(x1, y1, x2, y2);
            if (move != null) {
                boolean red = XiangqiUtils.isRed(host.getBoard().getBoard()[y2][x2]);
                if (isWatchMode() && (!host.isRedGo() && red || host.isRedGo() && !red)) {
                    log.log(System.Logger.Level.INFO, "连线识别行棋方可能错误，走子: " + move + "," + red + ", " + host.isRedGo());
                    // 连线识别行棋方错误，自动切换行棋方
                    host.switchPlayer(false);
                } else {
                    host.onMoveApplied(move);
                }
            } else {
                log.log(System.Logger.Level.WARNING, "连线走子被拒绝（疑似送将或识别出非法着法），棋盘未更新: " + x1 + "," + y1 + " -> " + x2 + "," + y2);
            }
        });
    }

    @Override
    public void linkerNotify(String message) {
        Platform.runLater(() -> DialogUtils.showWarningDialog("提示", message));
    }
}
