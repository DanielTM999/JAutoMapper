package dtm.mapper;

@FunctionalInterface
public interface MapperConverter<S, T> {
    T convert(S source);
}