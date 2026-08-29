package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.controller.handle.ChessManualCallBack;
import com.sojourners.chess.controller.handle.ManualController;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.enginee.EngineCallBack;
import com.sojourners.chess.linker.*;
import com.sojourners.chess.menu.BoardContextMenu;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ManualRecord;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.*;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

public class Controller implements ChessManualCallBack, EngineHost, LinkHost, GameHost {

    private static final System.Logger log = System.getLogger(Controller.class.getName());

    @FXML
    private Canvas canvas;

    @FXML
    private BorderPane borderPane;
    @FXML
    private Label infoShowLabel;
    @FXML
    private ToolBar statusToolBar;
    @FXML
    private Label timeShowLabel;
    @FXML
    private SplitPane splitPane;
    @FXML
    private SplitPane splitPane2;

    @FXML
    private ListView<ThinkData> listView;

    @FXML
    private ComboBox<String> engineComboBox;

    @FXML
    private ComboBox<String> linkComboBox;

    @FXML
    private ComboBox<String> hashComboBox;

    @FXML
    private ComboBox<String> threadComboBox;

    @FXML
    private RadioMenuItem menuOfLargeBoard;
    @FXML
    private RadioMenuItem menuOfBigBoard;
    @FXML
    private RadioMenuItem menuOfMiddleBoard;
    @FXML
    private RadioMenuItem menuOfSmallBoard;
    @FXML
    private RadioMenuItem menuOfAutoFitBoard;

    @FXML
    private RadioMenuItem menuOfDefaultBoard;
    @FXML
    private RadioMenuItem menuOfCustomBoard;

    @FXML
    private CheckMenuItem menuOfStepTip;
    @FXML
    private CheckMenuItem menuOfStepSound;
    @FXML
    private CheckMenuItem menuOfLinkBackMode;
    @FXML
    private CheckMenuItem menuOfLinkAnimation;
    @FXML
    private CheckMenuItem menuOfShowStatus;
    @FXML
    private CheckMenuItem menuOfShowNumber;

    @FXML
    private CheckMenuItem menuOfTopWindow;

    private Properties prop;

    // IT-7.2: 引擎域职责迁入 EngineController（引擎实例/分析/库表/趋势图）
    private EngineController engineController;

    // IT-7.3: 连线域
    private LinkController linkController;

    private ChessBoard board;

    private AbstractGraphLinker graphLinker;

    @FXML
    private Button analysisButton;
    @FXML
    private Button blackButton;
    @FXML
    private Button redButton;
    @FXML
    private Button reverseButton;
    @FXML
    private Button newButton;
    @FXML
    private Button copyButton;
    @FXML
    private Button pasteButton;
    @FXML
    private Button regretButton;

    @FXML
    private BorderPane charPane;

    @FXML
    private Button immediateButton;
    @FXML
    private Button bookSwitchButton;
    @FXML
    private Button linkButton;
    @FXML
    private Button changeTacticButton;

    @FXML
    private TableView<ManualRecord> recordTable;

    @FXML
    private TableView<BookData> bookTable;

    // IT-7.1: 对局模式状态所有权迁入 GameSession，以下为兼容别名（PR5 收尾时清理）
    private final GameSession session = new GameSession(this);
    private SimpleObjectProperty<Boolean> robotRed = session.robotRedProperty();
    private SimpleObjectProperty<Boolean> robotBlack = session.robotBlackProperty();
    private SimpleObjectProperty<Boolean> robotAnalysis = session.robotAnalysisProperty();
    private SimpleObjectProperty<Boolean> isReverse = session.isReverseProperty();
    private SimpleObjectProperty<Boolean> linkMode = session.linkModeProperty();
    private SimpleObjectProperty<Boolean> useOpenBook = session.useOpenBookProperty();

    /**
     * 走棋方
     */
    private boolean redGo;

    /**
     * 正在思考（用于连线判断）
     */
    private volatile boolean isThinking;

    /**
     * 变招列表
     */

    @FXML
    public void newButtonClick(ActionEvent event) {
        session.newGame();
    }

    @FXML
    void boardStyleSelected(ActionEvent event) {
        RadioMenuItem item = (RadioMenuItem) event.getTarget();
        if (item.equals(menuOfDefaultBoard)) {
            prop.setBoardStyle(ChessBoard.BoardStyle.DEFAULT);
        } else {
            prop.setBoardStyle(ChessBoard.BoardStyle.CUSTOM);
        }
        board.setBoardStyle(prop.getBoardStyle(), this.canvas);
    }

