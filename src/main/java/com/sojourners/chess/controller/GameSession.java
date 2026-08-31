package com.sojourners.chess.controller;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.controller.handle.ManualController;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.linker.AbstractGraphLinker;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.XiangqiUtils;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/**
 * 对局会话（IT-7.1/7.5）：模式状态 + 对局流程（新建局面/角色切换/连线停止）的单一持有者。
 * 引擎/连线/棋谱协作域经 {@link GameHost} 访问。
 */
public class GameSession {

    private static final System.Logger log = System.getLogger(GameSession.class.getName());

    private final SimpleObjectProperty<Boolean> robotRed = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> robotBlack = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> robotAnalysis = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> isReverse = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> linkMode = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> useOpenBook = new SimpleObjectProperty<>(false);

    private final GameHost host;

    private final Properties prop = Properties.getInstance();

    // 视图控件（bind 时注入）
    private Button redButton;
    private Button blackButton;
    private Button analysisButton;
    private Button immediateButton;

    public GameSession(GameHost host) {
        this.host = host;
    }

    public void bindButtons(Button redButton, Button blackButton, Button analysisButton, Button immediateButton) {
        this.redButton = redButton;
        this.blackButton = blackButton;
        this.analysisButton = analysisButton;
        this.immediateButton = immediateButton;
    }

    public SimpleObjectProperty<Boolean> robotRedProperty() {
        return robotRed;
    }

    public SimpleObjectProperty<Boolean> robotBlackProperty() {
        return robotBlack;
    }

    public SimpleObjectProperty<Boolean> robotAnalysisProperty() {
        return robotAnalysis;
    }

    public SimpleObjectProperty<Boolean> isReverseProperty() {
        return isReverse;
    }

    public SimpleObjectProperty<Boolean> linkModeProperty() {
        return linkMode;
    }

    public SimpleObjectProperty<Boolean> useOpenBookProperty() {
        return useOpenBook;
    }

    // ===== 对局流程（IT-7.5 迁入） =====

    public void newGame() {
        if (linkMode.getValue()) {
            stopGraphLink();
        }

        newChessBoard(null);
    }

    public void newFromOriginFen(String fenCode) {
        if ((fenCode != null && !fenCode.isEmpty())) {
            if (linkMode.getValue()) {
                stopGraphLink();
            }

            newChessBoard(fenCode);
            if (XiangqiUtils.isReverse(fenCode)) {
                host.reverseBoard();
            }
        }
    }

    public void newChessBoard(String fenCode) {
        newChessBoard(fenCode, false);
    }

    /**
     * 新建局面
     *
     * @param fenCode 传null 新建默认初始局面；传fenCode 则根据fen创建局面
     */
    public void newChessBoard(String fenCode, boolean fromManual) {
        // 重置按钮
        resetRobotModes();
        redButton.setDisable(false);
        blackButton.setDisable(false);
        immediateButton.setDisable(false);
        isReverse.setValue(false);
        // 引擎停止计算
        host.getEngineController().stop();
        // 绘制棋盘
        ChessBoard board = new ChessBoard(host.getCanvas(), prop.getBoardSize(), prop.getBoardStyle(), prop.isStepTip(), prop.isManualTip(),
                host.getEngineController().isLoaded() && host.getEngineController().getMultiPV() > 1, prop.isStepSound(), prop.isShowNumber(), fenCode);
        host.onBoardCreated(board);
        // 设置局面
        boolean redGo = (fenCode == null || fenCode.isEmpty()) ? true : fenCode.contains("w");
        host.setRedGo(redGo);
        fenCode = board.fenCode(redGo);
        // 设置棋谱
        if (!fromManual)
            host.getManualController().newChessManual(fenCode);
        // 重置趋势图
        host.getEngineController().refreshLineChart();
        // 重置引擎思考输出
        host.getThinkListView().getItems().clear();
        // 清空思考状态信息
        host.getInfoShowLabel().setText("");

        // 库招显示
        host.getEngineController().queryOpenBook();

        System.gc();
    }

    /** 重置三个机器人模式属性，不操作按钮禁用状态。 */
    public void resetRobotModes() {
        robotRed.setValue(false);
        robotBlack.setValue(false);
        robotAnalysis.setValue(false);
    }

