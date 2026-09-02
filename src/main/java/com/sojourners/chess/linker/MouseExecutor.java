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

        ExecuteResult r = tryClickAndVerify(p1, p2, ctx);
        if (r != ExecuteResult.FAILED) {
            return r;
        }

        r = tryClickAndVerify(p1, p2, ctx);
        if (r != ExecuteResult.FAILED) {
            return r;
        }

        if (ctx.degradeClick != null) {
            ctx.degradeClick.accept(p1, p2);
            return ExecuteResult.RETRY_FAILED_PROMOTED;
        }
        return ExecuteResult.FAILED;
    }

    private ExecuteResult tryClickAndVerify(Point p1, Point p2, ExecContext ctx) {
        ctx.primaryClick.accept(p1, p2);
        sleep(ctx.verifyWaitMs > 0 ? ctx.verifyWaitMs : VERIFY_WAIT_MS);
        BufferedImage img = ctx.screenshot.get();
        if (img == null) {
            return ExecuteResult.SCREENSHOT_INVALID;
        }
        char[][] after = new char[10][9];
        if (!ctx.recognizer.findChessBoard(img, after)) {
            return ExecuteResult.SCREENSHOT_INVALID;
        }
        return boardChanged(ctx.beforeBoard, after) ? ExecuteResult.SUCCESS : ExecuteResult.FAILED;
    }

    private static boolean boardChanged(char[][] before, char[][] after) {
        if (before == null) {
            return true;
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (before[i][j] != after[i][j]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 棋盘坐标转像素坐标
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

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}