package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.ArrayLiteralExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.AssignmentExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.BinaryExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.BooleanLiteralExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.Expression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.FunctionCallExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.IndexExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.NullLiteralExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.NumericLiteralExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.StringLiteralExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.UnaryExpression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.VariableExpression;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Recursive-descent parser for the DEAL expression grammar. */
public class Parser {
    private final List<Token> tokens;
    private int position;

    public Parser(List<Token> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    /** Parses exactly one complete expression. */
    public Expression parse() {
        if (tokens.isEmpty()) {
            throw error("Expected an expression", 0);
        }
        Expression expression = parseAssignment();
        if (!isAtEnd()) {
            throw error("Unexpected trailing token '" + peek().getText() + "'", peek());
        }
        return expression;
    }

    private Expression parseAssignment() {
        Expression expression = parseCoalesce();
        if (match(TokenType.ASSIGN)) {
            Token assignment = previous();
            if (!(expression instanceof Assignable assignable)) {
                throw error("Left-hand side of assignment must be assignable", assignment);
            }
            Expression value = parseAssignment();
            return new AssignmentExpression(assignable, value, covering(expression, value));
        }
        return expression;
    }

    private Expression parseCoalesce() {
        Expression expression = parseOr();
        while (match(TokenType.QUESTION_QUESTION)) {
            Expression right = parseOr();
            expression = new BinaryExpression(
                    expression, BinaryExpression.Operator.COALESCE, right, covering(expression, right));
        }
        return expression;
    }

    private Expression parseOr() {
        Expression expression = parseAnd();
        while (match(TokenType.OR_OR)) {
            Expression right = parseAnd();
            expression = new BinaryExpression(
                    expression, BinaryExpression.Operator.OR, right, covering(expression, right));
        }
        return expression;
    }

    private Expression parseAnd() {
        Expression expression = parseEquality();
        while (match(TokenType.AND_AND)) {
            Expression right = parseEquality();
            expression = new BinaryExpression(
                    expression, BinaryExpression.Operator.AND, right, covering(expression, right));
        }
        return expression;
    }

    private Expression parseEquality() {
        Expression expression = parseComparison();
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            Token operator = previous();
            BinaryExpression.Operator binaryOperator = operator.getType() == TokenType.EQUAL_EQUAL
                    ? BinaryExpression.Operator.EQUAL
                    : BinaryExpression.Operator.NOT_EQUAL;
            Expression right = parseComparison();
            expression = new BinaryExpression(expression, binaryOperator, right, covering(expression, right));
        }
        return expression;
    }

    private Expression parseComparison() {
        Expression expression = parseAdditive();
        while (match(TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL)) {
            BinaryExpression.Operator operator = switch (previous().getType()) {
                case LESS -> BinaryExpression.Operator.LESS;
                case LESS_EQUAL -> BinaryExpression.Operator.LESS_EQUAL;
                case GREATER -> BinaryExpression.Operator.GREATER;
                case GREATER_EQUAL -> BinaryExpression.Operator.GREATER_EQUAL;
                default -> throw new IllegalStateException("Unexpected comparison operator");
            };
            Expression right = parseAdditive();
            expression = new BinaryExpression(expression, operator, right, covering(expression, right));
        }
        return expression;
    }

