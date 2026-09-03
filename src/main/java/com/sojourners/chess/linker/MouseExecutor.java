package com.sojourners.chess.linker;

import com.sojourners.chess.yolo.OnnxModel;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 当前鼠标/Robot 执行实现，内置"点击 → 截图验证 → 重试 → 降级"闭环
 */
public class MouseExecutor implements IMoveExecutor {

    private static final int VERIFY_WAIT_MS = 500;

    @Override
    public ExecuteResult execute(Action action, ExecContext ctx) {
        Point p1 = getPosition(action.x1, action.y1, ctx.boardPos);
        Point p2 = getPosition(action.x2, action.y2, ctx.boardPos);

        ExecuteResult r = tryClickAndVerify(action, p1, p2, ctx);
        if (r != ExecuteResult.FAILED) {
            return r;
        }

        if (Thread.currentThread().isInterrupted()) {
            return ExecuteResult.FAILED;
        }

        r = tryClickAndVerify(action, p1, p2, ctx);
        if (r != ExecuteResult.FAILED) {
            return r;
        }

        if (Thread.currentThread().isInterrupted()) {
            return ExecuteResult.FAILED;
        }

        if (ctx.degradeClick != null) {
            ctx.degradeClick.accept(new Point(p1), new Point(p2));
            return ExecuteResult.RETRY_FAILED_PROMOTED;
        }
        return ExecuteResult.FAILED;
    }

    /**
     * Attempts one click-and-verify cycle: clicks the move, waits, captures screenshot, and verifies the move applied.
     *
     * @param action the move action
     * @param p1     source pixel point
     * @param p2     destination pixel point
     * @param ctx    execution context
     * @return SUCCESS if move verified, SCREENSHOT_INVALID if capture/recognition failed, FAILED if move didn't apply
     */
    private ExecuteResult tryClickAndVerify(Action action, Point p1, Point p2, ExecContext ctx) {
        ctx.primaryClick.accept(new Point(p1), new Point(p2));
        sleep(ctx.verifyWaitMs > 0 ? ctx.verifyWaitMs : VERIFY_WAIT_MS);
        if (Thread.currentThread().isInterrupted()) {
            return ExecuteResult.FAILED;
        }
        BufferedImage img = ctx.screenshot.get();
        if (img == null) {
            return ExecuteResult.SCREENSHOT_INVALID;
        }
        char[][] after = new char[10][9];
        if (!ctx.recognizer.findChessBoard(img, after)) {
            return ExecuteResult.SCREENSHOT_INVALID;
        }
        return moveApplied(ctx.beforeBoard, after, action) ? ExecuteResult.SUCCESS : ExecuteResult.FAILED;
    }

    /**
     * Verifies that a move actually applied by checking that the source square became empty and the destination has a piece.
     *
     * @param before the board state before the move (or null to skip verification)
     * @param after  the board state after the move
     * @param action the move action
     * @return true if the move applied (source empty, destination occupied), false otherwise
     */
    private static boolean moveApplied(char[][] before, char[][] after, Action action) {
        if (before == null) {
            return true;
        }
        return before[action.y1][action.x1] != ' '
                && after[action.y1][action.x1] == ' '
                && after[action.y2][action.x2] != ' ';
    }

    /**
     * Converts board coordinates to pixel coordinates within the board region.
     * Applies edge offsets to avoid clicking too close to the border.
     *
     * @param x        the column index [0,8]
     * @param y        the row index [0,9]
     * @param boardPos the board region rectangle
     * @return the pixel point in window-relative coordinates
     */
    public static Point getPosition(int x, int y, Rectangle boardPos) {
        double pieceWith = boardPos.width / (8 + OnnxModel.PADDING * 2);
        double pieceHeight = boardPos.height / (9 + OnnxModel.PADDING * 2);
        Point p = new Point((int) (boardPos.x + pieceWith * OnnxModel.PADDING + (x * pieceWith)),
                (int) (boardPos.y + pieceHeight * OnnxModel.PADDING + (y * pieceHeight)));
        if (x == 0) {
            p.x += 0.2 * pieceWith;
        } else if (x == 8) {
            p.x -= 0.2 * pieceWith;
        }
        if (y == 0) {
            p.y += 0.2 * pieceHeight;
        } else if (y == 9) {
            p.y -= 0.2 * pieceHeight;
        }
        return p;
    }

    /**
     * Sleeps for the specified duration, handling interruption by setting the interrupt flag.
     *
     * @param ms milliseconds to sleep
     */
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}