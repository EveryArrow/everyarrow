package com.everyarrow.dittoe.pipelines.behaviors.transformation;

public enum TokenType {
    IDENTIFIER,  // Function names (e.g., "sum", "multiply")
    NUMBER,      // Numeric values (e.g., 123)
    STRING,      // Text strings (e.g., "hello world")
    BOOLEAN,     // Boolean values (true, false)
    NULL,        // Null value
    LPAREN,      // Left parenthesis "("
    RPAREN,      // Right parenthesis ")"
    COMMA,       // Comma ","
    LBRACKET,    // Left bracket "["
    RBRACKET,    // Right bracket "]"
    ASSIGN,      // Assignment "="
    PLUS,        // Addition or unary plus "+"
    MINUS,       // Subtraction or unary minus "-"
    STAR,        // Multiplication "*"
    SLASH,       // Division "/"
    PERCENT,     // Remainder "%"
    BANG,        // Logical not "!"
    EQUAL_EQUAL, // Equality "=="
    BANG_EQUAL,  // Inequality "!="
    LESS,        // Less than "<"
    LESS_EQUAL,  // Less than or equal "<="
    GREATER,     // Greater than ">"
    GREATER_EQUAL, // Greater than or equal ">="
    AND_AND,     // Logical and "&&"
    OR_OR,       // Logical or "||"
    QUESTION_QUESTION // Null coalescing "??"
}
