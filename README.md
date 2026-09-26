# DTM AutoMapper

Biblioteca Java leve e baseada em reflexão para mapeamento de objetos. Copia dados entre classes automaticamente, com suporte a objetos aninhados, coleções, conversão de tipos, perfis internos para elementos de listas, referências de volta (pai ↔ filho) e políticas configuráveis para valores nulos e campos ausentes.

Grande parte da configuração é feita com **strings** (caminhos de campo, nomes de campo e padrões de data). A seção [Guia de strings](#guia-de-strings) descreve exatamente o que cada uma aceita; leia-a antes de configurar um perfil.

---

## Sumário

- [Instalação](#instalação)
- [Início rápido](#início-rápido)
- [Conceitos](#conceitos)
- [Registrando um mapper](#registrando-um-mapper)
- [Guia de strings](#guia-de-strings)
  - [Qual string vai em cada método](#qual-string-vai-em-cada-método)
  - [Caminho de origem](#caminho-de-origem)
  - [Caminho de destino](#caminho-de-destino)
  - [Nome simples de campo](#nome-simples-de-campo)
  - [Caminho a partir da raiz (`$.`)](#caminho-a-partir-da-raiz-)
  - [Padrões de data (`datePatterns`)](#padrões-de-data-datepatterns)
  - [Textos aceitos na conversão automática](#textos-aceitos-na-conversão-automática)
  - [Quando cada string é validada](#quando-cada-string-é-validada)
- [Como cada campo é resolvido](#como-cada-campo-é-resolvido)
- [API do MappingProfile](#api-do-mappingprofile)
- [Conversão automática de tipos](#conversão-automática-de-tipos)
- [Perfis internos (`nested`)](#perfis-internos-nested)
- [Referências de volta](#referências-de-volta)
- [Converters](#converters)
- [Mapeamento para coleções](#mapeamento-para-coleções)
- [Erros comuns](#erros-comuns)
- [Restrições](#restrições)
- [Exemplo completo](#exemplo-completo)

---

## Instalação

Via [JitPack](https://jitpack.io):

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.DanielTM999</groupId>
    <artifactId>JAutoMapper</artifactId>
    <version>1.1.0</version>
</dependency>
```

Requer Java 21+.

---

## Início rápido

```java
AutoMapper mapper = AutoMapperService.register(UserEntity.class, UserDTO.class);

UserDTO dto = mapper.map(entity, UserDTO.class);
```

Campos com o mesmo nome na origem e no destino são copiados automaticamente, sem configuração.

---

## Conceitos

- **Origem (source):** o objeto passado para `mapper.map(origem, Destino.class)`. Pode ser um objeto comum ou um `Map`.
- **Destino (target):** a classe criada e preenchida. Precisa de construtor sem argumentos.
- **Perfil (`MappingProfile`):** a configuração de um par origem → destino.
- **Perfil interno (`nested`):** a configuração de um par de classes que aparece *dentro* do destino (elemento de lista, campo objeto etc.).
- **Acesso por campo:** a lib lê e escreve os **campos** diretamente por reflexão, inclusive os `private` e os herdados de superclasses. Getters e setters **não** são usados. Por isso, todos os nomes nas strings são **nomes de campo Java**, com diferença entre maiúsculas e minúsculas.
- **Campos ignorados sempre:** campos `static` (como `serialVersionUID` e constantes) e campos sintéticos gerados pelo compilador (como `this$0`) nunca são lidos nem gravados. Também não entram nos `Map` gerados a partir de objetos e não contam como candidatos do `autoBackReference`.

---

## Registrando um mapper

| Método | Guarda no registro? | Uso |
|---|---|---|
| `AutoMapperService.register(Origem.class, Destino.class[, perfil])` | **Não** | Cria e devolve um mapper novo a cada chamada. Guarde a referência. |
| `AutoMapperService.getOrRegister(Origem.class, Destino.class[, perfil])` | **Sim** | Cria na primeira chamada e devolve o mesmo nas próximas. O perfil só roda na primeira. |
| `AutoMapperService.getAutoMapper(Origem.class, Destino.class)` | — | Busca um mapper criado por `getOrRegister`. Lança `MappingException` se não existir, inclusive quando ele foi criado só com `register`. |

```java
AutoMapperService.getOrRegister(PedidoDTO.class, Pedido.class, profile -> profile
        .map("descricaoDTO", "descricao"));

Pedido pedido = AutoMapperService.getAutoMapper(PedidoDTO.class, Pedido.class).map(dto, Pedido.class);
```

Em aplicações Spring, um lugar comum para registrar é um `@Component` com `@PostConstruct` usando `getOrRegister`.

---

## Guia de strings

### Qual string vai em cada método

| Método | Argumento | Tipo de string | Resolvida a partir de | Aceita `.` | Aceita `$.` |
|---|---|---|---|---|---|
| `map(sourcePath, targetField)` | **1º** `sourcePath` | [caminho de origem](#caminho-de-origem) | objeto de origem do perfil | sim | sim |
| `map(sourcePath, targetField)` | **2º** `targetField` | [caminho de destino](#caminho-de-destino) | classe de destino do perfil | sim | não |
| `ignore(targetField)` | `targetField` | caminho de destino | classe de destino do perfil | sim | não |
| `convertField(targetField, fn)` | `targetField` | caminho de destino | classe de destino do perfil | sim | não |
| `backReference(targetField, childField)` | `targetField` | caminho de destino | classe de destino do perfil | sim | não |
| `backReference(targetField, childField)` | `childField` | [nome simples](#nome-simples-de-campo) | classe do filho | **não** | não |
| `defaultValue(targetField, supplier)` | `targetField` | nome simples | qualquer campo com esse nome | **não** | não |
| `flatten(sourcePath)` | `sourcePath` | caminho de origem | objeto de origem do perfil | sim | sim |
| `datePatterns(patterns...)` | cada padrão | [padrão de data](#padrões-de-data-datepatterns) | — | — | — |

> ⚠️ **Ordem do `map`: primeiro a ORIGEM, depois o DESTINO.** `map("nomeNaOrigem", "nomeNoDestino")`. Inverter os argumentos é o erro mais comum e gera `Mapped field not found`.

---

### Caminho de origem

Usado no 1º argumento de `map` e em `flatten`. Diz **de onde ler** o valor.

**Sintaxe:** nomes de campo separados por ponto: `campo`, `campo.subcampo`, `a.b.c`.

**Regras:**

1. Cada parte é o **nome de um campo Java** do objeto atual. Maiúsculas e minúsculas contam, e espaços não são removidos.
2. Se o objeto atual for um `Map`, a parte é usada como **chave**: `map.get("parte")`.
3. Se algum objeto intermediário for `null`, o resultado é `null`, sem erro. Depois vale a `nullValuePolicy`.
4. **Não** é possível indexar listas nem arrays: `itens.0` e `itens[0]` não funcionam. Para pegar um elemento de uma lista, use `convertField` com [`Converters`](#converters).
5. Uma chave de `Map` que contém ponto não pode ser acessada.
6. Em um perfil raiz, o caminho parte do objeto passado para `map(...)`. Em um [perfil interno](#perfis-internos-nested), parte do **elemento** que está sendo mapeado. Para ler da raiz dentro de um perfil interno, use [`$.`](#caminho-a-partir-da-raiz-).

**Exemplos:**

```java
class PedidoDTO { ClienteDTO cliente; Map<String, Object> extras; }
class ClienteDTO { String nome; EnderecoDTO endereco; }

profile.map("cliente.nome", "nomeCliente");
profile.map("cliente.endereco.cidade", "cidade");
profile.map("extras.origem", "canal");
```

| Caminho | Resultado |
|---|---|
| `"cliente.nome"` | `pedidoDTO.cliente.nome` |
| `"cliente.endereco.cidade"` | `null` se `cliente` ou `endereco` forem `null` |
| `"extras.origem"` | `pedidoDTO.extras.get("origem")` |
| `"Cliente.nome"` | erro: o campo é `cliente`, com minúscula |
| `"itens.0.nome"` | erro: não indexa listas |

---

### Caminho de destino

Usado no 2º argumento de `map` e em `ignore`, `convertField` e no 1º argumento de `backReference`. Diz **qual campo do destino** recebe a configuração.

**Sintaxe:** nomes de campo separados por ponto, a partir da classe de destino do perfil.

**Regras:**

1. Cada parte é o nome de um campo Java da classe atual, incluindo os herdados.
2. Só é possível descer para campos de **classes do seu projeto**. Não dá para atravessar `List`, `Set`, `Map`, arrays, primitivos nem tipos `java.*`. `itens.nome` lança `Cannot navigate into JDK type`. Para configurar os elementos de uma lista, use [`nested`](#perfis-internos-nested).
3. `$.` **não** é aceito em caminhos de destino.
4. Um caminho com ponto (`endereco.cep`) configura aquele campo da classe interna só quando o objeto interno é mapeado **pelo mesmo perfil**. Se existir um `nested` para aquele par de classes, a configuração tem de ser feita dentro do `nested`.

**Exemplos:**

```java
class Pedido { String numero; Endereco endereco; List<Item> itens; }
class Endereco { String cep; }

profile.map("numeroDTO", "numero");
profile.ignore("endereco.cep");
profile.convertField("itens", ...);
profile.ignore("itens.nome");
```

A última linha lança erro. O certo é declarar um `nested(ItemDTO.class, Item.class, i -> i.ignore("nome"))`.

---

### Nome simples de campo

Usado em `defaultValue(String, ...)` e no 2º argumento de `backReference`.

- É **só o nome**, sem pontos.
- Em `defaultValue("ativo", ...)`, vale para **qualquer** campo chamado `ativo` em objetos mapeados por aquele perfil, inclusive objetos internos mapeados pelo mesmo perfil. O default por nome tem prioridade sobre o `defaultValue(Classe, ...)`.
- Em `backReference("itens", "pedido")`, `pedido` é o nome do campo **dentro da classe do filho** (`Item`), e não do destino.

---

### Caminho a partir da raiz (`$.`)

Um caminho de **origem** que começa com `$.` é lido a partir da **origem raiz**, isto é, o objeto passado para `mapper.map(...)`, mesmo dentro de um perfil interno.

```java
class PedidoDTO { ClienteDTO cliente; List<ItemDTO> itens; }

profile.nested(ItemDTO.class, Item.class, item -> item
        .map("descricao", "nome")
        .map("$.cliente.nome", "nomeCliente"));
```

| Caminho dentro do `nested` | Lê de |
|---|---|
| `"descricao"` | o `ItemDTO` atual |
| `"$.cliente.nome"` | `pedidoDTO.cliente.nome` |

- Prefixo exato: `$` seguido de `.`. `"$cliente"` e `"$ .cliente"` não são reconhecidos.
- Vale em `map` (1º argumento) e em `flatten`. Não vale em caminhos de destino.
- Em `mapper.map(lista, new CollectionReference<...>(){})`, a raiz é a própria coleção.

---

### Padrões de data (`datePatterns`)

Os padrões seguem a sintaxe de [`java.time.format.DateTimeFormatter.ofPattern`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/DateTimeFormatter.html#patterns), com o `Locale` padrão da JVM.

**Letras mais usadas:**

| Letra | Significado | Exemplo |
|---|---|---|
| `yyyy` / `uuuu` | ano | `2024` |
| `MM` | mês com 2 dígitos | `02` |
| `MMM` / `MMMM` | nome do mês (depende do Locale) | `fev.` / `fevereiro` |
| `dd` | dia do mês | `05` |
| `HH` | hora 0–23 | `14` |
| `hh` + `a` | hora 1–12 com AM/PM | `02 PM` |
| `mm` | **minuto** | `35` |
| `ss` | segundo | `09` |
| `SSS` | milissegundos | `123` |
| `'T'` | texto literal (entre aspas simples) | `2024-02-01T10:30` |

> ⚠️ **Armadilhas:** `MM` é mês, `mm` é minuto. `DD` é o *dia do ano*, não do mês; use `dd`. `YYYY` é o ano da semana ISO; use `yyyy`. Um padrão inválido (sintaxe errada) é **ignorado em silêncio**, e o próximo é tentado.

**Ordem de tentativa**, e o primeiro que funcionar vence (o texto passa por `trim` antes; texto vazio vira `null`):

| Destino | Tentativas |
|---|---|
| `LocalDate` | ISO data (`2024-02-01`) → ISO data-hora (`2024-02-01T10:30[:00]`, descarta a hora) → cada `datePatterns`, na ordem |
| `LocalDateTime` | ISO data-hora → ISO data (vira meia-noite) → cada `datePatterns`, na ordem (se o padrão só tiver data, vira meia-noite) |
| `LocalTime` | ISO hora (`14:35` ou `14:35:10`) → ISO data-hora (pega a hora) → cada `datePatterns`, na ordem |

**Exemplos com `datePatterns("dd/MM/yyyy", "dd/MM/yyyy HH:mm")`:**

| Texto | `LocalDate` | `LocalDateTime` | `LocalTime` |
|---|---|---|---|
| `"2024-02-01"` | 2024-02-01 | 2024-02-01T00:00 | falha |
| `"2024-02-01T10:30:00"` | 2024-02-01 | 2024-02-01T10:30 | 10:30 |
| `"05/06/2022"` | 2022-06-05 | 2022-06-05T00:00 | falha |
| `"05/06/2022 08:15"` | 2022-06-05 | 2022-06-05T08:15 | 08:15 |
| `"14:35"` | falha | falha | 14:35 |
| `"   "` | `null` | `null` | `null` |
| `"não informado"` | falha | falha | falha |

"Falha" segue a [`conversionFailurePolicy`](#conversionfailurepolicyconversionfailurepolicy-policy): lança erro (`FAIL`, o padrão) ou vira `null` (`SET_NULL`).

- `datePatterns` de um perfil interno **substitui** a lista do pai; não soma. Se precisar dos dois, repita os padrões.
- Tipos com fuso (`ZonedDateTime`, `OffsetDateTime`, `Instant`) e `java.util.Date` não têm conversão embutida. Use `converter(...)`.

---

### Textos aceitos na conversão automática

Aplica-se quando a origem é `String` e o destino é outro tipo. O texto passa por `trim`, e texto vazio vira `null`.

| Destino | Aceita | Não aceita |
|---|---|---|
| `int`/`Integer`, `long`/`Long`, `short`/`Short`, `byte`/`Byte` | `"42"`, `"-7"` | `"42.0"`, `"1.000"`, `"abc"` |
| `double`/`Double`, `float`/`Float` | `"10.5"`, `"1e3"` | `"10,5"` (vírgula decimal) |
| `BigDecimal` | `"10.50"`, `"-3"`, `"1E+3"` | `"1.234,56"`, `"R$ 10"` |
| `BigInteger` | `"12345678901234567890"` | `"1.5"` |
| `boolean`/`Boolean` | `"true"`, `"false"` (sem diferença de maiúsculas) | `"1"`, `"sim"`, `"S"` |
| `char`/`Character` | exatamente 1 caractere | `"ab"` |
| `UUID` | `"123e4567-e89b-12d3-a456-426614174000"` | outros formatos |
| `Enum` | nome **exato** da constante (`"ATIVO"`) | `"ativo"` (diferença de maiúsculas) |
| `LocalDate`/`LocalDateTime`/`LocalTime` | ver [Padrões de data](#padrões-de-data-datepatterns) | |

Para formatos brasileiros de número (`"1.234,56"`) ou booleanos como `"S"`/`"N"`, registre um [`converter`](#converterclasss-source-classt-target-mapperconverters-t-converter).

---

### Quando cada string é validada

| String | Validada em | Erro |
|---|---|---|
| Caminhos de destino (`map` 2º, `ignore`, `convertField`, `backReference`) | no **primeiro `mapper.map(...)`** daquele perfil; nos perfis internos, na primeira vez que o par é mapeado | `... field not found`, `Cannot navigate into ...` |
| `childField` do `backReference` | junto com o caminho de destino | `Back reference field not found` |
| Caminhos de origem (`map` 1º, `flatten`) | **só quando são lidos**, durante o mapeamento | `Field 'x' not found in class Y` |
| `datePatterns` | quando uma data é convertida | padrão inválido é ignorado |

> ⚠️ Um erro de digitação num caminho de origem **só aparece se o caminho for percorrido**. Em `"cliente.nomee"`, se `cliente` vier `null` o erro não aparece. Teste o mapeamento com uma origem completa.

Nada é validado no `register`/`getOrRegister`, exceto o conflito de perfis globais.

---

## Como cada campo é resolvido

Para cada campo do objeto de destino, nesta ordem:

1. **Pulado** se estiver em `ignore` ou se for o campo de [referência de volta](#referências-de-volta) do filho.
2. **Valor da origem**, na primeira regra que se aplicar:
   1. `map(caminho, campo)` explícito;
   2. campo (ou chave de `Map`) com o **mesmo nome** no objeto de origem;
   3. o mesmo nome em cada caminho de `flatten`, na ordem de declaração. O primeiro objeto que **tiver** o campo vence, mesmo que o valor seja `null`;
   4. se nada disso existir, o campo está **ausente**.
3. Campo ausente → `missingFieldPolicy`.
4. Valor `null` → `nullValuePolicy`.
5. `convertField` do campo, se houver.
6. Se o valor não for do tipo do campo: `converter(...)` por tipo (perfil e ancestrais) → conversão embutida → `conversionFailurePolicy` se falhar.
7. Se o valor ficou `null` nos passos 5–6, a `nullValuePolicy` é aplicada **de novo**.
8. Atribuição: `String`/`Integer` → `Enum` (nome exato/ordinal); objetos são mapeados recursivamente (com `nested`, se houver); coleções são mapeadas elemento a elemento.
9. Referência de volta aplicada no filho, se houver.

Ao final do objeto raiz (ou de cada elemento de um `nested`), roda o `afterMap`.

---

## API do MappingProfile

Todos os métodos devolvem o próprio perfil e podem ser encadeados.

### map(String sourcePath, String targetField)

Lê o valor de `sourcePath` ([caminho de origem](#caminho-de-origem)) e grava em `targetField` ([caminho de destino](#caminho-de-destino)).

```java
profile.map("address.city", "cityName");
profile.map("$.cliente.nome", "cliente");
```

Declarar o mesmo `targetField` duas vezes: vale a última.

### ignore(String targetField)

O campo do destino não é tocado e mantém o valor que tiver depois do construtor.

```java
profile.ignore("password");
```

### convertField(String targetField, MapperConverter<?, ?> converter)

Converte o valor de um campo específico. O conversor recebe o valor **já tratado pela `nullValuePolicy`**, então pode receber `null` ou o default. Se devolver `null`, a `nullValuePolicy` é aplicada de novo.

```java
profile.convertField("status", (String s) -> s.toUpperCase());
```

Declare o tipo do parâmetro no lambda (`(String s) -> ...`) para o compilador inferir os tipos.

### missingFieldPolicy(MissingFieldPolicy policy)

O que fazer quando **o campo do destino não tem correspondente na origem**: não existe o mesmo nome, nem `map`, nem `flatten`.

| Política | Comportamento |
|---|---|
| `FAIL` (padrão) | Lança `MappingException: Missing field 'x' required by target type T` |
| `IGNORE` | Não toca no campo |
| `DEFAULT` | `defaultValue` do campo → `defaultValue` do tipo → zero para primitivos, coleção/array/`Map` vazio, ou `null` |

### nullValuePolicy(NullValuePolicy policy)

O que fazer quando **o valor lido é `null`**.

| Política | Comportamento |
|---|---|
| `IGNORE` (padrão) | Grava `null` (primitivos: erro) |
| `SET_DEFAULT` | `defaultValue` do campo → `defaultValue` do tipo → zero para primitivos, coleção/array/`Map` vazio, ou `null` |
| `FAIL` | Lança `MappingException` |

### defaultValue(String targetField, Supplier<?> value)

Default para campos com esse **nome simples** (sem ponto). Usado por `DEFAULT` e `SET_DEFAULT`.

```java
profile.defaultValue("ativo", () -> true);
```

### defaultValue(Class<T> targetType, Supplier<T> value)

Default para todos os campos **declarados exatamente** com esse tipo. `defaultValue(List.class, ...)` não vale para um campo `ArrayList`.

```java
profile.defaultValue(String.class, () -> "N/A");
```

### conversionFailurePolicy(ConversionFailurePolicy policy)

| Política | Comportamento |
|---|---|
| `FAIL` (padrão) | Lança `MappingException: Cannot convert value of type A to B for 'campo'` |
| `SET_NULL` | Trata como `null`; em seguida vale a `nullValuePolicy` |

### converter(Class<S> source, Class<T> target, MapperConverter<S, T> converter)

Conversão por tipo, válida para todos os campos do perfil e dos perfis internos. Vence a conversão embutida.

- `source`: casa com o valor se `source.isAssignableFrom(valor.getClass())`.
- `target`: casa com o tipo do campo; para primitivos, registre o wrapper (`Integer.class` vale para `int`).
- Devolver `null` significa "sem valor", e a `nullValuePolicy` decide o que gravar.

```java
profile.converter(Integer.class, String.class, n -> n > 0 ? n.toString() : null);
profile.converter(String.class, Boolean.class, s -> s.equalsIgnoreCase("S"));
```

### datePatterns(String... patterns)

Formatos extras para textos de data e hora. Veja [Padrões de data](#padrões-de-data-datepatterns).

```java
profile.datePatterns("dd/MM/yyyy", "dd/MM/yyyy HH:mm:ss");
```

### flatten(String sourcePath)

Procura os campos do destino **pelo nome** também dentro de um sub-objeto da origem. Aceita [caminho de origem](#caminho-de-origem) e `$.`.

```java
class VeiculoDTO { String placa; Detalhes detalhes; }
class Detalhes { String marca; String placa; int ano; }
class Veiculo { String placa; String marca; int ano; }

profile.flatten("detalhes");
```

Resultado: `placa` vem de `VeiculoDTO.placa`, porque o nome direto na origem vence; `marca` e `ano` vêm de `detalhes`.

- Pode ser chamado várias vezes; os caminhos são consultados na ordem de declaração.
- Se o sub-objeto for `null`, é pulado.
- Vale só para o objeto do próprio perfil (a raiz, ou o elemento de um `nested`), e não é herdado.

### nested(...), nestedScope(...)

Veja [Perfis internos](#perfis-internos-nested).

### backReference(String targetField, String childField) e autoBackReference()

Veja [Referências de volta](#referências-de-volta).

### afterMap(BiConsumer<S, T> action)

Roda ao final do mapeamento de cada objeto raiz, com a origem e o destino prontos. Em um perfil interno, roda para cada elemento, com `(elementoOrigem, elementoDestino)`. Exceções viram `MappingException("Error in afterMap action")`.

```java
profile.afterMap((PedidoDTO origem, Pedido destino) -> destino.total = destino.calcularTotal());
```

---

## Conversão automática de tipos

Quando o valor lido não é do tipo do campo, a lib tenta converter:

| Origem | Destino |
|---|---|
| `String` | números, `BigDecimal`, `BigInteger`, `Boolean`, `Character`, `UUID`, `LocalDate`, `LocalDateTime`, `LocalTime` ([textos aceitos](#textos-aceitos-na-conversão-automática)) |
| `Number` | `Integer`, `Long`, `Short`, `Byte`, `Double`, `Float`, `BigDecimal`, `BigInteger` |
| `LocalDateTime` | `LocalDate`, `LocalTime` |
| `LocalDate` | `LocalDateTime` (meia-noite) |
| números, `Boolean`, `Character`, `Enum` (`name()`), `java.time` (ISO), `UUID` | `String` |
| `String`, `Integer` (ordinal) | `Enum` |

- Primitivos e wrappers são tratados como o mesmo tipo.
- Número → número usa as conversões do Java: `Double 1.9` → `int 1` (trunca); valores fora do intervalo estouram silenciosamente. `Double` → `BigDecimal` usa `toString()` (`195.23` → `195.23`).
- A conversão vale também para elementos de coleções de valores (`List<String>` → `List<Integer>`).
- Objetos do seu projeto não são "convertidos": são mapeados campo a campo.

---

## Perfis internos (`nested`)

```java
MappingProfile nested(Class<?> sourceType, Class<?> targetType, Consumer<MappingProfile> config);
MappingProfile nested(Class<?> sourceType, Class<?> targetType, Consumer<MappingProfile> config, NestedScope scope);
MappingProfile nestedScope(NestedScope scope);
```

Um perfil para um par de classes que aparece **dentro** do destino. Vale para elementos de listas e arrays, campos objeto, valores de `Map` e resultados de `convertField`. Não precisa de outro `AutoMapper`.

```java
profile.map("itensDTO", "itens")
       .nested(ItemDTO.class, Item.class, item -> item
               .map("descricao", "nome")
               .map("$.cliente.nome", "cliente"));
```

- É usado quando o destino é **exatamente** `targetType` e a classe do valor de origem é atribuível a `sourceType`.
- Dentro dele, os caminhos de origem partem do **elemento** (use `$.` para a raiz) e os de destino, de `targetType`.
- Pode declarar os próprios `nested`.

**Ordem de busca** ao mapear um objeto interno:

1. `nested` do perfil corrente;
2. `nested` dos perfis ancestrais;
3. perfis globais;
4. nenhum: o objeto é mapeado pelo perfil do pai, por nome de campo.

**Herança do pai** (quando o interno não define): `missingFieldPolicy`, `nullValuePolicy`, `conversionFailurePolicy`, `defaultValue(Classe, ...)`, `converter(...)`, `datePatterns(...)` (substitui, não soma), `autoBackReference()` e `nestedScope`.

**Não herdados:** `map`, `ignore`, `convertField`, `flatten`, `defaultValue(String, ...)`, `backReference` e `afterMap`.

### Escopo LOCAL e GLOBAL

| Escopo | Visível para |
|---|---|
| `LOCAL` (padrão) | só o mapper em que foi declarado |
| `GLOBAL` | qualquer mapper que encontre o mesmo par de classes |

```java
AutoMapperService.getOrRegister(Config.class, Config.class, profile -> profile
        .nestedScope(NestedScope.GLOBAL)
        .nested(EnderecoDTO.class, Endereco.class, e -> e.map("cep", "codigoPostal"))
        .nested(TelefoneDTO.class, Telefone.class, t -> t.map("ddd", "area"), NestedScope.LOCAL));
```

- Escopo efetivo = o explícito no `nested(..., scope)` → o `nestedScope` do perfil que declarou (ou de um ancestral) → `LOCAL`.
- Os globais são publicados ao final do `register`/`getOrRegister`. Registrar de novo o **mesmo** mapper substitui os seus globais. Se **outro** mapper declarar o mesmo par como global, lança `Global nested profile already registered for S -> T by A -> B`.

---

## Referências de volta

Para relacionamentos bidirecionais (por exemplo, entidades JPA em que o filho é dono da FK):

```java
class Pedido { List<Item> itens; }
class Item { String nome; Pedido pedido; }
```

### backReference(String targetField, String childField)

```java
profile.backReference("itens", "pedido");
```

- `targetField`: [caminho de destino](#caminho-de-destino) do campo que contém o(s) filho(s) (objeto, lista ou array).
- `childField`: [nome simples](#nome-simples-de-campo) do campo **na classe do filho** que recebe o dono.
- O `childField` não é mapeado a partir da origem, então não precisa existir nela, nem com `MissingFieldPolicy.FAIL`.
- Erros: `Back reference field not found: 'x' in type Item`, ou `Back reference field 'x' ... cannot hold Pedido` se o tipo não aceitar o dono.

### autoBackReference()

```java
profile.autoBackReference();
```

Ao mapear um filho, se a classe dele tiver **exatamente um** campo (não estático, de uma classe do projeto) cujo tipo aceita o objeto dono, esse campo recebe o dono.

- É herdado pelos perfis internos, então vale em qualquer profundidade (pedido → item → subitem).
- Se houver 0 ou mais de 1 candidato, nada é feito; use `backReference` nesses casos.
- O `backReference` explícito tem precedência.

---

## Converters

Conversores prontos, para usar com `convertField`:

| Método | Recebe | Devolve |
|---|---|---|
| `Converters.first()` | coleção ou array | primeiro elemento não nulo, ou `null` |
| `Converters.firstMatch(predicate)` | coleção ou array | primeiro que satisfaz o predicado → senão o primeiro elemento → senão `null` |

```java
profile.map("tabelas", "tabelaPrincipal")
       .convertField("tabelaPrincipal", Converters.firstMatch(Tabela::isPrincipal));
```

Se o elemento escolhido for de outra classe, ele é mapeado para o tipo do campo (com `nested`, se houver).

---

## Mapeamento para coleções

```java
AutoMapper mapper = AutoMapperService.register(List.class, List.class, profile -> profile
        .nested(ItemDTO.class, Item.class, item -> item.map("descricao", "nome")));

List<Item> itens = mapper.map(listaDTO, new CollectionReference<List<Item>>() {});
```

Tipos suportados: `List`, `Set`, `Queue`, `Deque` e qualquer `Collection` concreta com construtor sem argumentos. Campos de coleção precisam do tipo genérico declarado (`List<Item>`, e não `List`).

---

## Erros comuns

Toda falha é uma `MappingException`. Mensagens do tipo `Error mapping field: x` envolvem a causa real, que fica em `getCause()`.

| Mensagem | Causa provável | Solução |
|---|---|---|
| `Mapped field not found: 'x' while resolving path ...` | 2º argumento do `map` não existe no destino, muitas vezes por argumentos invertidos | `map(origem, destino)`, conferir o nome do campo |
| `Ignored field not found` / `Converter field not found` | caminho de destino errado em `ignore`/`convertField` | conferir o nome do campo no destino |
| `Field 'x' not found in class Y` | caminho de origem errado | conferir o nome do campo na origem |
| `Cannot navigate into JDK type: java.util.List (field: itens)` | caminho de destino atravessando uma lista | usar `nested` |
| `Missing field 'x' required by target type T` | `MissingFieldPolicy.FAIL` e o campo não existe na origem | `map`, `flatten`, `ignore` ou outra `missingFieldPolicy` |
| `Null value encountered for field 'x'` | `NullValuePolicy.FAIL` | outra política ou `defaultValue` |
| `Cannot convert value of type A to B for 'x'` | texto fora do formato ou tipo sem conversão | `datePatterns`, `converter` ou `ConversionFailurePolicy.SET_NULL` |
| `Back reference field not found` / `... cannot hold ...` | `childField` errado ou de tipo incompatível | conferir o campo na classe do filho |
| `Global nested profile already registered for S -> T by A -> B` | dois mappers declarando o mesmo par como global | deixar um deles `LOCAL` |
| `No AutoMapper registered for source ...` | `getAutoMapper` para um mapper criado com `register` | usar `getOrRegister` |
| `Target type mismatch. Expected: X, received: Y` | `map(origem, OutraClasse.class)` num mapper de outro destino | usar o mapper certo |
| `Cannot determine parameterized type for element: x` | campo de coleção sem tipo genérico | declarar `List<Item>` |

---

## Restrições

- Origem: não pode ser `null`, primitivo, enum, anotação ou interface, e precisa ser do tipo registrado (ou subtipo).
- Destino: classe concreta com construtor sem argumentos. Não pode ser enum, array, primitivo nem interface (exceto `Collection` no mapeamento para coleções).
- Campos `Map` no destino são suportados. As chaves são convertidas para `Integer`, `Long` ou `Double` quando o tipo da chave pede.
- Não há suporte a getters/setters, construtores com argumentos nem records como destino.
- Tipos com fuso horário e `java.util.Date` precisam de `converter(...)`.

---

## Exemplo completo

```java
class PedidoDTO {
    String numero;
    ClienteDTO cliente;
    List<ItemDTO> itensDTO;
}

class ClienteDTO {
    String nome;
    String dataCadastro;
}

class ItemDTO {
    String descricao;
    String quantidade;
    Double valor;
}

class Pedido {
    String numero;
    String nome;
    LocalDate dataCadastro;
    List<Item> itens;
}

class Item {
    String nome;
    int quantidade;
    BigDecimal valor;
    String cliente;
    Pedido pedido;
}

AutoMapper mapper = AutoMapperService.getOrRegister(PedidoDTO.class, Pedido.class, profile -> profile
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

Pedido pedido = mapper.map(pedidoDTO, Pedido.class);
```

| Campo de destino | De onde vem | Regra usada |
|---|---|---|
| `Pedido.numero` | `pedidoDTO.numero` | mesmo nome |
| `Pedido.nome` | `pedidoDTO.cliente.nome` | `flatten("cliente")` |
| `Pedido.dataCadastro` | `pedidoDTO.cliente.dataCadastro` (`"05/06/2022"`) | `flatten` + `datePatterns("dd/MM/yyyy")` |
| `Pedido.itens` | `pedidoDTO.itensDTO` | `map("itensDTO", "itens")` + `nested` |
| `Item.nome` | `itemDTO.descricao` | `map` no `nested` |
| `Item.quantidade` | `itemDTO.quantidade` (`"3"` → `3`) | conversão automática |
| `Item.valor` | `itemDTO.valor` (`Double` → `BigDecimal`) | conversão automática |
| `Item.cliente` | `pedidoDTO.cliente.nome` | `$.` no `nested` |
| `Item.pedido` | o próprio `Pedido` | `autoBackReference()` |
| qualquer `String` sem valor | `"Não informado"` | `SET_DEFAULT` + `defaultValue(String.class)` |
| data em formato inválido | `null` | `SET_NULL` |

---

## Licença

Este projeto está licenciado sob os termos da Licença MIT.
