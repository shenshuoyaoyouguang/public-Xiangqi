package com.sojourners.chess.board;

import com.sojourners.chess.util.XiangqiUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认棋盘渲染:矢量背景(径向渐变 + 木纹 + 暗角) + 立体棋子(径向渐变 + 双层描边 + 柔光投影 + 选中外发光)。
 * 所有渐变对象按尺寸维度缓存复用,避免在 paint() 中每帧 new。
 */
public class DefaultBoardRender extends BaseBoardRender {

    // ---------- 背景色板 ----------
    private static final Color BG_CENTER = Color.web("#E8C58A");
    private static final Color BG_MID = Color.web("#C9A05E");
    private static final Color BG_EDGE = Color.web("#A8783A");
    private static final Color WOOD_GRAIN = Color.web("#8A5A2A");
    private static final Color SHADOW_COLOR = Color.web("#1A0E04");

    // ---------- 红方色板 ----------
    private static final Color RED_HIGHLIGHT = Color.web("#F0C5B8");
    private static final Color RED_MAIN = Color.web("#C8341E");
    private static final Color RED_EDGE = Color.web("#8B1A0E");
    private static final Color RED_OUTER = Color.web("#6A1408");
    private static final Color RED_INNER = Color.web("#F0C5B8");
    private static final Color RED_CHAR = Color.web("#4A0A04");

    // ---------- 黑方色板 ----------
    private static final Color BLACK_HIGHLIGHT = Color.web("#B8D8D5");
    private static final Color BLACK_MAIN = Color.web("#1F8A8E");
    private static final Color BLACK_EDGE = Color.web("#0A4A4D");
    private static final Color BLACK_OUTER = Color.web("#063036");
    private static final Color BLACK_INNER = Color.web("#B8D8D5");
    private static final Color BLACK_CHAR = Color.web("#05282C");

    // ---------- 渐变缓存(类级别复用) ----------
    private final Map<String, RadialGradient> bgGradientCache = new HashMap<>();
    private final Map<String, RadialGradient> vignetteCache = new HashMap<>();
    private final Map<Integer, RadialGradient> redPieceGradientCache = new HashMap<>();
    private final Map<Integer, RadialGradient> blackPieceGradientCache = new HashMap<>();

    private Image bgImage;
    private Font font;
    private int fontSize;

    public DefaultBoardRender(Canvas canvas) {
        super(canvas);
        this.bgImage = new Image(ChessBoard.class.getResourceAsStream("/image/BOARD.JPG"));
    }

    @Override
    public void drawBackgroundImage(double width, double height) {
        String key = (int) width + "x" + (int) height;

        // 1. 底层径向渐变:中心微亮、边缘自然压暗
        RadialGradient bg = bgGradientCache.computeIfAbsent(key, k -> createBackgroundGradient(width, height));
        gc.setFill(bg);
        gc.fillRect(0, 0, width, height);

        // 2. 木纹条纹
        drawWoodGrain(width, height);

        // 3. 边缘暗角:提升焦点
        RadialGradient vignette = vignetteCache.computeIfAbsent(key, k -> createVignetteGradient(width, height));
        gc.setFill(vignette);
        gc.fillRect(0, 0, width, height);
    }

    @Override
    public Color getBackgroundColor() {
        int centerX = (int) (bgImage.getWidth() / 2);
        int centerY = (int) (bgImage.getHeight() / 2);
        return this.bgImage.getPixelReader().getColor(centerX, centerY);
    }

    @Override
    public void drawPieces(int pos, int piece, char[][] board, boolean isReverse, ChessBoard.BoardSize style) {
        drawPieces(pos, piece, board, isReverse, style, null);
    }

