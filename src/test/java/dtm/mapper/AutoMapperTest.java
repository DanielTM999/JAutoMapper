package dtm.mapper;

import dtm.mapper.enums.MissingFieldPolicy;
import dtm.mapper.enums.NestedScope;
import dtm.mapper.enums.NullValuePolicy;
import dtm.mapper.exceptions.MappingException;
import dtm.mapper.service.AutoMapperService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    public enum Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    public static class User {
        String id;
        String name;
        Status status;
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

    @Test
    void shouldMapSimpleField() {
        AutoMapper mapper = AutoMapperService.register(
                Map.class,
                Person.class,
                profile -> {
                    profile.map("name", "name");
                }
        );

        Map<String, Object> source = new HashMap<>();
        source.put("name", "Daniel");

        Person target = mapper.map(source, Person.class);

        assertNotNull(target);
        assertEquals("Daniel", target.name);
    }

    @Test
    void shouldMapNestedField() {
        AutoMapper mapper = AutoMapperService.register(
                Map.class,
                Person.class,
                profile -> {
                    profile.map("address.street", "address.street"); // caminho aninhado
                }
        );

        Map<String, Object> source = new HashMap<>();
        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("street", "Rua X");
        source.put("address", addressMap);

        Person target = mapper.map(source, Person.class);

        assertNotNull(target);
        assertNotNull(target.address);
        assertEquals("Rua X", target.address.street);
    }

    @Test
    void shouldMapEnumFromMap() {
        // registra o mapeamento
        AutoMapper mapper = AutoMapperService.register(
                Map.class,
                User.class
        );

        Map<String, Object> source = new HashMap<>();
        source.put("id", "100");
        source.put("name", "Daniel");
        source.put("status", "ACTIVE");

        // faz o mapeamento
        User target = mapper.map(source, User.class);

        // asserts
        assertNotNull(target);
        assertEquals("100", target.id);
        assertEquals("Daniel", target.name);
        assertEquals(Status.ACTIVE, target.status);
    }

    @Test
    void shouldMapEnumDirectly() {
        AutoMapper mapper = AutoMapperService.register(
                User.class,
                User.class
        );

        User source = new User();
        source.id = "101";
        source.name = "Alice";
        source.status = Status.PENDING;

        User target = mapper.map(source, User.class);

        assertNotNull(target);
        assertEquals("101", target.id);
        assertEquals("Alice", target.name);
        assertEquals(Status.PENDING, target.status);
    }

    public static class ItemSource {
        String name;

        ItemSource() {}

        ItemSource(String name) {
            this.name = name;
        }
    }

    public static class OrderSource {
        String code;
        List<ItemSource> items;
        ItemSource[] itemArray;
        ItemSource main;
    }

    public static class Item {
        String name;
        Order order;
    }

    public static class Order {
        String code;
        List<Item> items;
        Item[] itemArray;
        Item main;
    }

    public static class WrongItem {
        String name;
        String order;
    }

    public static class WrongOrder {
        String code;
        List<WrongItem> items;
    }

    private static OrderSource orderSourceWithItems() {
        OrderSource source = new OrderSource();
        source.code = "PED-1";
        source.items = new ArrayList<>(List.of(new ItemSource("A"), new ItemSource("B")));
        return source;
    }

    @Test
    void shouldSetBackReferenceOnCollectionElements() {
        AutoMapper mapper = AutoMapperService.register(
                OrderSource.class,
                Order.class,
                profile -> profile.backReference("items", "order")
        );

        Order target = mapper.map(orderSourceWithItems(), Order.class);

        assertEquals(2, target.items.size());
        assertEquals("A", target.items.get(0).name);
        for (Item item : target.items) {
            assertSame(target, item.order);
        }
    }

    @Test
    void shouldSetBackReferenceOnSingleObject() {
        AutoMapper mapper = AutoMapperService.register(
                OrderSource.class,
                Order.class,
                profile -> profile.backReference("main", "order")
        );

        OrderSource source = new OrderSource();
        source.main = new ItemSource("Principal");

        Order target = mapper.map(source, Order.class);

        assertNotNull(target.main);
        assertEquals("Principal", target.main.name);
        assertSame(target, target.main.order);
    }

    @Test
    void shouldSetBackReferenceOnArrayElements() {
        AutoMapper mapper = AutoMapperService.register(
                OrderSource.class,
                Order.class,
                profile -> profile.backReference("itemArray", "order")
        );

        OrderSource source = new OrderSource();
        source.itemArray = new ItemSource[]{new ItemSource("X"), new ItemSource("Y")};

        Order target = mapper.map(source, Order.class);

        assertEquals(2, target.itemArray.length);
        for (Item item : target.itemArray) {
            assertSame(target, item.order);
        }
    }

    @Test
    void shouldSetBackReferenceWhenChildrenComeFromConverter() {
        AutoMapper mapper = AutoMapperService.register(
                OrderSource.class,
                Order.class,
                profile -> profile
                        .convertField("items", (List<ItemSource> items) -> {
                            List<Item> converted = new ArrayList<>();
                            for (ItemSource itemSource : items) {
                                Item item = new Item();
                                item.name = itemSource.name.toLowerCase();
                                converted.add(item);
                            }
                            return converted;
                        })
                        .backReference("items", "order")
        );

        Order target = mapper.map(orderSourceWithItems(), Order.class);

        assertEquals("a", target.items.get(0).name);
        for (Item item : target.items) {
            assertSame(target, item.order);
        }
    }

    @Test
    void shouldFailWhenBackReferenceFieldDoesNotExist() {
        AutoMapper mapper = AutoMapperService.register(
                OrderSource.class,
                Order.class,
                profile -> profile.backReference("items", "parent")
        );

        assertThrows(MappingException.class, () -> mapper.map(orderSourceWithItems(), Order.class));
    }

    @Test
    void shouldFailWhenBackReferenceFieldHasIncompatibleType() {
        AutoMapper mapper = AutoMapperService.register(
                OrderSource.class,
                WrongOrder.class,
                profile -> profile.backReference("items", "order")
        );

        assertThrows(MappingException.class, () -> mapper.map(orderSourceWithItems(), WrongOrder.class));
    }

    @Test
    void shouldRunAfterMapWithSourceAndTarget() {
        List<Object[]> calls = new ArrayList<>();
        AutoMapper mapper = AutoMapperService.register(
                OrderSource.class,
                Order.class,
                profile -> profile
                        .backReference("items", "order")
                        .afterMap((OrderSource s, Order t) -> {
                            calls.add(new Object[]{s, t});
                            t.code = s.code + "-OK";
                        })
        );

        OrderSource source = orderSourceWithItems();
        Order target = mapper.map(source, Order.class);

        assertEquals(1, calls.size());
        assertSame(source, calls.get(0)[0]);
        assertSame(target, calls.get(0)[1]);
        assertEquals("PED-1-OK", target.code);
    }

    @Test
    void shouldRunAfterMapForEachElementOfCollection() {
        List<String> visited = new ArrayList<>();
        AutoMapper mapper = AutoMapperService.register(
                List.class,
                List.class,
                profile -> profile
                        .missingFieldPolicy(MissingFieldPolicy.IGNORE)
                        .afterMap((ItemSource s, Item t) -> {
                            visited.add(s.name);
                            t.name = t.name + "!";
                        })
        );

        List<ItemSource> source = new ArrayList<>(List.of(new ItemSource("A"), new ItemSource("B")));
        List<Item> target = mapper.map(source, new CollectionReference<List<Item>>() {});

        assertEquals(List.of("A", "B"), visited);
        assertEquals("A!", target.get(0).name);
        assertEquals("B!", target.get(1).name);
    }

    @Test
    void shouldWrapAfterMapExceptions() {
        AutoMapper mapper = AutoMapperService.register(
                OrderSource.class,
                Order.class,
                profile -> profile.afterMap((OrderSource s, Order t) -> {
                    throw new IllegalStateException("boom");
                })
        );

        MappingException ex = assertThrows(MappingException.class, () -> mapper.map(new OrderSource(), Order.class));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    public static class TagSource {
        String label;

        TagSource() {}

        TagSource(String label) {
            this.label = label;
        }
    }

    public static class Tag {
        String label;
        Line line;
    }

    public static class LineSource {
        String descricao;
        String qtd;
        List<TagSource> tags;

        LineSource() {}

        LineSource(String descricao, String qtd) {
            this.descricao = descricao;
            this.qtd = qtd;
        }
    }

    public static class Line {
        String description;
        int quantity;
        String note;
        Invoice invoice;
        List<Tag> tags;
    }

    public static class InvoiceSource {
        String number;
        List<LineSource> lines;
        LineSource highlight;
        Map<String, LineSource> byCode;
    }

    public static class Invoice {
        String number;
        List<Line> lines;
        Line highlight;
        Map<String, Line> byCode;
    }

    private static void lineProfile(MappingProfile profile) {
        profile.map("descricao", "description")
                .map("qtd", "quantity")
                .convertField("quantity", (String qtd) -> (qtd != null) ? Integer.parseInt(qtd) : 0)
                .ignore("note")
                .ignore("invoice");
    }

    private static InvoiceSource invoiceSource() {
        InvoiceSource source = new InvoiceSource();
        source.number = "NF-1";
        source.lines = new ArrayList<>(List.of(new LineSource("Caneta", "2"), new LineSource("Lapis", "5")));
        return source;
    }

    @Test
    void shouldApplyNestedProfileToListElements() {
        AutoMapper mapper = AutoMapperService.register(
                InvoiceSource.class,
                Invoice.class,
                profile -> profile.nested(LineSource.class, Line.class, AutoMapperTest::lineProfile)
        );

        Invoice target = mapper.map(invoiceSource(), Invoice.class);

        assertEquals("NF-1", target.number);
        assertEquals(2, target.lines.size());
        assertEquals("Caneta", target.lines.get(0).description);
        assertEquals(2, target.lines.get(0).quantity);
        assertEquals("Lapis", target.lines.get(1).description);
        assertEquals(5, target.lines.get(1).quantity);
        assertNull(target.lines.get(0).note);
    }

    @Test
    void shouldApplyNestedProfileToObjectFieldAndMapValues() {
        AutoMapper mapper = AutoMapperService.register(
                InvoiceSource.class,
                Invoice.class,
                profile -> profile.nested(LineSource.class, Line.class, AutoMapperTest::lineProfile)
        );

        InvoiceSource source = new InvoiceSource();
        source.highlight = new LineSource("Destaque", "9");
        source.byCode = new HashMap<>(Map.of("A1", new LineSource("Borracha", "3")));

        Invoice target = mapper.map(source, Invoice.class);

        assertEquals("Destaque", target.highlight.description);
        assertEquals(9, target.highlight.quantity);
        assertEquals("Borracha", target.byCode.get("A1").description);
        assertEquals(3, target.byCode.get("A1").quantity);
    }

    @Test
    void shouldApplyNestedProfileToConverterResult() {
        AutoMapper mapper = AutoMapperService.register(
                InvoiceSource.class,
                Invoice.class,
                profile -> profile
                        .map("lines", "highlight")
                        .convertField("highlight", (List<LineSource> lines) -> lines.get(lines.size() - 1))
                        .nested(LineSource.class, Line.class, AutoMapperTest::lineProfile)
        );

        Invoice target = mapper.map(invoiceSource(), Invoice.class);

        assertEquals("Lapis", target.highlight.description);
        assertEquals(5, target.highlight.quantity);
    }

    @Test
    void shouldInheritParentPoliciesInNestedProfile() {
        AutoMapper mapper = AutoMapperService.register(
                InvoiceSource.class,
                Invoice.class,
                profile -> profile
                        .missingFieldPolicy(MissingFieldPolicy.DEFAULT)
                        .nullValuePolicy(NullValuePolicy.SET_DEFAULT)
                        .defaultValue(String.class, () -> "N/A")
                        .nested(LineSource.class, Line.class, nested -> nested
                                .map("descricao", "description")
                                .ignore("quantity")
                                .ignore("invoice")
                                .ignore("tags"))
        );

        InvoiceSource source = new InvoiceSource();
        source.lines = new ArrayList<>(List.of(new LineSource(null, "1")));

        Invoice target = mapper.map(source, Invoice.class);

        assertEquals("N/A", target.number);
        assertEquals("N/A", target.lines.get(0).description);
        assertEquals("N/A", target.lines.get(0).note);
    }

    @Test
    void shouldOverrideInheritedDefaultsInNestedProfile() {
        AutoMapper mapper = AutoMapperService.register(
                InvoiceSource.class,
                Invoice.class,
                profile -> profile
                        .missingFieldPolicy(MissingFieldPolicy.DEFAULT)
                        .nullValuePolicy(NullValuePolicy.SET_DEFAULT)
                        .defaultValue(String.class, () -> "N/A")
                        .nested(LineSource.class, Line.class, nested -> nested
                                .defaultValue(String.class, () -> "LINHA")
                                .ignore("quantity")
                                .ignore("invoice")
                                .ignore("tags"))
        );

        InvoiceSource source = new InvoiceSource();
        source.lines = new ArrayList<>(List.of(new LineSource("Caneta", "1")));

        Invoice target = mapper.map(source, Invoice.class);

        assertEquals("N/A", target.number);
        assertEquals("LINHA", target.lines.get(0).note);
        assertEquals("LINHA", target.lines.get(0).description);
    }

    @Test
    void shouldSupportBackReferenceAfterMapAndDeepNestingInNestedProfiles() {
        AutoMapper mapper = AutoMapperService.register(
                InvoiceSource.class,
                Invoice.class,
                profile -> profile
                        .backReference("lines", "invoice")
                        .nested(LineSource.class, Line.class, line -> line
                                .map("descricao", "description")
                                .map("qtd", "quantity")
                                .convertField("quantity", (String qtd) -> Integer.parseInt(qtd))
                                .ignore("note")
                                .backReference("tags", "line")
                                .afterMap((LineSource s, Line l) -> l.note = s.descricao + "#" + s.qtd)
                                .nested(TagSource.class, Tag.class, tag -> tag
                                        .afterMap((TagSource s, Tag t) -> t.label = s.label.toUpperCase())))
        );

        InvoiceSource source = new InvoiceSource();
        LineSource lineSource = new LineSource("Caneta", "2");
        lineSource.tags = new ArrayList<>(List.of(new TagSource("azul"), new TagSource("bic")));
        source.lines = new ArrayList<>(List.of(lineSource));

        Invoice target = mapper.map(source, Invoice.class);

        Line line = target.lines.get(0);
        assertSame(target, line.invoice);
        assertEquals("Caneta#2", line.note);
        assertEquals(2, line.tags.size());
        assertEquals("AZUL", line.tags.get(0).label);
        assertEquals("BIC", line.tags.get(1).label);
        for (Tag tag : line.tags) {
            assertSame(line, tag.line);
        }
    }

    public static class GSrc {
        String codigo;

        GSrc() {}

        GSrc(String codigo) {
            this.codigo = codigo;
        }
    }

    public static class GTgt {
        String code;
    }

    public static class GHolderSource {
        GSrc item;
        List<GSrc> items;
    }

    public static class GHolder {
        GTgt item;
        List<GTgt> items;
    }

    public static class GlobalOwnerA {}

    private static void registerGlobalOwnerA() {
        AutoMapperService.register(
                GlobalOwnerA.class,
                GlobalOwnerA.class,
                profile -> profile.nested(GSrc.class, GTgt.class, nested -> nested.map("codigo", "code"), NestedScope.GLOBAL)
        );
    }

    private static GHolderSource holderSource() {
        GHolderSource source = new GHolderSource();
        source.item = new GSrc("X1");
        source.items = new ArrayList<>(List.of(new GSrc("X2")));
        return source;
    }

    @Test
    void shouldUseGlobalNestedProfileFromOtherMapper() {
        registerGlobalOwnerA();
        AutoMapper mapper = AutoMapperService.register(GHolderSource.class, GHolder.class);

        GHolder target = mapper.map(holderSource(), GHolder.class);

        assertEquals("X1", target.item.code);
        assertEquals("X2", target.items.get(0).code);
    }

    @Test
    void shouldPreferLocalNestedProfileOverGlobal() {
        registerGlobalOwnerA();
        AutoMapper mapper = AutoMapperService.register(
                GHolderSource.class,
                GHolder.class,
                profile -> profile.nested(GSrc.class, GTgt.class, nested -> nested
                        .map("codigo", "code")
                        .convertField("code", (String code) -> "local-" + code))
        );

        GHolder target = mapper.map(holderSource(), GHolder.class);

        assertEquals("local-X1", target.item.code);
        assertEquals("local-X2", target.items.get(0).code);
    }

    public static class PSrc {
        String a;
    }

    public static class PTgt {
        String b;
    }

    public static class QSrc {
        String a;
    }

    public static class QTgt {
        String b;
    }

    public static class PHolderSource {
        PSrc p;
        QSrc q;
    }

    public static class PHolder {
        PTgt p;
        QTgt q;
    }

    public static class GlobalOwnerB {}

    @Test
    void shouldUseParentNestedScopeAndKeepExplicitLocalOverride() {
        AutoMapperService.register(
                GlobalOwnerB.class,
                GlobalOwnerB.class,
                profile -> profile
                        .nestedScope(NestedScope.GLOBAL)
                        .nested(PSrc.class, PTgt.class, nested -> nested.map("a", "b"))
                        .nested(QSrc.class, QTgt.class, nested -> nested.map("a", "b"), NestedScope.LOCAL)
        );

        AutoMapper mapper = AutoMapperService.register(
                PHolderSource.class,
                PHolder.class,
                profile -> profile.missingFieldPolicy(MissingFieldPolicy.IGNORE)
        );

        PHolderSource source = new PHolderSource();
        source.p = new PSrc();
        source.p.a = "pa";
        source.q = new QSrc();
        source.q.a = "qa";

        PHolder target = mapper.map(source, PHolder.class);

        assertEquals("pa", target.p.b);
        assertNull(target.q.b);
    }

    public static class CSrc {}

    public static class CTgt {}

    public static class GlobalOwnerC {}

    public static class GlobalOwnerD {}

    @Test
    void shouldFailWhenGlobalNestedProfileIsDeclaredByAnotherOwner() {
        Runnable registerOwnerC = () -> AutoMapperService.register(
                GlobalOwnerC.class,
                GlobalOwnerC.class,
                profile -> profile.nested(CSrc.class, CTgt.class, nested -> {}, NestedScope.GLOBAL)
        );

        registerOwnerC.run();
        assertDoesNotThrow(registerOwnerC::run);

        MappingException ex = assertThrows(MappingException.class, () -> AutoMapperService.register(
                GlobalOwnerD.class,
                GlobalOwnerD.class,
                profile -> profile.nested(CSrc.class, CTgt.class, nested -> {}, NestedScope.GLOBAL)
        ));
        assertTrue(ex.getMessage().contains(GlobalOwnerC.class.getName()));
    }

    @Test
    void shouldResolveProfileSafelyOnConcurrentFirstMapping() throws Exception {
        AutoMapper mapper = AutoMapperService.register(
                InvoiceSource.class,
                Invoice.class,
                profile -> profile.nested(LineSource.class, Line.class, AutoMapperTest::lineProfile)
        );

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Invoice>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mapper.map(invoiceSource(), Invoice.class);
                }));
            }
            start.countDown();

            for (Future<Invoice> future : futures) {
                Invoice target = future.get(10, TimeUnit.SECONDS);
                assertEquals("Caneta", target.lines.get(0).description);
                assertEquals(5, target.lines.get(1).quantity);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
