/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.resolver;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.mule.metadata.api.model.MetadataFormat.JAVA;
import static org.mule.metadata.api.utils.MetadataTypeUtils.getDefaultValue;
import static org.mule.metadata.java.api.utils.JavaTypeUtils.getType;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.extension.api.util.ExtensionMetadataTypeUtils.isMap;
import static org.mule.runtime.extension.api.util.ExtensionMetadataTypeUtils.isParameterGroup;
import static org.mule.runtime.module.extension.internal.util.IntrospectionUtils.getAlias;
import static org.mule.runtime.module.extension.internal.util.IntrospectionUtils.getFields;
import org.mule.metadata.api.annotation.TypeIdAnnotation;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.MetadataType;
import org.mule.metadata.api.model.ObjectFieldType;
import org.mule.metadata.api.model.ObjectType;
import org.mule.metadata.api.utils.MetadataTypeUtils;
import org.mule.metadata.api.visitor.MetadataTypeVisitor;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.exception.IllegalParameterModelDefinitionException;
import org.mule.runtime.module.extension.internal.runtime.objectbuilder.DefaultObjectBuilder;
import org.mule.runtime.module.extension.internal.runtime.objectbuilder.DefaultResolverSetBasedObjectBuilder;
import org.mule.runtime.module.extension.internal.runtime.objectbuilder.ObjectBuilder;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * A {@link ValueResolver} wrapper which generates and returns default instances
 * if the {@link #delegate} returns {@code null}.
 * <p>
 * The values are generated according to the rules described in {@link NullSafe}.
 * <p>
 * Instances are to be obtained through the {@link #of(ValueResolver, MetadataType, MuleContext, ObjectTypeParametersResolver)} )}
 * factory method
 *
 * @param <T> the generic type of the produced values.
 * @since 4.0
 */
public class NullSafeValueResolverWrapper<T> implements ValueResolver<T> {

  private final ValueResolver<T> delegate;
  private final ValueResolver<T> fallback;

  /**
   * Creates a new instance
   *
   * @param delegate    the {@link ValueResolver} to wrap
   * @param type        the type of the value this resolver returns
   * @param muleContext the current {@link MuleContext}
   * @param <T>         the generic type of the produced values
   * @return a new null safe {@link ValueResolver}
   * @throws IllegalParameterModelDefinitionException if used on parameters of not supported types
   */
  public static <T> ValueResolver<T> of(ValueResolver<T> delegate,
                                        MetadataType type,
                                        MuleContext muleContext,
                                        ObjectTypeParametersResolver parametersResolver) {
    checkArgument(delegate != null, "delegate cannot be null");

    Reference<ValueResolver> value = new Reference<>();
    type.accept(new MetadataTypeVisitor() {

      @Override
      public void visitObject(ObjectType objectType) {
        Class clazz = getType(objectType);

        if (isMap(objectType)) {
          value.set(MapValueResolver.of(clazz, emptyList(), emptyList()));
          return;
        }

        String requiredFields = objectType.getFields().stream()
            .filter(f -> f.isRequired() && !isParameterGroup(f))
            .map(MetadataTypeUtils::getLocalPart)
            .collect(joining(", "));

        if (!isBlank(requiredFields)) {
          throw new IllegalParameterModelDefinitionException(
                                                             format("Class '%s' cannot be used with '@%s' parameter since it contains non optional fields: [%s]",
                                                                    clazz.getName(), NullSafe.class.getSimpleName(),
                                                                    requiredFields));
        }

        ResolverSet resolverSet = new ResolverSet();
        for (Field field : getFields(clazz)) {
          ValueResolver<?> resolver = null;
          ObjectFieldType objectField = objectType.getFieldByName(getAlias(field)).orElse(null);
          if (objectField == null) {
            continue;
          }

          Optional<String> defaultValue = getDefaultValue(objectField);
          if (defaultValue.isPresent()) {
            resolver = new TypeSafeExpressionValueResolver(defaultValue.get(), field.getType(), muleContext);

          } else if (isParameterGroup(objectField)) {
            DefaultObjectBuilder groupBuilder = new DefaultObjectBuilder(getType(objectField.getValue()));
            resolverSet.add(field.getName(), new ObjectBuilderValueResolver<>(groupBuilder));

            ObjectType childGroup = (ObjectType) objectField.getValue();
            parametersResolver.resolveParameters(childGroup, groupBuilder);
            parametersResolver.resolveParameterGroups(childGroup, groupBuilder);

          } else {
            NullSafe nullSafe = field.getAnnotation(NullSafe.class);
            if (nullSafe != null) {
              MetadataType nullSafeType;
              if (Object.class.equals(nullSafe.defaultImplementingType())) {
                nullSafeType = objectField.getValue();
              } else {
                nullSafeType = new BaseTypeBuilder(JAVA).objectType()
                    .with(new TypeIdAnnotation(nullSafe.defaultImplementingType().getName()))
                    .build();
              }

              resolver = NullSafeValueResolverWrapper.of(new StaticValueResolver<>(null), nullSafeType,
                                                         muleContext, parametersResolver);
            }
          }

          if (resolver != null) {
            resolverSet.add(field.getName(), resolver);
          }
        }

        ObjectBuilder<T> objectBuilder =
            new DefaultResolverSetBasedObjectBuilder(clazz, resolverSet);

        value.set(new ObjectBuilderValueResolver(objectBuilder));
      }

      @Override
      public void visitArrayType(ArrayType arrayType) {
        Class collectionClass = getType(arrayType);
        value.set(CollectionValueResolver.of(collectionClass, emptyList()));
      }

      @Override
      protected void defaultVisit(MetadataType metadataType) {
        throw new IllegalParameterModelDefinitionException(
                                                           format("Cannot use @%s on type '%s'", NullSafe.class.getSimpleName(),
                                                                  getType(metadataType)));
      }
    });

    return new NullSafeValueResolverWrapper<>(delegate, value.get());
  }

  private NullSafeValueResolverWrapper(ValueResolver<T> delegate, ValueResolver<T> fallback) {
    this.delegate = delegate;
    this.fallback = fallback;
  }

  @Override
  public T resolve(Event event) throws MuleException {
    T value = delegate.resolve(event);

    if (value == null) {
      value = fallback.resolve(event);
    }

    return value;
  }

  @Override
  public boolean isDynamic() {
    return delegate.isDynamic();
  }

}