    @Override
    public void drawPieces(int pos, int piece, char[][] board, boolean isReverse, ChessBoard.BoardSize style, ChessBoard.Point remark) {
        if (font == null || fontSize != getFontSize(style)) {
            fontSize = getFontSize(style);
            font = Font.loadFont(getClass().getResourceAsStream("/font/chessman.ttf"), fontSize);
        }
        // 绘制棋子
        int r = (piece - piece / 10) / 2;
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                String word = XiangqiUtils.map.get(board[i][j]);
                if (word != null) {
                    int x = pos + piece * getReverseX(j, isReverse);
                    int y = pos + piece * getReverseY(i, isReverse);
                    boolean selected = remark != null && remark.getX() == j && remark.getY() == i;
                    drawOnePiece(x, y, r, word, board[i][j], style, selected);
                }
            }
        }
    }

    /**
     * 绘制单个立体棋子:柔光投影 → 选中外发光 → 径向渐变主体 → 外圈深描边 → 内圈浅描边 → 汉字。
     */
    private void drawOnePiece(int x, int y, int r, String word, char pieceChar, ChessBoard.BoardSize style, boolean selected) {
        boolean isRed = XiangqiUtils.isRed(pieceChar);
        Color edgeColor = isRed ? RED_OUTER : BLACK_OUTER;
        Color innerColor = isRed ? RED_INNER : BLACK_INNER;
        Color charColor = isRed ? RED_CHAR : BLACK_CHAR;
        double bW = getPieceBw(style);
        double sW = getPieceSw(style);

        // 选中棋子整体放大 1.06x
        double drawR = selected ? r * 1.06 : r;

        // 1. 底部柔光投影(先于棋子绘制)
        gc.save();
        gc.setGlobalAlpha(0.22);
        gc.setFill(SHADOW_COLOR);
        double shadowW = 2 * drawR * 1.05;
        double shadowH = 2 * drawR * 0.35;
        gc.fillOval(x - shadowW / 2, y + drawR * 0.18 - shadowH / 2, shadowW, shadowH);
        gc.restore();

        // 2. 选中外发光
        if (selected) {
            gc.save();
            gc.setGlobalAlpha(0.35);
            gc.setStroke(innerColor);
            gc.setLineWidth(bW * 0.9);
            double glow = drawR * 1.2;
            gc.strokeOval(x - glow, y - glow, 2 * glow, 2 * glow);
            gc.restore();
        }

        // 3. 棋子主体:径向渐变(中心偏上偏左高光 → 主色 → 边缘更深)
        gc.setFill(getPieceGradient(isRed, r));
        gc.fillOval(x - drawR, y - drawR, 2 * drawR, 2 * drawR);

        // 4. 外圈深色描边
        gc.setStroke(edgeColor);
        gc.setLineWidth(bW);
        gc.strokeOval(x - drawR, y - drawR, 2 * drawR, 2 * drawR);

        // 5. 内圈浅色细描边,雕琢光感
        gc.setStroke(innerColor);
        gc.setLineWidth(sW);
        double inset = bW * 1.15;
        gc.strokeOval(x - drawR + inset, y - drawR + inset, 2 * (drawR - inset), 2 * (drawR - inset));

        // 6. 汉字
        gc.setFill(charColor);
        gc.setFont(font);
        gc.fillText(word, x - fontSize / 2, y + fontSize / 2 - fontSize / 5.5);
    }

    /**
     * 木纹条纹:0..height 之间等距的半透明深色横线。
     */
    private void drawWoodGrain(double width, double height) {
        gc.save();
        gc.setLineWidth(1.0);
        int lineCount = 12;
        for (int i = 1; i <= lineCount; i++) {
            double y = height * i / (lineCount + 1.0);
            double alpha = 0.055 + 0.035 * Math.sin(i * 1.9 + 0.5);
            alpha = Math.max(0.02, Math.min(0.09, alpha));
            gc.setStroke(WOOD_GRAIN.deriveColor(0d, 1d, 1d, alpha));
            gc.strokeLine(0, y, width, y);
        }
        gc.restore();
    }

    /**
     * 背景径向渐变(非等比,按实际画布坐标定位,保证任意尺寸下中心亮、边缘暗)。
     */
    private RadialGradient createBackgroundGradient(double width, double height) {
        return new RadialGradient(0d, 0d, width / 2d, height / 2d, Math.max(width, height) * 0.78d,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, BG_CENTER),
                new Stop(0.55, BG_MID),
                new Stop(1.0, BG_EDGE));
    }

    /**
     * 边缘暗角渐变:四角自然压暗,中间保持通透。
     */
    private RadialGradient createVignetteGradient(double width, double height) {
        return new RadialGradient(0d, 0d, width / 2d, height / 2d, Math.max(width, height) * 0.72d,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#000000", 0.0)),
                new Stop(0.62, Color.web("#000000", 0.0)),
                new Stop(0.85, Color.web("#281405", 0.12)),
                new Stop(1.0, Color.web("#1E0F04", 0.32)));
    }

    /**
     * 棋子径向渐变缓存:按 r(未放大半径)维度缓存,比例渐变随棋子包围盒自动缩放,
     * 同一尺寸下选中/未选中共用同一 Paint 对象。
     */
    private RadialGradient getPieceGradient(boolean isRed, int r) {
        Map<Integer, RadialGradient> cache = isRed ? redPieceGradientCache : blackPieceGradientCache;
        return cache.computeIfAbsent(r, k -> {
            Color highlight = isRed ? RED_HIGHLIGHT : BLACK_HIGHLIGHT;
            Color main = isRed ? RED_MAIN : BLACK_MAIN;
            Color edge = isRed ? RED_EDGE : BLACK_EDGE;
            return new RadialGradient(0d, 0d, 0.38, 0.35, 0.85, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, highlight),
                    new Stop(0.55, main),
                    new Stop(1.0, edge));
        });
    }

    /**
     * 棋子外圈线条宽度
     * @return
     */
    private double getPieceBw(ChessBoard.BoardSize style) {
        return getPieceSize(style) /  16d;
    }

    /**
     * 棋子内圈线条宽度
     * @return
     */
    private double getPieceSw(ChessBoard.BoardSize style) {
        return getPieceBw(style) / 4d;
    }

    private int getFontSize(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 2;
    }
}
