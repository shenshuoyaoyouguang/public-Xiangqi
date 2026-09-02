package com.sojourners.chess.linker;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 棋盘识别器抽象：截图识别棋盘位置与棋子状态
 */
public interface IRecognizer {

    Rectangle findBoardPosition(BufferedImage img);

    boolean findChessBoard(BufferedImage img, char[][] board);
}