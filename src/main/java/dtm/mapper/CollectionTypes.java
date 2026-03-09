package dtm.mapper;

import dtm.mapper.exceptions.MappingException;
import dtm.mapper.imple.SimpleParameterizedType;

import java.lang.reflect.Type;
import java.util.Collection;

public final class CollectionTypes {
    private CollectionTypes() {
        throw new MappingException("Cannot create instance of CollectionTypes");
    }

    public static Type of(Class<? extends Collection<?>> collectionClass, Class<?> elementClass) {
        return new SimpleParameterizedType(collectionClass, elementClass);
    }
}
