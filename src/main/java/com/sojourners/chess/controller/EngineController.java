package com.sojourners.chess.controller;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.enginee.EngineCallBack;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.DialogUtils;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;

import java.util.List;

/**
 * 引擎域（IT-7.2）：引擎生命周期、分析与开局库结果展示。
 * 对局状态经 {@link EngineHost} 访问；引擎回调直接进入本类，
 * bestMove 走子应用链经 host 回到 Controller（PR5 聚合层收编）。
 */
public class EngineController implements EngineCallBack {

    private static final System.Logger log = System.getLogger(EngineController.class.getName());

    // 预编译数字匹配正则：引擎输出每行会解析十余个字段，避免每字段重复编译 Pattern 抢占 CPU（IT-4.4 #67）
    private static final java.util.regex.Pattern NUMERIC_PATTERN = java.util.regex.Pattern.compile("^-?\\d+$");

    private final EngineHost host;

    private final Properties prop = Properties.getInstance();

    private Engine engine;

    private List<String> tacticList;

    private final ListView<ThinkData> listView;

    private final BorderPane charPane;

    private final TableView<BookData> bookTable;

    private final Label infoShowLabel;

    private final Label timeShowLabel;

    private XYChart.Series lineChartSeries;

    // 分析 UI 刷新节流（IT-4.4 #67）：引擎每行 info 都回调 thinkDetail，
    // 高频 Platform.runLater 会洪泛 FX 线程抢占 CPU，压低同机引擎 NPS。
    // 150ms 窗口内的中间帧合并为 pending，窗口结束后经 FX 队列补提交，保证最终帧不丢
    private long lastThinkUpdateTime = 0;
    private ThinkData pendingTd;
    private boolean pendingScheduled = false;

    public EngineController(EngineHost host, ListView<ThinkData> listView, BorderPane charPane,
                            TableView<BookData> bookTable, Label infoShowLabel, Label timeShowLabel) {
        this.host = host;
        this.listView = listView;
        this.charPane = charPane;
        this.bookTable = bookTable;
        this.infoShowLabel = infoShowLabel;
        this.timeShowLabel = timeShowLabel;
    }

    public boolean isLoaded() {
        return engine != null;
    }

    public int getMultiPV() {
        return engine == null ? 1 : engine.getMultiPV();
    }

