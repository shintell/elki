package de.lmu.ifi.dbs.elki.visualization.visualizers.vis2d;

import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.DatabaseEvent;
import de.lmu.ifi.dbs.elki.database.DatabaseListener;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.result.ClusterOrderEntry;
import de.lmu.ifi.dbs.elki.result.ClusterOrderResult;
import de.lmu.ifi.dbs.elki.visualization.VisualizationProjection;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClassManager.CSSNamingConflict;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualizer;
import de.lmu.ifi.dbs.elki.visualization.visualizers.VisualizerContext;

/**
 * Visualize an OPTICS cluster order by drawing connection lines.
 * 
 * @author Erich Schubert
 *
 * @param <NV> object type
 */
public class ClusterOrderVisualizer<NV extends NumberVector<NV,?>> extends Projection2DVisualizer<NV> {
  /**
   * A short name characterizing this Visualizer.
   */
  private static final String NAME = "Cluster Order";
  
  /**
   * CSS class name
   */
  private static final String CSSNAME = "co";
  
  /**
   * The result we visualize
   */
  protected ClusterOrderResult<?> result;
  
  /**
   * Initialize the visualizer.
   * 
   * @param context Context
   * @param result Result class.
   */
  public void init(VisualizerContext<? extends NV> context, ClusterOrderResult<?> result) {
    super.init(NAME, context);
    this.result = result;
  }

  @Override
  public Visualization visualize(SVGPlot svgp, VisualizationProjection proj, double width, double height) {
    return new ClusterOrderVisualization(context, svgp, proj, width, height);
  }

  /**
   * Cluster order visualizer.
   * 
   * @author Erich Schubert
   */
  // TODO: listen for CLUSTER ORDER changes.
  protected class ClusterOrderVisualization extends Projection2DVisualization<NV> implements DatabaseListener<NV> {
    /**
     * Container element.
     */
    private Element container;

    /**
     * Constructor.
     * 
     * @param context Context
     * @param svgp Plot
     * @param proj Projection
     * @param width Width
     * @param height Height
     */
    public ClusterOrderVisualization(VisualizerContext<? extends NV> context, SVGPlot svgp, VisualizationProjection proj, double width, double height) {
      super(context, svgp, proj, width, height);
      double margin = context.getStyleLibrary().getSize(StyleLibrary.MARGIN);
      this.container = super.setupCanvas(svgp, proj, margin, width, height);
      this.layer = new VisualizationLayer(Visualizer.LEVEL_STATIC, this.container);

      context.addDatabaseListener(this);
      redraw();
    }

    @Override
    public void destroy() {
      super.destroy();
      context.removeDatabaseListener(this);
    }

    @Override
    public void redraw() {
      // Implementation note: replacing the container element is faster than
      // removing all markers and adding new ones - i.e. a "bluk" operation
      // instead of incremental changes
      Element oldcontainer = null;
      if(container.hasChildNodes()) {
        oldcontainer = container;
        container = (Element) container.cloneNode(false);
      }

      CSSClass cls = new CSSClass(this, CSSNAME);
      context.getLineStyleLibrary().formatCSSClass(cls, 0, context.getStyleLibrary().getLineWidth(StyleLibrary.CLUSTERORDER));
      
      try {
        svgp.getCSSClassManager().addClass(cls);
      }
      catch(CSSNamingConflict e) {
        logger.error("CSS naming conflict.", e);
      }
      
      // get the Database
      Database<? extends NV> database = context.getDatabase();
      for (ClusterOrderEntry<?> ce : result) {
        DBID thisId = ce.getID();
        DBID prevId = ce.getPredecessorID();
        if (thisId == null || prevId == null) {
          continue;
        }
        double[] thisVec = proj.fastProjectDataToRenderSpace(database.get(thisId));
        double[] prevVec = proj.fastProjectDataToRenderSpace(database.get(prevId));
        
        Element arrow = svgp.svgLine(prevVec[0], prevVec[1], thisVec[0], thisVec[1]);
        SVGUtil.setCSSClass(arrow, cls.getName());
        
        container.appendChild(arrow);
      }
      if(oldcontainer != null && oldcontainer.getParentNode() != null) {
        oldcontainer.getParentNode().replaceChild(container, oldcontainer);
      }
      //logger.warning("Redraw completed, " + this);
    }

    @Override
    public void objectsChanged(@SuppressWarnings("unused") DatabaseEvent<NV> e) {
      synchronizedRedraw();
    }

    @Override
    public void objectsInserted(@SuppressWarnings("unused") DatabaseEvent<NV> e) {
      synchronizedRedraw();
    }

    @Override
    public void objectsRemoved(@SuppressWarnings("unused") DatabaseEvent<NV> e) {
      synchronizedRedraw();
    }
  }
}