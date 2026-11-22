package MCD.consistency;

final class ComparisonFixtures {
    private ComparisonFixtures() {
    }

    static double[][] consistent() {
        return new double[][] {{1, 3, 6}, {1.0 / 3, 1, 2}, {1.0 / 6, 0.5, 1}};
    }

    static double[][] inconsistent() {
        return new double[][] {{1, 3, 4}, {1.0 / 3, 1, 2}, {0.25, 0.5, 1}};
    }

    static double[][] paperOriginal() {
        return new double[][] {
                {1, 2, 5, 9}, {0.5, 1, 3, 8},
                {0.2, 1.0 / 3, 1, 4}, {1.0 / 9, 0.125, 0.25, 1}
        };
    }

    // Exactly the displayed decimal entries; do not silently replace reciprocals.
    static double[][] paperRounded() {
        return new double[][] {
                {1, 1.2964, 5, 9}, {0.7714, 1, 3, 8},
                {0.2, 1.0 / 3, 1, 2.0488}, {1.0 / 9, 0.125, 0.4881, 1}
        };
    }

    static PairwiseComparisonMatrix triad(double a01, double a12, double a02) {
        return new PairwiseComparisonMatrix(new double[][] {
                {1, a01, a02}, {1 / a01, 1, a12}, {1 / a02, 1 / a12, 1}
        });
    }

    static PairwiseComparisonMatrix circulant(double p, double q) {
        double[] row = {1, p, q, 1 / q, 1 / p};
        double[][] data = new double[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                data[i][j] = row[(j - i + 5) % 5];
            }
        }
        return new PairwiseComparisonMatrix(data);
    }
}
