package com.codecool.klondike;

public enum EnumSuit {
    HEARTS("hearts", "red"),
    DIAMONDS("diamonds", "red"),
    SPADES("spades", "black"),
    CLUBS("clubs", "black");

    private String suit;
    private String color;

    EnumSuit(String suit, String color) {
        this.suit = suit;
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    public String getSuitName() {
        return suit;
    }
}
