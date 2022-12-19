package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

/**
 * 扫描器类
 */
public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // 为了处理关键字，我们要查看标识符的词素是否是保留字之一。
    // 如果是，我们就使用该关键字特有的标记类型。我们在map中定义保留字的集合
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and",    AND);
        keywords.put("class",  CLASS);
        keywords.put("else",   ELSE);
        keywords.put("false",  FALSE);
        keywords.put("for",    FOR);
        keywords.put("fun",    FUN);
        keywords.put("if",     IF);
        keywords.put("nil",    NIL);
        keywords.put("or",     OR);
        keywords.put("print",  PRINT);
        keywords.put("return", RETURN);
        keywords.put("super",  SUPER);
        keywords.put("this",   THIS);
        keywords.put("true",   TRUE);
        keywords.put("var",    VAR);
        keywords.put("while",  WHILE);
    }

    // 添加下面三行代码，跟踪扫描器在源代码中的位置
    /**
     * start`字段指向被扫描的词素中的第一个字符，
     * `current`字段指向当前正在处理的字符。
     * `line`字段跟踪的是`current`所在的源文件行数，
     * 这样我们产生的标记就可以知道其位置。
     */
    private int start = 0;
    private int current = 0;
    private int line = 1;

    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    // 识别词素，每一次循环中，都可以扫描出一个token，这是扫描器真正的核心
    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;

            // 操作符处理
            /**
             * 单字符词素已经生效了，但是这不能涵盖Lox中的所有操作符
             * 比如`!`，这是单字符，对吧？有时候是的，但是如果下一个字符是等号，那么我们应该改用`!=` 词素。
             * 对于所有这些情况，我们都需要查看第二个字符。
             *
             * 这就像一个有条件的`advance()`。只有当前字符是我们正在寻找的字符时，我们才会消费。
             * 使用`match()`，我们分两个阶段识别这些词素。例如，当我们得到`!`时，我们会跳转到它的case分支。
             * 这意味着我们知道这个词素是以 `!`开始的。然后，我们查看下一个字符，以确认词素是一个 `!=` 还是仅仅是一个 `!`。
             */
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;

            // 对于出发的特殊处理，因为注释也是斜线开头
            // 这与其它的双字符操作符是类似的，区别在于我们找到第二个`/`时，还没有结束本次标记。相反，我们会继续消费字符直至行尾。
            /**
             * 注释是词素，但是它们没有含义，而且解析器也不想要处理它们。
             * 所以，我们达到注释末尾后，不会调用`addToken()`方法。
             * 当我们循环处理下一个词素时，`start`已经被重置了，注释的词素就消失在一阵烟雾中了。
             */
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;

            // 跳过无意义的字符：换行和空格
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;

            // 我们使用`peek()` 而不是`match()`来查找注释结尾的换行符。我们到这里希望能读取到换行符，这样我们就可以更新行数了
            case '\n':
                line++;
                break;

            // 处理字面量
            case '"': string(); break;

            // 处理词法错误
            // 错误的字符仍然会被前面调用的`advance()`方法消费。这样我们就不会陷入无限循环。
            /**
             * 同时要注意，我们一直在扫描，程序稍后可能还会出现其他错误
             *  如果我们能够一次检测出尽可能多的错误，将为用户带来更好的体验
             *  否则，他们会看到一个小错误并修复它，但是却出现下一个错误，不断重复这个过程
             */
            default:
//                Lox.error(line, "Unexpected character.");
                /**
                 * 处理数字字面量
                 * 在Lox中，所有的数字在运行时都是浮点数，但是同时支持整数和小数字面量。
                 * 一个数字字面量就是一系列数位，后面可以跟一个`.`和一或多个尾数
                 * 我们不允许小数点处于最开始或最末尾
                 */
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    // 处理保留字和标识符
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

//        addToken(IDENTIFIER);
        // 在扫描到标识符后，要检查是否与map中的某些项匹配
        // 如果匹配的话，就使用关键字的标记类型。否则，就是一个普通的用户定义的标识符。
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) type = IDENTIFIER;
        addToken(type);
    }

    /**
     * 消费剩余的数字字面量，与字符串处理类似
     * 我们在字面量的整数部分中尽可能多地获取数字。然后我们寻找小数部分，也就是一个小数点(`.`)后面至少跟一个数字。
     * 如果确实有小数部分，同样地，我们也尽可能多地获取数字。
     * 在定位到小数点之后需要继续前瞻第二个字符，因为我们只有确认其*后*有数字才会消费`.`
     */
    private void number() {
        while (isDigit(peek())) advance();

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();

            while (isDigit(peek())) advance();
        }

        // 最后，我们将词素转换为其对应的数值。
        // 我们的解释器使用Java的`Double`类型来表示数字，所以我们创建一个该类型的值。
        // 我们使用Java自带的解析方法将词素转换为真正的Java double。
        addToken(NUMBER,
                Double.parseDouble(source.substring(start, current)));
    }

    /**
     * 从以下方法可以看出，Lox支持多行字符串
     * 这意味着当我们在字符串内遇到新行时，我们也需要更新`line`值。
     */
    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // The closing ".
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    // 此方法有点像`advance()`方法，只是不会消费字符。这就是所谓的lookahead(前瞻)，因为它只关注当前未消费的字符
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    /**
     * 定位到小数点之后需要继续前瞻第二个字符，因为我们只有确认其*后*有数字才会消费`.`
     */
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    // 判断当前是否在处理数字
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // 此函数告诉我们是否已经消费完所有字符
    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        current++;
        return source.charAt(current - 1);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}
