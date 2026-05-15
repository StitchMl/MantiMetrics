package com.mantimetrics.model;

import java.util.Objects;

/**
 * Immutable class-level dataset row.
 */
@SuppressWarnings("unused")
public final class ClassData extends AbstractDatasetRow {

 private final String className;

 /**
 * Builds an immutable class row from its builder.
 *
 * @param builder builder containing the class-row state
 */
 private ClassData(Builder builder) {
 super(builder.buildCommon());
 this.className = Objects.requireNonNull(builder.className, "className");
 }

 /**
 * Returns the fully qualified or display name of the class entity.
 *
 * @return class name
 */
 public String getClassName() { return className; }

 /**
 * {@inheritDoc}
 */
 @Override
 public String getUniqueKey() { return data.path() + "#" + className; }

 /**
 * {@inheritDoc}
 */
 @Override
 public String toCsvLine() {
 return DatasetCsvFormatter.format(data, className);
 }

 /**
 * Creates a builder pre-populated with the current row values.
 *
 * @return builder initialized from the current row
 */
 public Builder toBuilder() {
 return new Builder()
 .copyCommonFrom(data)
 .className(className);
 }

 /**
 * Builder for immutable {@link ClassData} instances.
 */
 public static final class Builder extends MetricDatasetRowBuilder<Builder> {
 private String className;

 /**
 * Sets the class name for the row being built.
 *
 * @param value class name
 * @return current builder
 */
 public Builder className(String value) {
 this.className = Objects.requireNonNull(value, "className");
 return this;
 }

 /**
 * Builds the immutable class row.
 *
 * @return immutable class row
 */
 public ClassData build() {
 validateCommon();
 Objects.requireNonNull(className, "className missing");
 return new ClassData(this);
 }

 /**
 * {@inheritDoc}
 */
 @Override
 protected Builder self() {
 return this;
 }
 }
}
