package com.sojourners.chess.yolo;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.PathUtils;

import java.awt.image.BufferedImage;

public abstract class OnnxModel {

    private static final System.Logger log = System.getLogger(OnnxModel.class.getName());

    public static final double PADDING = 0.8d;

    public final float CONFIDENCE = 0.75f;

    public final int SIZE = 640;

    public static final char[] labels = {'n', 'b', 'a', 'k', 'r', 'c', 'p', 'R', 'N', 'A', 'K', 'B', 'C', 'P', '0'};

    OrtSession session;

    OrtEnvironment env;

    /**
     * Constructs an ONNX model by initializing the ONNX runtime and loading the model file.
     */
    public OnnxModel() {
        try {
            env = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions opt = new OrtSession.SessionOptions();
            opt.setIntraOpNumThreads(Properties.getInstance().getLinkThreadNum());

            String path = PathUtils.getJarPath() + getModelPath();

            session = env.createSession(path, opt);

        } catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "加载 ONNX 模型失败", e);
        }
    }

    /**
     * Gets the relative path to the model file (to be appended to jar path).
     *
     * @return the model file path relative to jar directory
     */
    public abstract String getModelPath();

    /**
     * Finds the board position in the given image.
     *
     * @param img the screenshot image to analyze
     * @return a Rectangle representing the board's bounding box, or null if not found
     */
    public abstract java.awt.Rectangle findBoardPosition(BufferedImage img);

    /**
     * Recognizes chess pieces on the board and populates the board array.
     *
     * @param img   the board region image
     * @param board a 10x9 char array to populate with piece labels
     * @return true if recognition succeeded, false otherwise
     */
    public abstract boolean findChessBoard(BufferedImage img, char[][] board);

}
