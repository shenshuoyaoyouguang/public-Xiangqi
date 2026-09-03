package com.sojourners.chess.linker;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 棋盘识别器抽象：截图识别棋盘位置与棋子状态
 */
public interface IRecognizer {

    /**
     * Finds the board position in the given image.
     *
     * @param img the screenshot image to analyze
     * @return a Rectangle representing the board's bounding box, or null if not found
     */
    Rectangle findBoardPosition(BufferedImage img);

    /**
     * Recognizes chess pieces on the board and populates the board array.
     *
     * @param img   the board region image
     * @param board a 10x9 char array to populate with piece labels (uppercase for red, lowercase for black, space for empty)
     * @return true if recognition succeeded, false otherwise
     */
    boolean findChessBoard(BufferedImage img, char[][] board);
}