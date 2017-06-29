package de.bioforscher.mmm.model.analysis.association;

import JavaMI.MutualInformation;
import de.bioforscher.mmm.ItemsetMiner;
import de.bioforscher.mmm.model.Itemset;
import de.bioforscher.mmm.model.analysis.AbstractItemsetMinerAnalyzer;
import de.bioforscher.mmm.model.analysis.ItemsetMinerAnalyzerException;
import de.bioforscher.mmm.model.metrics.DistributionMetric;
import de.bioforscher.singa.core.utility.Pair;
import de.bioforscher.singa.javafx.renderer.graphs.GraphDisplayApplication;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @author fk
 */
public class MutualInformationAnalyzer<LabelType extends Comparable<LabelType>> extends AbstractItemsetMinerAnalyzer<LabelType> {

    private static final Logger logger = LoggerFactory.getLogger(MutualInformationAnalyzer.class);
    private static final double MINIMAL_MUTUAL_INFORMATION = 1.0;
    private final Class<? extends DistributionMetric> distributionMetricType;
    private TreeMap<Double, Pair<Itemset<LabelType>>> mutualInformation;

    public MutualInformationAnalyzer(ItemsetMiner<LabelType> itemsetMiner, Class<? extends DistributionMetric> distributionMetricType) {
        super(itemsetMiner);
        this.distributionMetricType = distributionMetricType;
        logger.info("calculating mutual information for distribution metric {}", distributionMetricType);

        mutualInformation = new TreeMap<>(Collections.reverseOrder());
        calculateMutualInformation();
        createGraph();
    }

    private void createGraph() {
        logger.info("creating graph of itemset associations for mutual information  > {}", MINIMAL_MUTUAL_INFORMATION);
        List<Pair<Itemset<LabelType>>> connectedPairs = mutualInformation.entrySet().stream()
                                                                         .filter(entry -> entry.getKey() > MINIMAL_MUTUAL_INFORMATION)
                                                                         .map(Map.Entry::getValue)
                                                                         .collect(Collectors.toList());

        ItemsetGraph<LabelType> itemsetGraph = new ItemsetGraph<>();
        for (Pair<Itemset<LabelType>> connectedPair : connectedPairs) {

            ItemsetNode<LabelType> nodeOne = itemsetGraph.getNodes().stream()
                                                         .filter(node -> node.getItemset().equals(connectedPair.getFirst()))
                                                         .findFirst()
                                                         .orElse(new ItemsetNode<>(itemsetGraph.nextNodeIdentifier(), connectedPair.getFirst()));

            ItemsetNode<LabelType> nodeTwo = itemsetGraph.getNodes().stream()
                                                         .filter(node -> node.getItemset().equals(connectedPair.getSecond()))
                                                         .findFirst()
                                                         .orElse(new ItemsetNode<>(itemsetGraph.nextNodeIdentifier(), connectedPair.getSecond()));

            if (!itemsetGraph.containsNode(nodeOne)) {
                itemsetGraph.addNode(nodeOne);
            }
            if (!itemsetGraph.containsNode(nodeTwo)) {
                itemsetGraph.addNode(nodeTwo);
            }

            itemsetGraph.addEdgeBetween(nodeOne, nodeTwo);
        }

        GraphDisplayApplication.graph = itemsetGraph;
        GraphDisplayApplication.renderer = new ItemsetGraphRenderer<>();
        Application.launch(GraphDisplayApplication.class);
    }

    public TreeMap<Double, Pair<Itemset<LabelType>>> getMutualInformation() {
        return mutualInformation;
    }

    private void calculateMutualInformation() {
        DistributionMetric<LabelType> distributionMetric = itemsetMiner.getEvaluationMetrics().stream()
                                                                       .filter(distributionMetricType::isInstance)
                                                                       .map(dist -> (DistributionMetric<LabelType>) dist)
                                                                       .findFirst()
                                                                       .orElseThrow(() -> new ItemsetMinerAnalyzerException("no distribution metric found to calculate mutual information"));
        List<Itemset<LabelType>> totalItemsets = itemsetMiner.getTotalItemsets();
        for (int i = 0; i < itemsetMiner.getTotalItemsets().size(); i++) {
            Itemset<LabelType> itemsetOne = totalItemsets.get(i);
            for (int j = i + 1; j < totalItemsets.size(); j++) {
                Itemset<LabelType> itemsetTwo = totalItemsets.get(j);
                // cap to smaller observations
                List<Double> itemsetOneObservations = distributionMetric.getDistributions().get(itemsetOne).getObservations();
                List<Double> itemsetTwoObservations = distributionMetric.getDistributions().get(itemsetTwo).getObservations();
                int itemsetOneDistributionSize = itemsetOneObservations.size();
                int itemsetTwoDistributionSize = itemsetTwoObservations.size();

                if (itemsetOneDistributionSize > itemsetTwoDistributionSize) {
                    itemsetOneObservations = itemsetOneObservations.subList(0, itemsetTwoDistributionSize);
                } else if (itemsetTwoDistributionSize > itemsetOneDistributionSize) {
                    itemsetTwoObservations = itemsetTwoObservations.subList(0, itemsetOneDistributionSize);
                }

                logger.debug("calculating mutual information for pair {}_{}", itemsetOne, itemsetTwo);
                double mi = MutualInformation.calculateMutualInformation(itemsetOneObservations.stream().mapToDouble(Double::doubleValue).toArray(),
                                                                         itemsetTwoObservations.stream().mapToDouble(Double::doubleValue).toArray());
                mutualInformation.put(mi, new Pair<>(itemsetOne, itemsetTwo));
            }
        }
    }
}