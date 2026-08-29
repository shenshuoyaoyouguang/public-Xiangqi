package com.sojourners.chess.board;

import com.sojourners.chess.util.XiangqiUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认棋盘渲染(v5 重构:翡翠玉质中国风,六层立体棋子 + 厚绿玉盘 + 卷云纹+虎字章装饰)。
 * 棋盘:翡翠绿径向渐变盘面 + 深翡翠盘体侧面 + 4 角卷云纹 + 河界右端"虎"字印章。
 * 棋子:六层圆柱(柔投影 → 圆柱侧面 → 象牙白顶面 → 刻线双环 → 雕刻感汉字 → 高光弧)。
 * 所有渐变对象缓存复用(比例渐变随包围盒缩放),避免每帧分配。
 */
public class DefaultBoardRender extends BaseBoardRender {

    // ---------- 盘面色板(翡翠绿径向渐变) ----------
    private static final Color BG_CENTER = Color.web("#A8C870");
    private static final Color BG_MID = Color.web("#88B45A");
    private static final Color BG_EDGE = Color.web("#5C8A40");

    // ---------- 盘体 ----------
    private static final Color BOARD_SIDE = Color.web("#1F4520");
    private static final Color FRAME_DIVIDER = Color.web("#2A5530");
    private static final Color DECOR_GREEN = Color.web("#2A5530");
    private static final Color SEAL_RED = Color.web("#7A2A1E");

    // ---------- 棋子顶面(象牙白,增强明暗对比做 3D) ----------
    private static final Color PIECE_CENTER = Color.web("#FFFFFF");
    private static final Color PIECE_MID = Color.web("#FAF4E4");
    private static final Color PIECE_EDGE = Color.web("#E5D8BC");

    // ---------- 棋子圆柱侧面(薄壁亮绿,轻盈) ----------
    private static final Color SIDE_TOP = Color.web("#4A7A3E");
    private static final Color SIDE_BOTTOM = Color.web("#22421E");

    // ---------- 刻线与字 ----------
    private static final Color PIECE_RIM = Color.web("#1F4520");
    private static final Color RED_CHAR = Color.web("#B0230F");
    private static final Color BLACK_CHAR = Color.web("#1F4520");

    // ---------- 选中外发光 ----------
    private static final Color RED_GLOW = Color.web("#E85C3A");
    private static final Color BLACK_GLOW = Color.web("#5C8A40");

    private static final Color SHADOW_COLOR = Color.web("#0A1A0A");

