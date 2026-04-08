package com.mantimetrics.model;

public interface DatasetRow {
    String getUniqueKey();
    String getPath();
    int getStartLine();
    int getEndLine();
    int getCodeSmells();
    int getNSmells();
    boolean isBuggy();
    String toCsvLine();
}
