package de.bioforscher.mmm.model.analysis.association;

import de.bioforscher.mmm.model.Itemset;
import de.bioforscher.singa.mathematics.geometry.faces.Rectangle;
import de.bioforscher.singa.mathematics.graphs.model.AbstractNode;
import de.bioforscher.singa.mathematics.vectors.Vector2D;
import de.bioforscher.singa.mathematics.vectors.Vectors;

/**
 * @author fk
 */
public class ItemsetNode<LabelType extends Comparable<LabelType>> extends AbstractNode<ItemsetNode<LabelType>, Vector2D> {

    public static final Rectangle GRAPH_BOUNDING_BOX = new Rectangle(100, 100);

    private Itemset<LabelType> itemset;

    public ItemsetNode(int identifier, Itemset<LabelType> itemset) {
        super(identifier, Vectors.generateRandom2DVector(GRAPH_BOUNDING_BOX));
        this.itemset = itemset;
    }

    public ItemsetNode(ItemsetNode<LabelType> node) {
        super(node);
        // FIXME here we should probably use a copy of the itemset
        this.itemset = node.getItemset();
    }

    public Itemset<LabelType> getItemset() {
        return itemset;
    }

    @Override public String toString() {
        return "ItemsetNode{" +
               "itemset=" + itemset.toSimpleString() +
               '}';
    }
}
