package ca.hullabaloo.introspect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.TypeLiteral;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class IntrospectionTest {
  @SuppressWarnings({"UnusedDeclaration"})
  public interface SomeGenInt<T extends Number, B extends Collection> {
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public interface OtherType<T> {
  }

  public static class Unresolved<T> implements OtherType<T> {
  }

  public interface SomeOtherType<T> extends OtherType<T> {
  }

  public class TwoLevelsOfIndirectionIsMajorPain implements SomeOtherType<String> {
  }

  public class ThreeLevelsOfIndirectionIsCrazy extends TwoLevelsOfIndirectionIsMajorPain {
  }

  public interface ParameterizedList<T> extends OtherType<List<T>> {
  }

  public class StringParameterizedList implements ParameterizedList<String> {
  }

  public abstract static class AbstractPain
      implements OtherType<String>, SomeGenInt<Integer, List> {
  }

  public static class Pain extends AbstractPain implements Cloneable {
    public String getName() {
      return "foo";
    }

    public String getNameOfTheGame() {
      return "foo";
    }
  }

  public static class PainAndSuffering extends Pain implements Serializable {
  }

  public static class Suffering implements SomeGenInt {
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public abstract static class BasicTrouble<C, E> implements OtherType<E> {
  }

  public static class RealTrouble<X> extends BasicTrouble<X, String> {
  }

  public static class Basic<X extends Number, Y extends Collection>
      implements SomeGenInt<X, Y> {
  }

  public static class BasicInteger<Z extends Collection> extends Basic<Integer, Z> {
  }

  public static class BasicIntegerList extends BasicInteger<List>
      implements OtherType<String> {
  }

  public static class HasField {
    public int field;
  }

  public static class HidesField extends HasField {
    public int field;
  }

  private static List<Class> list(Class... items) {
    return Arrays.asList(items);
  }

  private static Type[] types(Class... items) {
    return items;
  }

  @Test
  public void testSuperclasses() {
    List<Class> supers =
        Lists.newArrayList(Introspection.superclassesOf(Pain.class));
    List<Class> expected = ImmutableList.<Class>of(Pain.class, AbstractPain.class);
    assertThat(supers, is(expected));
  }

  @Test
  public void testSuperclassesObject() {
    List<Class> supers =
        Lists.newArrayList(Introspection.superclassesOf(Object.class));
    List<Class> expected = ImmutableList.of();
    assertThat(supers, is(expected));
  }

  @Test
  public void testSuperclassesInterface() {
    List<Class> supers =
        Lists.newArrayList(Introspection.superclassesOf(OtherType.class));
    List<Class> expected = ImmutableList.of();
    assertThat(supers, is(expected));
  }

  @Test
  public void testParameterizedInterfaceArg() {
    assertThat(
        Introspection.hasParameterizedInterfaceTypes(Pain.class, SomeGenInt.class),
        is(true));

    Type[] types = Introspection.getParameterizedInterfaceTypes(
        Pain.class, SomeGenInt.class);
    assertThat(types, is(types(Integer.class, List.class)));
  }

  @Test
  public void testUnresolvedParameterizedInterface() {
    Introspection.getParameterizedInterfaceTypes(Unresolved.class, OtherType.class);
  }

  @Test
  public void testParameterizedInterfaceInSuperclass() {
    assertThat(Introspection.hasParameterizedInterfaceTypes(
        RealTrouble.class, OtherType.class), is(true));

    Type[] types = Introspection.getParameterizedInterfaceTypes(
        RealTrouble.class, OtherType.class);

    assertThat(types, equalTo(new Type[]{String.class}));
  }

  @Test
  public void testTwoLevelsOfIndirection() {
    Type[] types = Introspection.getParameterizedInterfaceTypes(TwoLevelsOfIndirectionIsMajorPain.class, OtherType.class);
    assertThat(types, equalTo(new Type[]{String.class}));
  }

  @Test
  public void testTwoLevelsOfIndirectionButNotIndirectly() {
    Type[] types = Introspection.getParameterizedInterfaceTypes(TwoLevelsOfIndirectionIsMajorPain.class, SomeOtherType.class);
    assertThat(types, equalTo(new Type[]{String.class}));
  }

  @Test
  public void testThreeLevelsOfIndirection() {
    Type[] types = Introspection.getParameterizedInterfaceTypes(ThreeLevelsOfIndirectionIsCrazy.class, OtherType.class);
    assertThat(types, equalTo(new Type[]{String.class}));
  }

  @Test
  public void testTwoLevelsOfIndirectionOnlyUsingOneLevel() {
    Type[] types = Introspection.getParameterizedInterfaceTypes(TwoLevelsOfIndirectionIsMajorPain.class, SomeOtherType.class);
    assertThat(types, equalTo(new Type[]{String.class}));
  }

  @Test
  public void testTwoLevelsOfIndirectionWhereTypeIsAlsoParameterized() {
    Type[] types = Introspection.getParameterizedInterfaceTypes(StringParameterizedList.class, OtherType.class);
    Type expected = new TypeLiteral<List<String>>() {
    }.getType();
    assertThat(types, equalTo(new Type[]{expected}));
  }

  @Test
  public void testPartialParameterizedInterfaceInSuperclass() {
    Type[] types;

    types = Introspection.getParameterizedInterfaceTypes(
        BasicIntegerList.class, SomeGenInt.class);
    assertThat(types, equalTo(new Type[]{Integer.class, List.class}));

    types = Introspection.getParameterizedInterfaceTypes(
        BasicIntegerList.class, OtherType.class);
    assertThat(types, equalTo(new Type[]{String.class}));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testGetParameterizedInterfaceTypeFailsIfNotFound() {
    // strip the generic so the compiler doesn't know this is a bad request
    Class collection = Collection.class;
    Introspection.getParameterizedInterfaceTypes(Pain.class, collection);
  }

  @Test
  public void testHasParameterizedInterfaceTypeReturnsFalseIfNotFound() {
    // strip the generic so the compiler doesn't know this is a bad request
    Class connection = Collection.class;
    assertThat(Introspection.hasParameterizedInterfaceTypes(Pain.class, connection), is(false));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testGetParameterizedInterfaceTypeFailsIfInterfaceNotParameterized() {
    Introspection.getParameterizedInterfaceTypes(
        Suffering.class, SomeGenInt.class);
  }

  @Test
  public void testHasParameterizedInterfaceTypeReturnsFalseIfInterfaceNotParameterized() {
    assertThat(Introspection.hasParameterizedInterfaceTypes(
        Suffering.class, SomeGenInt.class), is(false));
  }

  @Test
  public void testPropertyNameFromMethod() throws NoSuchMethodException {
    Method method = Pain.class.getMethod("getName");
    String property = Introspection.toPropertyName().apply(method);

    assertThat(property, is("name"));
  }

  @Test
  public void testPropertyNameFromMethodTwo() throws NoSuchMethodException {
    Method method = Pain.class.getMethod("getNameOfTheGame");
    String property = Introspection.toPropertyName().apply(method);

    assertThat(property, is("nameOfTheGame"));
  }

  @Test
  public void testGetFieldFound() {
    Field field = Introspection.getField(HasField.class, "field");
    assertThat(field.getName(), equalTo("field"));
    assertThat(field.getDeclaringClass(), equalTo((Class) HasField.class));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testGetFieldNotFound() {
    Introspection.getField(HasField.class, "notAField");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testGetFieldTooManyFields() {
    Introspection.getField(HidesField.class, "field");
  }
}
