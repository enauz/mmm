package bio.fkaiser.mmm.model.metrics;

import bio.fkaiser.mmm.model.Itemset;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An {@link EvaluationMetric} dependent on extracted {@link Itemset} observations.
 *
 * @author fk
 */
public interface ExtractionDependentMetric<LabelType extends Comparable<LabelType>> extends EvaluationMetric<LabelType> {

    Predicate<EvaluationMetric<?>> EXTRACTION_DEPENDENT_METRIC_FILTER = evaluationMetric -> evaluationMetric instanceof ExtractionDependentMetric;

    Set<Itemset<LabelType>> filterItemsets(Set<Itemset<LabelType>> itemsets, Map<Itemset<LabelType>, List<Itemset<LabelType>>> extractedItemsets);

    void filterExtractedItemsets();
}
