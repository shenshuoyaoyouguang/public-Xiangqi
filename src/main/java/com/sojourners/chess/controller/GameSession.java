package com.sojourners.chess.controller;

import javafx.beans.property.SimpleObjectProperty;

/**
 * 对局会话状态（IT-7.1）：机器人角色 / 分析 / 翻转 / 连线等全局模式状态的单一持有者。
 * Controller 与各职责域经此类读写；行棋方与棋盘生命周期随后续拆分 PR 迁入。
 */
public class GameSession {

    private final SimpleObjectProperty<Boolean> robotRed = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> robotBlack = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> robotAnalysis = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> isReverse = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> linkMode = new SimpleObjectProperty<>(false);

    private final SimpleObjectProperty<Boolean> useOpenBook = new SimpleObjectProperty<>(false);

    public SimpleObjectProperty<Boolean> robotRedProperty() {
        return robotRed;
    }

    public SimpleObjectProperty<Boolean> robotBlackProperty() {
        return robotBlack;
    }

    public SimpleObjectProperty<Boolean> robotAnalysisProperty() {
        return robotAnalysis;
    }

    public SimpleObjectProperty<Boolean> isReverseProperty() {
        return isReverse;
    }

    public SimpleObjectProperty<Boolean> linkModeProperty() {
        return linkMode;
    }

    public SimpleObjectProperty<Boolean> useOpenBookProperty() {
        return useOpenBook;
    }
}
