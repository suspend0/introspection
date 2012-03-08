package ca.hullabaloo.introspect;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.inject.util.Types;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * Utility methods for introspecting a class
 */
// This should really be a library; it's been copied from project to project and has collected a lot of extra stuff
@SuppressWarnings("unused")
public class Introspection {
  // === Methods =============================================

  /**
   * Returns an iterator that loops over all classes in this class's hierarchy,
   * excluding Object.class.  The returning iterator always includes the passed type,
   * unless you pass Object.class.
   */
  public static Iterator<Class> superclassesOf(final Class type) {
    return new AbstractIterator<Class>() {
      private Class next = type;

      @Override
      protected Class computeNext() {
        Class current = next;

        // check next to weed out interfaces
        next = next.getSuperclass();

        if (current == Object.class || next == null) {
          return endOfData();
        }

        return current;
      }
    };
  }

  /**
   * Returns public methods matching the provided predicate
   *
   * @see Class#getMethods()
   */
  public static Iterator<Method> getMethods(
      Class<?> type, Predicate<Method> pred) {
    return Iterators.filter(Arrays.asList(type.getMethods()).iterator(), pred);
  }

  // === Fields ================================================

  public static Field getField(Class<?> clazz, String name) {
    Predicate<Field> pred = withName(name);
    Iterator<Field> fields = getFields(clazz, pred);

    Preconditions.checkArgument(
        fields.hasNext(),
        "No field named '%s' in %s", name, clazz);

    Field field = fields.next();

    Preconditions.checkArgument(
        !fields.hasNext(),
        "More than one field named '%s' in %s", name, clazz);

    return field;
  }

  /**
   * Returns all fields from the provided class and all of its super classes
   * which have the provided annotation
   *
   * @see Class#getDeclaredFields()
   */
  public static Iterator<Field> getFields(
      Class<?> clazz, Class<? extends Annotation> annotation) {
    Predicate<Field> pred = hasAnnotation(annotation);
    return getFields(clazz, pred);
  }

  /**
   * Walks the class heirarchy, returning all the fields which match the passed
   * predicate.
   */
  public static Iterator<Field> getFields(
      final Class<?> clazz, final Predicate<Field> predicate) {
    return new AbstractIterator<Field>() {
      private Class<?> type = clazz;

      private Iterator<Field> fields = createFieldsIter();

      @Override
      protected Field computeNext() {
        while (true) {
          if (this.fields.hasNext()) {
            return this.fields.next();
          }
          this.type = this.type.getSuperclass();
          if (this.type == null || this.type == Object.class) {
            return endOfData();
          }
          this.fields = createFieldsIter();
        }
      }

      private Iterator<Field> createFieldsIter() {
        return Iterators.filter(
            Arrays.asList(this.type.getDeclaredFields()).iterator(),
            predicate);
      }
    };
  }

  // === Predicates ============================================

  public static Predicate<Field> withName(final String fieldName) {
    return new Predicate<Field>() {
      @Override
      public boolean apply(Field field) {
        return field.getName().equals(fieldName);
      }
    };
  }

  /**
   * Returns a predicate which matches fields with the provided annotation
   */
  public static <T extends AnnotatedElement> Predicate<T> hasAnnotation(
      final Class<? extends Annotation> annotation) {
    return new Predicate<T>() {
      @Override
      public boolean apply(AnnotatedElement input) {
        return input.isAnnotationPresent(annotation);
      }
    };
  }

  /**
   * Returns a predicate with matches methods with the provided return type
   */
  public static Predicate<Method> hasReturnType(final Class<?> returnType) {
    return new Predicate<Method>() {
      @Override
      public boolean apply(Method method) {
        return returnType.isAssignableFrom(method.getReturnType());
      }
    };
  }

