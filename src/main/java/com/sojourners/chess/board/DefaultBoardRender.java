package com.sojourners.chess.board;

import com.sojourners.chess.util.XiangqiUtils;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 默认棋盘渲染(批次 A 重构:程序化矢量,翡翠玉质/红黑双 palette)。
 * 盘面:径向渐变 + 程序化纹理(512x512 固定分辨率烘焙,拉伸叠加) + 玉纹 + 4 角卷云纹 + "虎"字方印。
 * 棋子:七层圆柱(柔影 → 侧面 → 顶面 → 刻线双环 → 浮雕汉字 → 高光弧 → 反光弧)。
 * 渐变用比例坐标,随包围盒缩放,每 palette 构建一次复用。
 */
public class DefaultBoardRender extends BaseBoardRender {

    // ---------- 选中外发光(功能指示色,不入 palette) ----------
    private static final Color RED_GLOW = Color.web("#E85C3A");
    private static final Color BLACK_GLOW = Color.web("#5C8A40");

    private final BoardPalette palette;
    private final RadialGradient redTopGrad, blackTopGrad;
    private final LinearGradient redSideGrad, blackSideGrad;

    public DefaultBoardRender(Canvas canvas, BoardPalette palette) {
        super(canvas, palette);
        this.palette = palette;
        this.redTopGrad = buildTopGrad(palette.red);
        this.blackTopGrad = buildTopGrad(palette.black);
        this.redSideGrad = buildSideGrad(palette.red);
        this.blackSideGrad = buildSideGrad(palette.black);
    }

