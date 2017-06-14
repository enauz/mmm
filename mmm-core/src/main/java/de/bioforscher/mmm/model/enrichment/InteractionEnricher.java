package de.bioforscher.mmm.model.enrichment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bioforscher.mmm.model.DataPoint;
import de.bioforscher.mmm.model.Item;
import de.bioforscher.pliprestprovider.model.InteractionType;
import de.bioforscher.pliprestprovider.model.PlipInteraction;
import de.bioforscher.singa.chemistry.descriptive.elements.ElementProvider;
import de.bioforscher.singa.chemistry.physical.atoms.Atom;
import de.bioforscher.singa.chemistry.physical.atoms.RegularAtom;
import de.bioforscher.singa.chemistry.physical.families.LigandFamily;
import de.bioforscher.singa.chemistry.physical.leaves.AtomContainer;
import de.bioforscher.singa.chemistry.physical.leaves.LeafSubstructure;
import de.bioforscher.singa.chemistry.physical.model.LeafIdentifier;
import de.bioforscher.singa.mathematics.graphs.model.Node;
import de.bioforscher.singa.mathematics.vectors.Vector;
import de.bioforscher.singa.mathematics.vectors.Vector3D;
import de.bioforscher.singa.mathematics.vectors.Vectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author fk
 */
public class InteractionEnricher implements DataPointEnricher<String> {

    public static final Map<InteractionType, String> INTERACTION_LABEL_MAP;
    public static final List<InteractionType> ACTIVE_INTERACTIONS;
    private static final Logger logger = LoggerFactory.getLogger(InteractionEnricher.class);
    private static final String PLIP_REST_PROVIDER_URL = "https://biosciences.hs-mittweida.de/plip/interaction/";
    private static final String PLIP_REST_PROVIDER_CREDENTIALS = "itemset-miner:Eimo~Naeng4ahb7i";


    static {
        INTERACTION_LABEL_MAP = new HashMap<>();
        INTERACTION_LABEL_MAP.put(InteractionType.HALOGEN_BOND, "hal");
        INTERACTION_LABEL_MAP.put(InteractionType.HYDROGEN_BOND, "hyb");
        INTERACTION_LABEL_MAP.put(InteractionType.HYDROPHOBIC, "hyp");
        INTERACTION_LABEL_MAP.put(InteractionType.METAL_COMPLEX, "mec");
        INTERACTION_LABEL_MAP.put(InteractionType.PI_CATION, "pic");
        INTERACTION_LABEL_MAP.put(InteractionType.PI_STACKING, "pis");
        INTERACTION_LABEL_MAP.put(InteractionType.SALT_BRIDGE, "sab");
        INTERACTION_LABEL_MAP.put(InteractionType.WATER_BRIDGE, "wab");

        ACTIVE_INTERACTIONS = new ArrayList<>();
        ACTIVE_INTERACTIONS.add(InteractionType.HYDROGEN_BOND);
        ACTIVE_INTERACTIONS.add(InteractionType.METAL_COMPLEX);
        ACTIVE_INTERACTIONS.add(InteractionType.PI_CATION);
        ACTIVE_INTERACTIONS.add(InteractionType.PI_STACKING);
        ACTIVE_INTERACTIONS.add(InteractionType.SALT_BRIDGE);
    }

    @Override
    public void enrichDataPoint(DataPoint<String> dataPoint) {

        logger.info("enriching data point {} with interaction information", dataPoint);

        String pdbIdentifier = dataPoint.getDataPointIdentifier().getPdbIdentifier();
        String chainIdentifier = dataPoint.getDataPointIdentifier().getChainIdentifier();

        Optional<Map<InteractionType, List<PlipInteraction>>> optionalInteractions = queryInteractions(pdbIdentifier, chainIdentifier);

        if (optionalInteractions.isPresent()) {
            Map<InteractionType, List<PlipInteraction>> interactions = optionalInteractions.get();
            for (InteractionType activeInteraction : ACTIVE_INTERACTIONS) {
                logger.debug("enriching data point {} with interactions of type {}", dataPoint, activeInteraction);
                if (interactions.containsKey(activeInteraction)) {
                    interactions.get(activeInteraction).forEach(interaction -> addInteractionItem(interaction, dataPoint));
                }
            }
        }
    }

    private void addInteractionItem(PlipInteraction interaction, DataPoint<String> dataPoint) {

        // determine next identifiers
        int nextLeafIdentifier = dataPoint.getItems().stream()
                                          .map(Item::getLeafSubstructure)
                                          .filter(Optional::isPresent)
                                          .map(Optional::get)
                                          .mapToInt(leafSubstructure -> leafSubstructure.getLeafIdentifier().getIdentifier())
                                          .max()
                                          .orElseThrow(() -> new RuntimeException("failed to determine next leaf identifer")) + 1;
        int nextAtomIdentifier = dataPoint.getItems().stream()
                                          .map(Item::getLeafSubstructure)
                                          .filter(Optional::isPresent)
                                          .map(Optional::get)
                                          .map(LeafSubstructure::getAllAtoms)
                                          .flatMap(Collection::stream)
                                          .mapToInt(Node::getIdentifier)
                                          .max()
                                          .orElseThrow(() -> new RuntimeException("failed to determine next atom identifer")) + 1;

        Vector interactionCentroid = Vectors.getCentroid(interaction.getInteractionCoordinates().stream()
                                                                    .map(Vector3D::new)
                                                                    .collect(Collectors.toList()));

        // create new atom container
        LigandFamily family = new LigandFamily("X", INTERACTION_LABEL_MAP.get(interaction.getInteractionType()));
        AtomContainer<LigandFamily> atomContainer = new AtomContainer<>(new LeafIdentifier(dataPoint.getDataPointIdentifier().getPdbIdentifier(),
                                                                                           0,
                                                                                           dataPoint.getDataPointIdentifier().getChainIdentifier(),
                                                                                           nextLeafIdentifier), family);

        Atom interactionPseudoAtom = new RegularAtom(nextAtomIdentifier, ElementProvider.UNKOWN, "CA", interactionCentroid.as(Vector3D.class));
        atomContainer.addNode(interactionPseudoAtom);
        Item<String> interactionItem = new Item<>(INTERACTION_LABEL_MAP.get(interaction.getInteractionType()), atomContainer);
        dataPoint.getItems().add(interactionItem);

        logger.debug("added {} to data point {}", interactionItem, dataPoint);
    }

    private Optional<Map<InteractionType, List<PlipInteraction>>> queryInteractions(String pdbIdentifier, String chainIdentifier) {
        try {
            // connect to the PLIP REST API and obtain interaction data
            URL url = new URL(PLIP_REST_PROVIDER_URL + pdbIdentifier + "/" + chainIdentifier);
            logger.info("querying PLIP REST service: {}", url);
            String encoding = new sun.misc.BASE64Encoder().encode(PLIP_REST_PROVIDER_CREDENTIALS.getBytes());
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("Authorization", "Basic " + encoding);
            connection.connect();
            try (InputStream inputStream = connection.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
                TypeReference<Map<InteractionType, List<PlipInteraction>>> typeReference = new TypeReference<Map<InteractionType, List<PlipInteraction>>>() {
                };
                return Optional.of(mapper.readValue(inputStream, typeReference));
            }
        } catch (IOException e) {
            logger.warn("failed to obtain PLIP results from server for {}_{}", pdbIdentifier, chainIdentifier, e);
        }
        return Optional.empty();
    }
}