  /**
   * Returns a predicate with matches methods with the provided return type
   * and argument list
   * <p/>
   * NOTE: this predicate does not handle wildcard return types.  Don't use with
   * wildcard return types.
   */
  public static Predicate<Method> hasGenericReturnType(
      final Class<?> baseType, final Class<?>... typeArguments) {
    Predicate<Method> pred = new Predicate<Method>() {
      @Override
      public boolean apply(Method method) {
        Type[] types = ((ParameterizedType) method.getGenericReturnType())
            .getActualTypeArguments();
        return Arrays.deepEquals(types, typeArguments);
      }
    };

    return Predicates.and(hasReturnType(baseType), pred);
  }

  /**
   * Returns a predicate which matches fields with the provided parameter types
   */
  public static Predicate<Method> hasArgumentTypes(final Class<?>... paramTypes) {
    return new Predicate<Method>() {
      @Override
      public boolean apply(Method input) {
        Class[] actualParamTypes = input.getParameterTypes();
        if (actualParamTypes.length != paramTypes.length) {
          return false;
        }

        for (int i = 0; i < paramTypes.length; i++) {
          if (!paramTypes[i].isAssignableFrom(actualParamTypes[i])) {
            return false;
          }
        }

        return true;
      }
    };
  }

  /**
   * Returns a predicate which matches methods starting with 'get' or 'is' that have
   * zero arguments
   */
  public static Predicate<Method> isGetter() {
    return Predicates.and(hasArgumentCount(0),
        Predicates.or(hasPrefix("get"), hasPrefix("is")));
  }

  /**
   * Returns a predicate which matches method starting with 'set' which have
   * only one argument
   */
  public static Predicate<Method> isSetter() {
    return Predicates.and(hasArgumentCount(1),
        Predicates.or(hasPrefix("set"), hasPrefix("is")));
  }

  /**
   * Returns a predicate which matches method starting with 'set' which have
   * a single parameter of the provided type and a void return type.
   */
  // Silly generics array creation warning.
  @SuppressWarnings({"unchecked"})
  public static Predicate<Method> isSetter(Class<?> type) {
    return Predicates.and(
        hasPrefix("set"),
        hasArgumentTypes(type),
        hasReturnType(Void.TYPE));
  }

  /**
   * returns a function which returns the name of a member
   */
  public static <T extends Member> Function<T, String> toName() {
    return new Function<T, String>() {
      @Override
      public String apply(T member) {
        return member.getName();
      }
    };
  }

  /**
   * Returns a function which returns the property name associated with a method
   */
  public static Function<Method, String> toPropertyName() {
    return new Function<Method, String>() {
      private final Predicate<Method> hasSetPrefix = hasPrefix("set");

      private final Predicate<Method> hasIsPrefix = hasPrefix("is");

      private final Predicate<Method> hasGetPrefix = hasPrefix("get");

      @Override
      public String apply(Method method) {
        if (hasGetPrefix.apply(method)) {
          return removePrefix(3, method.getName());
        } else if (hasSetPrefix.apply(method)) {
          return removePrefix(3, method.getName());
        } else if (hasIsPrefix.apply(method)) {
          return removePrefix(2, method.getName());
        } else {
          throw new IllegalArgumentException("not property method " + method);
        }
      }

      private String removePrefix(int prefix, String str) {
        // like str.subStr(len), except sets the first character to lower case
        char[] chars = str.toCharArray();
        char c = chars[prefix];
        c = Character.toLowerCase(c);
        chars[prefix] = c;
        return new String(chars, prefix, chars.length - prefix);
      }
    };
  }

  /**
   * returns a predicate which matches method or field names with the provided
   * prefix.   This is slightly different than a raw start-with, since the prefix
   * needs to be terminated with a camel-case or underscore.
   * <p/>
   * For example, the prefix 'get' matches '{@code getFoo()}' and '{@code get_foo()}'
   * but not '{@code gettingBack()}' or {@code get()}
   */
  public static <T extends Member> Predicate<T> hasPrefix(final String prefix) {
    return new Predicate<T>() {
      int len = prefix.length();

      @Override
      public boolean apply(T input) {
        String name = input.getName();
        return name.length() > len
            && name.startsWith(prefix)
            && (Character.isUpperCase(name.charAt(len))
            || name.charAt(len) == '_');
      }
    };
  }

