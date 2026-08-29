package com.sojourners.chess.media;

import javafx.scene.media.AudioClip;

import java.io.File;

public class SoundPlayer {
    private AudioClip pick;

    private AudioClip move;

    private AudioClip eat;

    private AudioClip check;

    private AudioClip over;

    public SoundPlayer(String pickSound, String moveSound, String eatSound, String checkSound, String overSound) {
        pick = safeClip(pickSound);
        move = safeClip(moveSound);
        eat = safeClip(eatSound);
        check = safeClip(checkSound);
        over = safeClip(overSound);
    }

    /**
     * 音频加载失败（无声卡设备、文件缺失等 headless 环境）时降级为静音，不阻断启动
     */
    private AudioClip safeClip(String sound) {
        try {
            return new AudioClip(new File(sound).toURI().toString());
        } catch (Exception e) {
            System.getLogger(SoundPlayer.class.getName()).log(System.Logger.Level.WARNING, "音效加载失败，静音降级: " + sound, e);
            return null;
        }
    }

    public void eat() {
        if (eat != null) eat.play();
    }

    public void pick() {
        if (pick != null) pick.play();
    }

    public void move() {
        if (move != null) move.play();
    }

    public void check() {
        if (check != null) check.play();
    }

    public void over() {
        if (over != null) over.play();
    }

}
