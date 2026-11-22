package MCD.consistency;

/** Arithmetic shared by consistency checks and the Koczkodaj index. */
final class NumericalSupport {
    private static final double LOG_TWO = Math.log(2);

    private NumericalSupport() {
    }

    /**
     * Computes log(x*y/z) without forming a possibly overflowing/underflowing
     * product. Inputs have already been validated as finite and positive.
     * Binary decomposition x = m_x * 2^e_x keeps each mantissa in [1, 2).
     */
    static double logProductRatio(double x, double y, double z) {
        int ex = binaryExponent(x);
        int ey = binaryExponent(y);
        int ez = binaryExponent(z);
        double mx = Math.scalb(x, -ex);
        double my = Math.scalb(y, -ey);
        double mz = Math.scalb(z, -ez);
        int exponent = ex + ey - ez;
        if (exponent >= -2 && exponent <= 2) {
            // Near consistency, log(x)+log(y)-log(z) loses small defects by
            // cancellation. FMA and log1p retain them, even at extreme scales.
            double denominator = Math.scalb(mz, -exponent);
            return Math.log1p(Math.fma(mx, my, -denominator) / denominator);
        }
        return exponent * LOG_TWO + Math.log(mx) + Math.log(my) - Math.log(mz);
    }

    private static int binaryExponent(double value) {
        int exponent = Math.getExponent(value);
        // getExponent returns -1023 for every subnormal. Scaling first recovers
        // its true exponent (as small as -1074) without losing the significand.
        return exponent == Double.MIN_EXPONENT - 1
                ? Math.getExponent(Math.scalb(value, 54)) - 54 : exponent;
    }
}
