package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.ArrayList;
import java.util.List;

/** Converts DEAL source text into positioned tokens. */
public class Tokenizer {
    private final String input;
    private int pos;

    public Tokenizer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            char current = input.charAt(pos);
            if (Character.isWhitespace(current)) {
                pos++;
            } else if (Character.isDigit(current)
                    || (current == '.' && hasNextDigit())) {
                tokens.add(readNumber());
            } else if (Character.isLetter(current) || current == '_') {
                tokens.add(readIdentifier());
            } else if (current == '"' || current == '\'') {
                tokens.add(readStringLiteral(current));
            } else {
                tokens.add(readSymbol());
            }
        }
        return tokens;
    }

    private boolean hasNextDigit() {
        return pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1));
    }

    private Token readIdentifier() {
        int start = pos;
        while (pos < input.length()) {
            char current = input.charAt(pos);
            if (!Character.isLetterOrDigit(current) && current != '_') {
                break;
            }
            pos++;
        }
        String identifier = input.substring(start, pos);
        if (identifier.equalsIgnoreCase("true") || identifier.equalsIgnoreCase("false")) {
            return new Token(TokenType.BOOLEAN, identifier, start, pos);
        }
        if (identifier.equalsIgnoreCase("null")) {
            return new Token(TokenType.NULL, identifier, start, pos);
        }
        return new Token(TokenType.IDENTIFIER, identifier, start, pos);
    }

    private Token readNumber() {
        int start = pos;
        consumeDigits();
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            consumeDigits();
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            int exponentPosition = pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            int exponentStart = pos;
            consumeDigits();
            if (exponentStart == pos) {
                throw new DealSyntaxException("Expected exponent digits", exponentPosition);
            }
        }
        return new Token(TokenType.NUMBER, input.substring(start, pos), start, pos);
    }

    private void consumeDigits() {
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
    }

    private Token readStringLiteral(char quote) {
        int start = pos++;
        StringBuilder value = new StringBuilder();
        while (pos < input.length()) {
            char current = input.charAt(pos++);
            if (current == quote) {
                return new Token(TokenType.STRING, value.toString(), start, pos);
            }
            if (current != '\\') {
                value.append(current);
                continue;
            }
            if (pos >= input.length()) {
                throw new DealSyntaxException("Unterminated string escape", pos - 1);
            }
            char escaped = input.charAt(pos++);
            value.append(switch (escaped) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case '\\' -> '\\';
                case '\'' -> '\'';
                case '"' -> '"';
                default -> throw new DealSyntaxException("Unsupported string escape: \\" + escaped, pos - 2);
            });
        }
        throw new DealSyntaxException("Unterminated string literal", start);
    }

    private Token readSymbol() {
        int start = pos;
        char current = input.charAt(pos++);
        return switch (current) {
            case '(' -> token(TokenType.LPAREN, "(", start);
            case ')' -> token(TokenType.RPAREN, ")", start);
            case ',' -> token(TokenType.COMMA, ",", start);
            case '[' -> token(TokenType.LBRACKET, "[", start);
            case ']' -> token(TokenType.RBRACKET, "]", start);
            case '+' -> token(TokenType.PLUS, "+", start);
            case '-' -> token(TokenType.MINUS, "-", start);
            case '*' -> token(TokenType.STAR, "*", start);
            case '/' -> token(TokenType.SLASH, "/", start);
            case '%' -> token(TokenType.PERCENT, "%", start);
            case '=' -> match('=')
                    ? token(TokenType.EQUAL_EQUAL, "==", start)
                    : token(TokenType.ASSIGN, "=", start);
            case '!' -> match('=')
                    ? token(TokenType.BANG_EQUAL, "!=", start)
                    : token(TokenType.BANG, "!", start);
            case '<' -> match('=')
                    ? token(TokenType.LESS_EQUAL, "<=", start)
                    : token(TokenType.LESS, "<", start);
            case '>' -> match('=')
                    ? token(TokenType.GREATER_EQUAL, ">=", start)
                    : token(TokenType.GREATER, ">", start);
            case '&' -> {
                require('&', "Expected '&' after '&'", start);
                yield token(TokenType.AND_AND, "&&", start);
            }
            case '|' -> {
                require('|', "Expected '|' after '|'", start);
                yield token(TokenType.OR_OR, "||", start);
            }
            case '?' -> {
                require('?', "Expected '?' after '?'", start);
                yield token(TokenType.QUESTION_QUESTION, "??", start);
            }
            default -> throw new DealSyntaxException("Unexpected character: " + current, start);
        };
    }

    private boolean match(char expected) {
        if (pos >= input.length() || input.charAt(pos) != expected) {
            return false;
        }
        pos++;
        return true;
    }

    private void require(char expected, String message, int start) {
        if (!match(expected)) {
            throw new DealSyntaxException(message, start);
        }
    }

    private static Token token(TokenType type, String text, int position) {
        return new Token(type, text, position);
    }
}
