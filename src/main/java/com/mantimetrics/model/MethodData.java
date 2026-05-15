package com.mantimetrics.model;

import java.util.Objects;

/**
 * Immutable method-level dataset row.
 */
@SuppressWarnings("unused")
public final class MethodData extends AbstractDatasetRow {

 private final String methodSignature;

 /**
 * Builds an immutable method row from its builder.
 *
 * @param builder builder containing the method-row state
 */
 private MethodData(Builder builder) {
 super(builder.buildCommon());
 this.methodSignature = Objects.requireNonNull(builder.methodSignature, "methodSignature");
 }

 /**
 * Returns the method signature used as entity identifier.
 *
 * @return method signature
 */
 public String getMethodSignature() { return methodSignature; }

 /**
 * {@inheritDoc}
 */
 @Override
 public String getUniqueKey() { return data.path() + "#" + methodSignature; }

 /**
 * {@inheritDoc}
 */
 @Override
 public String toCsvLine() {
 return DatasetCsvFormatter.format(data, methodSignature);
 }

 /**
 * Compares method rows using their serialized dataset content.
 *
 * @param other object to compare
 * @return {@code true} when both rows carry the same relevant data
 */
 @Override
 public boolean equals(Object other) {
 if (this == other) return true;
 if (!(other instanceof MethodData that)) return false;
 return isBuggy() == that.isBuggy()
 && getProjectName().equals(that.getProjectName())
 && getPath().equals(that.getPath())
 && methodSignature.equals(that.methodSignature)
 && getReleaseId().equals(that.getReleaseId())
 && getMetrics().equals(that.getMetrics())
 && getCommitHashes().equals(that.getCommitHashes());
 }

 /**
 * Returns a hash code consistent with {@link #equals(Object)}.
 *
 * @return row hash code
 */
 @Override
 public int hashCode() {
 return Objects.hash(
 getProjectName(),
 getPath(),
 methodSignature,
 getReleaseId(),
 getMetrics(),
 getCommitHashes(),
 isBuggy());
 }

 /**
 * Returns a concise debug representation of the method row.
 *
 * @return debug representation
 */
 @Override
 public String toString() {
 return "MethodData[" + getProjectName() + "/" + getPath() + "@"
 + getReleaseId() + ", signature=" + methodSignature + "]";
 }

 /**
 * Creates a builder pre-populated with the current row values.
 *
 * @return builder initialized from the current row
 */
 public Builder toBuilder() {
 return new Builder()
 .copyCommonFrom(data)
 .methodSignature(methodSignature);
 }

 /**
 * Builder for immutable {@link MethodData} instances.
 */
 public static final class Builder extends MetricDatasetRowBuilder<Builder> {
 private String methodSignature;

 /**
 * Sets the method signature for the row being built.
 *
 * @param value method signature
 * @return current builder
 */
 public Builder methodSignature(String value) {
 this.methodSignature = Objects.requireNonNull(value, "methodSignature");
 return this;
 }

 /**
 * Builds the immutable method row.
 *
 * @return immutable method row
 */
 public MethodData build() {
 validateCommon();
 Objects.requireNonNull(methodSignature, "methodSignature missing");
 return new MethodData(this);
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
