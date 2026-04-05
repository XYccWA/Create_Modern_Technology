package org.XYccWA.create_modern_technology.Capability;

public interface IRadiation {
    int getRadiation();
    void setRadiation(int value);
    void addRadiation(int value);
    void subtractRadiation(int value);

    default boolean isLethal() {
        return getRadiation() >= 500;
    }

    default boolean isSevere() {
        return getRadiation() >= 300;
    }

    default boolean isModerate() {
        return getRadiation() >= 150;
    }

    default boolean isMild() {
        return getRadiation() >= 50;
    }
}