package com.sojourners.chess.yolo;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.PathUtils;

import java.awt.image.BufferedImage;

public abstract class OnnxModel {

    public static final double PADDING = 0.8d;

    public final int SIZE = 640;

    /**
     * NMS 非极大值抑制的 IoU 阈值
     */
    public static final double NMS_IOU_THRESHOLD = 0.45d;

    public static final char[] labels = {'n', 'b', 'a', 'k', 'r', 'c', 'p', 'R', 'N', 'A', 'K', 'B', 'C', 'P', '0'};

    OrtSession session;

    OrtEnvironment env;

    public OnnxModel() {
        try {
            env = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions opt = new OrtSession.SessionOptions();
            opt.setIntraOpNumThreads(Properties.getInstance().getLinkThreadNum());

            String path = PathUtils.getJarPath() + getModelPath();

            session = env.createSession(path, opt);

        } catch (Exception e) {
            System.err.println("[OnnxModel] ONNX Runtime 初始化失败，连线功能将不可用: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public abstract String getModelPath();

    public abstract java.awt.Rectangle findBoardPosition(BufferedImage img);

    public abstract boolean findChessBoard(BufferedImage img, char[][] board);

}
