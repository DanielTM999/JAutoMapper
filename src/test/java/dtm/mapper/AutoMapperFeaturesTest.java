package dtm.mapper;

import dtm.mapper.enums.ConversionFailurePolicy;
import dtm.mapper.enums.MissingFieldPolicy;
import dtm.mapper.enums.NullValuePolicy;
import dtm.mapper.exceptions.MappingException;
import dtm.mapper.service.AutoMapperService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class AutoMapperFeaturesTest {

    public static class RawData {
        String data;
        String dataHora;
        String hora;
        String inteiro;
        String decimal;
        String ativo;
        Double valor;
        int numero;
        String dataBr;
        String dataBrSemHora;
        String vazio;
    }

    public static class TypedData {
        LocalDate data;
        LocalDateTime dataHora;
        LocalTime hora;
        int inteiro;
        BigDecimal decimal;
        Boolean ativo;
        BigDecimal valor;
        String numero;
        LocalDate dataBr;
        LocalDateTime dataBrSemHora;
        Integer vazio;
    }

    @Test
    void shouldConvertBuiltInTypesAutomatically() {
        AutoMapper mapper = AutoMapperService.register(
                RawData.class,
                TypedData.class,
                profile -> profile.datePatterns("dd/MM/yyyy")
        );

        RawData source = new RawData();
        source.data = "2024-02-01";
        source.dataHora = "2024-02-01T10:30:00";
        source.hora = "14:35";
        source.inteiro = " 42 ";
        source.decimal = "10.50";
        source.ativo = "TRUE";
        source.valor = 195.23;
        source.numero = 7;
        source.dataBr = "05/06/2022";
        source.dataBrSemHora = "05/06/2022";
        source.vazio = "  ";

        TypedData target = mapper.map(source, TypedData.class);

        assertEquals(LocalDate.of(2024, 2, 1), target.data);
        assertEquals(LocalDateTime.of(2024, 2, 1, 10, 30), target.dataHora);
        assertEquals(LocalTime.of(14, 35), target.hora);
        assertEquals(42, target.inteiro);
        assertEquals(new BigDecimal("10.50"), target.decimal);
        assertEquals(Boolean.TRUE, target.ativo);
        assertEquals(new BigDecimal("195.23"), target.valor);
        assertEquals("7", target.numero);
        assertEquals(LocalDate.of(2022, 6, 5), target.dataBr);
        assertEquals(LocalDateTime.of(2022, 6, 5, 0, 0), target.dataBrSemHora);
        assertNull(target.vazio);
    }

    public static class TextNumber {
        String value;
    }

    public static class IntNumber {
        int value;
    }

    public static class BoxedNumber {
        Integer value;
    }

    public static class TextHolder {
        String value;
        TextNumber item;
    }

    public static class IntHolder {
        int value;
        IntNumber item;
    }

    private static TextHolder textHolder() {
        TextHolder source = new TextHolder();
        source.value = "abcd";
        source.item = new TextNumber();
        source.item.value = "ab";
        return source;
    }

    @Test
    void shouldPreferProfileConverterAndInheritItInNestedProfiles() {
        AutoMapper mapper = AutoMapperService.register(
                TextHolder.class,
                IntHolder.class,
                profile -> profile
                        .converter(String.class, Integer.class, String::length)
                        .nested(TextNumber.class, IntNumber.class, nested -> {})
        );

        IntHolder target = mapper.map(textHolder(), IntHolder.class);

        assertEquals(4, target.value);
        assertEquals(2, target.item.value);
    }

    @Test
    void shouldOverrideInheritedConverterInNestedProfile() {
        AutoMapper mapper = AutoMapperService.register(
                TextHolder.class,
                IntHolder.class,
                profile -> profile
                        .converter(String.class, Integer.class, String::length)
                        .nested(TextNumber.class, IntNumber.class, nested -> nested
                                .converter(String.class, Integer.class, text -> 100))
        );

        IntHolder target = mapper.map(textHolder(), IntHolder.class);

        assertEquals(4, target.value);
        assertEquals(100, target.item.value);
    }

    @Test
    void shouldFailOnUnconvertibleValueByDefault() {
        AutoMapper mapper = AutoMapperService.register(TextNumber.class, IntNumber.class);

        TextNumber source = new TextNumber();
        source.value = "x";

        assertThrows(MappingException.class, () -> mapper.map(source, IntNumber.class));
    }

    @Test
    void shouldApplyNullPolicyWhenConversionFailsWithSetNull() {
        AutoMapper boxed = AutoMapperService.register(
                TextNumber.class,
                BoxedNumber.class,
                profile -> profile
                        .conversionFailurePolicy(ConversionFailurePolicy.SET_NULL)
                        .nullValuePolicy(NullValuePolicy.SET_DEFAULT)
                        .defaultValue(Integer.class, () -> -1)
        );
        AutoMapper primitive = AutoMapperService.register(
                TextNumber.class,
                IntNumber.class,
                profile -> profile
                        .conversionFailurePolicy(ConversionFailurePolicy.SET_NULL)
                        .nullValuePolicy(NullValuePolicy.SET_DEFAULT)
        );

        TextNumber source = new TextNumber();
        source.value = "x";

        assertEquals(-1, boxed.map(source, BoxedNumber.class).value);
        assertEquals(0, primitive.map(source, IntNumber.class).value);
    }

    @Test
    void shouldApplyNullPolicyWhenConverterReturnsNull() {
        AutoMapper mapper = AutoMapperService.register(
                TextNumber.class,
                BoxedNumber.class,
                profile -> profile
                        .nullValuePolicy(NullValuePolicy.SET_DEFAULT)
                        .defaultValue(Integer.class, () -> -1)
                        .converter(String.class, Integer.class, text -> null)
        );

        TextNumber source = new TextNumber();
        source.value = "10";

        assertEquals(-1, mapper.map(source, BoxedNumber.class).value);
    }

    public static class TextList {
        List<String> values;
    }

    public static class IntList {
        List<Integer> values;
    }

    @Test
    void shouldConvertValueElementsOfCollections() {
        AutoMapper mapper = AutoMapperService.register(TextList.class, IntList.class);

        TextList source = new TextList();
        source.values = new ArrayList<>(List.of("1", "2"));

        assertEquals(List.of(1, 2), mapper.map(source, IntList.class).values);
    }

    public static class Details {
        String color;
        String model;
        int year;
    }

    public static class Envelope {
        String color;
        Details details;
    }

    public static class Flat {
        String color;
        String model;
        int year;
        String missing;
    }

    @Test
    void shouldFlattenFieldsFromSubObject() {
        AutoMapper mapper = AutoMapperService.register(
                Envelope.class,
                Flat.class,
                profile -> profile.flatten("details").missingFieldPolicy(MissingFieldPolicy.IGNORE)
        );

        Envelope source = new Envelope();
        source.color = "raiz";
        source.details = new Details();
        source.details.color = "sub";
        source.details.model = "GOL";
        source.details.year = 2020;

        Flat target = mapper.map(source, Flat.class);

        assertEquals("raiz", target.color);
        assertEquals("GOL", target.model);
        assertEquals(2020, target.year);
        assertNull(target.missing);
    }

    @Test
    void shouldIgnoreNullFlattenedSubObject() {
        AutoMapper mapper = AutoMapperService.register(
                Envelope.class,
                Flat.class,
                profile -> profile.flatten("details").missingFieldPolicy(MissingFieldPolicy.IGNORE)
        );

        Envelope source = new Envelope();
        source.color = "raiz";

        Flat target = mapper.map(source, Flat.class);

        assertEquals("raiz", target.color);
        assertNull(target.model);
        assertEquals(0, target.year);
    }

    public static class NameSource {
        String name;

        NameSource() {}

        NameSource(String name) {
            this.name = name;
        }
    }

    public static class CustomerOrderSource {
        String customer;
        List<NameSource> items;
    }

    public static class CustomerItem {
        String name;
        String customer;
    }

    public static class CustomerOrder {
        List<CustomerItem> items;
    }

    @Test
    void shouldResolveRootPathInsideNestedProfile() {
        AutoMapper mapper = AutoMapperService.register(
                CustomerOrderSource.class,
                CustomerOrder.class,
                profile -> profile.nested(NameSource.class, CustomerItem.class, nested -> nested.map("$.customer", "customer"))
        );

        CustomerOrderSource source = new CustomerOrderSource();
        source.customer = "ACME";
        source.items = new ArrayList<>(List.of(new NameSource("A"), new NameSource("B")));

        CustomerOrder target = mapper.map(source, CustomerOrder.class);

        assertEquals("A", target.items.get(0).name);
        for (CustomerItem item : target.items) {
            assertEquals("ACME", item.customer);
        }
    }

    public static class TagSource {
        String label;

        TagSource() {}

        TagSource(String label) {
            this.label = label;
        }
    }

    public static class LineSource {
        String name;
        List<TagSource> tags;
    }

    public static class ParentSource {
        List<LineSource> lines;
        LineSource main;
        LineSource[] extra;
    }

    public static class Tag {
        String label;
        Line line;
    }

    public static class Line {
        String name;
        Parent parent;
        List<Tag> tags;
    }

    public static class Parent {
        List<Line> lines;
        Line main;
        Line[] extra;
    }

    private static LineSource lineSource(String name, String... tags) {
        LineSource line = new LineSource();
        line.name = name;
        line.tags = new ArrayList<>();
        for (String tag : tags) {
            line.tags.add(new TagSource(tag));
        }
        return line;
    }

    @Test
    void shouldFillBackReferencesAutomatically() {
        AutoMapper mapper = AutoMapperService.register(
                ParentSource.class,
                Parent.class,
                profile -> profile.autoBackReference()
        );

        ParentSource source = new ParentSource();
        source.lines = new ArrayList<>(List.of(lineSource("A", "x", "y"), lineSource("B")));
        source.main = lineSource("M");
        source.extra = new LineSource[]{lineSource("E")};

        Parent target = mapper.map(source, Parent.class);

        for (Line line : target.lines) {
            assertSame(target, line.parent);
        }
        assertSame(target, target.main.parent);
        assertSame(target, target.extra[0].parent);

        Line first = target.lines.get(0);
        assertEquals(2, first.tags.size());
        for (Tag tag : first.tags) {
            assertSame(first, tag.line);
        }
    }

    public static class NodeSource {
        String name;
    }

    public static class Node {
        String name;
        Graph a;
        Graph b;
    }

    public static class GraphSource {
        List<NodeSource> nodes;
    }

    public static class Graph {
        List<Node> nodes;
    }

    private static GraphSource graphSource() {
        GraphSource source = new GraphSource();
        NodeSource node = new NodeSource();
        node.name = "n1";
        source.nodes = new ArrayList<>(List.of(node));
        return source;
    }

    @Test
    void shouldSkipAmbiguousAutoBackReference() {
        AutoMapper mapper = AutoMapperService.register(
                GraphSource.class,
                Graph.class,
                profile -> profile.autoBackReference().missingFieldPolicy(MissingFieldPolicy.IGNORE)
        );

        Graph target = mapper.map(graphSource(), Graph.class);

        assertEquals("n1", target.nodes.get(0).name);
        assertNull(target.nodes.get(0).a);
        assertNull(target.nodes.get(0).b);
    }

    @Test
    void shouldPreferExplicitBackReferenceOverAutomatic() {
        AutoMapper mapper = AutoMapperService.register(
                GraphSource.class,
                Graph.class,
                profile -> profile
                        .autoBackReference()
                        .backReference("nodes", "b")
                        .missingFieldPolicy(MissingFieldPolicy.IGNORE)
        );

        Graph target = mapper.map(graphSource(), Graph.class);

        assertNull(target.nodes.get(0).a);
        assertSame(target, target.nodes.get(0).b);
    }

    public static class EmptySource {}

    public static class NullContainersSource {
        List<String> tags;
        String[] codes;
        Map<String, String> attrs;
        Integer count;
    }

    public static class Containers {
        List<String> tags;
        String[] codes;
        Map<String, String> attrs;
        int count;
    }

    @Test
    void shouldUseEmptyContainersForMissingFieldsWithDefaultPolicy() {
        AutoMapper mapper = AutoMapperService.register(
                EmptySource.class,
                Containers.class,
                profile -> profile.missingFieldPolicy(MissingFieldPolicy.DEFAULT)
        );

        Containers target = mapper.map(new EmptySource(), Containers.class);

        assertNotNull(target.tags);
        assertTrue(target.tags.isEmpty());
        assertEquals(0, target.codes.length);
        assertTrue(target.attrs.isEmpty());
        assertEquals(0, target.count);
    }

    @Test
    void shouldUseEmptyContainersAndZeroForNullValuesWithSetDefault() {
        AutoMapper mapper = AutoMapperService.register(
                NullContainersSource.class,
                Containers.class,
                profile -> profile.nullValuePolicy(NullValuePolicy.SET_DEFAULT)
        );

        Containers target = mapper.map(new NullContainersSource(), Containers.class);

        assertTrue(target.tags.isEmpty());
        assertEquals(0, target.codes.length);
        assertTrue(target.attrs.isEmpty());
        assertEquals(0, target.count);
    }

    public static class ClienteDTO {
        String nome;
        String dataCadastro;
    }

    public static class ItemDTO {
        String descricao;
        String quantidade;
        Double valor;

        ItemDTO() {}

        ItemDTO(String descricao, String quantidade, Double valor) {
            this.descricao = descricao;
            this.quantidade = quantidade;
            this.valor = valor;
        }
    }

    public static class PedidoDTO {
        String numero;
        ClienteDTO cliente;
        List<ItemDTO> itensDTO;
    }

    public static class Item {
        String nome;
        int quantidade;
        BigDecimal valor;
        String cliente;
        Pedido pedido;
    }

    public static class Pedido {
        String numero;
        String nome;
        LocalDate dataCadastro;
        List<Item> itens;
    }

    @Test
    void shouldMatchReadmeCompleteExample() {
        AutoMapper mapper = AutoMapperService.register(PedidoDTO.class, Pedido.class, profile -> profile
                .missingFieldPolicy(MissingFieldPolicy.DEFAULT)
                .nullValuePolicy(NullValuePolicy.SET_DEFAULT)
                .conversionFailurePolicy(ConversionFailurePolicy.SET_NULL)
                .defaultValue(String.class, () -> "Não informado")
                .datePatterns("dd/MM/yyyy")
                .autoBackReference()
                .flatten("cliente")
                .map("itensDTO", "itens")
                .nested(ItemDTO.class, Item.class, item -> item
                        .map("descricao", "nome")
                        .map("$.cliente.nome", "cliente")));

        PedidoDTO source = new PedidoDTO();
        source.numero = "P-1";
        source.cliente = new ClienteDTO();
        source.cliente.nome = "Ana";
        source.cliente.dataCadastro = "05/06/2022";
        source.itensDTO = new ArrayList<>(List.of(new ItemDTO("Caneta", "3", 195.23), new ItemDTO(null, "x", null)));

        Pedido target = mapper.map(source, Pedido.class);

        assertEquals("P-1", target.numero);
        assertEquals("Ana", target.nome);
        assertEquals(LocalDate.of(2022, 6, 5), target.dataCadastro);

        Item first = target.itens.get(0);
        assertEquals("Caneta", first.nome);
        assertEquals(3, first.quantidade);
        assertEquals(new BigDecimal("195.23"), first.valor);
        assertEquals("Ana", first.cliente);
        assertSame(target, first.pedido);

        Item second = target.itens.get(1);
        assertEquals("Não informado", second.nome);
        assertEquals(0, second.quantidade);
        assertNull(second.valor);
    }

    public static class DateText {
        String value;
    }

    public static class AsDate {
        LocalDate value;
    }

    public static class AsDateTime {
        LocalDateTime value;
    }

    public static class AsTime {
        LocalTime value;
    }

    private static Object convertReadmeDate(String text, Class<?> target) {
        DateText source = new DateText();
        source.value = text;
        if (target == AsDate.class) {
            return AutoMapperService.register(DateText.class, AsDate.class, readmeDateProfile()).map(source, AsDate.class).value;
        }
        if (target == AsDateTime.class) {
            return AutoMapperService.register(DateText.class, AsDateTime.class, readmeDateProfile()).map(source, AsDateTime.class).value;
        }
        return AutoMapperService.register(DateText.class, AsTime.class, readmeDateProfile()).map(source, AsTime.class).value;
    }

    private static Consumer<MappingProfile> readmeDateProfile() {
        return profile -> profile
                .datePatterns("dd/MM/yyyy", "dd/MM/yyyy HH:mm")
                .conversionFailurePolicy(ConversionFailurePolicy.SET_NULL);
    }

    @Test
    void shouldMatchReadmeDatePatternTable() {
        assertEquals(LocalDate.of(2024, 2, 1), convertReadmeDate("2024-02-01", AsDate.class));
        assertEquals(LocalDateTime.of(2024, 2, 1, 0, 0), convertReadmeDate("2024-02-01", AsDateTime.class));
        assertNull(convertReadmeDate("2024-02-01", AsTime.class));

        assertEquals(LocalDate.of(2024, 2, 1), convertReadmeDate("2024-02-01T10:30:00", AsDate.class));
        assertEquals(LocalDateTime.of(2024, 2, 1, 10, 30), convertReadmeDate("2024-02-01T10:30:00", AsDateTime.class));
        assertEquals(LocalTime.of(10, 30), convertReadmeDate("2024-02-01T10:30:00", AsTime.class));

        assertEquals(LocalDate.of(2022, 6, 5), convertReadmeDate("05/06/2022", AsDate.class));
        assertEquals(LocalDateTime.of(2022, 6, 5, 0, 0), convertReadmeDate("05/06/2022", AsDateTime.class));
        assertNull(convertReadmeDate("05/06/2022", AsTime.class));

        assertEquals(LocalDate.of(2022, 6, 5), convertReadmeDate("05/06/2022 08:15", AsDate.class));
        assertEquals(LocalDateTime.of(2022, 6, 5, 8, 15), convertReadmeDate("05/06/2022 08:15", AsDateTime.class));
        assertEquals(LocalTime.of(8, 15), convertReadmeDate("05/06/2022 08:15", AsTime.class));

        assertNull(convertReadmeDate("14:35", AsDate.class));
        assertNull(convertReadmeDate("14:35", AsDateTime.class));
        assertEquals(LocalTime.of(14, 35), convertReadmeDate("14:35", AsTime.class));

        assertNull(convertReadmeDate("   ", AsDate.class));
        assertNull(convertReadmeDate("não informado", AsDateTime.class));
    }

    @Test
    void shouldIgnoreInvalidDatePatternSilently() {
        AutoMapper mapper = AutoMapperService.register(
                DateText.class,
                AsDate.class,
                profile -> profile.datePatterns("dd/MM/yyyy{{", "dd/MM/yyyy")
        );

        DateText source = new DateText();
        source.value = "05/06/2022";

        assertEquals(LocalDate.of(2022, 6, 5), mapper.map(source, AsDate.class).value);
    }

    @Test
    void shouldMapRootCollectionWithNestedProfileAsInReadme() {
        AutoMapper mapper = AutoMapperService.register(
                List.class,
                List.class,
                profile -> profile
                        .missingFieldPolicy(MissingFieldPolicy.IGNORE)
                        .nested(ItemDTO.class, Item.class, item -> item.map("descricao", "nome"))
        );

        List<ItemDTO> source = new ArrayList<>(List.of(new ItemDTO("Caneta", "2", 1.5)));
        List<Item> target = mapper.map(source, new CollectionReference<List<Item>>() {});

        assertEquals("Caneta", target.get(0).nome);
        assertEquals(2, target.get(0).quantidade);
        assertEquals(new BigDecimal("1.5"), target.get(0).valor);
    }

    @Test
    void shouldPickFirstMatchingElement() {
        MapperConverter<Object, String> firstB = Converters.firstMatch((String s) -> s.startsWith("b"));
        MapperConverter<Object, String> first = Converters.first();

        assertEquals("b1", firstB.convert(List.of("a1", "b1", "b2")));
        assertEquals("a1", firstB.convert(List.of("a1", "c1")));
        assertEquals("b1", firstB.convert(new String[]{"a1", "b1"}));
        assertNull(firstB.convert(List.of()));
        assertNull(firstB.convert(null));
        assertEquals("a1", first.convert(List.of("a1", "b1")));
        assertNull(first.convert(List.of()));
    }
}
