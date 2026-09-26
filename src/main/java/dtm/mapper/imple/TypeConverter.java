package dtm.mapper.imple;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TypeConverter {

    public static final Object UNSUPPORTED = new Object();

    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
            int.class, Integer.class,
            long.class, Long.class,
            double.class, Double.class,
            float.class, Float.class,
            short.class, Short.class,
            byte.class, Byte.class,
            boolean.class, Boolean.class,
            char.class, Character.class
    );

    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    private TypeConverter() {}

    public static Class<?> wrap(Class<?> type) {
        return type.isPrimitive() ? WRAPPERS.get(type) : type;
    }

    public static boolean isCompatible(Object value, Class<?> targetType) {
        return wrap(targetType).isInstance(value);
    }

    public static Object convert(Object value, Class<?> targetType, List<String> datePatterns) {
        Class<?> target = wrap(targetType);

        if (target == String.class) return toText(value);
        if (value instanceof String text) return fromText(text.trim(), target, datePatterns);
        if (value instanceof Number number) return fromNumber(number, target);
        if (value instanceof LocalDateTime dateTime && target == LocalDate.class) return dateTime.toLocalDate();
        if (value instanceof LocalDateTime dateTime && target == LocalTime.class) return dateTime.toLocalTime();
        if (value instanceof LocalDate date && target == LocalDateTime.class) return date.atStartOfDay();

        return UNSUPPORTED;
    }

    private static Object toText(Object value) {
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        if (value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof CharSequence
                || value instanceof Temporal
                || value instanceof UUID) {
            return value.toString();
        }
        return UNSUPPORTED;
    }

    private static Object fromNumber(Number number, Class<?> target) {
        if (target == Integer.class) return number.intValue();
        if (target == Long.class) return number.longValue();
        if (target == Double.class) return number.doubleValue();
        if (target == Float.class) return number.floatValue();
        if (target == Short.class) return number.shortValue();
        if (target == Byte.class) return number.byteValue();
        if (target == BigDecimal.class) {
            return (number instanceof BigDecimal decimal) ? decimal : new BigDecimal(number.toString());
        }
        if (target == BigInteger.class) {
            if (number instanceof BigInteger integer) return integer;
            if (number instanceof BigDecimal decimal) return decimal.toBigInteger();
            return BigInteger.valueOf(number.longValue());
        }
        return UNSUPPORTED;
    }

    private static Object fromText(String text, Class<?> target, List<String> datePatterns) {
        if (target == Integer.class) return blankOr(text, () -> Integer.valueOf(text));
        if (target == Long.class) return blankOr(text, () -> Long.valueOf(text));
        if (target == Double.class) return blankOr(text, () -> Double.valueOf(text));
        if (target == Float.class) return blankOr(text, () -> Float.valueOf(text));
        if (target == Short.class) return blankOr(text, () -> Short.valueOf(text));
        if (target == Byte.class) return blankOr(text, () -> Byte.valueOf(text));
        if (target == BigDecimal.class) return blankOr(text, () -> new BigDecimal(text));
        if (target == BigInteger.class) return blankOr(text, () -> new BigInteger(text));
        if (target == Boolean.class) return blankOr(text, () -> parseBoolean(text));
        if (target == Character.class) return blankOr(text, () -> parseCharacter(text));
        if (target == UUID.class) return blankOr(text, () -> UUID.fromString(text));
        if (target == LocalDate.class) return blankOr(text, () -> parseLocalDate(text, datePatterns));
        if (target == LocalDateTime.class) return blankOr(text, () -> parseLocalDateTime(text, datePatterns));
        if (target == LocalTime.class) return blankOr(text, () -> parseLocalTime(text, datePatterns));
        return UNSUPPORTED;
    }

    private static Object blankOr(String text, ValueParser parser) {
        return text.isEmpty() ? null : parser.parse();
    }

    private static Boolean parseBoolean(String text) {
        if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
        throw new IllegalArgumentException("Cannot convert '" + text + "' to Boolean");
    }

    private static Character parseCharacter(String text) {
        if (text.length() == 1) return text.charAt(0);
        throw new IllegalArgumentException("Cannot convert '" + text + "' to Character");
    }

    private static LocalDate parseLocalDate(String text, List<String> datePatterns) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        for (String pattern : datePatterns) {
            try {
                return LocalDate.from(formatter(pattern).parse(text));
            } catch (RuntimeException ignored) {
            }
        }
        throw new IllegalArgumentException("Cannot convert '" + text + "' to LocalDate");
    }

    private static LocalDateTime parseLocalDateTime(String text, List<String> datePatterns) {
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        for (String pattern : datePatterns) {
            try {
                TemporalAccessor parsed = formatter(pattern).parseBest(text, LocalDateTime::from, LocalDate::from);
                return (parsed instanceof LocalDate date) ? date.atStartOfDay() : (LocalDateTime) parsed;
            } catch (RuntimeException ignored) {
            }
        }
        throw new IllegalArgumentException("Cannot convert '" + text + "' to LocalDateTime");
    }

    private static LocalTime parseLocalTime(String text, List<String> datePatterns) {
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text).toLocalTime();
        } catch (DateTimeParseException ignored) {
        }
        for (String pattern : datePatterns) {
            try {
                return LocalTime.from(formatter(pattern).parse(text));
            } catch (RuntimeException ignored) {
            }
        }
        throw new IllegalArgumentException("Cannot convert '" + text + "' to LocalTime");
    }

    private static DateTimeFormatter formatter(String pattern) {
        return FORMATTERS.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    @FunctionalInterface
    private interface ValueParser {
        Object parse();
    }
}
