package de.bioforscher.mmm.classify.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.bioforscher.mmm.model.Itemset;
import de.bioforscher.singa.chemistry.algorithms.superimposition.consensus.ConsensusAlignment;
import de.bioforscher.singa.chemistry.algorithms.superimposition.consensus.ConsensusContainer;
import de.bioforscher.singa.chemistry.parser.pdb.structures.StructureRepresentation;
import de.bioforscher.singa.chemistry.physical.branches.StructuralMotif;
import de.bioforscher.singa.mathematics.graphs.trees.BinaryTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Represents a library of {@link Itemset}s, either by the largest cluster consensus observation or for all observations.
 *
 * @author fk
 */
public class ItemsetLibrary {

    private static final TypeReference<ItemsetLibrary> TYPE_REFERENCE = new TypeReference<ItemsetLibrary>() {
    };
    private static final Logger logger = LoggerFactory.getLogger(ItemsetLibrary.class);
    private List<ItemsetLibraryEntry> entries;

    private ItemsetLibrary() {
    }

    private ItemsetLibrary(List<ItemsetLibraryEntry> entries) {
        this.entries = entries;
    }

    /**
     * Creates a {@link ItemsetLibrary} out of clustered {@link Itemset} observations. The largest cluster is determined by their consensus observation.
     *
     * @param clusteredItemsets   The clustered {@link Itemset}s for which an {@link ItemsetLibrary} should be created.
     * @param minimalClusterRatio The minimal ratio of which a cluster must exist for the consensus {@link Itemset} to be included in the library.
     * @return A new {@link ItemsetLibrary}.
     */
    public static ItemsetLibrary of(Map<Itemset<String>, ConsensusAlignment> clusteredItemsets, int minimalItemsetSize, double minimalClusterRatio) {
        logger.info("creating library for {} itemsets", clusteredItemsets.size());
        List<ItemsetLibraryEntry> entries = new ArrayList<>();
        for (Map.Entry<Itemset<String>, ConsensusAlignment> entry : clusteredItemsets.entrySet()) {
            Itemset<String> itemset = entry.getKey();
            if (itemset.getItems().size() < minimalItemsetSize) {
                continue;
            }
            // determine largest cluster
            TreeSet<BinaryTree<ConsensusContainer>> clusters = new TreeSet<>(Comparator.comparing(BinaryTree::size));
            ConsensusAlignment consensusAlignment = entry.getValue();
            clusters.addAll(consensusAlignment.getClusters());
            // determine total observation count
            int observationCount = consensusAlignment.getTopConsensusTree().getLeafNodes().size();
            BinaryTree<ConsensusContainer> largestCluster = clusters.last();
            int largestClusterCount = largestCluster.getLeafNodes().size();
            if ((largestClusterCount / (double) observationCount) < minimalClusterRatio) {
                logger.info("itemset {} not added to the library, largest cluster of size {} not sufficient", itemset, largestClusterCount);
                continue;
            } else {
                logger.info("itemset {} added to the library, largest cluster has size {}", itemset, largestClusterCount);
            }
            StructuralMotif structuralMotif = clusters.last().getRoot().getData().getStructuralMotif();
            String pdbLines = StructureRepresentation.composePdbRepresentation(structuralMotif.getOrderedLeafSubstructures());
            ItemsetLibraryEntry libraryEntry = new ItemsetLibraryEntry(entry.getKey(), pdbLines);
            entries.add(libraryEntry);
        }
        return new ItemsetLibrary(entries);
    }

    /**
     * Creates a {@link ItemsetLibrary} out of clustered {@link Itemset} observations. The largest cluster is determined by their consensus observation.
     *
     * @param itemsets The extracted {@link Itemset}s for which an {@link ItemsetLibrary} should be created.
     * @return A new {@link ItemsetLibrary}.
     */
    public static ItemsetLibrary of(List<Itemset<String>> itemsets, int minimalItemsetSize) {
        List<ItemsetLibraryEntry> entries = new ArrayList<>();
        for (Itemset<String> itemset : itemsets) {
            if (itemset.getItems().size() < minimalItemsetSize) {
                continue;
            }
            // TODO implement proper exception
            StructuralMotif structuralMotif = itemset.getStructuralMotif()
                                                     .orElseThrow(() -> new UnsupportedOperationException("itemset libraries can only be constructed out of itemset observations"));
            String pdbLines = StructureRepresentation.composePdbRepresentation(structuralMotif.getLeafSubstructures());
            ItemsetLibraryEntry entry = new ItemsetLibraryEntry(itemset, pdbLines);
            entries.add(entry);
        }
        return new ItemsetLibrary(entries);
    }

    /**
     * Reads an {@link ItemsetLibrary} from the given path.
     *
     * @param libraryPath The {@link Path} of the {@link ItemsetLibrary}.
     * @return The {@link ItemsetLibrary}
     * @throws IOException
     */
    public static ItemsetLibrary readFromPath(Path libraryPath) throws IOException {
        try (GZIPInputStream zip = new GZIPInputStream(new FileInputStream(libraryPath.toFile()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(zip, "UTF-8"))) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            return mapper.readValue(reader, TYPE_REFERENCE);
        }
    }

    /**
     * Reads an {@link ItemsetLibrary} from the given Json representation.
     *
     * @param json The Json representation of the {@link ItemsetLibrary}.
     * @return The {@link ItemsetLibrary}.
     * @throws IOException
     */
    public static ItemsetLibrary fromJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        return mapper.readValue(json, TYPE_REFERENCE);
    }

    /**
     * Writes the {@link ItemsetLibrary} to the given path.
     *
     * @param libraryPath The {@link Path} to which the {@link ItemsetLibrary} should be written.
     * @throws IOException
     */
    public void writeToPath(Path libraryPath) throws IOException {
        try (GZIPOutputStream zip = new GZIPOutputStream(new FileOutputStream(libraryPath.toFile()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip, "UTF-8"))) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);
            mapper.writeValue(writer, this);
        }
    }

    /**
     * Converts this {@link ItemsetLibrary} into a Json representation.
     *
     * @return The Json representation.
     * @throws JsonProcessingException
     */
    public String toJson() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);
        return mapper.writeValueAsString(this);
    }

    public List<ItemsetLibraryEntry> getEntries() {
        return entries;
    }

}