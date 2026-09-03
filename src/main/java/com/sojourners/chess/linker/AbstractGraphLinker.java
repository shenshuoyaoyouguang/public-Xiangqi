package com.sojourners.chess.linker;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.PathUtils;
import com.sojourners.chess.util.XiangqiUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public abstract class AbstractGraphLinker implements GraphLinker, Runnable {

    private static final System.Logger log = System.getLogger(AbstractGraphLinker.class.getName());

    /**
     * 扫描线程
     */
    private Thread thread;
    /**
     * 棋盘区域
     */
    private Rectangle boardPos;
    /**
     * 识别棋盘 暂存
     */
    private char[][] board2 = new char[10][9];

    private char[][] board1 = new char[10][9];

    /**
     * 上一帧棋盘截图，用于差量识别
     */
    private BufferedImage prevImg;

    private IRecognizer recognizer;

    private IMoveExecutor moveExecutor;

    private LinkerCallBack callBack;

    private Robot robot;

    private int count;

    // 连续差异过大计数（IT-4.3 #64）：瞬态动画遮挡时等待识别恢复，避免误判新局面清空棋谱
    private int diffErrorCount;

    private volatile boolean pause;

    private Properties prop;

    /**
     * 上一帧截图是否与当前帧相同（用于区分"截图不变"与"识别失败"）
     */
    private boolean frameUnchanged;

    /**
     * 上次成功识别并翻转后的 isReverse，供截图不变时复用
     */
    private boolean lastIsReverse;

    /**
     * 上次定位棋盘时的目标窗口位置，用于检测窗口几何变化
     */
    private Rectangle lastWindowPos;

    /**
     * Constructs the abstract graph linker with the specified callback.
     *
     * @param callBack the callback interface for notifying the controller of linking events
     * @throws AWTException if Robot creation fails
     */
    public AbstractGraphLinker(LinkerCallBack callBack) throws AWTException {
        this.callBack = callBack;
        robot = new Robot();
        this.count = 0;
        this.recognizer = new YoloRecognizer();
        this.moveExecutor = new MouseExecutor();
        this.prop = Properties.getInstance();
        this.pause = false;
    }

    /**
     * 开始连线
     */
    @Override
    public void start() {
        getTargetWindowId();
    }

    /**
     * Starts the board scanning thread (resets board position and previous image state).
     */
    void scan() {
        this.boardPos = null;
        this.prevImg = null;
        this.thread = Thread.ofVirtual().unstarted(this);
        this.thread.start();
    }

    /**
     * Checks if two board arrays are identical.
     *
     * @param board1 first board (10x9 char array)
     * @param board2 second board (10x9 char array)
     * @return true if both boards are non-null and every cell matches, false otherwise
     */
    private boolean isSame(char[][] board1, char[][] board2) {
        if (board1 == null || board2 == null) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (board1[i][j] != board2[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Pauses the linking scan (stops processing new moves until resumed).
     */
    public void pause() {
        this.pause = true;
    }

    /**
     * Resumes the linking scan (allows processing new moves).
     */
    public void resume() {
        this.pause = false;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            if (!findBoardPosition()) {
                sleep(1000);
                continue;
            }
            if (!initChessBoard()) {
                sleep(1000);
                continue;
            }
            int stableFrames = 0;
            while (!Thread.currentThread().isInterrupted()) {
                long baseTime = prop.getLinkScanTime();
                long scanTime = stableFrames > 10 ? Math.min(baseTime * 2, 2000L) : baseTime;
                sleep(scanTime);
                if (!callBack.isThinking() && !pause) {

                    boolean isReverse;
                    boolean frameChanged;

                    if (!findChessBoard(board2)) {
                        if (!frameUnchanged) {
                            stableFrames++;
                            continue;
                        }
                        isReverse = lastIsReverse;
                        frameChanged = false;
                    } else {
                        try {
                            isReverse = reverse(board2);
                        } catch (Exception e) {
                            log.log(System.Logger.Level.WARNING, "识别棋盘翻转状态失败，跳过本次扫描", e);
                            continue;
                        }
                        lastIsReverse = isReverse;
                        frameChanged = true;
                    }

                    if (isSame(board2, callBack.getEngineBoard())) {
                        stableFrames++;
                        continue;
                    }
                    stableFrames = 0;

                    Action action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                    if (frameChanged && prop.isLinkAnimation() && needConfirm(board2, callBack.getEngineBoard(), action)) {
                        boolean f = false;
                        do {
                            char[][] tmp = board1;
                            board1 = board2;
                            board2 = tmp;

                            if (!findChessBoard(board2)) {
                                f = true;
                                break;
                            }

                            try {
                                isReverse = reverse(board2);
                            } catch (Exception e) {
                                log.log(System.Logger.Level.WARNING, "确认走子动画时识别棋盘翻转状态失败", e);
                                f = true;
                                break;
                            }
                        } while (!isSame(board1, board2));

                        if (f) continue;

                        action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                    }

                    if (action != null) {
                        log.log(System.Logger.Level.INFO, "连线识别到走子操作: " + action);
                        if (action.flag == 1) {
                            diffErrorCount = 0;
                            callBack.linkerMove(action.x1, action.y1, action.x2, action.y2);

                        } else if (action.flag == 2) {
                            diffErrorCount = 0;
                            if (isReverse) {
                                action.y1 = 9 - action.y1;
                                action.y2 = 9 - action.y2;
                                action.x1 = 8 - action.x1;
                                action.x2 = 8 - action.x2;
                            }
                            ExecContext ctx = new ExecContext(
                                    boardPos, toPhysical(board2, isReverse), 0, recognizer,
                                    () -> screenshot(false),
                                    (p1, p2) -> {
                                        if (prop.isLinkBackMode()) {
                                            mouseClickByBack(p1, p2);
                                        } else {
                                            mouseClickByFront(getTargetWindowPosition(), p1, p2);
                                        }
                                    },
                                    prop.isLinkBackMode()
                                            ? (p1, p2) -> mouseClickByFront(getTargetWindowPosition(), p1, p2)
                                            : null
                            );
                            ExecuteResult r = moveExecutor.execute(action, ctx);
                            if (r == ExecuteResult.SCREENSHOT_INVALID) {
                                callBack.linkerNotify("画面不可识别");
                            } else if (r == ExecuteResult.RETRY_FAILED_PROMOTED) {
                                callBack.linkerNotify("后台走棋失败，已降级为前台走棋");
                            }

                        } else if (action.flag == 3) {
                            // IT-4.3 #64: 将军等动画瞬态遮挡也会造成差异过大，
                            // 连续多次才判定为新局面重新初始化，避免棋谱被误清空
                            diffErrorCount++;
                            if (diffErrorCount >= 3) {
                                log.log(System.Logger.Level.INFO, "连线识别连续 " + diffErrorCount + " 次差异过大，判定为新局面，重新初始化棋盘");
                                boardPos = null;
                                prevImg = null;
                                break;
                            }
                            log.log(System.Logger.Level.INFO, "连线识别差异过大（连续 " + diffErrorCount + " 次），等待识别恢复");
                            continue;
                        }
                        if (action.flag == 4) {
                            count++;
                            if (count > 9) {
                                boardPos = null;
                                prevImg = null;
                                break;
                            }
                        } else {
                            count = 0;
                        }
                    }

                }
            }
        }
    }

    /**
     * Determines if the detected action needs animation confirmation (waiting for piece to settle).
     * Required for rook/cannon captures where the captured piece may still be animating.
     *
     * @param linkBoard   the board state recognized from the link
     * @param engineBoard the board state from the engine
     * @param action      the detected action
     * @return true if animation confirmation is needed, false otherwise
     */
    private boolean needConfirm(char[][] linkBoard, char[][] engineBoard, Action action) {
        if (action == null) {
            return false;
        }
        if (action.flag == 3) {
            return true;
        }
        if (action.flag != 1 || !(linkBoard[action.y2][action.x2] == 'r' || linkBoard[action.y2][action.x2] == 'R' || linkBoard[action.y2][action.x2] == 'c' || linkBoard[action.y2][action.x2] == 'C') || !(engineBoard[action.y2][action.x2] == ' ')) {
            return false;
        }
        if (linkBoard[action.y2][action.x2] == 'r' || linkBoard[action.y2][action.x2] == 'R') {
            int x = -1, y = -1;
            if (action.x1 == action.x2) {
                x = action.x1;
                if (action.y2 > action.y1) {
                    y = action.y2 + 1;
                } else {
                    y = action.y2 - 1;
                }
            }
            if (action.y1 == action.y2) {
                y = action.y1;
                if (action.x2 > action.x1) {
                    x = action.x2 + 1;
                } else {
                    x = action.x2 - 1;
                }
            }
            if (x < 0 || x > 8 || y < 0 || y > 9 || engineBoard[y][x] != ' ' && XiangqiUtils.isRed(engineBoard[action.y1][action.x1]) == XiangqiUtils.isRed(engineBoard[y][x])) {
                return false;
            }
        }
        if (linkBoard[action.y2][action.x2] == 'c' || linkBoard[action.y2][action.x2] == 'C') {
            if (action.x1 == action.x2) {
                int x = action.x1, y;
                int p;
                if (action.y2 > action.y1) {
                    y = action.y2 + 1;
                    p = 1;
                } else {
                    y = action.y2 - 1;
                    p = -1;
                }
                if (y < 0 || y > 9) {
                    return false;
                }
                if (engineBoard[y][x] != ' ') {
                    for (int i = y + p; i >= 0 && i <= 9; i += p) {
                        if (engineBoard[i][x] != ' ' && XiangqiUtils.isRed(engineBoard[i][x]) == XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return false;
                        } else if (engineBoard[i][x] != ' ' && XiangqiUtils.isRed(engineBoard[i][x]) != XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            if (action.y1 == action.y2) {
                int x, y = action.y1;
                int p;
                if (action.x2 > action.x1) {
                    x = action.x2 + 1;
                    p = 1;
                } else {
                    x = action.x2 - 1;
                    p = -1;
                }
                if (x < 0 || x > 8 || y < 0 || y > 9) {
                    return false;
                }
                if (engineBoard[y][x] != ' ') {
                    for (int j = x + p; j >= 0 && j <= 8; j += p) {
                        if (engineBoard[y][j] != ' ' && XiangqiUtils.isRed(engineBoard[y][j]) == XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return false;
                        } else if (engineBoard[y][j] != ' ' && XiangqiUtils.isRed(engineBoard[y][j]) != XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 对比棋盘，计算出当前操作（等价于原 compareBoard，内部拆分为三个纯函数）
     * flag： 1对方已走棋，需要同步到引擎
     *      2引擎已走棋，需要同步到目标平台
     *      3识别到新棋局
     *      4可能识别到新棋局
     * @param linkBoard
     * @param engineBoard
     * @param isReverse
     * @param watchMode
     * @return
     */
    private Action compareBoard(char[][] linkBoard, char[][] engineBoard, boolean isReverse, boolean watchMode) {
        DiffResult diff = diffBoards(linkBoard, engineBoard);

        if (diff.diff1 > 2 || diff.diff2 >= 2 && diff.diff3 > 2) {
            return new Action(3);
        }

        List<Candidate> candidates = classifyAction(diff.diffList, linkBoard, engineBoard, isReverse, watchMode);

        Action action = null;
        int sum = 0;
        for (Candidate candidate : candidates) {
            if (checkMoveLegality(candidate.flag, candidate.from, candidate.to, linkBoard, engineBoard)) {
                sum++;
                action = new Action(candidate.flag, candidate.from.y, candidate.from.x, candidate.to.y, candidate.to.x);
            }
        }

        if (sum == 1) {
            return action;
        }

        if (diff.diff1 + diff.diff2 + diff.diff3 > 2) {
            return new Action(4);
        }

        return null;
    }

    /**
     * 计算两棋盘差异，产出差异点集与分类计数
     * 注意：Point.x 表示行 [0,9]，Point.y 表示列 [0,8]
     */
    public static DiffResult diffBoards(char[][] linkBoard, char[][] engineBoard) {
        DiffResult result = new DiffResult();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (linkBoard[i][j] != engineBoard[i][j]) {
                    result.diffList.add(new Point(i, j));
                    if (linkBoard[i][j] != ' ' && engineBoard[i][j] != ' ') {
                        result.diff1++;
                    } else if (linkBoard[i][j] != ' ' && engineBoard[i][j] == ' ') {
                        result.diff2++;
                    } else {
                        result.diff3++;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 分类走棋方向（flag 1/2）与起止坐标
     * isReverse 表示棋盘翻转，watchMode 表示观战模式
     */
    public static List<Candidate> classifyAction(List<Point> diffList, char[][] linkBoard, char[][] engineBoard, boolean isReverse, boolean watchMode) {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < diffList.size(); i++) {
            for (int j = i + 1; j < diffList.size(); j++) {
                Point p1 = diffList.get(i), p2 = diffList.get(j);
                boolean f = false;
                int flag = 0;
                Point from = null, to = null;
                if (linkBoard[p1.x][p1.y] == engineBoard[p2.x][p2.y] && linkBoard[p1.x][p1.y] != ' ') {
                    if (linkBoard[p2.x][p2.y] == ' ' && engineBoard[p1.x][p1.y] == ' ') {
                        if (watchMode || isReverse && XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) || !isReverse && !XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                            flag = 1;
                            from = p2;
                            to = p1;
                            f = true;
                        } else if (isReverse && !XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) || !isReverse && XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                            flag = 2;
                            from = p1;
                            to = p2;
                            f = true;
                        }
                    }
                    if (linkBoard[p2.x][p2.y] == ' ' && engineBoard[p1.x][p1.y] != ' ' && XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) != XiangqiUtils.isRed(engineBoard[p1.x][p1.y])) {
                        flag = 1;
                        from = p2;
                        to = p1;
                        f = true;
                    }
                    if (!watchMode && engineBoard[p1.x][p1.y] == ' ' && linkBoard[p2.x][p2.y] != ' ' && XiangqiUtils.isRed(engineBoard[p2.x][p2.y]) != XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                        flag = 2;
                        from = p1;
                        to = p2;
                        f = true;
                    }
                }
                if (linkBoard[p2.x][p2.y] == engineBoard[p1.x][p1.y] && linkBoard[p2.x][p2.y] != ' ') {
                    if (linkBoard[p1.x][p1.y] == ' ' && engineBoard[p2.x][p2.y] == ' ') {
                        if (watchMode || isReverse && XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) || !isReverse && !XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                            flag = 1;
                            from = p1;
                            to = p2;
                            f = true;
                        } else if (isReverse && !XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) || !isReverse && XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                            flag = 2;
                            from = p2;
                            to = p1;
                            f = true;
                        }
                    }
                    if (linkBoard[p1.x][p1.y] == ' ' && engineBoard[p2.x][p2.y] != ' ' && XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) != XiangqiUtils.isRed(engineBoard[p2.x][p2.y])) {
                        flag = 1;
                        from = p1;
                        to = p2;
                        f = true;
                    }
                    if (!watchMode && engineBoard[p2.x][p2.y] == ' ' && linkBoard[p1.x][p1.y] != ' ' && XiangqiUtils.isRed(engineBoard[p1.x][p1.y]) != XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                        flag = 2;
                        from = p2;
                        to = p1;
                        f = true;
                    }
                }
                if (f) {
                    candidates.add(new Candidate(flag, from, to));
                }
            }
        }
        return candidates;
    }

    /**
     * 走棋合法性校验：flag 1 用 engineBoard 校验，flag 2 用 linkBoard 校验
     */
    public static boolean checkMoveLegality(int flag, Point from, Point to, char[][] linkBoard, char[][] engineBoard) {
        return flag == 1 && XiangqiUtils.canGo(engineBoard, from.x, from.y, to.x, to.y)
                || flag == 2 && XiangqiUtils.canGo(linkBoard, from.x, from.y, to.x, to.y);
    }

    /**
     * 棋盘差异结果
     */
    public static class DiffResult {
        public List<Point> diffList = new ArrayList<>();
        public int diff1 = 0;
        public int diff2 = 0;
        public int diff3 = 0;
    }

    /**
     * 走棋候选（flag + 起止坐标）
     */
    public static class Candidate {
        public int flag;
        public Point from;
        public Point to;
        public Candidate(int flag, Point from, Point to) {
            this.flag = flag;
            this.from = from;
            this.to = to;
        }
    }

    /**
     * Sleeps for the specified time, handling InterruptedException by re-interrupting the thread.
     *
     * @param time milliseconds to sleep
     */
    void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            log.log(System.Logger.Level.WARNING, "连线扫描线程休眠被中断", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Captures a screenshot in foreground mode using Robot.
     *
     * @param windowPos the screen region to capture
     * @return the captured image, or null if windowPos has zero width or height
     */
    public BufferedImage screenshotByFront(Rectangle windowPos) {
        if (windowPos.width == 0 || windowPos.height == 0) {
            return null;
        }
        return robot.createScreenCapture(windowPos);
    }

    /**
     * Performs a mouse click in foreground mode by moving the cursor to p1, clicking, moving to p2, and clicking again.
     * Restores the original cursor position after completion.
     *
     * @param windowPos the target window's position and size
     * @param p1        first click point (source) in window-relative coordinates
     * @param p2        second click point (destination) in window-relative coordinates
     */
    @Override
    public void mouseClickByFront(Rectangle windowPos, Point p1, Point p2) {

        Point mouse = MouseInfo.getPointerInfo().getLocation();

        robot.mouseMove(windowPos.x + p1.x, windowPos.y+ p1.y);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (prop.getMouseClickDelay() > 0) {
            robot.delay(prop.getMouseClickDelay());
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (prop.getMouseMoveDelay() > 0) {
            robot.delay(prop.getMouseMoveDelay());
        }
        robot.mouseMove(windowPos.x + p2.x, windowPos.y + p2.y);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (prop.getMouseClickDelay() > 0) {
            robot.delay(prop.getMouseClickDelay());
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.mouseMove((int) mouse.getX(), (int) mouse.getY());

    }

    /**
     * Finds the board position in the target window. If already found, validates that the window hasn't moved.
     *
     * @return true if board position is found and valid, false if not found or window geometry changed
     */
    boolean findBoardPosition() {
        if (this.boardPos != null) {
            Rectangle currentPos = getTargetWindowPosition();
            if (currentPos == null || lastWindowPos == null
                    || currentPos.x != lastWindowPos.x
                    || currentPos.y != lastWindowPos.y
                    || currentPos.width != lastWindowPos.width
                    || currentPos.height != lastWindowPos.height) {
                this.boardPos = null;
                this.prevImg = null;
                if (currentPos != null) {
                    lastWindowPos = currentPos;
                }
                return false;
            }
            return true;
        }
        BufferedImage img = screenshot(true);
        this.boardPos = this.recognizer.findBoardPosition(img);
        if (this.boardPos != null) {
            lastWindowPos = getTargetWindowPosition();
        }
        return this.boardPos != null;
    }

    /**
     * Captures a screenshot of either the full window or just the board region.
     *
     * @param fullScreen true to capture the full window, false to capture only the board region
     * @return the captured image
     */
    BufferedImage screenshot(boolean fullScreen) {
        if (prop.isLinkBackMode()) {
            BufferedImage img = screenshotByBack(fullScreen ? null : boardPos);
            return img;

        } else {
            Rectangle pos = getTargetWindowPosition();
            if (!fullScreen) {
                pos.setLocation(pos.x + boardPos.x, pos.y + boardPos.y);
                pos.setSize(boardPos.width, boardPos.height);
            }
            BufferedImage img = screenshotByFront(pos);
            return img;
        }
    }


    /**
     * Finds the chess board state by capturing and recognizing the current board region.
     * Implements frame-based deduplication: if the screenshot is identical to the previous frame, returns false.
     *
     * @param board the 10x9 char array to populate with recognized pieces
     * @return true if board was successfully recognized and validated, false otherwise
     */
    private boolean findChessBoard(char[][] board) {
        long start = System.currentTimeMillis();
        // 截图
        BufferedImage img = screenshot(false);
        if (img == null) {
            frameUnchanged = false;
            return false;
        }
        // 差量识别：棋盘区域与上一帧无变化则跳过重复识别
        if (prevImg != null && imageEqual(prevImg, img)) {
            frameUnchanged = true;
            return false;
        }
        frameUnchanged = false;
        prevImg = img;
        // ai识别棋盘棋子
        boolean aiOk = this.recognizer.findChessBoard(img, board);
        boolean valid = aiOk && XiangqiUtils.validateChessBoard(board);
        log.log(System.Logger.Level.DEBUG, "连线识别耗时 " + (System.currentTimeMillis() - start) + "ms ai识别=" + (aiOk ? "成功" : "失败") + " 校验=" + (valid ? "通过" : "失败"));
        if (!aiOk) {
            saveFailedSample(img, null, start);
            return false;
        }
        if (!valid) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    sb.append(board[i][j]);
                }
                sb.append('\n');
            }
            log.log(System.Logger.Level.DEBUG, "连线识别棋盘校验失败，识别结果:\n" + sb);
            saveFailedSample(img, board, start);
            return false;
        }
        return true;
    }

    /**
     * 识别失败样本留存（IT-4.2）：截图 + 识别结果 + 上下文存档到 samples/ 目录，
     * 供识别质量专项（IT-13.x）分析。同一失败 10 秒内不重复留存，避免扫描循环刷盘。
     */
    private volatile long lastSampleTime;

    /**
     * Saves a failed recognition sample (screenshot and metadata) to the samples/ directory for quality analysis.
     * Throttled to once per 10 seconds to avoid disk flooding during repeated failures.
     *
     * @param img   the screenshot that failed recognition
     * @param board the recognized board (or null if AI recognition failed entirely)
     * @param start the timestamp when recognition started (for timing calculation)
     */
    private void saveFailedSample(BufferedImage img, char[][] board, long start) {
        long now = System.currentTimeMillis();
        if (now - lastSampleTime < 10_000) {
            return;
        }
        lastSampleTime = now;
        try {
            File dir = new File(PathUtils.getJarPath() + "samples");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            File imgFile = new File(dir, "sample_" + stamp + ".png");
            ImageIO.write(img, "png", imgFile);
            StringBuilder sb = new StringBuilder();
            sb.append("时间: ").append(stamp).append('\n');
            sb.append("识别耗时: ").append(System.currentTimeMillis() - start).append("ms\n");
            sb.append("失败类型: ").append(board == null ? "AI识别失败（未检出棋盘）" : "棋盘校验失败（识别结果非法）").append('\n');
            if (board != null) {
                sb.append("识别结果:\n");
                for (int i = 0; i < 10; i++) {
                    for (int j = 0; j < 9; j++) {
                        sb.append(board[i][j]);
                    }
                    sb.append('\n');
                }
            }
            Files.writeString(new File(dir, "sample_" + stamp + ".txt").toPath(), sb.toString());
            log.log(System.Logger.Level.INFO, "连线识别失败，样本已留存: " + imgFile.getName());
        } catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "识别失败样本留存失败", e);
        }
    }

    /**
     * Checks pixel-by-pixel equality between two images.
     *
     * @param a first image
     * @param b second image
     * @return true if both images are non-null, have the same dimensions, and identical RGB values at every pixel
     */
    private boolean imageEqual(BufferedImage a, BufferedImage b) {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        for (int x = 0; x < a.getWidth(); x++) {
            for (int y = 0; y < a.getHeight(); y++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Detects if the board is reversed (red at top) by locating the kings, and flips the board array if necessary.
     *
     * @param board the board to check and potentially flip in-place
     * @return true if the board was reversed (and has been flipped), false if normal orientation
     * @throws Exception if both kings are missing
     */
    private boolean reverse(char[][] board) throws Exception {
        // 是否翻转
        int rowRedKing = -1, rowBlackKing = -1;
        for (int i = 0; i < 10; i++) {
            for (int j = 3; j < 6; j++) {
                if (board[i][j] == 'k') {
                    rowBlackKing = i;
                } else if (board[i][j] == 'K') {
                    rowRedKing = i;
                }
            }
        }
        if (rowBlackKing == -1 && rowRedKing == -1) {
            throw new Exception("find king failed.");
        }
        boolean isReverse = rowRedKing >= 0 && rowRedKing <= 2 || rowBlackKing >= 7 && rowBlackKing <= 9;
        if (isReverse) {
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 9; j++) {
                    char tmp = board[i][j];
                    board[i][j] = board[9 - i][8 - j];
                    board[9 - i][8 - j] = tmp;
                }
            }
        }
        return isReverse;
    }

    /**
     * Converts a logical (already flipped to standard orientation) board to a physical (on-screen) board copy for verification.
     *
     * @param logical   the board in logical orientation (red at bottom)
     * @param isReverse whether the screen display is reversed
     * @return the physical board (same as logical if not reversed, flipped copy if reversed)
     */
    private static char[][] toPhysical(char[][] logical, boolean isReverse) {
        if (!isReverse) {
            return logical;
        }
        char[][] physical = new char[10][9];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                physical[i][j] = logical[9 - i][8 - j];
            }
        }
        return physical;
    }

    /**
     * Initializes the board state by recognizing the current position and notifying the callback with the FEN code.
     *
     * @return true if initialization succeeded, false if recognition or reverse detection failed
     */
    private boolean initChessBoard() {
        if (!findChessBoard(board2)) {
            return false;
        }

        boolean isReverse = false;
        try {
            isReverse = reverse(board2);
        } catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "初始化连线棋盘时识别翻转状态失败", e);
            return false;
        }
        // 是否红走
        String fenCode = ChessBoard.fenCode(board2, null);
        boolean redGo = !isReverse || "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR".equals(fenCode);
        fenCode = ChessBoard.fenCode(board2, redGo);
        // 回调，初始化棋盘
        callBack.linkerInitChessBoard(fenCode, isReverse);
        return true;
    }

    /**
     * Automatically clicks on the board to execute a move (used for manual move forwarding in link mode).
     *
     * @param x1 source column [0,8]
     * @param y1 source row [0,9]
     * @param x2 destination column [0,8]
     * @param y2 destination row [0,9]
     */
    public void autoClick(int x1, int y1, int x2, int y2) {

        Point p1 = MouseExecutor.getPosition(x1, y1, boardPos);
        Point p2 = MouseExecutor.getPosition(x2, y2, boardPos);
        if (prop.isLinkBackMode()) {
            mouseClickByBack(p1, p2);
        } else {
            Rectangle windowPos = getTargetWindowPosition();
            mouseClickByFront(windowPos, p1, p2);
        }
    }

    /**
     * 停止连线
     */
    @Override
    public void stop() {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        this.boardPos = null;
        this.prevImg = null;
    }

    /**
     * Recognizes chess pieces on the board from an image (used for testing or external calls).
     *
     * @param img the board image to analyze
     * @return a 10x9 char array with recognized pieces, or null if recognition failed
     */
    public char[][] findChessBoard(BufferedImage img) {
        char[][] tmp = new char[10][9];
        if (this.recognizer.findChessBoard(img, tmp)) {
            return tmp;
        } else {
            return null;
        }
    }
}