  /**
   * Returns a predicate which matches methods with a specific number of arguments
   */
  public static Predicate<Method> hasArgumentCount(final int count) {
    return new Predicate<Method>() {
      @Override
      public boolean apply(Method method) {
        return method.getParameterTypes().length == count;
      }
    };
  }

  // === Collection Generics ============================================

  /**
   * Returns the generic type argument for the provided collection field
   *
   * @throws IllegalArgumentException if the field's type is not a subclass of
   *                                  {@link Collection}
   */
  public static Class<?> getCollectionType(Field field) {
    Preconditions.checkArgument(
        Collection.class.isAssignableFrom(field.getType()));

    return (Class<?>) ((ParameterizedType) field.getGenericType())
        .getActualTypeArguments()[0];
  }

  /**
   * Returns the generic type argument for the return value of the provided method
   *
   * @throws IllegalArgumentException if the method's returntype is not a subclass of
   *                                  {@link Collection}
   */
  public static Class<?> getCollectionType(Method method) {
    Preconditions.checkArgument(
        Collection.class.isAssignableFrom(method.getReturnType()));

    return (Class<?>) ((ParameterizedType) method.getGenericReturnType())
        .getActualTypeArguments()[0];
  }

  public static Type getGenericTypeArgument(final Class<?> rootType) {
    Type[] arguments = getGenericTypeArguments(rootType);
    if (arguments.length != 1) {
      throw new IllegalArgumentException(
          "Had more than one generic argument: " + rootType);
    }

    return arguments[0];
  }

  /**
   * Returns the generic type arguments from the nearest superclass of the
   * provided type.  Usefull if you have instrumented classes.
   * <p/>
   * Note this does not inspect any generic interfaces
   */
  public static Type[] getGenericTypeArguments(final Class<?> rootType) {
    for (Class type = rootType; type != Object.class; type = type.getSuperclass()) {
      Type generic = type.getGenericSuperclass();
      if (generic instanceof ParameterizedType) {
        ParameterizedType ptype = (ParameterizedType) generic;
        return ptype.getActualTypeArguments();
      }
    }
    throw new IllegalArgumentException("not a generic type: " + rootType);
  }

  /**
   * Test if one the rootType has a parameterized implementation of an interface.
   */
  public static <I, T extends I> boolean hasParameterizedInterfaceTypes(
      final Class<T> rootType, Class<I> interfaceType) {
    return getParameterizedInterfaceTypesOrNull(rootType, interfaceType) != null;
  }

  /**
   * Return the parameterization values of the rootType class for a given interface type,
   * implemented by it or one of its superclasses.
   */
  public static <I, T extends I> Type[] getParameterizedInterfaceTypes(
      final Class<T> rootType, Class<I> interfaceType) {

    Type[] typeArgs = getParameterizedInterfaceTypesOrNull(rootType, interfaceType);
    if (typeArgs == null) {
      throw new IllegalArgumentException(
          rootType.getName() + " does not have parameterized implementation of " + interfaceType);
    }

    return typeArgs;
  }

  private static <I, T extends I> Type[] getParameterizedInterfaceTypesOrNull(
      final Class<T> rootType, Class<I> interfaceType) {

    ParameterizedType pt = resolveWithGenericInterface(rootType, interfaceType);
    if (pt == null) {
      return null;
    }

    // We try our best to resolve type variables (Note that the call to resolveTypeVariables modifies typeArgs)
    Type[] typeArgs = pt.getActualTypeArguments();
    resolveTypeVariables(rootType, typeArgs);

    // This may still have TypeVariables in it (for example if we were passed
    // (ArrayList.class, List.class) we'd return {TypeVariable "E"}; since
    // ArrayList is itself still a generic List.
    return typeArgs;
  }