    @FXML
    void boardSizeSelected(ActionEvent event) {
        RadioMenuItem item = (RadioMenuItem) event.getTarget();
        if (item.equals(menuOfLargeBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.LARGE_BOARD);
        } else if (item.equals(menuOfBigBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.BIG_BOARD);
        } else if (item.equals(menuOfMiddleBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.MIDDLE_BOARD);
        } else if (item.equals(menuOfAutoFitBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.AUTOFIT_BOARD);
        } else {
            prop.setBoardSize(ChessBoard.BoardSize.SMALL_BOARD);
        }
        board.setBoardSize(prop.getBoardSize());
        if (prop.getBoardSize() == ChessBoard.BoardSize.AUTOFIT_BOARD) {
            board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), splitPane.getDividerPositions()[0]);
        }
    }
    @FXML
    void stepTipChecked(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setStepTip(item.isSelected());
        board.setStepTip(prop.isStepTip());
    }

    @FXML
    void showNumberClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setShowNumber(item.isSelected());
        board.setShowNumber(prop.isShowNumber());
    }

    @FXML
    void topWindowClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setTopWindow(item.isSelected());
        App.topWindow(prop.isTopWindow());
    }

    @FXML
    void linkBackModeChecked(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        if (linkMode.getValue()) {
            session.stopGraphLink();
        }
        prop.setLinkBackMode(item.isSelected());
    }

    @FXML
    void linkAnimationChecked(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setLinkAnimation(item.isSelected());
    }

    @FXML
    void stepSoundClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setStepSound(item.isSelected());
        board.setStepSound(prop.isStepSound());
    }

    @FXML
    void showStatusBarClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setLinkShowInfo(item.isSelected());
        statusToolBar.setVisible(item.isSelected());
        board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), splitPane.getDividerPositions()[0]);
    }

    @FXML
    public void analysisButtonClick(ActionEvent event) {
        session.toggleMode(robotAnalysis, true);
    }

    private void engineStop() {
        engineController.stop();
    }

    private void engineGo() {
        engineController.go();
    }

    @Override
    public void setThinking(boolean thinking) {
        this.isThinking = thinking;
    }

    @Override
    public AbstractGraphLinker getGraphLinker() {
        return graphLinker;
    }

    @Override
    public void onBoardCreated(com.sojourners.chess.board.ChessBoard board) {
        this.board = board;
    }

    @Override
    public javafx.scene.control.ListView<com.sojourners.chess.model.ThinkData> getThinkListView() {
        return listView;
    }

    @Override
    public javafx.scene.control.Label getInfoShowLabel() {
        return infoShowLabel;
    }

    @Override
    public javafx.scene.canvas.Canvas getCanvas() {
        return canvas;
    }

    @Override
    public void setRedGo(boolean redGo) {
        this.redGo = redGo;
    }

    @Override
    public com.sojourners.chess.controller.handle.ManualController getManualController() {
        return chessManualHandle;
    }

    @Override
    public EngineController getEngineController() {
        return engineController;
    }

    @Override
    public boolean isThinking() {
        return this.isThinking;
    }

    @Override
    public void newChessBoardForLink(String fenCode) {
        session.newChessBoard(fenCode);
    }

    @Override
    public void reverseBoard() {
        reverseButtonClick(null);
    }

    @Override
    public void applyLinkMode(String value) {
        setLinkMode(value);
    }

    @Override
    public com.sojourners.chess.controller.handle.ManualController getChessManualHandle() {
        return chessManualHandle;
    }

    @Override
    public GameSession getSession() {
        return session;
    }

    @Override
    public boolean isRedGo() {
        return redGo;
    }

    @Override
    public ChessBoard getBoard() {
        return board;
    }

    @FXML
    public void immediateButtonClick(ActionEvent event) {
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue()) {
            engineController.moveNow();
        }
    }

    @FXML
    public void changeTacticButtonClick(ActionEvent event) {
        engineController.changeTactic();
    }

    @FXML
    public void blackButtonClick(ActionEvent event) {
        session.toggleMode(robotBlack, false);
    }

    @FXML
    public void engineManageClick(ActionEvent e) {
        App.openEngineDialog();
        // 重新设置引擎列表
        refreshEngineComboBox();
        // 如果引擎被卸载，则关闭
        if ((prop.getEngineName() == null || prop.getEngineName().isEmpty())) {
            session.resetRobotModes();
            engineController.unloadEngine();
        }
    }

    @FXML
    public void redButtonClick(ActionEvent event) {
        session.toggleMode(robotRed, false);
    }

    @FXML
    public void canvasClick(MouseEvent event) {

        if (event.getButton() == MouseButton.PRIMARY) {
            // IT-12.1 #58: 连线自动走棋时允许人工走子（连线设置中开启），点击即走子并代点到平台
            boolean manualMoveAllowed = linkMode.getValue() && Boolean.TRUE.equals(prop.getLinkManualMove());
            String move = board.mouseClick((int) event.getX(), (int) event.getY(),
                    redGo && (!robotRed.getValue() || manualMoveAllowed),
                    !redGo && (!robotBlack.getValue() || manualMoveAllowed));

            if (move != null) {
                if (manualMoveAllowed) {
                    // 人工走子代点到第三方平台
                    ChessBoard.Step manualStep = board.stepForBoard(move);
                    int mx1 = manualStep.getStart().getX(), my1 = manualStep.getStart().getY();
                    int mx2 = manualStep.getEnd().getX(), my2 = manualStep.getEnd().getY();
                    if (robotBlack.getValue()) {
                        my1 = 9 - my1; my2 = 9 - my2; mx1 = 8 - mx1; mx2 = 8 - mx2;
                    }
                    graphLinker.autoClick(mx1, my1, mx2, my2);
                }
                onMoveApplied(move);
            }

            BoardContextMenu.getInstance().hide();

        } else if (event.getButton() == MouseButton.SECONDARY) {

            BoardContextMenu.getInstance().show(this.canvas, Side.RIGHT, event.getX() - this.canvas.widthProperty().doubleValue(), event.getY());
        }

    }

    // IT-11.1 #68: 引擎预测的对手应手（ponder 命中判定用），null 表示无 pending ponder
    private String pendingPonderMove;
    // IT-11.1 #68: 引擎走子待应用标志——应用后启动 ponder 而非常规分析
    private boolean startPonderOnNextMove;

    @Override
    public void onMoveApplied(String move) {
        if (startPonderOnNextMove) {
            // 引擎自身走子的应用：走子后以预测的对手应手启动 ponder 后台思考
            startPonderOnNextMove = false;
            applyMoveBookkeeping(move);
            engineController.startPonder(chessManualHandle.getFenCode(), chessManualHandle.getMoveList(), pendingPonderMove);
            return;
        }
        if (pendingPonderMove != null) {
            // 对手走子与引擎预测比较：命中则 ponderhit 继续计算，落空则废弃后走常规分析
            String expected = pendingPonderMove;
            pendingPonderMove = null;
            if (move.equals(expected) && Boolean.TRUE.equals(prop.getPonderEnable())) {
                applyMoveBookkeeping(move);
                engineController.ponderhit();
                return; // 引擎已在算该局面，无需重启分析
            }
            engineController.stop(); // 预测落空，废弃 ponder 思考
        }
        applyMoveBookkeeping(move);
        // 触发引擎走棋
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue() || robotAnalysis.getValue()) {
            engineController.go();
        } else {
            engineController.queryOpenBook();
        }
    }

    private void applyMoveBookkeeping(String move) {
        // 记录棋谱
        List<String> nextList = chessManualHandle.boardMove(move, board.translate(move, true));
        board.setManualList(nextList);
        // 趋势图
        refreshLineChart();
        // 切换行棋方
        redGo = !redGo;
    }

    @Override
    public void refreshLineChart() {
        engineController.refreshLineChart();
    }

    private void doOpenBook() {
        engineController.queryOpenBook();
    }

    @FXML
    public void copyButtonClick(ActionEvent e) {
        String fenCode = board.fenCode(redGo);
        ClipboardUtils.setText(fenCode);
    }

    @FXML
    public void pasteButtonClick(ActionEvent e) {
        String fenCode = ClipboardUtils.getText();
        if ((fenCode != null && !fenCode.isEmpty()) && fenCode.split("/").length == 10) {
            session.newFromOriginFen(fenCode);
        }
    }

    @FXML
    public void importImageMenuClick(ActionEvent e) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(PathUtils.getJarPath()));
        File file = fileChooser.showOpenDialog(App.getMainStage());
        if (file != null) {
            importFromImgFile(file);
        }
    }

    @FXML
    public void exportImageMenuClick(ActionEvent e) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(PathUtils.getJarPath()));
        fileChooser.setInitialFileName("tchess_export_" + new java.text.SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()) + ".png");
        File file = fileChooser.showSaveDialog(App.getMainStage());
        if (file != null) {
            try {
                WritableImage writableImage = new WritableImage((int) this.canvas.getWidth(), (int) this.canvas.getHeight());
                canvas.snapshot(null, writableImage);
                RenderedImage renderedImage = SwingFXUtils.fromFXImage(writableImage, null);
                ImageIO.write(renderedImage, "png", file);
            } catch (IOException ex) {
                log.log(System.Logger.Level.ERROR, "导出棋盘图片失败", ex);
            }
        }
    }

    @FXML
    public void aboutClick(ActionEvent e) {
        DialogUtils.showInfoDialog("关于", "TCHESS"
                + System.lineSeparator() + "Built on : " + App.BUILT_ON
                + System.lineSeparator() + "Author : T"
                + System.lineSeparator() + "Version : " + App.VERSION);
    }

    @FXML
    public void upgradeClick(ActionEvent e) {
        SystemUtils.openBrowser("https://github.com/sojourners/public-Xiangqi/releases");
    }

    @FXML
    public void instructionClick(ActionEvent e) {
        SystemUtils.openBrowser("https://github.com/sojourners/public-Xiangqi/blob/master/MANUAL.md");
    }

    @FXML
    public void homeClick(ActionEvent e) {
        SystemUtils.openBrowser("https://github.com/sojourners/public-Xiangqi");
    }

    @FXML
    void localBookManageButtonClick(ActionEvent e) {
        if (App.openLocalBookDialog()) {
            OpenBookManager.getInstance().setLocalOpenBooks();
        }

    }

    @FXML
    void timeSettingButtonClick(ActionEvent e) {
        App.openTimeSetting();
    }

    @FXML
    void bookSettingButtonClick(ActionEvent e) {
        App.openBookSetting();
    }

    @FXML
    void linkSettingClick(ActionEvent e) {
        App.openLinkSetting();

    }

    @FXML
    public void reverseButtonClick(ActionEvent event) {
        isReverse.setValue(!isReverse.getValue());
        board.reverse(isReverse.getValue());
    }

    @FXML
    void colorSettingClick(ActionEvent e) {
        if (App.openColorSetting()) {
            App.refreshTheme();
            board.refresh();
        }
    }

    @FXML
    private void bookSwitchButtonClick(ActionEvent e) {
        useOpenBook.setValue(!useOpenBook.getValue());
        prop.setBookSwitch(useOpenBook.getValue());

        doOpenBook();
    }

    @FXML
    private void linkButtonClick(ActionEvent e) {
        if (!engineController.isLoaded()) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        linkMode.setValue(!linkMode.getValue());
        if (linkMode.getValue()) {
            graphLinker.start();
        } else {
            session.stopGraphLink();
        }
    }

    public void initialize() {
        // 读取配置
        prop = Properties.getInstance();
        // IT-7.2: 引擎域
        engineController = new EngineController(this, listView, charPane, bookTable, infoShowLabel, timeShowLabel);
        // IT-7.3: 连线域
        linkController = new LinkController(this, linkComboBox);
        // 思考细节listView
        engineController.initListView();
        // 按钮
        setButtonTips();
        // 棋盘
        initChessBoard();
        // 库招表
        engineController.initBookTable();
        // 引擎view
        initEngineView();
        // 连线器
        initGraphLinker();
        // 按钮监听
        initButtonListener();
        // autofit board size listener
        initAutoFitBoardListener();
        // canvas drag listener
        initCanvasDragListener();
        // line chart
        engineController.initLineChart();
        // init chess manual
        chessManualHandle = new ManualController(chessManualPane, menuOfChessNotation, menuOfShowTactic, notationTree,
                manualTitleLabel, recordTable, subRecordTable, remarkText,
                manualBackButton, manualDeleteButton, manualDownButton, manualFinalButton,
                manualForwardButton, manualFrontButton, manualPlayButton, manualUpButton,
                openManualButton, saveManualButton, manualScoreButton, competitionNameText, competitionCityText, competitionDateText,
                competitionRedText, competitionBlackText, this);

        useOpenBook.setValue(prop.getBookSwitch());
        // 初始化棋局
        session.newChessBoard(null);
        // 加载引擎
        engineController.loadEngine(prop.getEngineName());
    }

    private void importFromBufferImage(BufferedImage img) {
        char[][] result = graphLinker.findChessBoard(img);
        if (result != null) {
            if (!XiangqiUtils.validateChessBoard(result) && !DialogUtils.showConfirmDialog("提示", "检测到局面不合法，可能会导致引擎退出或者崩溃，是否继续？")) {
                return;
            }
            String fenCode = ChessBoard.fenCode(result, true);
            session.newFromOriginFen(fenCode);
        }
    }

    private void importFromImgFile(File f) {
        if (f.exists() && PathUtils.isImage(f.getAbsolutePath())) {
            try {
                BufferedImage img = ImageIO.read(f);
                importFromBufferImage(img);

            } catch (IOException e) {
                log.log(System.Logger.Level.ERROR, "读取棋盘图片文件失败", e);
            }
        }
    }

    private void initCanvasDragListener() {
        this.canvas.setOnDragDropped(event -> {
            File f = event.getDragboard().getFiles().get(0);
            importFromImgFile(f);
        });
        this.canvas.setOnDragOver(event -> {
            event.acceptTransferModes(TransferMode.ANY);
            event.consume();
        });
    }

    private void initAutoFitBoardListener() {
        borderPane.widthProperty().addListener((observableValue, number, t1) -> {
            board.autoFitSize(t1.doubleValue(), borderPane.getHeight(), splitPane.getDividerPositions()[0]);
        });
        borderPane.heightProperty().addListener((observableValue, number, t1) -> {
            board.autoFitSize(borderPane.getWidth(), t1.doubleValue(), splitPane.getDividerPositions()[0]);
        });
        splitPane.getDividers().get(0).positionProperty().addListener((observableValue, number, t1) -> {
            board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), t1.doubleValue());
        });
    }

    public void initStage() {
        borderPane.setPrefWidth(prop.getStageWidth());
        borderPane.setPrefHeight(prop.getStageHeight());
        splitPane.setDividerPosition(0, prop.getSplitPos());
        splitPane2.setDividerPosition(0, prop.getSplitPos2());

        // 窗口置顶
        menuOfTopWindow.setSelected(prop.isTopWindow());
        App.topWindow(prop.isTopWindow());
    }

    private void setButtonTips() {
        newButton.setTooltip(new Tooltip("新局面"));
        copyButton.setTooltip(new Tooltip("复制局面"));
        pasteButton.setTooltip(new Tooltip("粘贴局面"));
        regretButton.setTooltip(new Tooltip("悔棋"));
        reverseButton.setTooltip(new Tooltip("翻转"));
        redButton.setTooltip(new Tooltip("引擎执红"));
        blackButton.setTooltip(new Tooltip("引擎执黑"));
        analysisButton.setTooltip(new Tooltip("分析模式"));
        immediateButton.setTooltip(new Tooltip("立即出招"));
        changeTacticButton.setTooltip(new Tooltip("变招"));
        linkButton.setTooltip(new Tooltip("连线"));
        bookSwitchButton.setTooltip(new Tooltip("启用库招"));

    }

    private void initChessBoard() {
        // 棋步提示
        menuOfStepTip.setSelected(prop.isStepTip());
        // 走棋音效
        menuOfStepSound.setSelected(prop.isStepSound());
        // 连线后台模式
        menuOfLinkBackMode.setSelected(prop.isLinkBackMode());
        // 连线动画确认
        menuOfLinkAnimation.setSelected(prop.isLinkAnimation());
        // show number
        menuOfShowNumber.setSelected(prop.isShowNumber());
        // 显示状态栏
        menuOfShowStatus.setSelected(prop.isLinkShowInfo());
        // 棋盘大小
        if (prop.getBoardSize() == ChessBoard.BoardSize.LARGE_BOARD) {
            menuOfLargeBoard.setSelected(true);
        } else if (prop.getBoardSize() == ChessBoard.BoardSize.BIG_BOARD) {
            menuOfBigBoard.setSelected(true);
        } else if (prop.getBoardSize() == ChessBoard.BoardSize.MIDDLE_BOARD) {
            menuOfMiddleBoard.setSelected(true);
        } else if (prop.getBoardSize() == ChessBoard.BoardSize.AUTOFIT_BOARD) {
            menuOfAutoFitBoard.setSelected(true);
        } else {
            menuOfSmallBoard.setSelected(true);
        }
        // 棋盘样式
        if (prop.getBoardStyle() == ChessBoard.BoardStyle.DEFAULT) {
            menuOfDefaultBoard.setSelected(true);
        } else {
            menuOfCustomBoard.setSelected(true);
        }
        // 右键菜单
        initBoardContextMenu();
        // 状态栏
        this.infoShowLabel.prefWidthProperty().bind(statusToolBar.widthProperty().subtract(120));
        engineController.refreshTimeStrategyLabel();
        this.statusToolBar.setVisible(prop.isLinkShowInfo());
    }

    private void initBoardContextMenu() {
        BoardContextMenu.getInstance().setOnAction(event -> {
            MenuItem item = (MenuItem) event.getTarget();
            if ("复制局面FEN".equals(item.getText())) {
                copyButtonClick(null);
            } else if ("粘贴局面FEN".equals(item.getText())) {
                pasteButtonClick(null);
            } else if ("交换行棋方".equals(item.getText())) {
                switchPlayer(true);
            } else if ("编辑局面".equals(item.getText())) {
                editChessBoardClick(null);
            } else if ("复制局面图片".equals(item.getText())) {
                copyImageMenuClick(null);
            } else if ("粘贴局面图片".equals(item.getText())) {
                pasteImageMenuClick(null);
            } else if ("复制棋谱".equals(item.getText())) {
                chessManualHandle.copyChessManual();
            } else if ("粘贴棋谱".equals(item.getText())) {
                chessManualHandle.pasteChessManual();
            }
        });
    }

    @FXML
    public void copyImageMenuClick(ActionEvent event) {
        WritableImage writableImage = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, writableImage);
        BufferedImage bi =SwingFXUtils.fromFXImage(writableImage, null);
        ClipboardUtils.setImage(bi);
    }

    @FXML
    public void pasteImageMenuClick(ActionEvent event) {
        Image img = ClipboardUtils.getImage();
        if (img != null) {
            importFromBufferImage((BufferedImage) img);
        }
    }

    @FXML
    public void editChessBoardClick(ActionEvent e) {
        String fenCode = App.openEditChessBoard(board.getBoard(), redGo, isReverse.getValue());
        session.newFromOriginFen(fenCode);
    }

    /**
     * new from origin fen that maybe reverse, and stop link mode at the same time
     * @param fenCode
     */
    private void newFromOriginFen(String fenCode) {
        session.newFromOriginFen(fenCode);
    }

    private void initEngineView() {
        // 引擎列表 线程数 哈希表大小
        refreshEngineComboBox();
        for (int i = 1; i <= Runtime.getRuntime().availableProcessors(); i++) {
            threadComboBox.getItems().add(String.valueOf(i));
        }
        hashComboBox.getItems().addAll("16", "32", "64", "128", "256", "512", "1024", "2048", "4096");
        // 加载设置
        threadComboBox.setValue(String.valueOf(prop.getThreadNum()));
        hashComboBox.setValue(String.valueOf(prop.getHashSize()));
    }


    private void initGraphLinker() {
        linkController.initGraphLinker();
        this.graphLinker = linkController.getGraphLinker();
    }

    private void refreshEngineComboBox() {
        engineComboBox.getItems().clear();
        for (EngineConfig ec : prop.getEngineConfigList()) {
            engineComboBox.getItems().add(ec.getName());
        }
        engineComboBox.setValue(prop.getEngineName());
    }

    private void initButtonListener() {
        addListener(redButton, robotRed);
        addListener(blackButton, robotBlack);
        addListener(analysisButton, robotAnalysis);
        addListener(reverseButton, isReverse);
        addListener(linkButton, linkMode);
        addListener(bookSwitchButton, useOpenBook);

        threadComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                int num = Integer.parseInt(t1);
                if (num != prop.getThreadNum()) {
                    prop.setThreadNum(num);
                }
            }
        });
        hashComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                int size = Integer.parseInt(t1);
                if (size != prop.getHashSize()) {
                    prop.setHashSize(size);
                }
            }
        });
        engineComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                if ((t1 != null && !t1.isEmpty()) && !t1.equals(prop.getEngineName())) {
                    // 保存引擎设置
                    prop.setEngineName(t1);
                    // 重置三个按钮
                    session.resetRobotModes();
                    redButton.setDisable(false);
                    blackButton.setDisable(false);
                    immediateButton.setDisable(false);
                    // 停止连线
                    if (linkMode.getValue()) {
                        session.stopGraphLink();
                    }
                    // 加载新引擎
                    engineController.loadEngine(t1);
                }
            }
        });
        linkComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                setLinkMode(t1);
            }
        });
    }

    private void setLinkMode(String t1) {
        session.setLinkMode(t1);
    }

    private void addListener(Button button, ObjectProperty property) {
        property.addListener((ChangeListener<Boolean>) (observableValue, aBoolean, t1) -> {
            setButtonSelected(button, t1);
        });
        setButtonSelected(button, Boolean.TRUE.equals(property.getValue()));
    }

    private void setButtonSelected(Button button, boolean selected) {
        String selectedStylesheet = this.getClass().getResource("/style/selected-button.css").toString();
        if (selected) {
            if (!button.getStylesheets().contains(selectedStylesheet)) {
                button.getStylesheets().add(selectedStylesheet);
            }
            if (!button.getStyleClass().contains("selected-state")) {
                button.getStyleClass().add("selected-state");
            }
        } else {
            button.getStylesheets().remove(selectedStylesheet);
            button.getStyleClass().remove("selected-state");
        }
    }

    @Override
    public void onEngineBestMove(String first, String second) {
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue()) {
            ChessBoard.Step s = board.stepForBoard(first);

            // IT-11.1 #68: 引擎给出 ponder 预测应手且启用后台思考时（人机对弈，连线模式暂不启用），
            // 走子应用后立即以预测应手启动 ponder 后台计算
            if (Boolean.TRUE.equals(prop.getPonderEnable()) && second != null && !linkMode.getValue()) {
                startPonderOnNextMove = true;
                pendingPonderMove = second;
            }

            Platform.runLater(() -> {
                board.move(s.getStart().getX(), s.getStart().getY(), s.getEnd().getX(), s.getEnd().getY());
                board.setTip(second, null, 1);

                onMoveApplied(first);
            });

            if (linkMode.getValue()) {
                engineController.autoClickTactic(s);
            }
        }
    }

    @FXML
    public void bookTableClick(MouseEvent event) {
        engineController.onBookTableClick(event);
    }

    @FXML
    public void exit() {
        engineController.unloadEngine();

        OpenBookManager.getInstance().close();

        graphLinker.stop();

        prop.setStageWidth(borderPane.getWidth());
        prop.setStageHeight(borderPane.getHeight());
        prop.setSplitPos(splitPane.getDividerPositions()[0]);
        prop.setSplitPos2(splitPane2.getDividerPositions()[0]);

        prop.save();

        Platform.exit();
    }

    /**
     * 图形连线初始化棋盘
     * @param fenCode
     * @param isReverse
     */
    @Override
    public void switchPlayer(boolean f) {
        session.switchPlayer(f);
    }

    // ------------- 棋谱管理 start -----------------
    private ManualController chessManualHandle;
    @FXML
    private BorderPane chessManualPane;
    @FXML
    private CheckMenuItem menuOfChessNotation;
    @FXML
    private CheckMenuItem menuOfShowTactic;
    @FXML
    private TreeView notationTree;
    @FXML
    private Label manualTitleLabel;
    @FXML
    private ListView subRecordTable;
    @FXML
    private TextArea remarkText;
    @FXML
    private Button manualBackButton;
    @FXML
    private Button manualDeleteButton;
    @FXML
    private Button manualDownButton;
    @FXML
    private Button manualFinalButton;
    @FXML
    private Button manualForwardButton;
    @FXML
    private Button manualFrontButton;
    @FXML
    private Button manualPlayButton;
    @FXML
    private Button manualUpButton;
    @FXML
    private Button openManualButton;
    @FXML
    private Button saveManualButton;
    @FXML
    private Button manualScoreButton;
    @FXML
    private TextField competitionNameText;
    @FXML
    private TextField competitionCityText;
    @FXML
    private TextField competitionDateText;
    @FXML
    private TextField competitionRedText;
    @FXML
    private TextField competitionBlackText;

    @FXML
    void menuOfShowTacticClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setManualTip(item.isSelected());
        board.setManualTip(item.isSelected());
    }
    @FXML
    void openChessManualFolder(ActionEvent event) {
        chessManualHandle.openChessNotationFolder(event);
    }
    @FXML
    void deleteButtonClick(ActionEvent event) {
        session.checkLinkMode();
        chessManualHandle.deleteButtonClick(event);
    }
    @FXML
    void scoreButtonClick(ActionEvent event) {
        if (!engineController.isLoaded()) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        session.checkLinkMode();
        chessManualHandle.scoreButtonClick(event);
    }
    @FXML
    void playButtonClick(ActionEvent event) {
        session.checkLinkMode();
        chessManualHandle.playButtonClick(event);
    }
    @FXML
    void downwardButtonClick(ActionEvent event) {
        session.checkLinkMode();
        chessManualHandle.manualButtonClick(8);
    }
    @FXML
    void upwardButtonClick(ActionEvent event) {
        session.checkLinkMode();
        chessManualHandle.manualButtonClick(7);
    }

    @Override
    public void turnOnAnalysisMode() {
        if (!robotAnalysis.getValue()) {
            analysisButtonClick(null);
        }
    }

    @Override
    public void turnOffAnalysisMode() {
        if (robotAnalysis.getValue()) {
            analysisButtonClick(null);
        }
    }

    @Override
    public void newChessBoardFromManual(String fenCode) {
        session.newChessBoard(fenCode, true);
    }

    @Override
    public void browseChessRecord(String fenCode, List<String> moveList, boolean redGo, List<String> nextList) {
        session.checkLinkMode();
        // 棋盘
        board.browseChessRecord(fenCode, moveList);
        board.setManualList(nextList);
        this.redGo = redGo;
        // 趋势图
        refreshLineChart();
        // 引擎走棋
        if (robotRed.getValue() && robotBlack.getValue()) {
            // 如果引擎执红同时执黑，取消状态（否则会有问题）
            robotRed.setValue(false);
            robotBlack.setValue(false);
            engineStop();
        } else if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue() || robotAnalysis.getValue()) {
            // 轮到引擎走棋或者分析模式
            engineGo();
        } else {
            // 其他情况，停止引擎思考
            engineStop();
            // 库招显示
            doOpenBook();
        }
    }

    @Override
    public void setNextList(List<String> nextList) {
        board.setManualList(nextList);
    }

    private void checkLinkMode() {
        session.checkLinkMode();
    }

    @FXML
    void recordTableClick(MouseEvent event) {
        session.checkLinkMode();
        chessManualHandle.manualButtonClick(5);
    }

    @FXML
    public void backButtonClick(ActionEvent event) {
        session.checkLinkMode();
        chessManualHandle.manualButtonClick(2);
    }

    @FXML
    public void regretButtonClick(ActionEvent event) {
        session.checkLinkMode();
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue()) {
            chessManualHandle.manualButtonClick(2);
        } else {
            chessManualHandle.manualButtonClick(6);
        }
    }

    @FXML
    void forwardButtonClick(ActionEvent event) {
        session.checkLinkMode();
        chessManualHandle.manualButtonClick(3);
    }

    @FXML
    void finalButtonClick(ActionEvent event) {
        session.checkLinkMode();
        chessManualHandle.manualButtonClick(4);
    }

    @FXML
    void frontButtonClick(ActionEvent event) {
        session.checkLinkMode();
        chessManualHandle.manualButtonClick(1);
    }

    @FXML
    void openChessManualFile(ActionEvent event) {
        chessManualHandle.openChessManualFile(event);
    }

    @FXML
    void saveAsChessManualFile(ActionEvent event) {
        chessManualHandle.saveAsChessManualFile(event);
    }

    @FXML
    void saveChessManualFile(ActionEvent event) {
        chessManualHandle.saveChessManualFile(event);
    }
    // ------------- 棋谱管理 end -----------------
}
