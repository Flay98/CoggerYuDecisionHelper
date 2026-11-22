package MCD.consistency;

/** Immutable row-major upper triangle; implementation detail, not a new matrix model. */
final class GeneticChromosome {
    private final int size;
    private final double[] genes;
    private final PairwiseComparisonMatrix matrix;

    private GeneticChromosome(int size, double[] genes, PairwiseComparisonMatrix matrix) {
        this.size = size;
        this.genes = genes.clone();
        this.matrix = matrix;
    }

    static GeneticChromosome fromMatrix(PairwiseComparisonMatrix matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("Matrix must not be null");
        }
        double[] genes = new double[geneCount(matrix.size())];
        int gene = 0;
        for (int i = 0; i < matrix.size(); i++) {
            if (matrix.get(i, i) != 1) {
                throw new IllegalArgumentException("Genetic representation requires diagonal entries exactly equal to 1");
            }
            for (int j = i + 1; j < matrix.size(); j++) {
                double value = matrix.get(i, j);
                if (!Double.isFinite(1 / value)) {
                    throw new IllegalArgumentException("Upper comparisons must have finite reconstructed reciprocals");
                }
                genes[gene++] = value;
            }
        }
        // Preserve the actual input phenotype, including accepted reciprocal
        // roundoff. Descendants reconstruct lower entries by the reciprocal rule.
        return new GeneticChromosome(matrix.size(), genes, matrix);
    }

    static GeneticChromosome fromGenes(int size, double[] genes) {
        if (genes == null || genes.length != geneCount(size)) {
            throw new IllegalArgumentException("Wrong upper-triangle chromosome length");
        }
        double[][] data = new double[size][size];
        int gene = 0;
        for (int i = 0; i < size; i++) {
            data[i][i] = 1;
            for (int j = i + 1; j < size; j++) {
                double value = genes[gene++];
                double reciprocal = 1 / value;
                if (!Double.isFinite(value) || value <= 0 || !Double.isFinite(reciprocal) || reciprocal <= 0) {
                    throw new IllegalArgumentException("Genes and reciprocals must be finite and positive");
                }
                data[i][j] = value;
                data[j][i] = reciprocal;
            }
        }
        return new GeneticChromosome(size, genes, new PairwiseComparisonMatrix(data));
    }

    static int geneCount(int size) {
        long count = (long) size * (size - 1) / 2;
        if (size < 2 || count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Unsupported chromosome dimension");
        }
        return (int) count;
    }

    int size() {
        return size;
    }

    double gene(int index) {
        return genes[index];
    }

    double[] genes() {
        return genes.clone();
    }

    int length() {
        return genes.length;
    }

    PairwiseComparisonMatrix matrix() {
        return matrix;
    }

}
