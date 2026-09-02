package com.sojourners.chess.linker;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.XiangqiUtils;


import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;


public abstract class AbstractGraphLinker implements GraphLinker, Runnable {

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

    private volatile boolean pause;

    private Properties prop;

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

    void scan() {
        this.thread = Thread.ofVirtual().unstarted(this);
        this.thread.start();
    }

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

    public void pause() {
        this.pause = true;
    }
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

                    if (!findChessBoard(board2)) {
                        stableFrames++;
                        continue;
                    }

                    boolean isReverse;
                    try {
                        isReverse = reverse(board2);
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (isSame(board2, callBack.getEngineBoard())) {
                        stableFrames++;
                        continue;
                    }
                    stableFrames = 0;

                    Action action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                    if (prop.isLinkAnimation() && needConfirm(board2, callBack.getEngineBoard(), action)) {
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
                                e.printStackTrace();
                                f = true;
                                break;
                            }
                        } while (!isSame(board1, board2));

                        if (f) continue;

                        action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                    }

                    if (action != null) {
                        System.out.println("action " + action);
                        if (action.flag == 1) {
                            callBack.linkerMove(action.x1, action.y1, action.x2, action.y2);

                        } else if (action.flag == 2) {
                            if (isReverse) {
                                action.y1 = 9 - action.y1;
                                action.y2 = 9 - action.y2;
                                action.x1 = 8 - action.x1;
                                action.x2 = 8 - action.x2;
                            }
                            ExecContext ctx = new ExecContext(
                                    boardPos, board2, 0, recognizer,
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
                            boardPos = null;
                            break;
                        }
                        if (action.flag == 4) {
                            count++;
                            if (count > 9) {
                                boardPos = null;
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

    void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 前台截图
     * @param windowPos
     * @return
     */
    public BufferedImage screenshotByFront(Rectangle windowPos) {
        if (windowPos.width == 0 || windowPos.height == 0) {
            return null;
        }
        return robot.createScreenCapture(windowPos);
    }

    /**
     * 前台点击
     * @param windowPos
     * @param p1
     * @param p2
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
     * 后台截图，默认不支持（子类按需覆盖）
     */
    @Override
    public BufferedImage screenshotByBack(Rectangle windowPos) {
        return null;
    }

    /**
     * 后台点击，默认不支持（子类按需覆盖）
     */
    @Override
    public void mouseClickByBack(Point p1, Point p2) {
    }

    /**
     * 寻找棋盘区域
     * @return
     */
    boolean findBoardPosition() {
        if (this.boardPos != null) {
            return true;
        }
        BufferedImage img = screenshot(true);
        this.boardPos = this.recognizer.findBoardPosition(img);
        return this.boardPos != null;
    }

    /**
     * 截图
     * @param fullScreen
     * @return
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


    private boolean findChessBoard(char[][] board) {
        // 截图
        BufferedImage img = screenshot(false);
        if (img == null) {
            return false;
        }
        // 差量识别：棋盘区域与上一帧无变化则跳过重复识别
        if (prevImg != null && imageEqual(prevImg, img)) {
            return false;
        }
        prevImg = img;
        // ai识别棋盘棋子
        if (!this.recognizer.findChessBoard(img, board)) {
            return false;
        }
        boolean f = XiangqiUtils.validateChessBoard(board);
        if (!f) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    System.out.print(board[i][j]);
                }
                System.out.println();
            }
        }
        return f;
    }

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
     * 初始化棋盘局面
     * @return
     */
    private boolean initChessBoard() {
        if (!findChessBoard(board2)) {
            return false;
        }

        boolean isReverse = false;
        try {
            isReverse = reverse(board2);
        } catch (Exception e) {
            e.printStackTrace();
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
     * 自动点击走棋
     * @param x1
     * @param y1
     * @param x2
     * @param y2
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
    }

    // find chess board from image
    public char[][] findChessBoard(BufferedImage img) {
        char[][] tmp = new char[10][9];
        if (this.recognizer.findChessBoard(img, tmp)) {
            return tmp;
        } else {
            return null;
        }
    }
}
