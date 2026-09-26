package dtm.mapper.imple;


import dtm.mapper.MapperConverter;
import dtm.mapper.MappingProfile;
import dtm.mapper.enums.ConversionFailurePolicy;
import dtm.mapper.enums.MissingFieldPolicy;
import dtm.mapper.enums.NestedScope;
import dtm.mapper.enums.NullValuePolicy;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DefaultMappingProfile implements MappingProfile {

    private final Class<?> targetType;
    private final Class<?> sourceType;
    private final DefaultMappingProfile parent;

    private final Map<String, String> mappings = new LinkedHashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();
    private final Map<String, MapperConverter<?, ?>> fieldConverters = new HashMap<>();
    private final Map<String, Supplier<?>> fieldDefaults = new HashMap<>();
    private final Map<Class<?>, Supplier<?>> typeDefaults = new HashMap<>();
    private final Map<String, String> backReferences = new LinkedHashMap<>();
    private final List<BiConsumer<Object, Object>> afterMapActions = new ArrayList<>();
    private final Map<ClassPairKey, NestedDeclaration> nestedDeclarations = new LinkedHashMap<>();
    private final Map<ClassPairKey, MapperConverter<?, ?>> typeConverters = new LinkedHashMap<>();
    private final List<String> flattenPaths = new ArrayList<>();

    private MissingFieldPolicy missingFieldPolicy;
    private NullValuePolicy nullValuePolicy;
    private NestedScope nestedScope;
    private ConversionFailurePolicy conversionFailurePolicy;
    private List<String> datePatterns;
    private Boolean autoBackReference;

    public DefaultMappingProfile(Class<?> targetType, Class<?> sourceType) {
        this(targetType, sourceType, null);
    }

    public DefaultMappingProfile(Class<?> targetType, Class<?> sourceType, DefaultMappingProfile parent) {
        this.targetType = targetType;
        this.sourceType = sourceType;
        this.parent = parent;
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

    @Override
    public MappingProfile backReference(String targetField, String childField) {
        Objects.requireNonNull(targetField);
        Objects.requireNonNull(childField);
        backReferences.put(targetField, childField);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S, T> MappingProfile afterMap(BiConsumer<S, T> action) {
        Objects.requireNonNull(action);
        afterMapActions.add((BiConsumer<Object, Object>) action);
        return this;
    }

    @Override
    public MappingProfile nested(Class<?> sourceType, Class<?> targetType, Consumer<MappingProfile> config) {
        return declareNested(sourceType, targetType, config, null);
    }

    @Override
    public MappingProfile nested(Class<?> sourceType, Class<?> targetType, Consumer<MappingProfile> config, NestedScope scope) {
        return declareNested(sourceType, targetType, config, Objects.requireNonNull(scope));
    }

    @Override
    public MappingProfile nestedScope(NestedScope scope) {
        this.nestedScope = Objects.requireNonNull(scope);
        return this;
    }

    @Override
    public <S, T> MappingProfile converter(Class<S> sourceType, Class<T> targetType, MapperConverter<S, T> converter) {
        Objects.requireNonNull(sourceType);
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(converter);
        typeConverters.put(new ClassPairKey(sourceType, targetType), converter);
        return this;
    }

    @Override
    public MappingProfile datePatterns(String... patterns) {
        Objects.requireNonNull(patterns);
        List<String> values = new ArrayList<>();
        for (String pattern : patterns) {
            values.add(Objects.requireNonNull(pattern));
        }
        this.datePatterns = List.copyOf(values);
        return this;
    }

    @Override
    public MappingProfile conversionFailurePolicy(ConversionFailurePolicy policy) {
        this.conversionFailurePolicy = Objects.requireNonNull(policy);
        return this;
    }

    @Override
    public MappingProfile flatten(String sourcePath) {
        flattenPaths.add(Objects.requireNonNull(sourcePath));
        return this;
    }

    @Override
    public MappingProfile autoBackReference() {
        this.autoBackReference = Boolean.TRUE;
        return this;
    }

    public MapperConverter<?, ?> findTypeConverter(Class<?> valueType, Class<?> targetType) {
        MapperConverter<?, ?> exact = typeConverters.get(new ClassPairKey(valueType, targetType));
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<ClassPairKey, MapperConverter<?, ?>> entry : typeConverters.entrySet()) {
            ClassPairKey key = entry.getKey();
            if (key.target().equals(targetType) && key.source().isAssignableFrom(valueType)) {
                return entry.getValue();
            }
        }
        return (parent != null) ? parent.findTypeConverter(valueType, targetType) : null;
    }

    public List<String> getDatePatterns() {
        if (datePatterns != null) return datePatterns;
        return (parent != null) ? parent.getDatePatterns() : List.of();
    }

    public ConversionFailurePolicy getConversionFailurePolicy() {
        if (conversionFailurePolicy != null) return conversionFailurePolicy;
        return (parent != null) ? parent.getConversionFailurePolicy() : ConversionFailurePolicy.FAIL;
    }

    public List<String> getFlattenPaths() {
        return Collections.unmodifiableList(flattenPaths);
    }

    public boolean isAutoBackReference() {
        if (autoBackReference != null) return autoBackReference;
        return parent != null && parent.isAutoBackReference();
    }

    private MappingProfile declareNested(Class<?> sourceType, Class<?> targetType, Consumer<MappingProfile> config, NestedScope scope) {
        Objects.requireNonNull(sourceType);
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(config);
        DefaultMappingProfile nestedProfile = new DefaultMappingProfile(targetType, sourceType, this);
        config.accept(nestedProfile);
        nestedDeclarations.put(new ClassPairKey(sourceType, targetType), new NestedDeclaration(nestedProfile, scope));
        return this;
    }

    public DefaultMappingProfile findNested(Class<?> source, Class<?> target) {
        NestedDeclaration exact = nestedDeclarations.get(new ClassPairKey(source, target));
        if (exact != null) {
            return exact.profile();
        }
        for (Map.Entry<ClassPairKey, NestedDeclaration> entry : nestedDeclarations.entrySet()) {
            ClassPairKey key = entry.getKey();
            if (key.target().equals(target) && key.source().isAssignableFrom(source)) {
                return entry.getValue().profile();
            }
        }
        return (parent != null) ? parent.findNested(source, target) : null;
    }

    public NestedScope effectiveScope(NestedDeclaration declaration) {
        return (declaration.scope() != null) ? declaration.scope() : getNestedScope();
    }

    public Map<ClassPairKey, NestedDeclaration> getNestedDeclarations() {
        return Collections.unmodifiableMap(nestedDeclarations);
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
        Supplier<?> supplier = typeDefaults.get(type);
        if (supplier == null && parent != null) {
            return parent.getTypeDefault(type);
        }
        return supplier;
    }

    public MissingFieldPolicy getMissingFieldPolicy() {
        if (missingFieldPolicy != null) return missingFieldPolicy;
        return (parent != null) ? parent.getMissingFieldPolicy() : MissingFieldPolicy.FAIL;
    }

    public NullValuePolicy getNullValuePolicy() {
        if (nullValuePolicy != null) return nullValuePolicy;
        return (parent != null) ? parent.getNullValuePolicy() : NullValuePolicy.IGNORE;
    }

    public NestedScope getNestedScope() {
        if (nestedScope != null) return nestedScope;
        return (parent != null) ? parent.getNestedScope() : NestedScope.LOCAL;
    }

    public Map<String, MapperConverter<?, ?>> getFieldConverters(){
        return fieldConverters;
    }

    public Map<String, String> getBackReferences() {
        return Collections.unmodifiableMap(backReferences);
    }

    public List<BiConsumer<Object, Object>> getAfterMapActions() {
        return Collections.unmodifiableList(afterMapActions);
    }

    public DefaultMappingProfile getParent() {
        return parent;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public Class<?> getSourceType() {
        return sourceType;
    }

    public record NestedDeclaration(DefaultMappingProfile profile, NestedScope scope) {}
}
