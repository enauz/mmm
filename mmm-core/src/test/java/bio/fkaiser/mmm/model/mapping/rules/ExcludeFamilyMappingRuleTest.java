package bio.fkaiser.mmm.model.mapping.rules;

import bio.fkaiser.mmm.io.DataPointReader;
import bio.fkaiser.mmm.io.DataPointReaderConfiguration;
import bio.fkaiser.mmm.model.DataPoint;
import bio.fkaiser.mmm.model.Item;
import bio.fkaiser.mmm.model.mapping.DataPointLabelMapper;
import bio.fkaiser.mmm.model.mapping.MappingRule;
import de.bioforscher.singa.core.utility.Resources;
import de.bioforscher.singa.structure.model.families.AminoAcidFamily;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * @author fk
 */
public class ExcludeFamilyMappingRuleTest {

    @Test
    public void shouldExcludeItemsBasedOnFamily() throws IOException {

        DataPointReaderConfiguration dataPointReaderConfiguration = new DataPointReaderConfiguration();

        String interfaceStructuresPath = Resources.getResourceAsFileLocation("interface_structures");
        List<Path> interfaceStructures = Files.list(Paths.get(interfaceStructuresPath)).collect(Collectors.toList());
        DataPointReader dataPointReader = new DataPointReader(dataPointReaderConfiguration, interfaceStructures);
        List<DataPoint<String>> dataPoints = dataPointReader.readDataPoints();

        MappingRule<String> mappingRule = new ExcludeFamilyMappingRule<>(AminoAcidFamily.UNKNOWN);
        DataPointLabelMapper<String> dataPointLabelMapper = new DataPointLabelMapper<>(mappingRule);
        dataPoints = dataPoints.stream()
                               .map(dataPointLabelMapper::mapDataPoint)
                               .collect(Collectors.toList());

        boolean noUnknownLabels = dataPoints.stream()
                                            .map(DataPoint::getItems)
                                            .flatMap(Collection::stream)
                                            .map(Item::getLabel)
                                            .noneMatch(label -> label.equals(AminoAcidFamily.UNKNOWN.getThreeLetterCode()));
        assertTrue(noUnknownLabels);
    }

}