    // ---------- 渐变缓存 ----------
    private final Map<String, RadialGradient> bgGradientCache = new HashMap<>();
    private final Map<Integer, RadialGradient> pieceGradientCache = new HashMap<>();
    private final LinearGradient pieceSideGradient = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, SIDE_TOP), new Stop(0.7, SIDE_BOTTOM), new Stop(1, SIDE_BOTTOM));

    private Font font;
    private int fontSize;

    public DefaultBoardRender(Canvas canvas) {
        super(canvas);
    }

    @Override
    public void drawBackgroundImage(double width, double height) {
        String key = (int) width + "x" + (int) height;
        double sideH = height * 0.012;
        double r = height * 0.014;

        // 1. 盘体(深翡翠,底部露出 sideH 厚度)
        gc.setFill(BOARD_SIDE);
        gc.fillRoundRect(0, 0, width, height, r, r);

        // 2. 盘面(翡翠绿径向渐变,中心亮边缘微暗)
        RadialGradient bg = bgGradientCache.computeIfAbsent(key, k -> createBackgroundGradient(width, height - sideH));
        gc.setFill(bg);
        gc.fillRoundRect(0, 0, width, height - sideH, r, r);

        // 3. 盘面与侧面分界线(细,受光)
        gc.save();
        gc.setGlobalAlpha(0.6);
        gc.setStroke(FRAME_DIVIDER);
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(0.5, 0.5, width - 1, height - sideH - 0.5, r, r);
        gc.restore();

        // 4. 中国风装饰(4 角卷云纹 + 河界右端"虎"字章)
        drawBoardDecorations(width, height - sideH);
    }

    /**
     * 棋盘装饰:4 角内缘深绿卷云纹 + 河界右端"虎"字印章。
     * 印章画在楚河汉界文字右侧的河内空白处,该区域任何局面都不会被棋子遮挡。
     */
    private void drawBoardDecorations(double w, double h) {
        gc.save();
        double m = Math.min(w, h) * 0.022;
        double seg = Math.min(w, h) * 0.022;

        // 4 角内缘卷云纹(深绿)
        gc.setStroke(DECOR_GREEN);
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.6);
        gc.setLineCap(StrokeLineCap.ROUND);
        // 左上
        gc.beginPath();
        gc.moveTo(m + seg, m);
        gc.lineTo(m, m);
        gc.lineTo(m, m + seg);
        gc.stroke();
        // 右上
        gc.beginPath();
        gc.moveTo(w - m - seg, m);
        gc.lineTo(w - m, m);
        gc.lineTo(w - m, m + seg);
        gc.stroke();
        // 左下
        gc.beginPath();
        gc.moveTo(m + seg, h - m);
        gc.lineTo(m, h - m);
        gc.lineTo(m, h - m - seg);
        gc.stroke();
        // 右下
        gc.beginPath();
        gc.moveTo(w - m - seg, h - m);
        gc.lineTo(w - m, h - m);
        gc.lineTo(w - m, h - m - seg);
        gc.stroke();

        // "虎"字印章(印泥红底 + 深字),位于河界右端
        // 由画布宽反推格距(仅用于装饰定位):w = piece * (9 + 2/6)
        double piece = w * 3d / 28d;
        double sealR = Math.min(w, h) * 0.04;
        double sx = piece * 7.77d;
        double sy = piece * 5.167d;
        gc.setFill(SEAL_RED);
        gc.fillOval(sx - sealR, sy - sealR, sealR * 2, sealR * 2);
        gc.setFill(Color.web("#1A0808"));
        double sealFontSize = sealR * 1.5;
        gc.setFont(Font.font(sealFontSize));
        double tOff = sealFontSize / 2.5;
        gc.fillText("虎", sx - tOff, sy + tOff * 0.85);
        gc.restore();
    }

    @Override
    public Color getBackgroundColor() {
        // 演示棋盘条用与盘面一致的翡翠绿中间调
        return BG_MID;
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
     * 绘制单个棋子:六层圆柱结构。
     */
    private void drawOnePiece(int x, int y, int r, String word, char pieceChar, ChessBoard.BoardSize style, boolean selected) {
        boolean isRed = XiangqiUtils.isRed(pieceChar);
        Color charColor = isRed ? RED_CHAR : BLACK_CHAR;
        Color glowColor = isRed ? RED_GLOW : BLACK_GLOW;
        double bW = getPieceBw(style);
        double sW = getPieceSw(style);

        double drawR = selected ? r * 1.06 : r;
        double sideH = drawR * 0.08;      // 薄壁侧面,轻盈
        double topCY = y - sideH;
        double botCY = y + sideH;

        // L1 底部柔投影(极轻)
        gc.save();
        gc.setGlobalAlpha(0.12);
        gc.setFill(SHADOW_COLOR);
        gc.fillOval(x - drawR * 0.98, botCY - drawR * 0.08, 2 * drawR * 0.98, 2 * drawR * 0.18);
        gc.restore();

        // 选中发光环
        if (selected) {
            gc.save();
            gc.setGlobalAlpha(0.4);
            gc.setStroke(glowColor);
            gc.setLineWidth(bW * 0.8);
            double glow = drawR + sideH * 0.7;
            gc.strokeOval(x - glow, y - glow, 2 * glow, 2 * glow);
            gc.restore();
        }

        // L2 圆柱薄壁侧面(半透明降低厚重感)
        gc.save();
        gc.setGlobalAlpha(0.88);
        gc.setFill(pieceSideGradient);
        gc.beginPath();
        gc.moveTo(x - drawR, topCY);
        gc.lineTo(x - drawR, botCY);
        gc.arc(x, botCY, drawR, drawR, 180, 180);
        gc.lineTo(x + drawR, topCY);
        gc.closePath();
        gc.fill();
        gc.restore();

        // L3 顶面(高对比渐变:亮高光→暗边缘,立体受光)
        gc.setFill(getPieceGradient(r));
        gc.fillOval(x - drawR, topCY - drawR, 2 * drawR, 2 * drawR);

        // L4 刻线双环(外环实、内环浅)
        gc.setStroke(PIECE_RIM);
        gc.setLineWidth(bW);
        double o1 = bW * 1.6;
        gc.strokeOval(x - drawR + o1, topCY - drawR + o1, 2 * (drawR - o1), 2 * (drawR - o1));
        gc.save();
        gc.setGlobalAlpha(0.55);
        gc.setLineWidth(sW);
        double o2 = bW * 2.6;
        gc.strokeOval(x - drawR + o2, topCY - drawR + o2, 2 * (drawR - o2), 2 * (drawR - o2));
        gc.restore();

        // L5 汉字(纯主色,干净通透)
        double ty = topCY + fontSize / 2 - fontSize / 5.5;
        gc.setFont(font);
        gc.setFill(charColor);
        gc.fillText(word, x - fontSize / 2, ty);

        // L6 顶部高光弧(增强)
        gc.save();
        gc.setGlobalAlpha(0.7);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(bW * 0.9);
        gc.setLineCap(StrokeLineCap.ROUND);
        double hOff = bW * 0.9;
        // JavaFX 弧线角度:0°=3点钟,正角度逆时针(90°=12点钟),100~175 即左上沿
        gc.strokeArc(x - drawR + hOff, topCY - drawR + hOff, 2 * (drawR - hOff), 2 * (drawR - hOff), 100, 75, ArcType.OPEN);
        gc.restore();

        // L7 底部反光弧(rim light,3D 圆润感)
        gc.save();
        gc.setGlobalAlpha(0.3);
        gc.setStroke(Color.web("#E8F0D8"));
        gc.setLineWidth(bW * 0.6);
        gc.setLineCap(StrokeLineCap.ROUND);
        double rOff = bW * 0.8;
        gc.strokeArc(x - drawR + rOff, topCY - drawR + rOff, 2 * (drawR - rOff), 2 * (drawR - rOff), 240, 60, ArcType.OPEN);
        gc.restore();
    }

    private RadialGradient createBackgroundGradient(double width, double height) {
        return new RadialGradient(0d, 0d, width / 2d, height / 2d, Math.max(width, height) * 0.78d,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, BG_CENTER),
                new Stop(0.5, BG_MID),
                new Stop(1.0, BG_EDGE));
    }

    private RadialGradient getPieceGradient(int r) {
        return pieceGradientCache.computeIfAbsent(r, k ->
                new RadialGradient(0d, 0d, 0.38, 0.32, 0.9, true, CycleMethod.NO_CYCLE,
                        new Stop(0.0, PIECE_CENTER),
                        new Stop(0.6, PIECE_MID),
                        new Stop(1.0, PIECE_EDGE)));
    }

    private double getPieceBw(ChessBoard.BoardSize style) {
        return getPieceSize(style) /  16d;
    }

    private double getPieceSw(ChessBoard.BoardSize style) {
        return getPieceBw(style) / 4d;
    }

    private int getFontSize(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 2;
    }
}
