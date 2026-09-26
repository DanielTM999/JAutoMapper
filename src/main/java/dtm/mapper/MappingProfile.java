package dtm.mapper;


import dtm.mapper.enums.ConversionFailurePolicy;
import dtm.mapper.enums.MissingFieldPolicy;
import dtm.mapper.enums.NestedScope;
import dtm.mapper.enums.NullValuePolicy;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface MappingProfile {

    MappingProfile map(String sourcePath, String targetField);

    MappingProfile ignore(String targetField);

    MappingProfile convertField(String targetField, MapperConverter<?, ?> converter);

    MappingProfile missingFieldPolicy(MissingFieldPolicy policy);

    MappingProfile defaultValue(String targetField, Supplier<?> value);

    <T> MappingProfile defaultValue(Class<T> targetType, Supplier<T> value);

    MappingProfile nullValuePolicy(NullValuePolicy policy);

    MappingProfile backReference(String targetField, String childField);

    <S, T> MappingProfile afterMap(BiConsumer<S, T> action);

    MappingProfile nested(Class<?> sourceType, Class<?> targetType, Consumer<MappingProfile> config);

    MappingProfile nested(Class<?> sourceType, Class<?> targetType, Consumer<MappingProfile> config, NestedScope scope);

    MappingProfile nestedScope(NestedScope scope);

    <S, T> MappingProfile converter(Class<S> sourceType, Class<T> targetType, MapperConverter<S, T> converter);

    MappingProfile datePatterns(String... patterns);

    MappingProfile conversionFailurePolicy(ConversionFailurePolicy policy);

    MappingProfile flatten(String sourcePath);

    MappingProfile autoBackReference();

}
