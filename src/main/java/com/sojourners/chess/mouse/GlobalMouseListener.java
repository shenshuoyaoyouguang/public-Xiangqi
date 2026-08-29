package com.sojourners.chess.mouse;


import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.mouse.NativeMouseEvent;
import org.jnativehook.mouse.NativeMouseInputListener;

public class GlobalMouseListener implements NativeMouseInputListener {

    private static final System.Logger log = System.getLogger(GlobalMouseListener.class.getName());

    private MouseListenCallBack cb;

    public void nativeMouseClicked(NativeMouseEvent e) {
        log.log(System.Logger.Level.DEBUG, "全局鼠标钩子捕获点击: " + e.getClickCount());

        this.cb.mouseClick();
    }

    public void nativeMousePressed(NativeMouseEvent e) {

    }

    public void nativeMouseReleased(NativeMouseEvent e) {

    }

    public void nativeMouseMoved(NativeMouseEvent e) {

    }

    public void nativeMouseDragged(NativeMouseEvent e) {

    }

    public GlobalMouseListener(MouseListenCallBack cb) {
        this.cb = cb;
    }

    public void startListenMouse() throws NativeHookException {
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeMouseListener(this);
    }
    public void stopListenMouse() throws NativeHookException {
        GlobalScreen.removeNativeMouseListener(this);
        GlobalScreen.unregisterNativeHook();
    }

}