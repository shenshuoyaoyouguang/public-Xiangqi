package com.sojourners.chess.enginee;


import com.sojourners.chess.config.Properties;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.PathUtils;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 引擎封装
 */
public class Engine {

    private static final System.Logger log = System.getLogger(Engine.class.getName());

    // 预编译数字匹配正则：引擎输出每行会解析十余个字段，避免每字段重复编译 Pattern 抢占 CPU（IT-4.4 #67）
    private static final java.util.regex.Pattern NUMERIC_PATTERN = java.util.regex.Pattern.compile("^-?\\d+$");

    private Process process;

    private String protocol;

    private String engineName;

    // 当前分析的 FEN 与 go 发起时间，用于 bestmove 时输出可定位现场（IT-4.1）
    private volatile String currentFen;

    private volatile long goStartTime;

    private AnalysisModel analysisModel;
    private long analysisValue;

    private volatile boolean threadNumChange;
    private int threadNum;

    private volatile boolean hashSizeChange;
    private int hashSize;

    /**
     * 停止标志位
     */
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);

    /**
     * 是否有活跃的 analysis 搜索（仅 analysis 路径置 true，ponder 不走此路径）
     */
    private volatile boolean searchActive;

    /**
     * analysis 世代号：每次发起 analysis 递增，bestmove 据此丢弃过期结果
     */
    private final AtomicLong generation = new AtomicLong(0);
    private volatile long currentGeneration = 0;

    /**
     * stop 屏障：stop() 等待 bestMove 消费 stop 信号后再发起新 analysis
     */
    private final Object stopLock = new Object();
    private volatile boolean stopConsumed = true;
    private static final long STOP_CONSUME_TIMEOUT = 1000L;

    // IT-11.1: ponder 后台思考状态（ponderhitSent：已通知引擎命中，其后的 bestmove 为本方回合着法）
    private volatile boolean pondering;
    private volatile boolean ponderhitSent;
    private volatile long time;

    private BufferedReader reader;

    private BufferedWriter writer;

    private EngineCallBack cb;

    private Thread thread;

    private Random random;

    private int multiPV;

    public enum AnalysisModel {
        FIXED_TIME,
        FIXED_STEPS,
        FIXED_NODES,
        INFINITE;
    }

    /**
     * Constructs and initializes an engine instance by launching the engine process and configuring options.
     *
     * @param ec the engine configuration (path, protocol, options)
     * @param cb the callback for receiving engine output (bestmove, think details, book results)
     * @throws IOException if the engine process cannot be started
     */
    public Engine(EngineConfig ec, EngineCallBack cb) throws IOException {
        this.protocol = ec.getProtocol();
        this.engineName = ec.getName();
        this.cb = cb;
        this.random = new SecureRandom();

        this.time = Integer.MAX_VALUE;

        if (ec.getOptions().get("MultiPV") != null) {
            multiPV = Integer.parseInt(ec.getOptions().get("MultiPV"));
        } else {
            multiPV = 1;
        }

        process = Runtime.getRuntime().exec(ec.getPath(), null, PathUtils.getParentDir(ec.getPath()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        thread = Thread.startVirtualThread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.log(System.Logger.Level.DEBUG, "引擎输出: " + line);
                    if (line.contains("depth") || line.contains("nps")) {
                        thinkDetail(line);
                    } else if (line.contains("bestmove")) {
                        bestMove(line);
                    }
                }
            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "读取引擎输出异常", e);
            }
        });

        cmd(protocol);

        for (Map.Entry<String, String> entry : ec.getOptions().entrySet()) {
            if ("uci".equals(this.protocol)) {
                cmd("setoption name " + entry.getKey() + " value " + entry.getValue());
            } else if ("ucci".equals(this.protocol)) {
                cmd("setoption " + entry.getKey() + " " + entry.getValue());
            }
        }
    }

    /**
     * Gets the MultiPV setting (number of principal variations to analyze).
     *
     * @return the MultiPV value
     */
    public int getMultiPV() {
        return multiPV;
    }

    /**
     * Sleeps for the specified time, logging a warning if interrupted.
     *
     * @param t milliseconds to sleep
     */
    private void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            log.log(System.Logger.Level.WARNING, "引擎等待延时被中断", e);
        }
    }

    /**
     * Tests an engine executable to detect its protocol (UCI or UCCI) and extract default options.
     *
     * @param filePath the path to the engine executable
     * @param options  a map to populate with detected option names and default values
     * @return "uci" if UCI protocol detected, "ucci" if UCCI detected, null if neither
     */
    public static String test(String filePath, LinkedHashMap<String, String> options) {
        Process p = null;
        Thread h = null;
        BufferedWriter bw = null;
        BufferedReader br = null;
        try {
            p = Runtime.getRuntime().exec(filePath);
            bw = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));

            AtomicBoolean f = new AtomicBoolean(false);
            BufferedReader finalBr = br;
            (h = Thread.ofVirtual().unstarted(() -> {
                try {
                    String line;
                    while ((line = finalBr.readLine()) != null) {
                        if ("uciok".equals(line) || "ucciok".equals(line) ) {
                            f.set(true);
                        }
                        if (line.startsWith("option") && line.contains("name") && line.contains("type") && line.contains("default")
                                && !line.contains("Threads") && !line.contains("Hash")) {

                            String[] str = line.split("name|type|default");
                            String key = str[1].trim();
                            String value = str[3].trim().split(" ")[0];
                            options.put(key, value);
                        }
                    }
                } catch (Exception e) {
                    log.log(System.Logger.Level.ERROR, "测试引擎时读取输出异常", e);
                }
            })).start();

            bw.write("uci" + System.getProperty("line.separator"));
            bw.flush();
            Thread.sleep(1000);
            if (f.get()) {
                return "uci";
            }

            bw.write("ucci" + System.getProperty("line.separator"));
            bw.flush();
            Thread.sleep(1000);
            if (f.get()) {
                return "ucci";
            }

            return null;

        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "测试引擎异常", e);
            return null;
        } finally {
            if (p != null) {
                p.destroy();
            }
            if (h.isAlive()) {
                h.interrupt();
            }
            try {
                if (bw != null) {
                    bw.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (Exception e) {
                log.log(System.Logger.Level.WARNING, "关闭引擎测试流异常", e);
            }
        }
    }

    /**
     * Validates that a move string is well-formed (4 characters, valid file/rank ranges).
     *
     * @param move the move string to validate
     * @return true if the move is valid format, false otherwise
     */
    private boolean validateMove(String move) {
        if (move == null || move.isEmpty() || move.length() != 4) {
            return false;
        }
        if (move.charAt(0) < 'a' || move.charAt(0) > 'i' || move.charAt(2) < 'a' || move.charAt(2) > 'i') {
            return false;
        }
        if (move.charAt(1) < '0' || move.charAt(1) > '9' || move.charAt(3) < '0' || move.charAt(3) > '9') {
            return false;
        }
        return true;
    }
    /**
     * Processes a bestmove response from the engine, applying stop barrier logic, ponder state filtering, and generation staleness checks.
     * If valid, forwards the bestmove to the callback after applying configured delay.
     *
     * @param msg the bestmove line from the engine (e.g., "bestmove h2e2 ponder h9g7")
     */
    private void bestMove(String msg) {
        long gen = currentGeneration;
        searchActive = false;
        boolean stale = stopFlag.getAndSet(false);
        synchronized (stopLock) {
            stopConsumed = true;
            stopLock.notifyAll();
        }
        if (stale) {
            return;
        }
        if (gen != currentGeneration) {
            return;
        }
        if (pondering && !ponderhitSent) {
            // 未经 ponderhit 的 ponder 搜索结果是预测局面的应手而非当前局面的，丢弃
            pondering = false;
            ponderhitSent = false;
            return;
        }
        pondering = false;

        String[] str = msg.split(" ");
        if (str.length < 2 || !validateMove(str[1])) {
            return;
        }
        log.log(System.Logger.Level.INFO, "引擎分析完成 引擎=" + engineName + " 耗时=" + (System.currentTimeMillis() - goStartTime) + "ms fen=" + currentFen + " bestmove=" + str[1]);
        if (Properties.getInstance().getEngineDelayEnd() > 0 && Properties.getInstance().getEngineDelayEnd() >= Properties.getInstance().getEngineDelayStart()) {
            int t = random.nextInt(Properties.getInstance().getEngineDelayStart(), Properties.getInstance().getEngineDelayEnd());
            sleep(t);
        }
        cb.bestMove(str[1], str.length == 4 ? str[3] : null);
    }
    /**
     * Parses an info line from the engine (depth, score, nps, time, pv, multipv) and forwards it to the callback.
     * Also manages stopFlag logic based on depth and time to prevent premature stop acknowledgment.
     *
     * @param msg the info line from the engine
     */
    private void thinkDetail(String msg) {
        String[] str = msg.split(" ");
        ThinkData td = new ThinkData();
        List<String> detail = new ArrayList<>();
        td.setDetail(detail);
        int flag = 0;
        for (int i = 0; i < str.length; i++) {
            if (flag != 0) {
                if (flag == 6) {
                    detail.add(str[i]);
                } else {
                    if (NUMERIC_PATTERN.matcher(str[i]).matches()) {
                        if (flag == 1) {
                            td.setNps(Long.parseLong(str[i]));

                        } else if (flag == 2) {
                            td.setTime(Long.parseLong(str[i]));

                        } else if (flag == 3) {
                            td.setDepth(Integer.parseInt(str[i]));

                        } else if (flag == 4) {
                            td.setMate(Integer.parseInt(str[i]));

                        } else if (flag == 5) {
                            td.setScore(Integer.parseInt(str[i]));

                        } else if (flag == 7) {
                            td.setPv(Integer.parseInt(str[i]));
                        }
                        flag = 0;
                    } else {
                        continue;
                    }
                }
            } else {
                if ("depth".equals(str[i])) {
                    flag = 3;
                } else if ("score".equals(str[i])) {
                    if ("mate".equals(str[i + 1])) {
                        flag = 4;
                    } else {
                        flag = 5;
                    }
                } else if ("mate".equals(str[i])) {
                    flag = 4;
                } else if ("nps".equals(str[i])) {
                    flag = 1;
                } else if ("time".equals(str[i])) {
                    flag = 2;
                } else if ("pv".equals(str[i])) {
                    flag = 6;
                } else if ("multipv".equals(str[i])) {
                    flag = 7;
                }
            }
        }

        if (td.getDepth() != null && td.getDepth() < 5) {
            stopFlag.set(false);
        }
        if (td.getTime() != null) {
            if (td.getTime() < this.time || td.getTime() > 0 && td.getTime() < 70) {
                stopFlag.set(false);
            }
            this.time = td.getTime();
        }

        if (td.getDetail().size() > 0) {
            cb.thinkDetail(td);
        }
    }

    /**
     * Starts analysis for the given position, first querying opening book if enabled, then falling back to engine analysis.
     *
     * @param fenCode the FEN code of the position
     * @param moves   the move list leading to this position
     * @param board   the board representation (for book lookup)
     * @param redGo   true if red to move
     */
    public void analysis(String fenCode, List<String> moves, char[][] board, boolean redGo) {
        Thread.startVirtualThread(() -> {
            if (Properties.getInstance().getBookSwitch()) {
                long s = System.currentTimeMillis();
                List<BookData> results = OpenBookManager.getInstance().queryBook(board, redGo, moves.size() / 2 >= Properties.getInstance().getOffManualSteps());
                log.log(System.Logger.Level.DEBUG, "查询库时间" + (System.currentTimeMillis() - s));
                this.cb.showBookResults(results);
                if (results.size() > 0 && this.analysisModel != AnalysisModel.INFINITE) {
                    if (Properties.getInstance().getBookDelayEnd() > 0 && Properties.getInstance().getBookDelayEnd() >= Properties.getInstance().getBookDelayStart()) {
                        int t = random.nextInt(Properties.getInstance().getBookDelayStart(), Properties.getInstance().getBookDelayEnd());
                        sleep(t);
                    }
                    this.cb.bestMove(results.get(0).getMove(), null);
                    return;
                }

            }
            this.analysis(fenCode, moves, null);
        });
    }

    /**
     * Starts engine analysis for the given position with optional searchmoves restriction (tactics mode).
     *
     * @param fenCode    the FEN code of the position
     * @param moves      the move list leading to this position
     * @param tacticList optional list of candidate moves to restrict search (searchmoves), or null for unrestricted
     */
    public void analysis(String fenCode, List<String> moves, List<String> tacticList) {
        stop();
        awaitStopConsumed();

        currentGeneration = generation.incrementAndGet();

        if (threadNumChange) {
            cmd(("uci".equals(this.protocol) ? "setoption name Threads value " : "setoption Threads ") + threadNum);
            this.threadNumChange = false;
        }
        if (hashSizeChange) {
            cmd(("uci".equals(this.protocol) ? "setoption name Hash value " : "setoption Hash ") + hashSize);
            this.hashSizeChange = false;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("position fen ").append(fenCode);
        this.currentFen = fenCode;
        this.goStartTime = System.currentTimeMillis();
        if (moves != null && moves.size() > 0) {
            sb.append(" moves");
            for (String move : moves) {
                sb.append(" ").append(move);
            }
        }
        cmd(sb.toString());

        boolean hasTactics = tacticList != null && !tacticList.isEmpty();
        if (hasTactics) {
            sb = new StringBuilder();
            sb.append(" searchmoves");
            for (String tactic : tacticList) {
                sb.append(" ").append(tactic);
            }
        }
        if (analysisModel == AnalysisModel.FIXED_STEPS) {
            cmd("go depth " + analysisValue + (hasTactics ? sb.toString() : ""));
        } else if (analysisModel == AnalysisModel.FIXED_TIME) {
            cmd("go movetime " + analysisValue + (hasTactics ? sb.toString() : ""));
        } else if (analysisModel == AnalysisModel.FIXED_NODES) {
            cmd("go nodes " + analysisValue + (hasTactics ? sb.toString() : ""));
        } else {
            cmd("go infinite" + (hasTactics ? sb.toString() : ""));
        }
        searchActive = true;
    }

    /**
     * Requests the engine to return its current best move immediately by sending the stop command.
     */
    public void moveNow() {
        cmd("stop");
    }

    /**
     * Stops the current analysis or ponder search. Sets the stop barrier if a search is active to ensure subsequent
     * analysis waits for the stop to be consumed by bestMove.
     */
    public void stop() {
        if (searchActive) {
            synchronized (stopLock) {
                stopConsumed = false;
            }
        }
        stopFlag.set(true);
        cmd("stop");
        pondering = false;
        ponderhitSent = false;
    }

    /**
     * 等待 bestMove 消费 stop 信号，避免快速连续 analysis 时旧 bestmove 污染新局面。
     * 超时 1s 兜底，防止引擎异常无 bestmove 时死等。
     */
    private void awaitStopConsumed() {
        synchronized (stopLock) {
            long deadline = System.currentTimeMillis() + STOP_CONSUME_TIMEOUT;
            while (!stopConsumed) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                try {
                    stopLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * IT-11.1 #68: 启动 ponder 后台思考（预测对手应手后继续计算）。
     * @param fenCode   当前局面 FEN
     * @param moves     已走着法序列
     * @param ponderMove 引擎预测的对手应手
     */
    public void startPonder(String fenCode, List<String> moves, String ponderMove) {
        stop();
        StringBuilder sb = new StringBuilder();
        sb.append("position fen ").append(fenCode);
        if (moves != null && moves.size() > 0) {
            sb.append(" moves");
            for (String m : moves) {
                sb.append(" ").append(m);
            }
        }
        sb.append(" ").append(ponderMove);
        cmd(sb.toString());
        // go ponder 必须带上与常规 go 一致的时限参数：ponderhit 后引擎据此收束并返回 bestmove，
        // 否则命中后无限搜索挂起；searchmoves 属于上一局面的根节点，不带入
        cmd("go ponder" + ponderLimit());
        pondering = true;
        ponderhitSent = false;
        // ponder 不走 analysis 路径，显式清除活跃标志，避免 stop() 误触 stop 屏障
        searchActive = false;
    }

    /**
     * Notifies the engine that the ponder prediction was correct (opponent played the expected move).
     * Only sends ponderhit once per ponder session.
     */
    public void ponderhit() {
        if (pondering && !ponderhitSent) {
            cmd("ponderhit");
            ponderhitSent = true;
        }
    }

    /**
     * Builds the time limit parameters for "go ponder" based on the current analysis model.
     *
     * @return the limit string (e.g., " depth 12", " movetime 5000", " nodes 4096", or "" for INFINITE)
     */
    private String ponderLimit() {
        if (analysisModel == AnalysisModel.FIXED_STEPS) {
            return " depth " + analysisValue;
        } else if (analysisModel == AnalysisModel.FIXED_TIME) {
            return " movetime " + analysisValue;
        } else if (analysisModel == AnalysisModel.FIXED_NODES) {
            return " nodes " + analysisValue;
        }
        return "";
    }

    /**
     * Checks if the engine is currently pondering (background thinking on predicted move).
     *
     * @return true if pondering is active, false otherwise
     */
    public boolean isPondering() {
        return pondering;
    }

    /**
     * Sends a command to the engine via the writer stream and flushes it.
     *
     * @param command the command string to send (without line terminator)
     */
    private void cmd(String command) {
        log.log(System.Logger.Level.DEBUG, "引擎命令: " + command);
        try {
            writer.write(command + System.getProperty("line.separator"));
            writer.flush();
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "向引擎发送命令失败", e);
        }
    }

    /**
     * Sets the engine thread count. The change will be applied before the next analysis.
     *
     * @param threadNum the number of threads to use
     */
    public void setThreadNum(int threadNum) {
        if (threadNum != this.threadNum) {
            this.threadNum = threadNum;
            this.threadNumChange = true;
        }

    }

    /**
     * Sets the engine hash table size in MB. The change will be applied before the next analysis.
     *
     * @param hashSize the hash size in megabytes
     */
    public void setHashSize(int hashSize) {
        if (hashSize != this.hashSize) {
            this.hashSize = hashSize;
            this.hashSizeChange = true;
        }
    }

    /**
     * Sets the analysis time control model and value.
     *
     * @param model the analysis model (FIXED_TIME, FIXED_STEPS, FIXED_NODES, or INFINITE)
     * @param v     the value (milliseconds for time, depth for steps, node count for nodes, ignored for infinite)
     */
    public void setAnalysisModel(AnalysisModel model, long v) {
        this.analysisModel = model;
        this.analysisValue = v;
    }

    /**
     * Closes the engine by sending quit, interrupting the reader thread, destroying the process, and closing streams.
     */
    public void close() {
        try {
            if (process.isAlive()) {
                cmd("quit");
            }

            if (thread.isAlive()) {
                thread.interrupt();
            }

            if (process.isAlive()) {
                process.destroy();
            }

            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        } catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "关闭引擎进程异常", e);
        }
    }
}