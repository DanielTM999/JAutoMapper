package dtm.mapper.imple;


import dtm.mapper.MapperConverter;
import dtm.mapper.MappingProfile;
import dtm.mapper.enums.MissingFieldPolicy;
import dtm.mapper.enums.NullValuePolicy;

import java.util.*;
import java.util.function.Supplier;

public class DefaultMappingProfile implements MappingProfile {

    private final Class<?> targetType;
    private final Class<?> sourceType;

    private final Map<String, String> mappings = new LinkedHashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();
    private final Map<String, MapperConverter<?, ?>> fieldConverters = new HashMap<>();
    private final Map<String, Supplier<?>> fieldDefaults = new HashMap<>();
    private final Map<Class<?>, Supplier<?>> typeDefaults = new HashMap<>();

    private MissingFieldPolicy missingFieldPolicy = MissingFieldPolicy.FAIL;
    private NullValuePolicy nullValuePolicy = NullValuePolicy.IGNORE;

    public DefaultMappingProfile(Class<?> targetType, Class<?> sourceType) {
        this.targetType = targetType;
        this.sourceType = sourceType;
    }

    @Override
    public MappingProfile map(String sourcePath, String targetField) {
        Objects.requireNonNull(sourcePath);
        Objects.requireNonNull(targetField);
        mappings.put(targetField, sourcePath);
        return this;
    }

    @Override
    public MappingProfile ignore(String targetField) {
        Objects.requireNonNull(targetField);
        ignoredFields.add(targetField);
        return this;
    }

    @Override
    public MappingProfile convertField(String targetField, MapperConverter<?, ?> converter) {
        Objects.requireNonNull(targetField);
        Objects.requireNonNull(converter);
        fieldConverters.put(targetField, converter);
        return this;
    }

    @Override
    public MappingProfile missingFieldPolicy(MissingFieldPolicy policy) {
        this.missingFieldPolicy = Objects.requireNonNull(policy);
        return this;
    }

    @Override
    public MappingProfile defaultValue(String targetField, Supplier<?> value) {
        Objects.requireNonNull(targetField);
        Objects.requireNonNull(value);
        fieldDefaults.put(targetField, value);
        return this;
    }

    @Override
    public <T> MappingProfile defaultValue(Class<T> targetType, Supplier<T> value) {
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(value);
        typeDefaults.put(targetType, value);
        return this;
    }

    @Override
    public MappingProfile nullValuePolicy(NullValuePolicy policy) {
        this.nullValuePolicy = Objects.requireNonNull(policy);
        return this;
    }

    public Map<String, String> getMappings() {
        return Collections.unmodifiableMap(mappings);
    }

    public Set<String> getIgnoredFields() {
        return Collections.unmodifiableSet(ignoredFields);
    }

    public MapperConverter<?, ?> getFieldConverter(String targetField) {
        return fieldConverters.get(targetField);
    }

    public Supplier<?> getFieldDefault(String targetField) {
        return fieldDefaults.get(targetField);
    }

    public Supplier<?> getTypeDefault(Class<?> type) {
        return typeDefaults.get(type);
    }

    public MissingFieldPolicy getMissingFieldPolicy() {
        return (missingFieldPolicy != null) ? missingFieldPolicy : MissingFieldPolicy.IGNORE;
    }

    public NullValuePolicy getNullValuePolicy() {
        return (nullValuePolicy != null) ? nullValuePolicy : NullValuePolicy.IGNORE;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public Class<?> getSourceType() {
        return sourceType;
    }
}