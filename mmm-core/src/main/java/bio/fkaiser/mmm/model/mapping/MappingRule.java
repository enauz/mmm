package bio.fkaiser.mmm.model.mapping;

import bio.fkaiser.mmm.model.Item;
import bio.fkaiser.mmm.model.mapping.rules.*;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Optional;
import java.util.function.Function;

/**
 * An interface for {@link MappingRule}s.
 *
 * @author fk
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = ChemicalGroupsMappingRule.class),
               @JsonSubTypes.Type(value = FunctionalGroupsMappingRule.class),
               @JsonSubTypes.Type(value = NoAminoAcidsMappingRule.class),
               @JsonSubTypes.Type(value = ExcludeFamilyMappingRule.class),
               @JsonSubTypes.Type(value = InteractionShellMappingRule.class)})

public interface MappingRule<LabelType extends Comparable<LabelType>> extends Function<Item<LabelType>, Item<LabelType>> {
    Optional<Item<LabelType>> mapItem(Item<LabelType> item);
}