    /**
     * 切换引擎角色（机器人黑/红/分析模式）。
     * isAnalysis=true 时，启用会取消另两项且禁用红/黑/立即按钮。
     * 黑/红启用时仅在轮到该方行棋时启动引擎；取消时若为该方行棋则停止引擎。
     */
    public void toggleMode(SimpleObjectProperty<Boolean> mode, boolean isAnalysis) {
        if (!host.getEngineController().isLoaded()) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        boolean enable = !mode.getValue();
        mode.setValue(enable);

        if (isAnalysis) {
            if (enable) {
                robotRed.setValue(false);
                robotBlack.setValue(false);
            }
            redButton.setDisable(enable);
            blackButton.setDisable(enable);
            immediateButton.setDisable(enable);
            if (enable) host.getEngineController().go(); else host.getEngineController().stop();
        } else {
            boolean isRedMode = (mode == robotRed);
            boolean isMyTurn = isRedMode ? host.isRedGo() : !host.isRedGo();
            if (isMyTurn) {
                if (enable) host.getEngineController().go(); else host.getEngineController().stop();
            }
        }

        if (linkMode.getValue() && !enable) {
            stopGraphLink();
        }
    }

    public void stopGraphLink() {
        if (host.getGraphLinker() != null) {
            host.getGraphLinker().stop();
        }

        host.getEngineController().stop();

        redButton.setDisable(false);
        robotRed.setValue(false);

        blackButton.setDisable(false);
        robotBlack.setValue(false);

        analysisButton.setDisable(false);
        robotAnalysis.setValue(false);

        linkMode.setValue(false);
    }

    public void checkLinkMode() {
        if (linkMode.getValue()) {
            stopGraphLink();
        }
    }

    /**
     * 连线模式下设置走子/引擎联动（IT-7.5 迁入）：自动走棋走黑/红、观战开启分析。
     */
    public void setLinkMode(String t1) {
        if (linkMode.getValue()) {
            // 只接受两个枚举值；可编辑 ComboBox 可能传任意字符串
            if (!"自动走棋".equals(t1) && !"观战模式".equals(t1)) {
                return;
            }
            if ("自动走棋".equals(t1)) {
                // 观战模式切换自动走棋，先停止引擎
                host.getEngineController().stop();
                // 走黑棋/红棋
                if (isReverse.getValue()) {
                    blackButton.setDisable(false);
                    robotBlack.setValue(true);

                    redButton.setDisable(true);
                    robotRed.setValue(false);

                    analysisButton.setDisable(true);
                    robotAnalysis.setValue(false);

                    if (!host.isRedGo()) {
                        host.getEngineController().go();
                    }
                } else {
                    redButton.setDisable(false);
                    robotRed.setValue(true);

                    blackButton.setDisable(true);
                    robotBlack.setValue(false);

                    analysisButton.setDisable(true);
                    robotAnalysis.setValue(false);

                    if (host.isRedGo()) {
                        host.getEngineController().go();
                    }
                }
            } else {
                analysisButton.setDisable(false);
                robotAnalysis.setValue(true);

                blackButton.setDisable(true);
                robotBlack.setValue(false);

                redButton.setDisable(true);
                robotRed.setValue(false);

                immediateButton.setDisable(true);

                host.getEngineController().go();
            }
        }
    }

    public void switchPlayer(boolean f) {
        host.getEngineController().stop();

        host.getGraphLinker().pause();

        boolean tmpRed = robotRed.getValue(), tmpBlack = robotBlack.getValue(), tmpAnalysis = robotAnalysis.getValue(), tmpLink = linkMode.getValue(), tmpReverse = isReverse.getValue();

        String fenCode = host.getBoard().fenCode(f ? !host.isRedGo() : host.isRedGo());
        newChessBoard(fenCode);

        isReverse.setValue(tmpReverse);
        host.getBoard().reverse(tmpReverse);
        robotRed.setValue(tmpRed);
        robotBlack.setValue(tmpBlack);
        robotAnalysis.setValue(tmpAnalysis);
        linkMode.setValue(tmpLink);

        host.getGraphLinker().resume();
        if (robotRed.getValue() && host.isRedGo() || robotBlack.getValue() && !host.isRedGo() || robotAnalysis.getValue()) {
            host.getEngineController().go();
        }
    }
}
