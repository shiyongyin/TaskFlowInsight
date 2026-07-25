package com.syy.taskflowinsight.tracking.projection.internal;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;

import java.util.Objects;

/**
 * 对允许发布的EXACT scalar facts执行固定支付卡与SSN检测。
 *
 * <p>实现只读取有界canonical facts，不调用业务对象、任意regex或可插拔detector，因而结果确定且工作量受值预算约束。</p>
 *
 * @since 4.0.0
 */
public final class SensitiveValueDetector {

    /** 支付卡候选允许的最少数字位。 */
    private static final int MIN_CARD_DIGITS = 13;

    /** 支付卡候选允许的最多数字位。 */
    private static final int MAX_CARD_DIGITS = 19;

    private SensitiveValueDetector() {
    }

    /**
     * 完整扫描一个exact scalar的全部canonical文本facts。
     *
     * @param snapshot 已受单值预算约束且不持有业务对象的值事实
     * @return 任一fact包含有效Luhn候选或完整SSN时为true
     */
    public static boolean isSensitive(ValueSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.representation() != ValueSnapshot.Representation.EXACT || !snapshot.isScalar()) {
            return false;
        }
        return snapshot.canonicalTextFacts().stream()
                .anyMatch(fact -> containsSsn(fact) || containsLuhnCard(fact));
    }

    private static boolean containsSsn(String value) {
        for (int start = 0; start + 11 <= value.length(); start++) {
            if (matchesSsnAt(value, start)
                    && isSsnBoundary(value, start - 1)
                    && isSsnBoundary(value, start + 11)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSsnAt(String value, int start) {
        return isAsciiDigit(value.charAt(start))
                && isAsciiDigit(value.charAt(start + 1))
                && isAsciiDigit(value.charAt(start + 2))
                && value.charAt(start + 3) == '-'
                && isAsciiDigit(value.charAt(start + 4))
                && isAsciiDigit(value.charAt(start + 5))
                && value.charAt(start + 6) == '-'
                && isAsciiDigit(value.charAt(start + 7))
                && isAsciiDigit(value.charAt(start + 8))
                && isAsciiDigit(value.charAt(start + 9))
                && isAsciiDigit(value.charAt(start + 10));
    }

    private static boolean isSsnBoundary(String value, int index) {
        if (index < 0 || index >= value.length()) {
            return true;
        }
        char adjacent = value.charAt(index);
        return !isAsciiDigit(adjacent)
                && !(adjacent >= 'A' && adjacent <= 'Z')
                && !(adjacent >= 'a' && adjacent <= 'z')
                && adjacent != '_';
    }

    private static boolean containsLuhnCard(String value) {
        for (int start = 0; start < value.length(); start++) {
            if (!isAsciiDigit(value.charAt(start)) || startsInsideDigitRun(value, start)) {
                continue;
            }
            char[] digits = new char[MAX_CARD_DIGITS];
            int digitCount = 0;
            int cursor = start;
            while (cursor < value.length() && isCardCandidateChar(value.charAt(cursor))) {
                char current = value.charAt(cursor++);
                if (isAsciiDigit(current)) {
                    if (digitCount == MAX_CARD_DIGITS) {
                        digitCount++;
                        break;
                    }
                    digits[digitCount++] = current;
                }
            }
            if (digitCount >= MIN_CARD_DIGITS
                    && digitCount <= MAX_CARD_DIGITS
                    && (cursor >= value.length() || !isAsciiDigit(value.charAt(cursor)))
                    && passesLuhn(digits, digitCount)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsInsideDigitRun(String value, int start) {
        return start > 0 && isAsciiDigit(value.charAt(start - 1));
    }

    private static boolean isCardCandidateChar(char value) {
        return isAsciiDigit(value) || value == ' ' || value == '-';
    }

    private static boolean passesLuhn(char[] digits, int length) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = length - 1; index >= 0; index--) {
            int digit = digits[index] - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }
}
