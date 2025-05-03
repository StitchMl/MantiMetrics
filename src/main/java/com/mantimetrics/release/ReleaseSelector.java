package com.mantimetrics.release;

import java.util.List;

public class ReleaseSelector {
    public List<String> selectFirstPercent(List<String> tags, int percent) {
        int cutoff = (int) Math.ceil(tags.size() * percent / 100.0);
        return tags.subList(0, Math.max(1, cutoff));
    }
}
