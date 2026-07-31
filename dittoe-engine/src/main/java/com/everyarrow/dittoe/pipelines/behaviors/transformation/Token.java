package com.everyarrow.dittoe.pipelines.behaviors.transformation;

public class Token {
    private final TokenType type;
    private final String text;
    private final int position;
    private final int endPosition;

    public Token(TokenType type, String text) {
        this(type, text, -1);
    }

    public Token(TokenType type, String text, int position) {
        this(type, text, position, position < 0 ? -1 : position + text.length());
    }

    public Token(TokenType type, String text, int position, int endPosition) {
        this.type = type;
        this.text = text;
        this.position = position;
        this.endPosition = endPosition;
    }

    public TokenType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getPosition() {
        return position;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public DealSourceSpan sourceSpan() {
        return position < 0 ? DealSourceSpan.unknown() : new DealSourceSpan(position, endPosition);
    }

    @Override
    public String toString() {
        return type + "(" + text + ")@" + position + ":" + endPosition;
    }
}
