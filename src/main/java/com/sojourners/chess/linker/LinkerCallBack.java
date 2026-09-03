package com.sojourners.chess.linker;

public interface LinkerCallBack {

    /**
     * Initializes the chess board with the recognized position from the link.
     *
     * @param fenCode   the FEN code representing the board position
     * @param isReverse true if the board display is reversed (red at top)
     */
    void linkerInitChessBoard(String fenCode, boolean isReverse);

    /**
     * Gets the current board state from the engine.
     *
     * @return a 10x9 char array representing the engine's board state
     */
    char[][] getEngineBoard();

    /**
     * Checks if the engine is currently thinking.
     *
     * @return true if the engine is analyzing a position, false otherwise
     */
    boolean isThinking();

    /**
     * Checks if the linker is in watch mode (observing both sides' moves).
     *
     * @return true if in watch mode, false otherwise
     */
    boolean isWatchMode();

    /**
     * Notifies the controller that a move was detected on the linked platform.
     *
     * @param x1 source column [0,8]
     * @param y1 source row [0,9]
     * @param x2 destination column [0,8]
     * @param y2 destination row [0,9]
     */
    void linkerMove(int x1, int y1, int x2, int y2);

    /**
     * Notifies the user of a linker status or error message.
     *
     * @param message the message to display
     */
    void linkerNotify(String message);
}
