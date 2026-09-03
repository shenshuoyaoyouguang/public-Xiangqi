package com.sojourners.chess.linker;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 走棋执行上下文，携带每次执行所需的能力与状态
 */
public class ExecContext {

    public final Rectangle boardPos;
    public final char[][] beforeBoard;
    public final int verifyWaitMs;
    public final IRecognizer recognizer;
    public final Supplier<BufferedImage> screenshot;
    public final BiConsumer<Point, Point> primaryClick;
    public final BiConsumer<Point, Point> degradeClick;

    public ExecContext(Rectangle boardPos, char[][] beforeBoard, int verifyWaitMs,
                       IRecognizer recognizer, Supplier<BufferedImage> screenshot,
                       BiConsumer<Point, Point> primaryClick, BiConsumer<Point, Point> degradeClick) {
        this.boardPos = boardPos;
        this.beforeBoard = beforeBoard;
        this.verifyWaitMs = verifyWaitMs;
        this.recognizer = recognizer;
        this.screenshot = screenshot;
        this.primaryClick = primaryClick;
        this.degradeClick = degradeClick;
    }
}