    public void unloadEngine() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }

    public void moveNow() {
        if (engine != null) {
            engine.moveNow();
        }
    }

    public void stop() {
        if (engine != null) {
            engine.stop();
        }
    }

    public void go() {
        if (engine == null) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        if (session().robotRedProperty().getValue() && host.isRedGo() || session().robotBlackProperty().getValue() && !host.isRedGo()) {
            host.setThinking(true);
        } else {
            host.setThinking(false);
        }

        // 重置变招列表
        tacticList = null;

        engine.setThreadNum(prop.getThreadNum());
        engine.setHashSize(prop.getHashSize());
        engine.setAnalysisModel(session().robotAnalysisProperty().getValue() ? Engine.AnalysisModel.INFINITE : prop.getAnalysisModel(), prop.getAnalysisValue());
        engine.analysis(host.getChessManualHandle().getFenCode(), host.getChessManualHandle().getMoveList(), host.getBoard().getBoard(), host.isRedGo());
    }

    public void changeTactic() {
        if (session().robotRedProperty().getValue() && host.isRedGo() || session().robotBlackProperty().getValue() && !host.isRedGo() || session().robotAnalysisProperty().getValue()) {
            stop();
            if (tacticList == null || tacticList.size() <= 1) {
                tacticList = host.getBoard().getTacticList(host.isRedGo());
            }
            if (!listView.getItems().isEmpty()) {
                for (ThinkData td : listView.getItems()) {
                    if (td.getPv() == 1) {
                        tacticList.remove(td.getDetail().get(0));
                        break;
                    }
                }
            }
            engine.setThreadNum(prop.getThreadNum());
            engine.setHashSize(prop.getHashSize());
            engine.setAnalysisModel(session().robotAnalysisProperty().getValue() ? Engine.AnalysisModel.INFINITE : prop.getAnalysisModel(), prop.getAnalysisValue());
            engine.analysis(host.getChessManualHandle().getFenCode(), host.getChessManualHandle().getMoveList(), tacticList);
        }
    }

    public void loadEngine(String name) {
        try {
            if ((name != null && !name.isEmpty())) {
                for (EngineConfig ec : prop.getEngineConfigList()) {
                    if (name.equals(ec.getName())) {
                        if (engine != null) {
                            engine.close();
                        }
                        engine = new Engine(ec, this);
                        host.getBoard().showMultiPV(engine.getMultiPV() > 1);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "加载引擎失败", e);
        }
    }

    /**
     * 连线模式下自动点击走棋
     */
    public void autoClickTactic(ChessBoard.Step step) {
        if (step != null) {
            int x1 = step.getStart().getX(), y1 = step.getStart().getY();
            int x2 = step.getEnd().getX(), y2 = step.getEnd().getY();
            if (session().robotBlackProperty().getValue()) {
                y1 = 9 - y1;
                y2 = 9 - y2;
                x1 = 8 - x1;
                x2 = 8 - x2;
            }
            host.getGraphLinker().autoClick(x1, y1, x2, y2);
        }
        host.setThinking(false);
    }

    @Override
    public void bestMove(String first, String second) {
        host.onEngineBestMove(first, second);
    }

    @Override
    public void thinkDetail(ThinkData td) {
        if (session().robotRedProperty().getValue() && host.isRedGo() || !session().robotRedProperty().getValue() && session().robotBlackProperty().getValue() || session().robotAnalysisProperty().getValue()) {
            td.generate(host.isRedGo(), session().isReverseProperty().getValue(), host.getBoard());
            if (td.getValid()) {
                long now = System.currentTimeMillis();
                if (now - lastThinkUpdateTime < 150) {
                    pendingTd = td;
                    if (!pendingScheduled) {
                        pendingScheduled = true;
                        Platform.runLater(this::flushPendingThinkDetail);
                    }
                    return;
                }
                lastThinkUpdateTime = now;
                submitThinkDetail(td);
            }
        }
    }

    private void flushPendingThinkDetail() {
        pendingScheduled = false;
        ThinkData t = pendingTd;
        pendingTd = null;
        if (t != null) {
            lastThinkUpdateTime = System.currentTimeMillis();
            submitThinkDetail(t);
        }
    }

    private void submitThinkDetail(ThinkData td) {
        Platform.runLater(() -> {
            listView.getItems().addFirst(td);
            if (listView.getItems().size() > 128) {
                listView.getItems().removeLast();
            }

            if (prop.isLinkShowInfo()) {
                infoShowLabel.setText(td.getTitle() + " | " + td.getBody());
                setScoreStyle(infoShowLabel, td.getScore());
                timeShowLabel.setText(getTimeStrategyString());
            }

            host.getBoard().setTip(td.getDetail().get(0), td.getDetail().size() > 1 ? td.getDetail().get(1) : null, td.getPv());

            if (td.getPv() == 1) {
                host.getChessManualHandle().setScore(td.getScore(), td.getMate());
            }
        });
    }

    public String getTimeStrategyString() {
        switch (prop.getAnalysisModel()) {
            case Engine.AnalysisModel.FIXED_TIME:
                return "固定时间" + prop.getAnalysisValue() / 1000d + "秒";
            case Engine.AnalysisModel.FIXED_STEPS:
                return "固定深度" + prop.getAnalysisValue() + "层";
            case Engine.AnalysisModel.FIXED_NODES:
                long nodes = prop.getAnalysisValue();
                if (nodes > 1000) {
                    nodes /= 1000;
                    return "固定节点" + nodes + "K个";
                } else {
                    return "固定节点" + nodes + "个";
                }
            default:
                return "";
        }
    }

    public void refreshTimeStrategyLabel() {
        timeShowLabel.setText(getTimeStrategyString());
    }

    public void setScoreStyle(Label label, double score) {
        label.getStyleClass().removeAll("positive-score", "negative-score");
        label.getStyleClass().add(score >= 0 ? "positive-score" : "negative-score");
    }

    @Override
    public void showBookResults(List<BookData> list) {
        this.bookTable.getItems().clear();
        for (BookData bd : list) {
            String move = bd.getMove();
            bd.setWord(host.getBoard().translate(move, false));
            this.bookTable.getItems().add(bd);
        }
    }

    public void onBookTableClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            if (host.isRedGo() && !session().robotRedProperty().getValue() || !host.isRedGo() && !session().robotBlackProperty().getValue() || session().robotAnalysisProperty().getValue()) {
                BookData bd = bookTable.getSelectionModel().getSelectedItem();
                if (bd == null) {
                    return;
                }
                Platform.runLater(() -> {
                    host.getBoard().move(bd.getMove());
                    host.onMoveApplied(bd.getMove());
                });
            }
        }
    }

    public void initLineChart() {
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis(-1000, 1000, 500);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);
        yAxis.setTickMarkVisible(false);
        yAxis.setMinorTickVisible(false);

        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setMinHeight(100);
        lineChart.setLegendVisible(false);
        lineChart.setCreateSymbols(false);
        lineChart.setVerticalGridLinesVisible(false);
        lineChart.getStylesheets().add(this.getClass().getResource("/style/table.css").toString());

        lineChartSeries = new XYChart.Series();
        lineChart.getData().add(lineChartSeries);

        charPane.setCenter(lineChart);
    }

    public void refreshLineChart() {
        List<XYChart.Data> oldList = lineChartSeries.getData();
        List<XYChart.Data> newList = host.getChessManualHandle().getScoreList();
        int i = 0;
        while (i < oldList.size() && i < newList.size()) {
            XYChart.Data o = oldList.get(i);
            XYChart.Data n = newList.get(i);
            if (!o.getXValue().equals(n.getXValue()) || !o.getYValue().equals(n.getYValue())) {
                for (int j = oldList.size() - 1; j >= i; j--) {
                    oldList.remove(j);
                }
                break;
            }
            i++;
        }
        if (i < oldList.size()) {
            for (int j = oldList.size() - 1; j >= i; j--) {
                oldList.remove(j);
            }
        } else if (i < newList.size()) {
            oldList.addAll(newList.subList(i, newList.size()));
        }
    }

    public void queryOpenBook() {
        if (session().useOpenBookProperty().getValue()) {
            Thread.startVirtualThread(() -> {
                List<BookData> results = OpenBookManager.getInstance().queryBook(host.getBoard().getBoard(), host.isRedGo(), host.getChessManualHandle().getP() / 2 >= Properties.getInstance().getOffManualSteps());
                this.showBookResults(results);
            });
        } else {
            this.bookTable.getItems().clear();
        }
    }

    public void initBookTable() {
        TableColumn moveCol = bookTable.getColumns().get(0);
        moveCol.setCellValueFactory(new PropertyValueFactory<BookData, String>("word"));
        TableColumn scoreCol = bookTable.getColumns().get(1);
        scoreCol.setCellValueFactory(new PropertyValueFactory<BookData, Integer>("score"));
        TableColumn winRateCol = bookTable.getColumns().get(2);
        winRateCol.setCellValueFactory(new PropertyValueFactory<BookData, Double>("winRate"));
        TableColumn winNumCol = bookTable.getColumns().get(3);
        winNumCol.setCellValueFactory(new PropertyValueFactory<BookData, Integer>("winNum"));
        TableColumn drawNumCol = bookTable.getColumns().get(4);
        drawNumCol.setCellValueFactory(new PropertyValueFactory<BookData, Integer>("drawNum"));
        TableColumn loseNumCol = bookTable.getColumns().get(5);
        loseNumCol.setCellValueFactory(new PropertyValueFactory<BookData, Integer>("loseNum"));
        TableColumn noteCol = bookTable.getColumns().get(6);
        noteCol.setCellValueFactory(new PropertyValueFactory<BookData, String>("note"));
        TableColumn sourceCol = bookTable.getColumns().get(7);
        sourceCol.setCellValueFactory(new PropertyValueFactory<BookData, String>("source"));
    }

    private GameSession session() {
        return host.getSession();
    }
}
