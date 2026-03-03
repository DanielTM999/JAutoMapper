# DTM AutoMapper

Biblioteca Java leve e baseada em reflexão para mapeamento de objetos. Realiza a transferência de dados entre classes de forma automática, com suporte a objetos aninhados, coleções, conversores customizados e políticas configuráveis para valores nulos e campos ausentes.

---

## Instalação

Adicione a dependência ao seu projeto conforme o seu gerenciador de build (Maven ou Gradle).

---

## Início Rápido

### Mapeamento simples

```java
AutoMapper mapper = AutoMapperService.register(UserEntity.class, UserDTO.class);

UserEntity entity = new UserEntity("João", "Silva", 30);
UserDTO dto = mapper.map(entity, UserDTO.class);
```

Campos com o mesmo nome são mapeados automaticamente por convenção, sem nenhuma configuração adicional.

---

## Registrando um Mapper

Utilize `AutoMapperService.register(source, target)` para criar um mapper entre dois tipos.

```java
// Registro simples (sem perfil customizado)
AutoMapper mapper = AutoMapperService.register(Source.class, Target.class);

// Registro com perfil customizado
AutoMapper mapper = AutoMapperService.register(Source.class, Target.class, profile -> {
    profile.map("sourceName", "targetFullName")
           .ignore("internalCode")
           .nullValuePolicy(NullValuePolicy.SET_DEFAULT);
});
```

---

## API do MappingProfile

A interface `MappingProfile` permite personalizar completamente a forma como os campos são mapeados.

---

### map(String sourcePath, String targetField)

Mapeia um campo da origem (com suporte a notação de ponto para caminhos aninhados) para um campo de destino com nome diferente.

```java
profile.map("address.city", "cityName");
```

---

### ignore(String targetField)

Exclui um campo do destino do processo de mapeamento.

```java
profile.ignore("password");
```

---

### convertField(String targetField, MapperConverter conversor)

Aplica uma função de conversão customizada ao mapear um campo específico.

```java
profile.convertField("status", (String s) -> s.toUpperCase());
```

---

### missingFieldPolicy(MissingFieldPolicy policy)

Define o comportamento quando um campo da origem não existe no destino.

| Política  | Comportamento                                              |
|-----------|------------------------------------------------------------|
| IGNORE    | Ignora o campo silenciosamente (padrão)                    |
| DEFAULT   | Atribui um valor padrão (zero, nulo ou valor customizado)  |
| FAIL      | Lança uma MappingException                                 |

```java
profile.missingFieldPolicy(MissingFieldPolicy.DEFAULT);
```

---

### nullValuePolicy(NullValuePolicy policy)

Define o comportamento quando o valor de um campo da origem é nulo.

| Política     | Comportamento                                             |
|--------------|-----------------------------------------------------------|
| IGNORE       | Mantém nulo no destino (padrão)                           |
| SET_DEFAULT  | Aplica um valor padrão registrado, se disponível          |
| FAIL         | Lança uma MappingException                                |

```java
profile.nullValuePolicy(NullValuePolicy.SET_DEFAULT);
```

---

### defaultValue(String targetField, Supplier value)

Define um valor padrão para um campo específico do destino.

```java
profile.defaultValue("ativo", () -> true);
```

---

### defaultValue(Class targetType, Supplier value)

Define um valor padrão para todos os campos de um determinado tipo.

```java
profile.defaultValue(String.class, () -> "N/A");
```

---

## Mapeamento para Coleções

Utilize `CollectionReference<T>` para mapear um objeto de origem diretamente em uma coleção tipada.

```java
AutoMapper mapper = AutoMapperService.register(Source.class, Target.class);

List<Target> resultado = mapper.map(sourceList, new CollectionReference<List<Target>>() {});
```

Tipos de coleção suportados: `List`, `Set`, `Queue`, `Deque` e qualquer implementação concreta de `Collection` que possua um construtor sem argumentos.

---

## Conversor Customizado

Implemente a interface `MapperConverter<S, T>` para criar lógicas de conversão reutilizáveis.

```java
MapperConverter<String, LocalDate> conversorData = LocalDate::parse;

profile.convertField("dataNascimento", conversorData);
```

---

## Restrições e Validações

A biblioteca aplica as seguintes regras no momento do mapeamento e lança `MappingException` caso sejam violadas:

- Origem e destino não podem ser nulos, primitivos, enums, anotações ou interfaces.
- O tipo de destino não pode ser abstrato, exceto quando for uma `Collection`.
- O tipo de destino deve corresponder ao tipo registrado no momento do `register(...)`.
- A classe de destino deve possuir um construtor público sem argumentos.
- Campos do tipo `Map` como destino ainda não são suportados.

---

## Mapeamento de Objetos Aninhados

Objetos aninhados são mapeados recursivamente. Tanto a origem quanto o destino devem possuir campos com nomes correspondentes ou configuração explícita via `map(...)`.

```java
// Origem
class Pedido {
    Cliente cliente;
}

// Destino
class PedidoDTO {
    ClienteDTO cliente;
}

// Os tipos aninhados são resolvidos e instanciados automaticamente
AutoMapper mapper = AutoMapperService.register(Pedido.class, PedidoDTO.class);
PedidoDTO dto = mapper.map(pedido, PedidoDTO.class);
```

---

## Cache de Resolução de Campos

A resolução de campos é armazenada em cache internamente via `ConcurrentHashMap`, tornando mapeamentos repetidos do mesmo tipo eficientes. O cache percorre toda a hierarquia da classe, incluindo superclasses, portanto campos herdados são resolvidos automaticamente.

---

## Exemplo Completo

```java
// Classes de domínio
class UserEntity {
    String primeiroNome;
    String sobrenome;
    String email;
    String tokenInterno;
    Endereco endereco;
}

class UserDTO {
    String nome;
    String email;
    String cidade;
}

// Registro com perfil customizado
AutoMapper mapper = AutoMapperService.register(UserEntity.class, UserDTO.class, profile -> {
    profile
        .map("primeiroNome", "nome")
        .map("endereco.cidade", "cidade")
        .ignore("tokenInterno")
        .nullValuePolicy(NullValuePolicy.SET_DEFAULT)
        .defaultValue(String.class, () -> "Não informado");
});

// Mapeamento
UserEntity entity = buscarDoBancoDeDados();
UserDTO dto = mapper.map(entity, UserDTO.class);
```

---

## Licenca

Este projeto esta licenciado sob os termos da Licenca MIT.