    private Expression parseAdditive() {
        Expression expression = parseMultiplicative();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            BinaryExpression.Operator operator = previous().getType() == TokenType.PLUS
                    ? BinaryExpression.Operator.ADD
                    : BinaryExpression.Operator.SUBTRACT;
            Expression right = parseMultiplicative();
            expression = new BinaryExpression(expression, operator, right, covering(expression, right));
        }
        return expression;
    }

    private Expression parseMultiplicative() {
        Expression expression = parseUnary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            BinaryExpression.Operator operator = switch (previous().getType()) {
                case STAR -> BinaryExpression.Operator.MULTIPLY;
                case SLASH -> BinaryExpression.Operator.DIVIDE;
                case PERCENT -> BinaryExpression.Operator.REMAINDER;
                default -> throw new IllegalStateException("Unexpected multiplicative operator");
            };
            Expression right = parseUnary();
            expression = new BinaryExpression(expression, operator, right, covering(expression, right));
        }
        return expression;
    }

    private Expression parseUnary() {
        if (match(TokenType.BANG, TokenType.MINUS, TokenType.PLUS)) {
            Token operatorToken = previous();
            UnaryExpression.Operator operator = switch (previous().getType()) {
                case BANG -> UnaryExpression.Operator.NOT;
                case MINUS -> UnaryExpression.Operator.NEGATE;
                case PLUS -> UnaryExpression.Operator.PLUS;
                default -> throw new IllegalStateException("Unexpected unary operator");
            };
            Expression operand = parseUnary();
            return new UnaryExpression(operator, operand,
                    new DealSourceSpan(operatorToken.getPosition(), operand.sourceSpan().end()));
        }
        return parseIndexing();
    }

    private Expression parseIndexing() {
        Expression expression = parsePrimary();
        while (match(TokenType.LBRACKET)) {
            Expression index = parseAssignment();
            Token closingBracket = consume(TokenType.RBRACKET, "Expected ']' after index expression");
            expression = new IndexExpression(expression, index,
                    new DealSourceSpan(expression.sourceSpan().start(), closingBracket.getEndPosition()));
        }
        return expression;
    }

    private Expression parsePrimary() {
        if (match(TokenType.NUMBER)) {
            Token token = previous();
            return new NumericLiteralExpression(parseNumber(token), token.sourceSpan());
        }
        if (match(TokenType.STRING)) {
            Token token = previous();
            return new StringLiteralExpression(token.getText(), token.sourceSpan());
        }
        if (match(TokenType.BOOLEAN)) {
            Token token = previous();
            return new BooleanLiteralExpression(Boolean.parseBoolean(token.getText()), token.sourceSpan());
        }
        if (match(TokenType.NULL)) {
            return new NullLiteralExpression(previous().sourceSpan());
        }
        if (match(TokenType.LBRACKET)) {
            return parseArrayLiteral(previous());
        }
        if (match(TokenType.IDENTIFIER)) {
            Token identifier = previous();
            if (match(TokenType.LPAREN)) {
                return parseFunctionCall(identifier);
            }
            return new VariableExpression(identifier.getText(), identifier.sourceSpan());
        }
        if (match(TokenType.LPAREN)) {
            Expression expression = parseAssignment();
            consume(TokenType.RPAREN, "Expected ')' after expression");
            return expression;
        }
        throw isAtEnd()
                ? error("Unexpected end of expression", endPosition())
                : error("Unexpected token '" + peek().getText() + "'", peek());
    }

    private Expression parseArrayLiteral(Token openingBracket) {
        List<Expression> elements = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
            do {
                elements.add(parseAssignment());
            } while (match(TokenType.COMMA));
        }
        Token closingBracket = consume(TokenType.RBRACKET, "Expected ']' after array literal");
        return new ArrayLiteralExpression(elements,
                new DealSourceSpan(openingBracket.getPosition(), closingBracket.getEndPosition()));
    }

    private Expression parseFunctionCall(Token function) {
        List<Expression> arguments = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                arguments.add(parseAssignment());
            } while (match(TokenType.COMMA));
        }
        Token closingParenthesis = consume(TokenType.RPAREN, "Expected ')' after function arguments");
        return new FunctionCallExpression(function.getText(), arguments,
                new DealSourceSpan(function.getPosition(), closingParenthesis.getEndPosition()));
    }

    private Number parseNumber(Token token) {
        String text = token.getText();
        try {
            if (text.contains(".") || text.contains("e") || text.contains("E")) {
                double value = Double.parseDouble(text);
                if (!Double.isFinite(value)) {
                    throw error("Numeric literal is not finite", token);
                }
                return value;
            }
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                try {
                    return Long.valueOf(text);
                } catch (NumberFormatException ignoredLong) {
                    return new BigInteger(text);
                }
            }
        } catch (NumberFormatException e) {
            throw error("Invalid numeric literal '" + text + "'", token);
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                position++;
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return tokens.get(position++);
        }
        throw isAtEnd() ? error(message, endPosition()) : error(message, peek());
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().getType() == type;
    }

    private boolean isAtEnd() {
        return position >= tokens.size();
    }

    private Token peek() {
        return tokens.get(position);
    }

    private Token previous() {
        return tokens.get(position - 1);
    }

    private int endPosition() {
        if (tokens.isEmpty()) {
            return 0;
        }
        Token last = tokens.getLast();
        return last.getEndPosition();
    }

    private static DealSyntaxException error(String message, Token token) {
        return error(message, token.getPosition());
    }

    private static DealSyntaxException error(String message, int position) {
        int safePosition = Math.max(position, 0);
        return new DealSyntaxException(message, new DealSourceSpan(safePosition, safePosition));
    }

    private static DealSourceSpan covering(Expression first, Expression second) {
        return DealSourceSpan.covering(first.sourceSpan(), second.sourceSpan());
    }
}
