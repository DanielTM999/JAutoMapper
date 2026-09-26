package dtm.mapper.service;


import dtm.mapper.AutoMapper;
import dtm.mapper.CollectionReference;
import dtm.mapper.MapperConverter;
import dtm.mapper.MappingProfile;
import dtm.mapper.enums.ConversionFailurePolicy;
import dtm.mapper.enums.NestedScope;
import dtm.mapper.enums.NodeKind;
import dtm.mapper.exceptions.MappingException;
import dtm.mapper.imple.ClassPairKey;
import dtm.mapper.imple.DefaultMappingProfile;
import dtm.mapper.imple.DefaultMappingProfile.NestedDeclaration;
import dtm.mapper.imple.TypeConverter;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutoMapperService implements AutoMapper {

    private static final Object MISSING = new Object();
    private static final Map<ClassPairKey, AutoMapperService> MAPPERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> CLASS_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<ClassPairKey, GlobalEntry> GLOBAL_PROFILES = new ConcurrentHashMap<>();
    private static final Map<ClassPairKey, Optional<Field>> AUTO_BACK_REFERENCES = new ConcurrentHashMap<>();
    private static final String ROOT_PATH_PREFIX = "$.";

    private final DefaultMappingProfile mappingProfile;
    private final Map<ResolvedKey, ResolvedProfile> resolvedProfiles;

    public static AutoMapper register(Class<?> source, Class<?> target) {
        return register(source, target, null);
    }

    public static AutoMapper register(Class<?> source, Class<?> target, Consumer<MappingProfile> mappingProfileConsumer) {
        if (source == null) {
            throw new MappingException("Source type cannot be null");
        }

        if (target == null) {
            throw new MappingException("Target type cannot be null");
        }

        return createService(source, target, mappingProfileConsumer);
    }

    public static AutoMapper getAutoMapper(Class<?> source, Class<?> target) {
        if (source == null) {
            throw new MappingException("Source type cannot be null");
        }

        if (target == null) {
            throw new MappingException("Target type cannot be null");
        }

        ClassPairKey key = new ClassPairKey(source, target);
        AutoMapperService autoMapperService = MAPPERS.get(key);
        if (autoMapperService == null) {
            throw new MappingException(
                    "No AutoMapper registered for source "
                            + source.getName()
                            + " and target "
                            + target.getName()
            );
        }
        return autoMapperService;
    }


    public static AutoMapper getOrRegister(Class<?> source, Class<?> target) {
       return getOrRegister(source, target, null);
    }

    public static AutoMapper getOrRegister(Class<?> source, Class<?> target, Consumer<MappingProfile> mappingProfileConsumer) {
        if (source == null) {
            throw new MappingException("Source type cannot be null");
        }
        if (target == null) {
            throw new MappingException("Target type cannot be null");
        }

        ClassPairKey key = new ClassPairKey(source, target);

        return MAPPERS.computeIfAbsent(key, k -> createService(source, target, mappingProfileConsumer));
    }

    private static AutoMapperService createService(Class<?> source, Class<?> target, Consumer<MappingProfile> mappingProfileConsumer) {
        DefaultMappingProfile mappingProfile = new DefaultMappingProfile(target, source);

        if (mappingProfileConsumer != null) {
            mappingProfileConsumer.accept(mappingProfile);
        }

        publishGlobalProfiles(mappingProfile, new ClassPairKey(source, target));

        return new AutoMapperService(mappingProfile);
    }


    protected AutoMapperService(DefaultMappingProfile mappingProfile) {
        this.mappingProfile = mappingProfile;
        this.resolvedProfiles = new ConcurrentHashMap<>();
    }


    @Override
    public <T> T map(Object source, Class<T> targetType) {
        validTargetType(targetType);
        validSource(source);

        MappingContext context = MappingContext.root(resolve(mappingProfile, targetType), source);

        T target = createInstanceForElement(targetType);

        mapNode(context, source, target, source.getClass(), targetType);

        runAfterMap(context, source, target);

        return target;
    }

    @Override
    public <T extends Collection<?>> T map(Object source, CollectionReference<T> collectionReferenceType) throws MappingException {
        return map(source, collectionReferenceType.getType());
    }

    @Override
    public <T extends Collection<?>> T map(Object source, Type targetType) throws MappingException {
        Class<?> targetClass;
        if (targetType instanceof ParameterizedType paramType) {
            Type rawType = paramType.getRawType();
            if (rawType instanceof Class<?> clazz) {
                targetClass = clazz;
            }else {
                throw new MappingException("invalid target type");
            }
        }else if (targetType instanceof Class<?> clazz) {
            targetClass = clazz;
        }else {
            throw new MappingException("invalid target type");
        }

        Class<?> targetTypeGeneric = getFirstParameterizedType(targetType, targetClass.getName());

        validTargetType(targetClass);
        validSource(source);

        MappingContext context = MappingContext.root(resolve(mappingProfile, targetTypeGeneric), source);

        Collection<?> target = createCollectionFromType(targetClass);

        mapNode(context, source, target, source.getClass(), targetClass, targetTypeGeneric);

        return (T)target;
    }

    private void validTargetType(Class<?> targetType) {
        if (targetType == null) {
            throw new MappingException("Target type cannot be null");
        }

        if (targetType.isEnum()) {
            throw new MappingException("Target type cannot be an enum: " + targetType.getName());
        }

        if (targetType.isArray()) {
            throw new MappingException("Target type cannot be an array: " + targetType.getName());
        }

        if (targetType.isPrimitive()) {
            throw new MappingException("Target type cannot be a primitive: " + targetType.getName());
        }

        if (targetType.isAnnotation()) {
            throw new MappingException("Target type cannot be an annotation: " + targetType.getName());
        }

        if (targetType.isInterface() && !Collection.class.isAssignableFrom(targetType)) {
            throw new MappingException("Target type cannot be an interface: " + targetType.getName());
        }

        if (!targetType.equals(mappingProfile.getTargetType())) {
            throw new MappingException(
                    "Target type mismatch. Expected: "
                            + mappingProfile.getTargetType().getName()
                            + ", received: "
                            + targetType.getName()
            );
        }

        if (Modifier.isAbstract(targetType.getModifiers()) && !Collection.class.isAssignableFrom(targetType)) {
            throw new MappingException("Target type cannot be abstract: " + targetType.getName());
        }
    }

    private void validSource(Object source) {
        if (source == null) {
            throw new MappingException("Source object cannot be null");
        }

        Class<?> sourceType = source.getClass();

        if (sourceType.isEnum()) {
            throw new MappingException("Source type cannot be an enum: " + sourceType.getName());
        }

        if (sourceType.isPrimitive()) {
            throw new MappingException("Source type cannot be a primitive: " + sourceType.getName());
        }

        if (sourceType.isAnnotation()) {
            throw new MappingException("Source type cannot be an annotation: " + sourceType.getName());
        }

        if (sourceType.isInterface()) {
            throw new MappingException("Source type cannot be an interface: " + sourceType.getName());
        }

        if (Modifier.isAbstract(sourceType.getModifiers())) {
            if (!mappingProfile.getSourceType().isAssignableFrom(sourceType)) {
                throw new MappingException(
                        "Source type is abstract and cannot be mapped directly: " + sourceType.getName()
                );
            }
        }

        if (!mappingProfile.getSourceType().isAssignableFrom(sourceType)) {
            throw new MappingException(
                    "Source type mismatch. Expected: "
                            + mappingProfile.getSourceType().getName()
                            + ", received: "
                            + sourceType.getName()
            );
        }
    }


    private static void publishGlobalProfiles(DefaultMappingProfile rootProfile, ClassPairKey owner) {
        Map<ClassPairKey, DefaultMappingProfile> globals = new LinkedHashMap<>();
        collectGlobalProfiles(rootProfile, globals);
        if (globals.isEmpty()) return;

        synchronized (GLOBAL_PROFILES) {
            for (ClassPairKey key : globals.keySet()) {
                GlobalEntry existing = GLOBAL_PROFILES.get(key);
                if (existing != null && !existing.owner().equals(owner)) {
                    throw new MappingException(
                            "Global nested profile already registered for " + key.describe()
                                    + " by " + existing.owner().describe()
                    );
                }
            }
            globals.forEach((key, profile) -> GLOBAL_PROFILES.put(key, new GlobalEntry(profile, owner)));
        }
    }

    private static void collectGlobalProfiles(DefaultMappingProfile profile, Map<ClassPairKey, DefaultMappingProfile> globals) {
        for (Map.Entry<ClassPairKey, NestedDeclaration> entry : profile.getNestedDeclarations().entrySet()) {
            NestedDeclaration declaration = entry.getValue();
            if (profile.effectiveScope(declaration) == NestedScope.GLOBAL) {
                globals.put(entry.getKey(), declaration.profile());
            }
            collectGlobalProfiles(declaration.profile(), globals);
        }
    }

    private static DefaultMappingProfile findGlobalProfile(Class<?> source, Class<?> target) {
        GlobalEntry exact = GLOBAL_PROFILES.get(new ClassPairKey(source, target));
        if (exact != null) {
            return exact.profile();
        }
        for (Map.Entry<ClassPairKey, GlobalEntry> entry : GLOBAL_PROFILES.entrySet()) {
            ClassPairKey key = entry.getKey();
            if (key.target().equals(target) && key.source().isAssignableFrom(source)) {
                return entry.getValue().profile();
            }
        }
        return null;
    }

    private ResolvedProfile resolve(DefaultMappingProfile profile, Class<?> targetType) {
        return resolvedProfiles.computeIfAbsent(
                new ResolvedKey(profile, targetType),
                key -> new ResolvedProfile(profile, targetType)
        );
    }

    private MappingContext childContext(MappingContext parentContext, Object source, Class<?> targetType, Field skipField) {
        if (resolveKind(targetType) == NodeKind.OBJECT) {
            Class<?> sourceType = source.getClass();
            DefaultMappingProfile nestedProfile = parentContext.resolved().profile().findNested(sourceType, targetType);
            if (nestedProfile == null) {
                nestedProfile = findGlobalProfile(sourceType, targetType);
            }
            if (nestedProfile != null) {
                return new MappingContext(resolve(nestedProfile, targetType), parentContext.rootSource(), source, skipField, true);
            }
        }
        return new MappingContext(parentContext.resolved(), parentContext.rootSource(), parentContext.pathRoot(), skipField, false);
    }

    private Object mapChild(MappingContext parentContext, Object source, Object target, Class<?> targetType, Field skipField) {
        MappingContext context = childContext(parentContext, source, targetType, skipField);
        Object mapped = mapNode(context, source, target, source.getClass(), targetType);
        if (context.nested()) {
            runAfterMap(context, source, mapped);
        }
        return mapped;
    }

    private void applyBackReference(Field childField, Object child, Object owner) throws IllegalAccessException {
        if (child == null || childField == null) return;
        childField.set(child, owner);
    }

    private Field backReferenceField(MappingContext context, Field ownerField, Class<?> childType, Class<?> ownerType) {
        Field explicit = context.resolved().backReferenceFields().get(ownerField);
        if (explicit != null) {
            return explicit;
        }
        if (!context.resolved().profile().isAutoBackReference()) {
            return null;
        }
        return AUTO_BACK_REFERENCES
                .computeIfAbsent(new ClassPairKey(childType, ownerType), key -> findAutoBackReference(childType, ownerType))
                .orElse(null);
    }

    private Optional<Field> findAutoBackReference(Class<?> childType, Class<?> ownerType) {
        if (resolveKind(childType) != NodeKind.OBJECT) {
            return Optional.empty();
        }
        Field found = null;
        for (Field field : getFieldsForClass(childType)) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Class<?> fieldType = field.getType();
            if (resolveKind(fieldType) != NodeKind.OBJECT) continue;
            if (!fieldType.isAssignableFrom(ownerType)) continue;
            if (found != null) {
                return Optional.empty();
            }
            found = field;
        }
        return Optional.ofNullable(found);
    }

    private Object convertValue(MappingContext context, Object value, Class<?> targetType, String element) {
        if (value == null || TypeConverter.isCompatible(value, targetType)) {
            return value;
        }

        DefaultMappingProfile profile = context.resolved().profile();
        Class<?> wrappedTarget = TypeConverter.wrap(targetType);

        try {
            @SuppressWarnings("unchecked")
            MapperConverter<Object, Object> converter = (MapperConverter<Object, Object>) profile.findTypeConverter(value.getClass(), wrappedTarget);
            if (converter != null) {
                return converter.convert(value);
            }
            if (resolveKind(targetType) != NodeKind.VALUE || targetType.isEnum()) {
                return value;
            }
            Object converted = TypeConverter.convert(value, targetType, profile.getDatePatterns());
            if (converted != TypeConverter.UNSUPPORTED) {
                return converted;
            }
            return conversionFailure(context, value, targetType, element, null);
        } catch (MappingException e) {
            throw e;
        } catch (Exception e) {
            return conversionFailure(context, value, targetType, element, e);
        }
    }

    private Object conversionFailure(MappingContext context, Object value, Class<?> targetType, String element, Exception cause) {
        if (context.resolved().profile().getConversionFailurePolicy() == ConversionFailurePolicy.SET_NULL) {
            return null;
        }
        throw new MappingException(
                "Cannot convert value of type " + value.getClass().getName()
                        + " to " + targetType.getName()
                        + " for '" + element + "'",
                cause
        );
    }

    private Object resolveFromFlatten(MappingContext context, Object source, Field targetField) throws IllegalAccessException, NoSuchFieldException {
        if (source != context.pathRoot()) {
            return MISSING;
        }
        for (String path : context.resolved().profile().getFlattenPaths()) {
            Object holder = resolveSourceValueByPath(context, path);
            if (holder == null) continue;
            Object value = resolveSourceValue(holder, resolveKind(holder.getClass()), targetField);
            if (value != MISSING) {
                return value;
            }
        }
        return MISSING;
    }

    private Object emptyValueFor(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == boolean.class) return false;
            if (type == double.class) return 0d;
            if (type == float.class) return 0f;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
            if (type == char.class) return '\0';
        }
        if (type.isArray()) {
            return Array.newInstance(type.getComponentType(), 0);
        }
        if (Collection.class.isAssignableFrom(type)) {
            return createCollectionFromType(type);
        }
        if (Map.class.isAssignableFrom(type)) {
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                return new LinkedHashMap<>();
            }
            return createInstanceForElement(type);
        }
        return null;
    }

    private void runAfterMap(MappingContext context, Object source, Object target) {
        for (BiConsumer<Object, Object> action : context.resolved().profile().getAfterMapActions()) {
            try {
                action.accept(source, target);
            } catch (MappingException e) {
                throw e;
            } catch (Exception e) {
                throw new MappingException("Error in afterMap action", e);
            }
        }
    }


    private static Field findFieldInHierarchy(Class<?> type, String fieldName) {
        if (type == null) {
            return null;
        }

        if (type.isPrimitive()) {
            return null;
        }

        Package pkg = type.getPackage();
        if (pkg != null && pkg.getName().startsWith("java.")) {
            return null;
        }

        Class<?> current = type;

        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private static void validateNavigableField(Field field, Class<?> fieldType) {

        if (fieldType.isPrimitive()) {
            throw new MappingException(
                    "Cannot navigate into primitive field: '" +
                            field.getName() + "'"
            );
        }

        Package pkg = fieldType.getPackage();
        if (pkg != null && pkg.getName().startsWith("java.")) {
            throw new MappingException(
                    "Cannot navigate into JDK type: " +
                            fieldType.getName() +
                            " (field: " + field.getName() + ")"
            );
        }
    }


    private Object mapNode(
            MappingContext context,
            Object sourceNode,
            Object targetNode,
            Class<?> sourceType,
            Class<?> targetType
    ){
        return mapNode(context, sourceNode, targetNode, sourceType, targetType, null);
    }

    private Object mapNode(
            MappingContext context,
            Object sourceNode,
            Object targetNode,
            Class<?> sourceType,
            Class<?> targetType,
            Class<?> targetTypeGeneric
    ){
        NodeKind kind = resolveKind(targetType);

        switch (kind) {
            case OBJECT -> mapObject(context, sourceNode, targetNode);
            case MAP -> mapMap(context, sourceNode, targetNode);
            case COLLECTION -> mapCollection(context, sourceNode, targetNode, targetTypeGeneric);
            case VALUE -> targetNode = assignValue(sourceNode, targetNode);
        }
        return targetNode;
    }

    private void mapObject(MappingContext context, Object source, Object target) {
        NodeKind sourceKind = resolveKind(source.getClass());
        ResolvedProfile resolved = context.resolved();

        List<Field> targetFields = getFieldsForClass(target.getClass());

        for (Field targetField : targetFields) {
            if(resolved.ignoredFields().contains(targetField)) continue;
            if(targetField.equals(context.skipField())) continue;
            try {
                Object sourceValue;
                if(resolved.customMapperFields().containsKey(targetField)) {
                    sourceValue = resolveSourceValueByPath(context, resolved.customMapperFields().get(targetField));
                }else{
                    sourceValue = resolveSourceValue(source, sourceKind, targetField);
                    if (sourceValue == MISSING) {
                        sourceValue = resolveFromFlatten(context, source, targetField);
                    }
                }

                if (sourceValue == MISSING) {
                    sourceValue = handleMissingFieldPolicy(context, targetField);
                }
                if (sourceValue == MISSING) continue;
                sourceValue = handleNullValuePolicy(context, sourceValue, targetField);
                if(resolved.converterFields().containsKey(targetField)) {
                    @SuppressWarnings("unchecked")
                    MapperConverter<Object, Object> converter = (MapperConverter<Object, Object>) resolved.converterFields().get(targetField);
                    sourceValue = converter.convert(sourceValue);
                }
                sourceValue = convertValue(context, sourceValue, targetField.getType(), targetField.getName());
                if (sourceValue == null) {
                    sourceValue = handleNullValuePolicy(context, null, targetField);
                }
                assignResolvedValue(context, sourceValue, target, targetField);
            }catch (Exception e) {
                throw new MappingException(
                        "Error mapping field: " + targetField.getName(),
                        e
                );
            }
        }
    }

    private void mapMap(MappingContext context, Object source, Object target) {
        mapMap(context, source, target, null, null);
    }

    private void mapMap(MappingContext context, Object source, Object target, Class<?> targetKeyType, Class<?> targetValueType) {
        if (source == null || target == null) return;
        if(!(target instanceof Map)) return;

        Map<Object, Object> targetMap = (Map<Object, Object>) target;

        if (source instanceof Map<?, ?> sourceMap) {
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                Object key = coerceMapKey(entry.getKey(), targetKeyType);
                Object value = mapMapValue(context, entry.getValue(), targetValueType);
                targetMap.put(key, value);
            }
        }else{
            List<Field> fields = getFieldsForClass(source.getClass());
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object raw   = field.get(source);
                    Object value = mapMapValue(context, raw, targetValueType);
                    targetMap.put(field.getName(), value);
                } catch (IllegalAccessException e) {
                    throw new MappingException(
                            "Error reading field '" + field.getName() + "' while mapping to Map", e
                    );
                }
            }
        }
    }

    private void mapCollection(MappingContext context, Object source, Object target, Class<?> targetGenericType) {
        if (source == null) return;
        Class<?> sourceClass = source.getClass();
        Class<?> targetType = target.getClass();

        try{
            if(sourceClass.isArray()){
                assignArraySourceValueRoot(context, source, target, targetGenericType);
            }else if(Collection.class.isAssignableFrom(sourceClass)){
                assignCollectionSourceValueRoot(context, source, target, targetGenericType);
            }else if(source instanceof Map<?,?> map){
                assignCollectionSourceValueRoot(context, map.values(), target, targetGenericType);
            }
        }catch (Exception e) {
            throw new MappingException(
                    "Error mapping element: " + targetType.getName(),
                    e
            );
        }

    }

    private Object assignValue(Object source, Object target) {

        return source;
    }

    private Object resolveSourceValue(Object source, NodeKind sourceKind, Field targetField) throws IllegalAccessException {

        if (sourceKind == NodeKind.OBJECT) {
            Field sourceField = findFieldInHierarchy(source.getClass(), targetField.getName());
            if (sourceField == null) return MISSING;

            sourceField.setAccessible(true);
            return sourceField.get(source);
        }

        if (sourceKind == NodeKind.MAP) {
            Map<?, ?> map = (Map<?, ?>) source;
            return map.get(targetField.getName());
        }

        return null;
    }

    private Object handleNullValuePolicy(MappingContext context, Object sourceValue, Field targetField) {

        if (sourceValue != null) {
            return sourceValue;
        }

        DefaultMappingProfile profile = context.resolved().profile();

        return switch (profile.getNullValuePolicy()) {
            case IGNORE -> null;
            case SET_DEFAULT -> {
                Supplier<?> defaultSupplier =
                        profile.getFieldDefault(targetField.getName()) != null
                                ? profile.getFieldDefault(targetField.getName())
                                : profile.getTypeDefault(targetField.getType());

                yield defaultSupplier != null ? defaultSupplier.get() : emptyValueFor(targetField.getType());
            }
            case FAIL -> throw new MappingException(
                    "Null value encountered for field '"
                            + targetField.getName()
                            + "' with NullValuePolicy.FAIL"
            );
            default -> null;
        };
    }

    private Object handleMissingFieldPolicy(MappingContext context, Field targetField) {

        DefaultMappingProfile profile = context.resolved().profile();

        return switch (profile.getMissingFieldPolicy()) {
            case IGNORE -> MISSING;

            case DEFAULT -> {
                Supplier<?> supplier = profile.getFieldDefault(targetField.getName()) != null
                                ? profile.getFieldDefault(targetField.getName())
                                : profile.getTypeDefault(targetField.getType());

                if(supplier != null){
                    yield supplier.get();
                }

                yield emptyValueFor(targetField.getType());
            }

            case FAIL -> throw new MappingException(
                    "Missing field '" + targetField.getName()
                            + "' required by target type "
                            + targetField.getDeclaringClass().getName()
            );
        };
    }

    private void assignResolvedValue(MappingContext context, Object sourceValue, Object target, Field targetField) throws IllegalAccessException {
        Class<?> fieldType = targetField.getType();
        NodeKind targetKind = resolveKind(fieldType);

        targetField.setAccessible(true);

        switch (targetKind) {

            case VALUE -> {

                if(fieldType.isEnum() && sourceValue != null){
                    Class<? extends Enum> enumClass = (Class<? extends Enum>) fieldType;

                    if (sourceValue instanceof Enum<?> sourceEnum) {
                        if (sourceEnum.getClass() != enumClass) {
                            try {
                                sourceValue = Enum.valueOf(enumClass, sourceEnum.name());
                            } catch (IllegalArgumentException e) {
                                throw new MappingException(
                                        "Não foi possível converter enum " + sourceEnum +
                                                " para o tipo " + enumClass.getName(), e
                                );
                            }
                        }
                    } else if(sourceValue instanceof String enumString){
                        try {
                            sourceValue = Enum.valueOf(enumClass, enumString);
                        } catch (IllegalArgumentException e) {
                            throw new MappingException(
                                    "Não foi possível converter " + enumString +
                                            " para o tipo " + enumClass.getName(), e
                            );
                        }
                    }else if(sourceValue instanceof Integer ordinal){
                        Enum<?>[] constants = enumClass.getEnumConstants();
                        if (ordinal >= 0 && ordinal < constants.length) {
                            sourceValue = constants[ordinal];
                        } else {
                            throw new MappingException(
                                    "Ordinal " + ordinal + " não válido para enum " + enumClass.getName()
                            );
                        }
                    }
                }

                targetField.set(target, sourceValue);
            }

            case OBJECT -> {
                if (sourceValue == null) {
                    return;
                }
                Object targetValue = targetField.get(target);
                if (targetValue == null) {
                    targetValue = createInstanceForElement(targetField.getType());
                    targetField.set(target, targetValue);
                }

                Field childField = backReferenceField(context, targetField, targetField.getType(), target.getClass());
                mapChild(context, sourceValue, targetValue, targetField.getType(), childField);
                applyBackReference(childField, targetValue, target);
            }

            case MAP -> {
                if (sourceValue == null) return;

                Object targetValue = targetField.get(target);
                if (targetValue == null) {
                    targetValue = createInstanceForElement(fieldType);
                    targetField.set(target, targetValue);
                }


                Class<?> keyType   = resolveMapGenericType(targetField, 0);
                Class<?> valueType = resolveMapGenericType(targetField, 1);

                mapMap(context, sourceValue, targetValue, keyType, valueType);

            }

            case COLLECTION -> {
                assignCollectionValue(context, sourceValue, target, targetField);
            }
        }
    }


    private void assignCollectionValue(MappingContext context, Object sourceValue, Object target, Field targetField) throws IllegalAccessException {
        if (sourceValue == null) return;
        Class<?> sourceClass = sourceValue.getClass();

        if(sourceClass.isArray()){
            assignArraySourceValue(context, sourceValue, target, targetField);
        }else if(Collection.class.isAssignableFrom(sourceClass)){
            assignCollectionSourceValue(context, sourceValue, target, targetField);
        }else{
            throw new MappingException(
                    "Source value for field '" + targetField.getName() + "' is neither an array nor a collection. Found type: " + sourceClass.getName()
            );
        }

    }



    private void assignArraySourceValue(MappingContext context, Object sourceValue, Object target, Field targetField) throws IllegalAccessException {
        if (sourceValue == null) return;

        int length = Array.getLength(sourceValue);
        List<Object> sourceCollection = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            sourceCollection.add(Array.get(sourceValue, i));
        }

        assignCollectionSourceValue(context, sourceCollection, target, targetField);
    }

    private void assignCollectionSourceValue(MappingContext context, Object sourceValue, Object target, Field targetField) throws IllegalAccessException {
        if (!(sourceValue instanceof Collection<?> sourceCollection)) return;

        Class<?> targetType = targetField.getType();
        Class<?> targetComponentType = targetType.isArray()
                ? targetType.getComponentType()
                : getFirstParameterizedType(targetField);

        NodeKind sourceElementKind = resolveKind(targetComponentType);
        Field childField = backReferenceField(context, targetField, targetComponentType, target.getClass());


        if (targetType.isArray()) {
            Object targetArray = Array.newInstance(targetComponentType, sourceCollection.size());
            int i = 0;
            for (Object elem : sourceCollection) {
                if (elem != null) {
                    Object mappedElem = mapCollectionElement(context, elem, sourceElementKind, targetComponentType, childField);
                    if (mappedElem == null) continue;
                    applyBackReference(childField, mappedElem, target);
                    Array.set(targetArray, i++, mappedElem);
                }
            }
            targetField.set(target, targetArray);
            return;
        }

        if (Collection.class.isAssignableFrom(targetType)) {
            Collection<Object> targetCollection = createCollectionFromType(targetType);
            for (Object elem : sourceCollection) {
                if (elem != null) {
                    Object mappedElem = mapCollectionElement(context, elem, sourceElementKind, targetComponentType, childField);
                    if (mappedElem == null) continue;
                    applyBackReference(childField, mappedElem, target);
                    targetCollection.add(mappedElem);
                }
            }
            targetField.set(target, targetCollection);
            return;
        }

        throw new MappingException("Target field is neither array nor collection: " + targetField.getName());
    }



    private void assignArraySourceValueRoot(MappingContext context, Object sourceValue, Object target, Class<?> targetComponentType){
        if (sourceValue == null) return;

        int length = Array.getLength(sourceValue);
        List<Object> sourceCollection = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            sourceCollection.add(Array.get(sourceValue, i));
        }

        assignCollectionSourceValueRoot(context, sourceCollection, target, targetComponentType);
    }

    private void assignCollectionSourceValueRoot(MappingContext context, Object sourceValue, Object target, Class<?> targetComponentType) {
        if (!(sourceValue instanceof Collection<?> sourceCollection)) return;

        Class<?> targetType = target.getClass();
        NodeKind sourceElementKind = resolveKind(targetComponentType);

        if (targetType.isArray()) {
            int i = 0;
            for (Object elem : sourceCollection) {
                if (elem != null) {
                    Object mappedElem = mapCollectionElement(context, elem, sourceElementKind, targetComponentType, null);
                    if (sourceElementKind != NodeKind.VALUE) runAfterMap(context, elem, mappedElem);
                    Array.set(target, i++, mappedElem);
                }
            }
            return;
        }

        if (Collection.class.isAssignableFrom(targetType)) {
            Collection<Object> targetCollection = (Collection<Object>) target;
            for (Object elem : sourceCollection) {
                if (elem != null) {
                    Object mappedElem = mapCollectionElement(context, elem, sourceElementKind, targetComponentType, null);
                    if (mappedElem == null) continue;
                    if (sourceElementKind != NodeKind.VALUE) runAfterMap(context, elem, mappedElem);
                    targetCollection.add(mappedElem);
                }
            }
            return;
        }

    }




    private Object mapCollectionElement(MappingContext context, Object elem, NodeKind elementKind, Class<?> targetComponentType, Field skipField) {
        if (elementKind == NodeKind.VALUE) {
            return convertValue(context, elem, targetComponentType, targetComponentType.getSimpleName() + " element");
        }

        Object instanceObj = createInstanceForElement(targetComponentType);
        return mapChild(context, elem, instanceObj, targetComponentType, skipField);
    }


    private <T> T createInstanceForElement(Class<T> targetType){
        try{
            if(Collection.class.isAssignableFrom(targetType)){
                return targetType.cast(createCollectionFromType(targetType));
            }else if(Map.class.isAssignableFrom(targetType)){
                return targetType.cast(new ConcurrentHashMap<>());
            }
            return targetType.getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            throw new MappingException(
                    "Constructor of target type threw an exception: "
                            + targetType.getName(),
                    e.getCause()
            );
        } catch (InstantiationException e) {
            throw new MappingException(
                    "Target type cannot be instantiated (is it abstract or an interface?): "
                            + targetType.getName(),
                    e
            );
        } catch (IllegalAccessException e) {
            throw new MappingException(
                    "No-args constructor is not accessible for target type: " + targetType.getName(),
                    e
            );
        } catch (NoSuchMethodException e) {
            throw new MappingException(
                    "Target type does not have a no-args constructor: " + targetType.getName(),
                    e
            );
        }
    }

    private static NodeKind resolveKind(Class<?> type) {

        if (Map.class.isAssignableFrom(type)) {
            return NodeKind.MAP;
        }

        if (Iterable.class.isAssignableFrom(type) || type.isArray()) {
            return NodeKind.COLLECTION;
        }

        if (
                type.isPrimitive() ||
                type.isEnum() ||
                type.getPackage() != null &&
                type.getPackage().getName().startsWith("java.")
        ) {
            return NodeKind.VALUE;
        }

        return NodeKind.OBJECT;
    }

    private List<Field> getFieldsForClass(Class<?> type) {
        return CLASS_FIELD_CACHE.computeIfAbsent(type, clazz -> {
            List<Field> fields = new ArrayList<>();
            Class<?> current = clazz;

            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    field.setAccessible(true);
                    fields.add(field);
                }
                current = current.getSuperclass();
            }
            return fields;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> createCollectionFromType(Class<?> collectionType) {
        if (collectionType.isInterface() || Modifier.isAbstract(collectionType.getModifiers())) {
            if (List.class.isAssignableFrom(collectionType)) {
                return new ArrayList<>();
            } else if (Set.class.isAssignableFrom(collectionType)) {
                if (LinkedHashSet.class.isAssignableFrom(collectionType)) {
                    return new LinkedHashSet<>();
                } else {
                    return new HashSet<>();
                }
            } else if (Queue.class.isAssignableFrom(collectionType)) {
                if (Deque.class.isAssignableFrom(collectionType)) {
                    return new ArrayDeque<>();
                } else {
                    return new LinkedList<>();
                }
            } else if (Collection.class.isAssignableFrom(collectionType)) {
                return new ArrayList<>();
            } else {
                throw new MappingException("Unsupported collection interface: " + collectionType.getName());
            }
        }

        try {
            return (Collection<T>) collectionType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new MappingException("Cannot instantiate collection of type: " + collectionType.getName(), e);
        }
    }

    private static Class<?> getFirstParameterizedType(Field field) {
        Type genericType = field.getGenericType();
        return getFirstParameterizedType(genericType, field.getName());
    }

    private static Class<?> getFirstParameterizedType(Type genericType, String element){
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type[] typeArgs = parameterizedType.getActualTypeArguments();
            if (typeArgs.length > 0) {
                Type firstArg = typeArgs[0];

                if (firstArg instanceof Class<?> clazz) {
                    return clazz;
                } else if (firstArg instanceof ParameterizedType nestedParamType) {
                    return (Class<?>) nestedParamType.getRawType();
                }
            }
        }

        throw new MappingException(
                "Cannot determine parameterized type for element: " + element
        );
    }

    private Object resolveSourceValueByPath(MappingContext context, String sourcePath) throws IllegalAccessException, NoSuchFieldException {
        if (sourcePath.startsWith(ROOT_PATH_PREFIX)) {
            return resolveSourceValueByPath(context.rootSource(), sourcePath.substring(ROOT_PATH_PREFIX.length()));
        }
        return resolveSourceValueByPath(context.pathRoot(), sourcePath);
    }

    private Object resolveSourceValueByPath(Object source, String sourcePath) throws IllegalAccessException, NoSuchFieldException {
        if (source == null) return null;

        String[] parts = sourcePath.split("\\.");
        Object current = source;
        NodeKind sourceRootKind = resolveKind(source.getClass());
        if (sourceRootKind != NodeKind.MAP && sourceRootKind != NodeKind.OBJECT) {
            throw new MappingException(
                    "Cannot access property path '" + sourcePath + "' on unsupported source type: "
                            + (source != null ? source.getClass().getName() : "null")
            );
        }


        for (String part : parts) {
            if (current == null) return null;

            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            }else {
                Field field = findFieldInHierarchy(current.getClass(), part);
                if (field == null) {
                    throw new MappingException(
                            "Field '" + part + "' not found in class " + current.getClass().getName()
                    );
                }
                field.setAccessible(true);
                current = field.get(current);
            }
        }

        return current;
    }

    private Object mapMapValue(MappingContext context, Object value, Class<?> targetValueType) {
        if (value == null) return null;

        Class<?> effectiveType = (targetValueType != null) ? targetValueType : value.getClass();
        NodeKind kind = resolveKind(effectiveType);

        return switch (kind) {
            case VALUE -> value;

            case OBJECT -> {
                Object instance = createInstanceForElement(effectiveType);
                yield mapChild(context, value, instance, effectiveType, null);
            }

            case COLLECTION -> {
                Collection<Object> col = createCollectionFromType(effectiveType);
                mapCollection(context, value, col, null);
                yield col;
            }

            case MAP -> {
                Map<Object, Object> nested = new ConcurrentHashMap<>();
                mapMap(context, value, nested, null, null);
                yield nested;
            }
        };
    }

    private Object coerceMapKey(Object key, Class<?> targetKeyType) {
        if (key == null || targetKeyType == null || targetKeyType.isInstance(key)) return key;

        String raw = key.toString();
        try {
            if (targetKeyType == Integer.class || targetKeyType == int.class)    return Integer.parseInt(raw);
            if (targetKeyType == Long.class    || targetKeyType == long.class)   return Long.parseLong(raw);
            if (targetKeyType == Double.class  || targetKeyType == double.class) return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new MappingException(
                    "Cannot coerce map key '" + raw + "' to " + targetKeyType.getName(), e
            );
        }
        return key;
    }

    private Class<?> resolveMapGenericType(Field field, int argIndex) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > argIndex) {
                Type arg = args[argIndex];
                if (arg instanceof Class<?> c) return c;
                if (arg instanceof ParameterizedType nested) return (Class<?>) nested.getRawType();
            }
        }
        return null;
    }

    private record MappingContext(ResolvedProfile resolved, Object rootSource, Object pathRoot, Field skipField, boolean nested) {
        static MappingContext root(ResolvedProfile resolved, Object source) {
            return new MappingContext(resolved, source, source, null, false);
        }
    }

    private record ResolvedKey(DefaultMappingProfile profile, Class<?> targetType) {}

    private record GlobalEntry(DefaultMappingProfile profile, ClassPairKey owner) {}

    private static final class ResolvedProfile {

        private final DefaultMappingProfile profile;
        private final Set<Field> ignoredFields = new HashSet<>();
        private final Map<Field, String> customMapperFields = new HashMap<>();
        private final Map<Field, MapperConverter<?, ?>> converterFields = new HashMap<>();
        private final Map<Field, Field> backReferenceFields = new HashMap<>();

        ResolvedProfile(DefaultMappingProfile profile, Class<?> target) {
            this.profile = profile;
            profile.getIgnoredFields().forEach(path -> ignoredFields.add(resolveTargetFieldPath(path, target, "Ignored field")));
            profile.getMappings().forEach((path, sourcePath) -> customMapperFields.put(resolveTargetFieldPath(path, target, "Mapped field"), sourcePath));
            profile.getFieldConverters().forEach((path, converter) -> converterFields.put(resolveTargetFieldPath(path, target, "Converter field"), converter));
            profile.getBackReferences().forEach((path, childFieldName) -> resolveBackReference(path, childFieldName, target));
        }

        DefaultMappingProfile profile() {
            return profile;
        }

        Set<Field> ignoredFields() {
            return ignoredFields;
        }

        Map<Field, String> customMapperFields() {
            return customMapperFields;
        }

        Map<Field, MapperConverter<?, ?>> converterFields() {
            return converterFields;
        }

        Map<Field, Field> backReferenceFields() {
            return backReferenceFields;
        }

        private void resolveBackReference(String ownerFieldRaw, String childFieldName, Class<?> target) {
            Field ownerField = resolveTargetFieldPath(ownerFieldRaw, target, "Back reference field");
            Class<?> ownerFieldType = ownerField.getType();

            Class<?> childType;
            if (ownerFieldType.isArray()) {
                childType = ownerFieldType.getComponentType();
            } else if (Collection.class.isAssignableFrom(ownerFieldType)) {
                childType = getFirstParameterizedType(ownerField);
            } else {
                childType = ownerFieldType;
            }

            Field childField = findFieldInHierarchy(childType, childFieldName);
            if (childField == null) {
                throw new MappingException(
                        "Back reference field not found: '" + childFieldName +
                                "' in type " + childType.getName() +
                                " (declared for '" + ownerFieldRaw + "')"
                );
            }

            Class<?> ownerType = ownerField.getDeclaringClass();
            if (!childField.getType().isAssignableFrom(ownerType)) {
                throw new MappingException(
                        "Back reference field '" + childFieldName +
                                "' in type " + childType.getName() +
                                " has type " + childField.getType().getName() +
                                " which cannot hold " + ownerType.getName()
                );
            }

            childField.setAccessible(true);
            backReferenceFields.put(ownerField, childField);
        }

        private static Field resolveTargetFieldPath(String fieldNameRaw, Class<?> target, String label) {
            String[] parts = fieldNameRaw.split("\\.");

            Class<?> currentType = target;
            Field field = null;

            StringBuilder resolvedPath = new StringBuilder(target.getName());
            Iterator<String> iter = Arrays.asList(parts).iterator();

            while (iter.hasNext()) {
                String part = iter.next();
                field = findFieldInHierarchy(currentType, part);
                if (field == null) {
                    throw new MappingException(
                            label + " not found: '" + part +
                                    "' while resolving path '" + fieldNameRaw +
                                    "' starting from type " + resolvedPath
                    );
                }

                field.setAccessible(true);
                Class<?> fieldType = field.getType();

                if (iter.hasNext()) {
                    validateNavigableField(field, fieldType);
                }

                resolvedPath.append(".").append(part);
                currentType = fieldType;
            }

            return field;
        }
    }

}
