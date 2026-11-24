package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.CosineCorrectionOptions;
import MCD.consistency.experiments.MatrixGenerator;
import MCD.consistency.reproduction.ReproductionFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Parameters of OUR predeclared study, not undocumented parameters attributed to the authors. */
public record KurennykhExperimentPlan(int schemaVersion, String profile, List<Integer> sizes, int matricesPerSize,
        long masterSeed, int maxCorrections, double targetCi, double minimumComparison, double maximumComparison,
        int warmupPerSize, int maxWallSeconds, int runTimeoutSeconds, long maxArchiveBytes, String generator,
        String primarySquaredDeviation, String primaryVariation, String weightMethod, String tieRule,
        String meanConfidenceInterval, Integer authorSampleSizePerDimension) {
    public KurennykhExperimentPlan {
        if (schemaVersion!=1 || profile==null || !List.of("SMOKE","REPORT").contains(profile) || sizes==null || sizes.isEmpty()
                || sizes.stream().anyMatch(n->n==null || n<3 || n>10) || sizes.stream().distinct().count()!=sizes.size()
                || matricesPerSize<1 || matricesPerSize>1000 || maxCorrections<0 || targetCi!=.1
                || minimumComparison!=1d/9 || maximumComparison!=9 || warmupPerSize<0 || warmupPerSize>100
                || maxWallSeconds<1 || runTimeoutSeconds<1 || maxArchiveBytes<1 || !"DISCRETE_EXPERT".equals(generator)
                || !"E_FULL_FORMULA_11".equals(primarySquaredDeviation) || !"V_UPPER".equals(primaryVariation)
                || !"EXISTING_PERRON_RIGHT_EIGENVECTOR".equals(weightMethod)
                || !"EXACT_DOUBLE_TIES_LOWEST_ORIGINAL_INDEX_ORDINAL_RANKS".equals(tieRule)
                || !"NORMAL_CLT_95_Z_1.959963984540054".equals(meanConfidenceInterval) || authorSampleSizePerDimension!=null)
            throw new IllegalArgumentException("Invalid or unsupported Kurennykh reproduction plan");
        sizes=List.copyOf(sizes);
    }
    public static KurennykhExperimentPlan load(Path root,String profile) throws IOException {
        var p=KurennykhResultWriter.JSON.fromJson(ReproductionFiles.readJson(root.resolve("config/"+profile+".json")),KurennykhExperimentPlan.class);
        ReproductionFiles.require(p.profile().equals(profile),"Profile name mismatch");return p;
    }
    public int totalMatrices() { return sizes.size()*matricesPerSize; }
    public CosineCorrectionOptions options() { return new CosineCorrectionOptions(targetCi,maxCorrections,1e-12,0,minimumComparison,maximumComparison); }
    public long seed(int n,int sample) { return MatrixGenerator.seed(masterSeed,MatrixGenerator.Type.DISCRETE_EXPERT,n,sample); }
    public static String id(int n,int sample) { return String.format(Locale.ROOT,"K2018_n%02d_m%04d",n,sample); }
}
