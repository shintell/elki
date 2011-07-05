package experimentalcode.marisa.index.xtree.common;

import de.lmu.ifi.dbs.elki.index.tree.spatial.BulkSplit.Strategy;
import de.lmu.ifi.dbs.elki.index.tree.spatial.SpatialEntry;
import de.lmu.ifi.dbs.elki.index.tree.spatial.SpatialPointLeafEntry;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.persistent.PageFile;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import experimentalcode.marisa.index.xtree.XDirectoryEntry;
import experimentalcode.marisa.index.xtree.XTreeBase;

/**
 * The XTree is a spatial index structure extending the R*-Tree.
 * 
 * <p>
 * Reference: <br>
 * Stefan Berchtold, Daniel A. Keim, Hans-Peter Kriegel: The X-tree: An Index
 * Structure for High-Dimensional Data<br>
 * In Proc. 22nd Int. Conf. on Very Large Data Bases (VLDB'96), Bombay, India,
 * 1996.
 * </p>
 * 
 * @author Marisa Thoma
 * @param <O> Database object type
 */
@Title("X-Tree")
@Description("Index structure for High-Dimensional data")
@Reference(authors = "S. Berchtold, D. A. Keim, H.-P. Kriegel", title = "The X-tree: An Index Structure for High-Dimensional Data", booktitle = "Proc. 22nd Int. Conf. on Very Large Data Bases (VLDB'96), Bombay, India, 1996", url = "http://www.vldb.org/conf/1996/P028.PDF")
public class XTree extends XTreeBase<XTreeNode, SpatialEntry> {
  /**
   * The logger for this class.
   */
  private static final Logging logger = Logging.getLogger(XTree.class);

  /**
   * Constructor.
   * 
   * @param pagefile Page file
   * @param bulk
   * @param bulkLoadStrategy
   * @param insertionCandidates
   * @param relativeMinEntries
   * @param relativeMinFanout
   * @param reinsert_fraction
   * @param max_overlap
   * @param overlap_type
   */
  public XTree(PageFile<XTreeNode> pagefile, boolean bulk, Strategy bulkLoadStrategy, int insertionCandidates, double relativeMinEntries, double relativeMinFanout, float reinsert_fraction, float max_overlap, int overlap_type) {
    super(pagefile, bulk, bulkLoadStrategy, insertionCandidates, relativeMinEntries, relativeMinFanout, reinsert_fraction, max_overlap, overlap_type);
  }

  /**
   * Creates an entry representing the root node.
   */
  @Override
  protected SpatialEntry createRootEntry() {
    return new XDirectoryEntry(0, null);
  }

  @Override
  protected SpatialEntry createNewDirectoryEntry(XTreeNode node) {
    return new XDirectoryEntry(node.getPageID(), node.computeMBR());
  }

  /**
   * Creates a new leaf node with the specified capacity.
   * 
   * @return a new leaf node
   */
  @Override
  protected XTreeNode createNewLeafNode() {
    return new XTreeNode(leafCapacity, true, SpatialPointLeafEntry.class);
  }

  /**
   * Creates a new directory node with the specified capacity.
   * 
   * @return a new directory node
   */
  @Override
  protected XTreeNode createNewDirectoryNode() {
    return new XTreeNode(dirCapacity, false, XDirectoryEntry.class);
  }

  @Override
  protected Logging getLogger() {
    return logger;
  }

  @Override
  protected Class<XTreeNode> getNodeClass() {
    return XTreeNode.class;
  }
}