package MCD.consistency.reproduction.kurennykh;

/** One measured matrix, flat stable CSV schema. Indices are zero-based, positions one-based. */
public record KurennykhRunResult(String profile,String planSha256,String id,int size,int sample,long generationSeed,
        String inputSha256,String status,String detail,double elapsedMillis,double initialCi,double finalCi,
        double initialK,double finalK,double deltaCi,int iterations,int acceptedCorrections,int uniqueTouchedComparisons,
        int changedComparisons,double eFull,double eUpper,double vUpper,Double vChanged,double vOffdiagonal,double logDeviation,
        boolean initiallyAcceptable,boolean targetReached,boolean correctionRequired,boolean successfullyCorrected,
        int originalLeader,int finalLeader,int initialPosition,int finalPosition,int deltaPosition,
        boolean leaderChanged,boolean originalBestRankChanged,int initialTiedLeaders,int finalTiedLeaders,
        double targetCi,int maxCorrections,double minimumComparison,double maximumComparison) { }
