package dtm.mapper;

import dtm.mapper.exceptions.MappingException;

import java.util.Collection;

public interface AutoMapper {
    <T> T map(Object source, Class<T> targetType) throws MappingException;
    <T extends Collection<?>> T map(Object source, CollectionReference<T> targetType) throws MappingException;
}
