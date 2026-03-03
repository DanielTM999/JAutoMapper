package dtm.mapper;

import dtm.mapper.service.AutoMapperService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AutoMapperTest {

    public static class PersonBase {
        protected String id;
    }

    public static class Address {
        String street;
    }

    public static class Person extends PersonBase {
        String name;
        Address address;
    }

    public static class PersonWithTags extends PersonBase {
        String name;
        List<String> tags;
    }


    @Test
    void shouldRunMappingFlowWithoutErrors() {
        AutoMapper mapper = AutoMapperService.register(
                Person.class,
                Person.class
        );

        Person source = new Person();
        source.name = "Daniel";
        source.address = new Address();
        source.address.street = "Rua X";

        Person target = assertDoesNotThrow(() ->
                mapper.map(source, Person.class)
        );

        assertNotNull(target);
    }

    @Test
    void shouldMapMapToObjectWithNestedMap() {
        AutoMapper mapper = AutoMapperService.register(
                Map.class,
                Person.class
        );

        Map<String, Object> source = new HashMap<>();
        source.put("id", "1");
        source.put("name", "Daniel");

        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("street", "Rua X");

        source.put("address", addressMap);

        Person target = mapper.map(source, Person.class);

        assertNotNull(target);

        assertEquals("1", target.id);
        assertEquals("Daniel", target.name);

        assertNotNull(target.address);
        assertEquals("Rua X", target.address.street);
    }

    @Test
    void shouldMapMapWithListToObject() {
        AutoMapper mapper = AutoMapperService.register(
                Map.class,
                PersonWithTags.class
        );

        Map<String, Object> source = new HashMap<>();
        source.put("id", "42");
        source.put("name", "Alice");

        source.put("tags", new String[]{"tag1", "tag2", "tag3"});

        PersonWithTags target = mapper.map(source, PersonWithTags.class);

        assertNotNull(target);
        assertEquals("42", target.id);
        assertEquals("Alice", target.name);

        assertNotNull(target.tags);
        assertEquals(3, target.tags.size());
        assertTrue(target.tags.contains("tag1"));
        assertTrue(target.tags.contains("tag2"));
        assertTrue(target.tags.contains("tag3"));
    }

    @Test
    void shouldMapMapWithListCollectionsToObject() {
        AutoMapper mapper = AutoMapperService.register(
                Map.class,
                PersonWithTags.class
        );

        Map<String, Object> source = new HashMap<>();
        source.put("id", "42");
        source.put("name", "Alice");

        source.put("tags", new ArrayList<>(List.of("tag1", "tag2", "tag3")));

        PersonWithTags target = mapper.map(source, PersonWithTags.class);

        assertNotNull(target);
        assertEquals("42", target.id);
        assertEquals("Alice", target.name);

        assertNotNull(target.tags);
        assertEquals(3, target.tags.size());
        assertTrue(target.tags.contains("tag1"));
        assertTrue(target.tags.contains("tag2"));
        assertTrue(target.tags.contains("tag3"));
    }

    @Test
    void shouldMapMapValuesToCollectionIgnoringKeys() {
        AutoMapper mapper = AutoMapperService.register(
                Map.class,
                List.class
        );

        Map<String, Object> source = new HashMap<>();
        source.put("first", "Alice");
        source.put("second", "Bob");
        source.put("third", "Charlie");

        List<String> target = mapper.map(source, new CollectionReference<List<String>>(){});

        assertNotNull(target);
        assertEquals(3, target.size());
        assertTrue(target.contains("Alice"));
        assertTrue(target.contains("Bob"));
        assertTrue(target.contains("Charlie"));
    }

    @Test
    void shouldMapArrayValuesToCollection() {
        AutoMapper mapper = AutoMapperService.register(
                String[].class,
                List.class
        );

        String[] source = new String[]{"Alice", "Bob", "Charlie"};

        List<String> target = mapper.map(source, new CollectionReference<List<String>>() {});

        assertNotNull(target);
        assertEquals(3, target.size());
        assertTrue(target.contains("Alice"));
        assertTrue(target.contains("Bob"));
        assertTrue(target.contains("Charlie"));
    }


}