  /**
   * Attempts to resolve any type variables in the provided Type[], replacing them with
   * the {@link java.lang.reflect.ParameterizedType#getActualTypeArguments() actual type arguments}.
   *
   * @return true if the passed array was modified in any way
   */
  private static boolean resolveTypeVariables(Class rootType, Type[] typeArgs) {
    boolean modified = false;
    for (int arg = 0; arg < typeArgs.length; arg++) {
      if (typeArgs[arg] instanceof TypeVariable) {
        // cast safe b/c we're only dealing with classes
        @SuppressWarnings({"unchecked"})
        TypeVariable<Class> var = (TypeVariable<Class>) typeArgs[arg];
        ParameterizedType pt = resolveParameterizedType(rootType, var);
        // if null, we can't resolve the variable
        if (pt != null) {
          // actual arguments are in the same position as the declaration, so we just
          // search one array and then read from the other
          int idx = -1;
          for (TypeVariable declared : var.getGenericDeclaration().getTypeParameters()) {
            idx++;
            if (declared.equals(var)) {
              Type actual = pt.getActualTypeArguments()[idx];
              typeArgs[arg] = actual;
              modified = true;
              // We may have resolved to a TypeVariable or a partially-resolved ParameterizedType
              // so look again at this slot.
              arg--;
            }
          }
        }
      } else if (typeArgs[arg] instanceof ParameterizedType) {
        ParameterizedType pt = (ParameterizedType) typeArgs[arg];
        Type[] ptArgs = pt.getActualTypeArguments();
        if (resolveTypeVariables(rootType, ptArgs)) {
          typeArgs[arg] = Types.newParameterizedType(pt.getRawType(), ptArgs);
          modified = true;
        }
      }
    }
    return modified;
  }

  /**
   * Walks the super-type and interface graph until a {@link ParameterizedType} that matches the provided
   * {@link java.lang.reflect.TypeVariable#getGenericDeclaration() generic declaration} is found.
   *
   * @return null if nothing can be found.
   */
  private static ParameterizedType resolveParameterizedType(Class rootType, TypeVariable<Class> find) {
    Iterator<Class> supers = superclassesOf(rootType);
    Class subclass = null;
    while (supers.hasNext()) {
      Class c = supers.next();
      if (subclass != null && c.equals(find.getGenericDeclaration())) {
        // cast safe b/c this is a generic declaration
        return (ParameterizedType) subclass.getGenericSuperclass();
      }

      ParameterizedType pt = resolveWithGenericInterface(c, find.getGenericDeclaration());
      if (pt != null) {
        return pt;
      }

      subclass = c;
    }

    return null;
  }

  /** Find the ParameterizedType corresponding to the 'interfaceType' implemented by 'type' */
  private static ParameterizedType resolveWithGenericInterface(Class type, final Class interfaceType) {
    // Crawl the type and all its interfaces recursively to find the parameterized type matching the interface
    Queue<Class> toVisit = Lists.newLinkedList();
    toVisit.add(type);

    while (!toVisit.isEmpty()) {
      Class currentType = toVisit.remove();
      for (Type i : currentType.getGenericInterfaces()) {
        if (i instanceof ParameterizedType) {
          ParameterizedType pt = (ParameterizedType) i;
          if (pt.getRawType().equals(interfaceType)) {
            return pt;
          }
        }
      }

      toVisit.addAll(getSuperclassAndInterfaces(currentType));
    }

    return null;
  }

  private static Set<Class> getSuperclassAndInterfaces(Class type) {
    Set<Class> superclassAndInterfaces = Sets.newHashSet();
    if(type.getSuperclass() != null) {
      superclassAndInterfaces.add(type.getSuperclass());
    }
    superclassAndInterfaces.addAll(Lists.newArrayList(type.getInterfaces()));
    return superclassAndInterfaces;
  }
}
