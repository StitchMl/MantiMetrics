package com.mantimetrics.feature;

/**
 * Immutable class-level static metrics kept for Milestone 1.
 *
 * @param loc lines of code of the class
 * @param wmc weighted methods per class (sum of member cyclomatic complexities)
 * @param lcom LCOM4 cohesion (number of connected method/field components)
 */
public record ClassMetrics(int loc, int wmc, int lcom) {
    /** @return lines of code */
    public int getLoc() { return loc; }
    /** @return weighted methods per class */
    public int getWmc() { return wmc; }
    /** @return LCOM4 cohesion */
    public int getLcom() { return lcom; }
}
