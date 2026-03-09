package dtm.mapper.imple;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

public final class SimpleParameterizedType implements ParameterizedType {

    private final Type rawType;
    private final Type[] typeArguments;

    public SimpleParameterizedType(Type rawType, Type... typeArguments) {
        this.rawType = rawType;
        this.typeArguments = typeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
        return typeArguments;
    }

    @Override
    public Type getRawType() {
        return rawType;
    }

    @Override
    public Type getOwnerType() {
        return null;
    }

    @Override
    public String toString() {
        return rawType.getTypeName() + "<" +
                Arrays.stream(typeArguments)
                        .map(Type::getTypeName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("") +
                ">";
    }
}