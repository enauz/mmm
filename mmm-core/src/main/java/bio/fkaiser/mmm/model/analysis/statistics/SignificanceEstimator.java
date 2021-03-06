package bio.fkaiser.mmm.model.analysis.statistics;

import bio.fkaiser.mmm.ItemsetMiner;
import bio.fkaiser.mmm.model.Distribution;
import bio.fkaiser.mmm.model.Itemset;
import bio.fkaiser.mmm.model.configurations.analysis.statistics.SignificanceEstimatorConfiguration;
import bio.fkaiser.mmm.model.metrics.CohesionMetric;
import bio.fkaiser.mmm.model.metrics.ConsensusMetric;
import bio.fkaiser.mmm.model.metrics.DistributionMetric;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * Uses the {@link DistributionSampler} to estimate the significance of found {@link Itemset}s regarding a defined {@link DistributionMetric}.
 * Currently supported are {@link CohesionMetric} and {@link ConsensusMetric}.
 *
 * @author fk
 */
@JsonTypeName("SIGNIFICANCE_ESTIMATOR")
public class SignificanceEstimator<LabelType extends Comparable<LabelType>> {

    private static final Logger logger = LoggerFactory.getLogger(SignificanceEstimator.class);

    private final ItemsetMiner<LabelType> itemsetMiner;
    private final SignificanceEstimatorType type;
    private final double ksCutoff;
    private final double significanceCutoff;
    private final TreeMap<Significance, Itemset<LabelType>> significantItemsets;
    private Map<Itemset<LabelType>, Distribution> backgroundDistributions;

    public SignificanceEstimator(ItemsetMiner<LabelType> itemsetMiner, SignificanceEstimatorConfiguration configuration) {
        this.itemsetMiner = itemsetMiner;
        type = configuration.getSignificanceType();
        ksCutoff = configuration.getKsCutoff();
        significanceCutoff = configuration.getSignificanceCutoff();
        significantItemsets = new TreeMap<>();
        sampleDistributions(configuration.getLevelOfParallelism(), configuration.getSampleSize());
    }

    /**
     * Samples background distributions with the given level of parallelism.
     *
     * @param levelOfParallelism The desired level of parallelism.
     * @param sampleSize         The desired sample size.
     */
    private void sampleDistributions(int levelOfParallelism, int sampleSize) {
        DistributionSampler<LabelType> distributionSampler = new DistributionSampler<>(itemsetMiner, type.getDistributionMetric(), levelOfParallelism, sampleSize);
        backgroundDistributions = distributionSampler.getBackgroundDistributions();
        backgroundDistributions.keySet().forEach(this::determineSignificance);
    }

    /**
     * Determines the significance for the given {@link Itemset} by modeling the background normal distribution.
     *
     * @param itemset The {@link Itemset} for which the significance should be calculated.
     */
    private void determineSignificance(Itemset<LabelType> itemset) {

        double[] values = backgroundDistributions.get(itemset).getObservations().stream()
                                                 .mapToDouble(Double::doubleValue).toArray();

        // model normal distribution
        DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics(values);
        double mean = descriptiveStatistics.getMean();
        double standardDeviation = descriptiveStatistics.getStandardDeviation();
        NormalDistribution normalDistribution = new NormalDistribution(mean, standardDeviation);

        // calculate KS p-value to estimate quality of fit
        double ks = TestUtils.kolmogorovSmirnovTest(normalDistribution, values, false);
        if (ks < ksCutoff) {
            logger.warn("itemset {} background distribution of type {} violates KS-cutoff, skipping", itemset, type);
            return;
        }

        double pValue = Double.NaN;
        if (type == SignificanceEstimatorType.COHESION) {
            pValue = normalDistribution.cumulativeProbability(itemset.getCohesion());
        } else if (type == SignificanceEstimatorType.CONSENSUS) {
            pValue = normalDistribution.cumulativeProbability(itemset.getConsensus());
        } else if (type == SignificanceEstimatorType.AFFINITY) {
            pValue = normalDistribution.cumulativeProbability(itemset.getAffinity());
        }

        logger.debug("p-value for itemset {} is {}", itemset.toSimpleString(), pValue);
        if (pValue < significanceCutoff) {
            Significance significance = new Significance(pValue, ks);
            significantItemsets.put(significance, itemset);
            itemset.setpValue(pValue);
            itemset.setKs(ks);
            logger.info("itemset {} is significant with {}", itemset.toSimpleString(), significance);
        } else {
            logger.info("itemset {} is insignificant", itemset.toSimpleString());
        }
    }

    public TreeMap<Significance, Itemset<LabelType>> getSignificantItemsets() {
        return significantItemsets;
    }

    /**
     * A data object to hold statistical measures, that is the p-value and the Kolmogorov-Smirnov value.
     */
    public static class Significance implements Comparable<Significance> {

        private final double pvalue;
        private final double ks;

        public Significance(double pvalue, double ks) {
            this.pvalue = pvalue;
            this.ks = ks;
        }

        @Override public String toString() {
            return "Significance{" +
                   "pvalue=" + pvalue +
                   ", ks=" + ks +
                   '}';
        }

        @Override public int compareTo(Significance o) {
            return Double.compare(pvalue, o.pvalue);
        }

        public double getKs() {
            return ks;
        }

        public double getPvalue() {
            return pvalue;
        }
    }
}
