package dtm.mapper.imple;

public record ClassPairKey(Class<?> source, Class<?> target) {

    public String describe() {
        return source.getName() + " -> " + target.getName();
    }
}