    private static RadialGradient buildTopGrad(BoardPalette.PieceColors pc) {
        return new RadialGradient(0, 0, 0.38, 0.30, 0.95, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, pc.top0),
                new Stop(0.45, pc.top1),
                new Stop(0.8, pc.top2),
                new Stop(1.0, pc.top3));
    }

    private static LinearGradient buildSideGrad(BoardPalette.PieceColors pc) {
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, pc.sideTop),
                new Stop(0.7, pc.sideBottom),
                new Stop(1, pc.sideBottom));
    }

    @Override
    public void drawBackgroundImage(double width, double height) {
        double sideH = height * 0.014;
        double r = height * 0.014;

        // 1. 盘体(深色,底部露出 sideH 厚度)
        gc.setFill(palette.frameSide);
        gc.fillRoundRect(0, 0, width, height, r, r);

        // 2. 盘面(径向渐变,中心亮边缘微暗)
        RadialGradient bg = new RadialGradient(0, 0, width / 2d, (height - sideH) / 2d,
                Math.max(width, height - sideH) * 0.78d, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, palette.bgCenter),
                new Stop(0.5, palette.bgMid),
                new Stop(1.0, palette.bgEdge));
        gc.setFill(bg);
        gc.fillRoundRect(0, 0, width, height - sideH, r, r);

        // 3. 纹理(固定分辨率烘焙,拉伸叠加,零逐帧像素工作)
        gc.save();
        gc.setGlobalAlpha(palette.texAlpha);
        gc.drawImage(getTextureImage(), 0, 0, width, height - sideH);
        gc.restore();

        // 4. 玉纹(确定性种子,逐帧描线,成本可忽)
        if (palette.veins) {
            Random rnd = new Random(palette.texSeed + 1);
            gc.save();
            gc.setStroke(palette.veinColor);
            gc.setLineWidth(Math.max(1.5, width / 400.0));
            gc.setLineCap(StrokeLineCap.ROUND);
            for (int i = 0; i < 4; i++) {
                gc.setGlobalAlpha(0.05 + rnd.nextDouble() * 0.04);
                double x0 = rnd.nextDouble() * width, y0 = rnd.nextDouble() * (height - sideH);
                double x1 = x0 + (rnd.nextDouble() - 0.3) * width * 0.5;
                double y1 = y0 + (rnd.nextDouble() - 0.5) * (height - sideH) * 0.3;
                double cx = (x0 + x1) / 2 + (rnd.nextDouble() - 0.5) * width * 0.2;
                double cy = (y0 + y1) / 2 + (rnd.nextDouble() - 0.5) * (height - sideH) * 0.2;
                gc.strokeLine(x0, y0, cx, cy);
                gc.strokeLine(cx, cy, x1, y1);
            }
            gc.restore();
        }

        // 5. 盘面与侧面分界线(细,受光)
        gc.save();
        gc.setGlobalAlpha(0.6);
        gc.setStroke(palette.frameDivider);
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(0.5, 0.5, width - 1, height - sideH - 0.5, r, r);
        gc.restore();

        // 6. 中国风装饰(4 角卷云纹 + "虎"字方印)
        drawBoardDecorations(width, height - sideH);
    }

    /**
     * 棋盘装饰:4 角内缘卷云纹 + 河界右端"虎"字印章。
     * 印章位于楚河汉界右侧的河内空白处,该区域任何局面都不会被棋子遮挡。
     */
    private void drawBoardDecorations(double w, double h) {
        // 格距与原点由 paint() 提供(避免与 BaseBoardRender 几何公式跨类魔法数同步)
        double piece = lastPiece;
        double pos = lastPos;

        // 4 角内缘卷云纹(外弧 270° + 内弧 180°)
        double m = Math.min(w, h) * 0.022;
        double s = Math.min(w, h) * 0.035;
        gc.save();
        gc.setStroke(palette.decor);
        gc.setGlobalAlpha(0.85);
        gc.setLineWidth(Math.max(1.2, piece / 55.0));
        gc.setLineCap(StrokeLineCap.ROUND);
        double[][] corners = {{m, m, 1, 1}, {w - m, m, -1, 1}, {m, h - m, 1, -1}, {w - m, h - m, -1, -1}};
        for (double[] c : corners) {
            double cx = c[0], cy = c[1], dx = c[2], dy = c[3];
            gc.strokeArc(cx - s / 2, cy - s / 2, s, s, 90, 270, ArcType.OPEN);
            gc.strokeArc(cx - s * 0.28, cy - s * 0.28, s * 0.56, s * 0.56, 90 - 180 * (dx * dy > 0 ? 1 : 0), 180, ArcType.OPEN);
        }
        gc.restore();

        // "虎"字印章(圆角方印,微旋,印泥红底浅字)
        double ss = piece * 0.42;
        double sx = pos + piece * 7.77d;
        double sy = pos + piece * 5.167d;
        gc.save();
        gc.translate(sx, sy);
        gc.rotate(3);
        gc.setFill(palette.sealBg);
        gc.fillRoundRect(-ss / 2, -ss / 2, ss, ss, ss * 0.18, ss * 0.18);
        gc.setStroke(palette.sealBorder);
        gc.setLineWidth(ss * 0.055);
        gc.strokeRoundRect(-ss / 2 + ss * 0.1, -ss / 2 + ss * 0.1, ss * 0.8, ss * 0.8, ss * 0.12, ss * 0.12);
        gc.setFill(palette.sealChar);
        gc.setFont(fontAt(ss * 0.6));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("虎", 0, ss * 0.04);
        gc.restore();
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);
    }

    @Override
    public Color getBackgroundColor() {
        // 演示棋盘条用与盘面一致的中间调
        return palette.bgMid;
    }

    @Override
    public void drawPieces(int pos, int piece, char[][] board, boolean isReverse, ChessBoard.BoardSize style) {
        drawPieces(pos, piece, board, isReverse, style, null);
    }

    @Override
    public void drawPieces(int pos, int piece, char[][] board, boolean isReverse, ChessBoard.BoardSize style, ChessBoard.Point remark) {
        int r = piece / 2;
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                String word = XiangqiUtils.map.get(board[i][j]);
                if (word != null) {
                    int x = pos + piece * getReverseX(j, isReverse);
                    int y = pos + piece * getReverseY(i, isReverse);
                    boolean selected = remark != null && remark.getX() == j && remark.getY() == i;
                    drawOnePiece(x, y, r, word, board[i][j], selected);
                }
            }
        }
    }

    /**
     * 绘制单个棋子:七层圆柱结构(编排器,各层独立方法)。
     */
    private void drawOnePiece(int x, int y, int r, String word, char pieceChar, boolean selected) {
        if (r <= 0) {
            return;  // 启动首帧:autofit 棋盘在首次布局前 piece=0,柔影等层会分配 0 尺寸图像
        }
        boolean isRed = XiangqiUtils.isRed(pieceChar);
        BoardPalette.PieceColors pc = isRed ? palette.red : palette.black;
        double drawR = selected ? r * 1.06 : r;
        double sideH = drawR * 0.13;
        double topCY = y - sideH;

        drawPieceShadow(x, y, drawR);
        if (selected) {
            drawPieceGlow(x, y, drawR, sideH, isRed);
        }
        drawPieceSide(x, y, drawR, sideH, topCY, isRed);
        drawPieceTop(x, drawR, topCY, isRed);
        drawPieceRings(x, drawR, topCY, pc);
        drawPieceCharacter(x, drawR, topCY, word, pc);
        drawPieceArcs(x, drawR, topCY);
    }

    // L1 柔和环境阴影(预渲染径向衰减椭圆)
    private void drawPieceShadow(double x, double y, double drawR) {
        WritableImage shadow = makeShadow((int) drawR);
        gc.drawImage(shadow, x - shadow.getWidth() / 2, y + drawR * 0.36);
    }

    // 选中发光环(功能指示,非 palette)
    private void drawPieceGlow(double x, double y, double drawR, double sideH, boolean isRed) {
        gc.save();
        gc.setGlobalAlpha(0.4);
        gc.setStroke(isRed ? RED_GLOW : BLACK_GLOW);
        gc.setLineWidth(drawR * 0.1);
        double glow = drawR + sideH * 0.7;
        gc.strokeOval(x - glow, y - glow, 2 * glow, 2 * glow);
        gc.restore();
    }

    // L2 圆柱侧面
    private void drawPieceSide(double x, double y, double drawR, double sideH, double topCY, boolean isRed) {
        gc.save();
        gc.setGlobalAlpha(0.92);
        gc.setFill(isRed ? redSideGrad : blackSideGrad);
        gc.beginPath();
        gc.moveTo(x - drawR, topCY);
        gc.lineTo(x - drawR, y + sideH);
        gc.arc(x, y + sideH, drawR, drawR, 180, 180);
        gc.lineTo(x + drawR, topCY);
        gc.closePath();
        gc.fill();
        gc.restore();
    }

    // L3 顶面(高对比径向渐变,立体受光)
    private void drawPieceTop(double x, double drawR, double topCY, boolean isRed) {
        gc.setFill(isRed ? redTopGrad : blackTopGrad);
        gc.fillOval(x - drawR, topCY - drawR, 2 * drawR, 2 * drawR);
    }

    // L4 刻线双环(外环实、内环浅)
    private void drawPieceRings(double x, double drawR, double topCY, BoardPalette.PieceColors pc) {
        double bw = drawR * 0.075;
        gc.setStroke(pc.ring);
        gc.setLineWidth(bw);
        double o1 = bw * 1.6;
        gc.strokeOval(x - drawR + o1, topCY - drawR + o1, 2 * (drawR - o1), 2 * (drawR - o1));
        gc.save();
        gc.setGlobalAlpha(0.45);
        gc.setLineWidth(bw / 2.5);
        double o2 = bw * 2.8;
        gc.strokeOval(x - drawR + o2, topCY - drawR + o2, 2 * (drawR - o2), 2 * (drawR - o2));
        gc.restore();
    }

    // L5 汉字(马善政楷体,阴刻:上缘暗影 + 下缘高光 + 主色纵向渐变)
    private void drawPieceCharacter(double x, double drawR, double topCY, String word, BoardPalette.PieceColors pc) {
        double fs = drawR * 0.92;
        gc.setFont(fontAt(fs));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        double ty = topCY + drawR * 0.03;
        double off = fs * 0.022;
        // 下缘高光(受光) + 上缘暗影(凹槽顶缘)
        gc.save();
        gc.setGlobalAlpha(0.5);
        gc.setFill(Color.WHITE);
        gc.fillText(word, x, ty + off);
        gc.restore();
        gc.save();
        gc.setGlobalAlpha(0.35);
        gc.setFill(Color.web("#3A2A18"));
        gc.fillText(word, x, ty - off);
        gc.restore();
        // 主色:上浅下深(HSB 亮度平移派生,保饱和度,任意 palette 字色通用)
        gc.setFill(new LinearGradient(0, 0, 0, 1, false, CycleMethod.NO_CYCLE,
                new Stop(0, shiftBrightness(pc.charColor, 0.25)),
                new Stop(0.6, pc.charColor),
                new Stop(1, shiftBrightness(pc.charColor, -0.25))));
        gc.fillText(word, x, ty);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);
    }

    private static Color shiftBrightness(Color c, double delta) {
        return Color.hsb(c.getHue(), c.getSaturation(),
                Math.max(0, Math.min(1, c.getBrightness() + delta)), c.getOpacity());
    }

    // L6 顶部高光弧 + L7 底部反光弧(rim light,3D 圆润感)
    private void drawPieceArcs(double x, double drawR, double topCY) {
        gc.save();
        gc.setGlobalAlpha(0.30);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(drawR * 0.055);
        gc.setLineCap(StrokeLineCap.ROUND);
        double hOff = drawR * 0.09;
        gc.strokeArc(x - drawR + hOff, topCY - drawR + hOff, 2 * (drawR - hOff), 2 * (drawR - hOff), 105, 70, ArcType.OPEN);
        gc.restore();
        gc.save();
        gc.setGlobalAlpha(0.22);
        gc.setStroke(palette.rimLight);
        gc.setLineWidth(drawR * 0.04);
        gc.setLineCap(StrokeLineCap.ROUND);
        double rOff = drawR * 0.08;
        gc.strokeArc(x - drawR + rOff, topCY - drawR + rOff, 2 * (drawR - rOff), 2 * (drawR - rOff), 240, 55, ArcType.OPEN);
        gc.restore();
    }

    // ================= 纹理/阴影(程序化生成,一次性) =================

    /**
     * 纹理固定分辨率烘焙(512x512 灰度双八度值噪声,每 palette 一次)。
     * 渲染时 drawImage 拉伸到盘面尺寸——无逐帧逐像素工作,无需尺寸桶缓存。
     */
    private static final Map<String, WritableImage> TEX_CACHE = new HashMap<>();

    private WritableImage getTextureImage() {
        return TEX_CACHE.computeIfAbsent(palette.name, k -> {
            int s = 512;
            WritableImage img = new WritableImage(s, s);
            PixelWriter pw = img.getPixelWriter();
            Random rnd = new Random(palette.texSeed);
            double fx = palette.texCellsX / (double) s, fy = palette.texCellsY / (double) s;
            int gw = (int) (s * fx) + 3, gh = (int) (s * fy) + 3;
            double[] g1 = new double[gw * gh];
            for (int i = 0; i < g1.length; i++) g1[i] = rnd.nextDouble();
            int gw2 = (int) (s * fx * 2.6) + 3, gh2 = (int) (s * fy * 2.6) + 3;
            double[] g2 = new double[gw2 * gh2];
            for (int i = 0; i < g2.length; i++) g2[i] = rnd.nextDouble();
            for (int y = 0; y < s; y++) {
                double gy = y * fy, gy2 = y * fy * 2.6;
                for (int x = 0; x < s; x++) {
                    double gx = x * fx, gx2 = x * fx * 2.6;
                    double n = 0.65 * valueNoise(g1, gw, gx, gy) + 0.35 * valueNoise(g2, gw2, gx2, gy2);
                    int g = (int) (128 + (n - 0.5) * 255 * palette.texContrast);
                    if (g < 0) g = 0;
                    if (g > 255) g = 255;
                    pw.setColor(x, y, Color.rgb(g, g, g, 1));
                }
            }
            return img;
        });
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double valueNoise(double[] grid, int gw, double gx, double gy) {
        int x0 = (int) gx, y0 = (int) gy;
        double tx = smooth(gx - x0), ty = smooth(gy - y0);
        double v00 = grid[y0 * gw + x0];
        double v10 = grid[y0 * gw + x0 + 1];
        double v01 = grid[(y0 + 1) * gw + x0];
        double v11 = grid[(y0 + 1) * gw + x0 + 1];
        return v00 + (v10 - v00) * tx + (v01 - v00) * ty + (v00 - v10 - v01 + v11) * tx * ty;
    }

    /**
     * 棋子柔影(预渲染径向衰减椭圆,4px 桶缓存:连续缩放下避免拖窗口时重建)。
     * 纯像素写入——未挂 Scene 的 Canvas buffer 是不透明白,不能用 Canvas 快照做透明图。
     */
    private static final Map<String, WritableImage> SHADOW_CACHE = new HashMap<>();

    private WritableImage makeShadow(int r) {
        int key = (r / 4) * 4;
        return SHADOW_CACHE.computeIfAbsent(palette.name + ":" + key, k -> {
            int w = (int) (r * 2.8), h = (int) (r * 1.3);
            WritableImage img = new WritableImage(w, h);
            PixelWriter pw = img.getPixelWriter();
            double cx = w / 2.0, cy = h / 2.0;
            Color c = palette.shadowColor;
            int cr = (int) (c.getRed() * 255), cg = (int) (c.getGreen() * 255), cb = (int) (c.getBlue() * 255);
            for (int y = 0; y < h; y++) {
                double dy = (y - cy) / (h / 2.0);
                for (int x = 0; x < w; x++) {
                    double dx = (x - cx) / (w / 2.0);
                    double d = Math.sqrt(dx * dx + dy * dy);
                    if (d >= 1) continue;
                    double a = 0.30 * Math.pow(1 - d, 1.4) * c.getOpacity();
                    pw.setColor(x, y, Color.rgb(cr, cg, cb, a));
                }
            }
            return img;
        });
    }
}
