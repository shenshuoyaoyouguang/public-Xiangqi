package com.sojourners.chess.linker;

import java.awt.*;
import java.awt.image.BufferedImage;

public interface GraphLinker {

    /**
     * Starts the linking process (prompts user to select target window).
     */
    void start();

    /**
     * Stops the linking scan thread and clears state.
     */
    void stop();

    /**
     * Obtains the target window ID/handle from the user (platform-specific).
     */
    void getTargetWindowId();

    /**
     * Gets the current position and size of the target window.
     *
     * @return a Rectangle with the window's screen coordinates and dimensions
     */
    Rectangle getTargetWindowPosition();

    /**
     * Captures a screenshot in background mode (window can be obscured).
     *
     * @param windowPos the region to capture, or null for the full client area
     * @return the captured image, or null if background capture is unsupported or fails
     */
    BufferedImage screenshotByBack(Rectangle windowPos);

    /**
     * Captures a screenshot in foreground mode (using Robot, requires window on top).
     *
     * @param windowPos the screen region to capture
     * @return the captured image, or null if the window is invalid
     */
    BufferedImage screenshotByFront(Rectangle windowPos);

    /**
     * Performs a mouse click in foreground mode by moving the cursor to the specified points.
     *
     * @param windowPos the target window's position and size
     * @param p1        first click point (source) in window-relative coordinates
     * @param p2        second click point (destination) in window-relative coordinates
     */
    void mouseClickByFront(Rectangle windowPos, Point p1, Point p2);

    /**
     * Performs a mouse click in background mode by sending window messages (platform-specific).
     *
     * @param p1 first click point (source) in window-relative coordinates
     * @param p2 second click point (destination) in window-relative coordinates
     */
    void mouseClickByBack(Point p1, Point p2);

}
