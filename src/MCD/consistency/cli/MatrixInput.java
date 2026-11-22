package MCD.consistency.cli;

import MCD.consistency.PairwiseComparisonMatrix;

import java.nio.file.Path;

/** Input metadata and the immutable validated matrix. */
public record MatrixInput(String name, Path path, PairwiseComparisonMatrix matrix) {
}
