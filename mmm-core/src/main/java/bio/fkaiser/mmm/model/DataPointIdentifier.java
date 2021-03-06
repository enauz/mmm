package bio.fkaiser.mmm.model;

import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Each {@link DataPoint} has to have an identifier, that is the PDB-ID and chain ID of origin.
 *
 * @author fk
 */
public class DataPointIdentifier {

    public static final Comparator<DataPointIdentifier> COMPARATOR = Comparator.comparing(DataPointIdentifier::getPdbIdentifier)
                                                                               .thenComparing(DataPointIdentifier::getChainIdentifier);

    public static final Pattern PDBID_PATTERN = Pattern.compile("[1-9][A-Za-z0-9]{3}");
    private final String pdbIdentifier;
    private String chainIdentifier;

    public DataPointIdentifier(String pdbIdentifier) {
        if (!PDBID_PATTERN.matcher(pdbIdentifier).find()) {
            throw new IllegalArgumentException("'" + pdbIdentifier + "' is no valid PDB-ID");
        }
        this.pdbIdentifier = pdbIdentifier;
    }

    public DataPointIdentifier(String pdbIdentifier, String chainIdentifier) {
        if (!PDBID_PATTERN.matcher(pdbIdentifier).find()) {
            throw new IllegalArgumentException("'" + pdbIdentifier + "' is no valid PDB-ID");
        }
        this.pdbIdentifier = pdbIdentifier;
        this.chainIdentifier = chainIdentifier;
    }

    @Override
    public String toString() {
        return pdbIdentifier + "_" + chainIdentifier;
    }

    public String getPdbIdentifier() {
        return pdbIdentifier;
    }

    public String getChainIdentifier() {
        return chainIdentifier;
    }
}
