package dtm.mapper.service;


import dtm.mapper.AutoMapper;
import dtm.mapper.CollectionReference;
import dtm.mapper.MappingProfile;
import dtm.mapper.enums.NodeKind;
import dtm.mapper.exceptions.MappingException;
import dtm.mapper.imple.DefaultMappingProfile;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutoMapperService implements AutoMapper {

    private static final Object MISSING = new Object();
    private static final Map<AutoMapperClassKey, AutoMapperService> MAPPERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> CLASS_FIELD_CACHE = new ConcurrentHashMap<>();

    private final DefaultMappingProfile mappingProfile;
    private final AtomicBoolean ignoredFieldsLoaded;
    private final Set<Field> ignoredFields;

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

        DefaultMappingProfile mappingProfile = new DefaultMappingProfile(target, source);

        if(mappingProfileConsumer != null) {
            mappingProfileConsumer.accept(mappingProfile);
        }

        return new AutoMapperService(mappingProfile);
    }

    public static AutoMapper getAutoMapper(Class<?> source, Class<?> target) {
        if (source == null) {
            throw new MappingException("Source type cannot be null");
        }

        if (target == null) {
            throw new MappingException("Target type cannot be null");
        }

        AutoMapperClassKey key = new AutoMapperClassKey(source, target);
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


    protected AutoMapperService(DefaultMappingProfile mappingProfile) {
        this.mappingProfile = mappingProfile;
        this.ignoredFieldsLoaded = new AtomicBoolean(false);
        this.ignoredFields = new HashSet<>();
    }


    @Override
    public <T> T map(Object source, Class<T> targetType) {
        validTargetType(targetType);
        validSource(source);
        if(ignoredFieldsLoaded.compareAndSet(false, true)) searchIgnorableFields(targetType);

        T target = createInstanceForElement(targetType);

        mapNode(source, target, source.getClass(), targetType);

        return target;
    }

    @Override
    public <T extends Collection<?>> T map(Object source, CollectionReference<T> collectionReferenceType) throws MappingException {
        Type targetType = collectionReferenceType.getType();
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

        Class<?> targetTypeGeneric = getFirstParameterizedType(collectionReferenceType.getType(), collectionReferenceType.getClass().getName());

        validTargetType(targetClass);
        validSource(source);
        if(ignoredFieldsLoaded.compareAndSet(false, true)) searchIgnorableFields(targetTypeGeneric);

        Collection<?> target = createCollectionFromType(targetClass);

        mapNode(source, target, source.getClass(), targetClass, targetTypeGeneric);

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

    private void searchIgnorableFields(Class<?> target) {
        Set<String> ignoredFieldsStr = mappingProfile.getIgnoredFields();

        for(String ignoredField : ignoredFieldsStr) {
            searchIgnorableFieldByName(ignoredField, target);
        }

    }

    private void searchIgnorableFieldByName(String fieldNameRaw, Class<?> target) {
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
                        "Ignored field not found: '" + part +
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

        this.ignoredFields.add(field);
    }

    private Field findFieldInHierarchy(Class<?> type, String fieldName) {
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

    private void validateNavigableField(Field field, Class<?> fieldType) {

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
            Object sourceNode,
            Object targetNode,
            Class<?> sourceType,
            Class<?> targetType
    ){
        return mapNode(sourceNode, targetNode, sourceType, targetType, null);
    }

    private Object mapNode(
            Object sourceNode,
            Object targetNode,
            Class<?> sourceType,
            Class<?> targetType,
            Class<?> targetTypeGeneric
    ){
        NodeKind kind = resolveKind(targetType);

        switch (kind) {
            case OBJECT -> mapObject(sourceNode, targetNode);
            case MAP -> mapMap(sourceNode, targetNode);
            case COLLECTION -> mapCollection(sourceNode, targetNode, targetTypeGeneric);
            case VALUE -> targetNode = assignValue(sourceNode, targetNode);
        }
        return targetNode;
    }

    private void mapObject(Object source, Object target) {
        NodeKind sourceKind = resolveKind(source.getClass());

        List<Field> targetFields = getFieldsForClass(target.getClass());

        for (Field targetField : targetFields) {
            if(ignoredFields.contains(targetField)) continue;
            try {
                Object sourceValue = resolveSourceValue(source, sourceKind, targetField);

                if (sourceValue == MISSING) {
                    sourceValue = handleMissingFieldPolicy(targetField);
                }
                if (sourceValue == MISSING) continue;
                sourceValue = handleNullValuePolicy(sourceValue, targetField);
                assignResolvedValue(sourceValue, target, targetField);
            }catch (Exception e) {
                throw new MappingException(
                        "Error mapping field: " + targetField.getName(),
                        e
                );
            }
        }
    }

    private void mapMap(Object source, Object target) {}

    private void mapCollection(Object source, Object target, Class<?> targetGenericType) {
        if (source == null) return;
        Class<?> sourceClass = source.getClass();
        Class<?> targetType = target.getClass();

        try{
            if(sourceClass.isArray()){
                assignArraySourceValueRoot(source, target, targetGenericType);
            }else if(Collection.class.isAssignableFrom(sourceClass)){
                assignCollectionSourceValueRoot(source, target, targetGenericType);
            }else if(source instanceof Map<?,?> map){
                assignCollectionSourceValueRoot(map.values(), target, targetGenericType);
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

    private Object handleNullValuePolicy(Object sourceValue, Field targetField) {

        if (sourceValue != null) {
            return sourceValue;
        }

        return switch (mappingProfile.getNullValuePolicy()) {
            case IGNORE -> null;
            case SET_DEFAULT -> {
                Supplier<?> defaultSupplier =
                        mappingProfile.getFieldDefault(targetField.getName()) != null
                                ? mappingProfile.getFieldDefault(targetField.getName())
                                : mappingProfile.getTypeDefault(targetField.getType());

                yield defaultSupplier != null ? defaultSupplier.get() : null;
            }
            case FAIL -> throw new MappingException(
                    "Null value encountered for field '"
                            + targetField.getName()
                            + "' with NullValuePolicy.FAIL"
            );
            default -> null;
        };
    }

    private Object handleMissingFieldPolicy(Field targetField) {

        return switch (mappingProfile.getMissingFieldPolicy()) {
            case IGNORE -> MISSING;

            case DEFAULT -> {
                Supplier<?> supplier = mappingProfile.getFieldDefault(targetField.getName()) != null
                                ? mappingProfile.getFieldDefault(targetField.getName())
                                : mappingProfile.getTypeDefault(targetField.getType());

                if(supplier != null){
                    yield supplier.get();
                }

                Class<?> type = targetField.getType();
                if(type.isPrimitive()){
                    if (type == int.class) yield 0;
                    if (type == long.class) yield 0L;
                    if (type == boolean.class) yield false;
                    if (type == double.class) yield 0d;
                    if (type == float.class) yield 0f;
                    if (type == short.class) yield (short) 0;
                    if (type == byte.class) yield (byte) 0;
                    if (type == char.class) yield '\0';
                }
                yield null;

            }

            case FAIL -> throw new MappingException(
                    "Missing field '" + targetField.getName()
                            + "' required by target type "
                            + targetField.getDeclaringClass().getName()
            );
        };
    }

    private void assignResolvedValue(Object sourceValue, Object target, Field targetField) throws IllegalAccessException {
        NodeKind targetKind = resolveKind(targetField.getType());

        targetField.setAccessible(true);

        switch (targetKind) {

            case VALUE -> {
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

                mapNode(
                        sourceValue,
                        targetValue,
                        sourceValue.getClass(),
                        targetField.getType()
                );
            }

            case MAP -> {
                throw new MappingException(
                        "MAP mapping not implemented yet for field: "
                                + targetField.getName()
                );
            }

            case COLLECTION -> {
                assignCollectionValue(sourceValue, target, targetField);
            }
        }
    }


    private void assignCollectionValue(Object sourceValue, Object target, Field targetField) throws IllegalAccessException {
        if (sourceValue == null) return;
        Class<?> sourceClass = sourceValue.getClass();

        if(sourceClass.isArray()){
            assignArraySourceValue(sourceValue, target, targetField);
        }else if(Collection.class.isAssignableFrom(sourceClass)){
            assignCollectionSourceValue(sourceValue, target, targetField);
        }else{
            throw new MappingException(
                    "Source value for field '" + targetField.getName() + "' is neither an array nor a collection. Found type: " + sourceClass.getName()
            );
        }

    }



    private void assignArraySourceValue(Object sourceValue, Object target, Field targetField) throws IllegalAccessException {
        if (sourceValue == null) return;

        int length = Array.getLength(sourceValue);
        List<Object> sourceCollection = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            sourceCollection.add(Array.get(sourceValue, i));
        }

        assignCollectionSourceValue(sourceCollection, target, targetField);
    }

    private void assignCollectionSourceValue(Object sourceValue, Object target, Field targetField) throws IllegalAccessException {
        if (!(sourceValue instanceof Collection<?> sourceCollection)) return;

        Class<?> targetType = targetField.getType();
        Class<?> targetComponentType = targetType.isArray()
                ? targetType.getComponentType()
                : getFirstParameterizedType(targetField);

        NodeKind sourceElementKind = resolveKind(targetComponentType);


        if (targetType.isArray()) {
            Object targetArray = Array.newInstance(targetComponentType, sourceCollection.size());
            int i = 0;
            for (Object elem : sourceCollection) {
                if (elem != null) {
                    Object mappedElem = mapCollectionElement(elem, sourceElementKind, targetComponentType);
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
                    Object mappedElem = mapCollectionElement(elem, sourceElementKind, targetComponentType);
                    targetCollection.add(mappedElem);
                }
            }
            targetField.set(target, targetCollection);
            return;
        }

        throw new MappingException("Target field is neither array nor collection: " + targetField.getName());
    }



    private void assignArraySourceValueRoot(Object sourceValue, Object target, Class<?> targetComponentType){
        if (sourceValue == null) return;

        int length = Array.getLength(sourceValue);
        List<Object> sourceCollection = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            sourceCollection.add(Array.get(sourceValue, i));
        }

        assignCollectionSourceValueRoot(sourceCollection, target, targetComponentType);
    }

    private void assignCollectionSourceValueRoot(Object sourceValue, Object target, Class<?> targetComponentType) {
        if (!(sourceValue instanceof Collection<?> sourceCollection)) return;

        Class<?> targetType = target.getClass();
        NodeKind sourceElementKind = resolveKind(targetComponentType);

        if (targetType.isArray()) {
            int i = 0;
            for (Object elem : sourceCollection) {
                if (elem != null) {
                    Object mappedElem = mapCollectionElement(elem, sourceElementKind, targetComponentType);
                    Array.set(target, i++, mappedElem);
                }
            }
            return;
        }

        if (Collection.class.isAssignableFrom(targetType)) {
            Collection<Object> targetCollection = (Collection<Object>) target;
            for (Object elem : sourceCollection) {
                if (elem != null) {
                    Object mappedElem = mapCollectionElement(elem, sourceElementKind, targetComponentType);
                    targetCollection.add(mappedElem);
                }
            }
            return;
        }

    }




    private Object mapCollectionElement(Object elem, NodeKind elementKind, Class<?> targetComponentType) {
        if (elementKind == NodeKind.VALUE) return elem;

        Object instanceObj = createInstanceForElement(targetComponentType);
        return mapNode(elem, instanceObj, elem.getClass(), targetComponentType);
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

    private NodeKind resolveKind(Class<?> type) {

        if (Map.class.isAssignableFrom(type)) {
            return NodeKind.MAP;
        }

        if (Iterable.class.isAssignableFrom(type) || type.isArray()) {
            return NodeKind.COLLECTION;
        }

        if (type.isPrimitive()
                || type.getPackage() != null && type.getPackage().getName().startsWith("java.")
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

    private Class<?> getFirstParameterizedType(Field field) {
        Type genericType = field.getGenericType();
        return getFirstParameterizedType(genericType, field.getName());
    }

    private Class<?> getFirstParameterizedType(Type genericType, String element){
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

    private record AutoMapperClassKey(Class<?> source, Class<?> target) {}

}
