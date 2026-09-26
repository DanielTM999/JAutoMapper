package dtm.mapper;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public final class Converters {

    private Converters() {}

    public static <T> MapperConverter<Object, T> first() {
        return value -> {
            List<Object> elements = elementsOf(value);
            return elements.isEmpty() ? null : cast(elements.getFirst());
        };
    }

    public static <T> MapperConverter<Object, T> firstMatch(Predicate<T> predicate) {
        return value -> {
            List<Object> elements = elementsOf(value);
            for (Object element : elements) {
                T candidate = cast(element);
                if (predicate.test(candidate)) {
                    return candidate;
                }
            }
            return elements.isEmpty() ? null : cast(elements.getFirst());
        };
    }

    private static List<Object> elementsOf(Object value) {
        List<Object> elements = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element != null) elements.add(element);
            }
        } else if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                if (element != null) elements.add(element);
            }
        }
        return elements;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
