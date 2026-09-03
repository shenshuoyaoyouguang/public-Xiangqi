package com.sojourners.chess.linker;

import com.sojourners.chess.yolo.OnnxModel;
import com.sojourners.chess.yolo.Yolo11Model;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 基于 ONNX YOLO 模型的棋盘识别器（当前默认视觉方案）
 */
public class YoloRecognizer implements IRecognizer {

    private final OnnxModel aiModel;

    public YoloRecognizer() {
        this.aiModel = new Yolo11Model();
    }

    @Override
    public Rectangle findBoardPosition(BufferedImage img) {
        return aiModel.findBoardPosition(img);
    }

    @Override
    public boolean findChessBoard(BufferedImage img, char[][] board) {
        return aiModel.findChessBoard(img, board);
    }
}