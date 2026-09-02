package com.sojourners.chess.board;

import javafx.scene.paint.Color;

/**
 * 棋盘调色板(矢量渲染)。
 * BoardStyle 枚举仅作为 palette key,持久化格式不变。
 */
public final class BoardPalette {

    /** 棋子配色(顶面径向渐变 4 stop + 侧面 + 刻线环 + 汉字) */
    public static final class PieceColors {
        public final Color top0, top1, top2, top3;
        public final Color sideTop, sideBottom;
        public final Color ring;
        public final Color charColor;

        public PieceColors(Color top0, Color top1, Color top2, Color top3,
                           Color sideTop, Color sideBottom, Color ring, Color charColor) {
            this.top0 = top0;
            this.top1 = top1;
            this.top2 = top2;
            this.top3 = top3;
            this.sideTop = sideTop;
            this.sideBottom = sideBottom;
            this.ring = ring;
            this.charColor = charColor;
        }
    }

    public final String name;
    // 盘面
    public final Color bgCenter, bgMid, bgEdge;
    public final Color frameSide, frameDivider;
    public final Color grid;
    public final Color riverText;
    public final Color decor;
    public final Color sealBg, sealChar, sealBorder;
    public final Color veinColor;
    // 棋子
    public final Color shadowColor;
    public final Color rimLight;
    public final PieceColors red, black;
    // 纹理(双八度值噪声,512x512 固定分辨率烘焙后拉伸)
    public final double texCellsX, texCellsY;
    public final int texSeed;
    public final double texContrast, texAlpha;
    public final boolean veins;

    private BoardPalette(String name, Color bgCenter, Color bgMid, Color bgEdge,
                         Color frameSide, Color frameDivider, Color grid, Color riverText, Color decor,
                         Color sealBg, Color sealChar, Color sealBorder, Color veinColor,
                         Color shadowColor, Color rimLight,
                         PieceColors red, PieceColors black,
                         double texCellsX, double texCellsY, int texSeed, double texContrast, double texAlpha,
                         boolean veins) {
        this.name = name;
        this.bgCenter = bgCenter;
        this.bgMid = bgMid;
        this.bgEdge = bgEdge;
        this.frameSide = frameSide;
        this.frameDivider = frameDivider;
        this.grid = grid;
        this.riverText = riverText;
        this.decor = decor;
        this.sealBg = sealBg;
        this.sealChar = sealChar;
        this.sealBorder = sealBorder;
        this.veinColor = veinColor;
        this.shadowColor = shadowColor;
        this.rimLight = rimLight;
        this.red = red;
        this.black = black;
        this.texCellsX = texCellsX;
        this.texCellsY = texCellsY;
        this.texSeed = texSeed;
        this.texContrast = texContrast;
        this.texAlpha = texAlpha;
        this.veins = veins;
    }

    /** 翡翠玉质:绿盘 + 象牙白棋子 */
    public static final BoardPalette EMERALD = new BoardPalette(
            "emerald",
            Color.web("#AECB7E"), Color.web("#8CB25C"), Color.web("#5F8C42"),
            Color.web("#1E421F"), Color.web("#2A5530"),
            Color.web("#274D1F"), Color.web("#2A5530"), Color.web("#2A5530"),
            Color.web("#A63A2B"), Color.web("#F2E6D0"), Color.web("#6E2418"),
            Color.web("#3E6A30"),
            Color.rgb(10, 20, 10), Color.web("#E8F0D8"),
            new PieceColors(Color.web("#FFFEF8"), Color.web("#F8F1DE"), Color.web("#EBDFC2"), Color.web("#D8C6A0"),
                    Color.web("#4E7C40"), Color.web("#22421E"), Color.web("#96784E"), Color.web("#9E2B18")),
            new PieceColors(Color.web("#FFFEF8"), Color.web("#F8F1DE"), Color.web("#EBDFC2"), Color.web("#D8C6A0"),
                    Color.web("#4E7C40"), Color.web("#22421E"), Color.web("#96784E"), Color.web("#2B2620")),
            37, 43, 3, 0.20, 0.15, true);

    /** 红黑:暖木盘 + 红/黑立体棋子白字(原位图风格矢量重画) */
    public static final BoardPalette REDBLACK = new BoardPalette(
            "redblack",
            Color.web("#D8B075"), Color.web("#C49555"), Color.web("#A87C42"),
            Color.web("#5A4028"), Color.web("#6A4A28"),
            Color.web("#40301C"), Color.web("#40301C"), Color.web("#6A4A28"),
            Color.web("#A63A2B"), Color.web("#F2E6D0"), Color.web("#6E2418"),
            Color.web("#6A4A28"),
            Color.rgb(20, 12, 8), Color.web("#E8D8C0"),
            new PieceColors(Color.web("#E06A50"), Color.web("#C03A26"), Color.web("#A02C1A"), Color.web("#7E2012"),
                    Color.web("#7A2012"), Color.web("#4A120A"), Color.web("#5E180C"), Color.web("#F7F2E8")),
            new PieceColors(Color.web("#6A625A"), Color.web("#453E38"), Color.web("#322C28"), Color.web("#211D19"),
                    Color.web("#26211D"), Color.web("#151210"), Color.web("#141110"), Color.web("#F2EDE2")),
            9, 92, 7, 0.22, 0.22, false);

    public static BoardPalette forStyle(ChessBoard.BoardStyle style) {
        return style == ChessBoard.BoardStyle.CUSTOM ? REDBLACK : EMERALD;
    }
}
