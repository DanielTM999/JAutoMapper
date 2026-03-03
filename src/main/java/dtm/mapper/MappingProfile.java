package dtm.mapper;


import dtm.mapper.enums.MissingFieldPolicy;
import dtm.mapper.enums.NullValuePolicy;

import java.util.function.Supplier;

public interface MappingProfile {

    MappingProfile map(String sourcePath, String targetField);

    MappingProfile ignore(String targetField);

    MappingProfile convertField(String targetField, MapperConverter<?, ?> converter);

    MappingProfile missingFieldPolicy(MissingFieldPolicy policy);

    MappingProfile defaultValue(String targetField, Supplier<?> value);

    <T> MappingProfile defaultValue(Class<T> targetType, Supplier<T> value);

    MappingProfile nullValuePolicy(NullValuePolicy policy);

}
