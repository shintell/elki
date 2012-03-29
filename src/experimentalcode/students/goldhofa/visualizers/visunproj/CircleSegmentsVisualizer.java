package experimentalcode.students.goldhofa.visualizers.visunproj;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2012
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.events.MouseEvent;

import de.lmu.ifi.dbs.elki.evaluation.clustering.pairsegments.Segment;
import de.lmu.ifi.dbs.elki.evaluation.clustering.pairsegments.Segments;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.MathUtil;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultListener;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import experimentalcode.students.goldhofa.SegmentSelection;
import experimentalcode.students.goldhofa.visualization.batikutil.CheckBox;
import experimentalcode.students.goldhofa.visualization.batikutil.CheckBoxListener;
import experimentalcode.students.goldhofa.visualization.batikutil.SwitchEvent;
import experimentalcode.students.goldhofa.visualization.batikutil.UnorderedList;
import experimentalcode.students.goldhofa.visualization.style.CSStylingPolicy;

/**
 * Visualizer to draw circle segments of clusterings
 * 
 * @author Sascha Goldhofer
 * @author Erich Schubert
 */
public class CircleSegmentsVisualizer extends AbstractVisualization implements ResultListener {
  /**
   * Class logger
   */
  private static final Logging logger = Logging.getLogger(CircleSegmentsVisualizer.class);

  /**
   * CircleSegments visualizer name
   */
  private static final String NAME = "CircleSegments";

  /** Minimum width (radian) of Segment */
  private final static double SEGMENT_MIN_ANGLE = 0.01;

  /** Gap (radian) between segments */
  private final static double SEGMENT_MIN_SEP_ANGLE = 0.005;

  /** Offset from center to first ring */
  private final static double RADIUS_INNER = 0.04 * StyleLibrary.SCALE;

  /** Margin between two rings */
  private final static double RADIUS_DISTANCE = 0.01 * StyleLibrary.SCALE;

  /** Radius of whole CircleSegments except selection border */
  private final static double RADIUS_OUTER = 0.47 * StyleLibrary.SCALE;

  /** Radius of highlight selection (outer ring) */
  private final static double RADIUS_SELECTION = 0.02 * StyleLibrary.SCALE;

  /**
   * CSS class name for the clusterings.
   */
  private static final String CLR_CLUSTER_CLASS_PREFIX = "clusterSegment";

  /**
   * CSS border class of a cluster
   */
  public static final String CLR_BORDER_CLASS = "clusterBorder";

  /**
   * CSS hover class for clusters of hovered segment
   */
  public static final String CLR_UNPAIRED_CLASS = "clusterUnpaired";

  /**
   * CSS hover class of a segment cluster
   */
  public static final String CLR_HOVER_CLASS = "clusterHover";

  /**
   * CSS class of selected Segment
   */
  public static final String SEG_UNPAIRED_SELECTED_CLASS = "unpairedSegmentSelected";

  /**
   * Style prefix
   */
  public static final String STYLE = "segments";

  /**
   * Style for border lines
   */
  public static final String STYLE_BORDER = STYLE + ".border";

  /**
   * Style for hover effect
   */
  public static final String STYLE_HOVER = STYLE + ".hover";

  /**
   * First color for producing segment-cluster colors
   */
  public static final String STYLE_GRADIENT_FIRST = STYLE + ".cluster.first";

  /**
   * Second color for producing segment-cluster colors
   */
  public static final String STYLE_GRADIENT_SECOND = STYLE + ".cluster.second";

  /**
   * Segmentation of Clusterings
   */
  protected final Segments segments;

  /**
   * The two main layers
   */
  private Element visLayer, ctrlLayer;

  /**
   * Map to connect segments to their visual elements
   */
  public Map<Segment, List<Element>> segmentToElements = new HashMap<Segment, List<Element>>();

  /**
   * Segment selection manager
   */
  protected final SegmentSelection selection;

  /**
   * Show unclustered Pairs in CircleSegments
   */
  boolean showUnclusteredPairs = false;

  /**
   * Styling policy
   */
  protected final CSStylingPolicy policy;

  /**
   * Flag to disallow an incrmental redraw
   */
  private boolean noIncrementalRedraw = true;

  /**
   * Constructor
   */
  public CircleSegmentsVisualizer(VisualizationTask task) {
    super(task);
    segments = task.getResult();
    policy = new CSStylingPolicy(segments, task.getContext().getStyleLibrary());
    selection = new SegmentSelection(policy, segments);
    // Listen for result changes (Selection changed)
    context.addResultListener(this);
  }

  public void toggleUnclusteredPairs(boolean show) {
    noIncrementalRedraw = true;
    showUnclusteredPairs = show;
    synchronizedRedraw();
  }

  @Override
  public void resultChanged(Result current) {
    super.resultChanged(current);
    // Redraw on style result changes.
    if(current == context.getStyleResult()) {
      // When switching to a different policy, unhighlight segments.
      if(context.getStyleResult().getStylingPolicy() != policy) {
        policy.deselectAllObjects();
      }
      synchronizedRedraw();
    }
  }

  @Override
  protected void incrementalRedraw() {
    if(noIncrementalRedraw) {
      super.incrementalRedraw();
    }
    else {
      redrawSelection();
    }
  }

  @Override
  public void redraw() {
    logger.debug("Full redraw");
    noIncrementalRedraw = false; // Done that.

    // initialize css (needs clusterSize!)
    addCSSClasses(segments.getHighestClusterCount());

    layer = svgp.svgElement(SVGConstants.SVG_G_TAG);
    visLayer = svgp.svgElement(SVGConstants.SVG_G_TAG);
    // Setup scaling for canvas: 0 to StyleLibrary.SCALE (usually 100 to avoid a
    // Java drawing bug!)
    String transform = SVGUtil.makeMarginTransform(task.width, task.height, StyleLibrary.SCALE, StyleLibrary.SCALE, 0) + "  translate(" + (StyleLibrary.SCALE * .5) + " " + (StyleLibrary.SCALE * .5) + ")";
    visLayer.setAttribute(SVGConstants.SVG_TRANSFORM_ATTRIBUTE, transform);
    ctrlLayer = svgp.svgElement(SVGConstants.SVG_G_TAG);

    // and create svg elements
    drawSegments();

    //
    // Build Interface
    //
    CheckBox checkbox = new CheckBox(svgp, showUnclusteredPairs, "Show unclustered pairs");
    checkbox.addCheckBoxListener(new CheckBoxListener() {
      public void switched(SwitchEvent evt) {
        toggleUnclusteredPairs(evt.isOn());
      }
    });

    // list to store all elements
    UnorderedList info = new UnorderedList(svgp);

    // Add ring:clustering info
    Element clrInfo = getClusteringInfo();
    info.addItem(clrInfo, Integer.valueOf(clrInfo.getAttribute(SVGConstants.SVG_HEIGHT_ATTRIBUTE)));
    // checkbox
    info.addItem(checkbox.asElement(), 20);

    ctrlLayer.setAttribute(SVGConstants.SVG_TRANSFORM_ATTRIBUTE, "scale(" + (0.25 / StyleLibrary.SCALE) + ")");
    ctrlLayer.appendChild(info.asElement());

    layer.appendChild(visLayer);
    layer.appendChild(ctrlLayer);
  }

  /**
   * Define and add required CSS classes
   */
  protected void addCSSClasses(int maxClusterSize) {
    StyleLibrary style = context.getStyleLibrary();

    // CLUSTER BORDER
    CSSClass cssReferenceBorder = new CSSClass(this.getClass(), CLR_BORDER_CLASS);
    cssReferenceBorder.setStatement(SVGConstants.SVG_FILL_ATTRIBUTE, style.getColor(STYLE_BORDER));
    svgp.addCSSClassOrLogError(cssReferenceBorder);

    // CLUSTER HOVER
    CSSClass cluster_hover = new CSSClass(this.getClass(), CLR_HOVER_CLASS);
    // Note: !important is needed to override the regular color assignment
    cluster_hover.setStatement(SVGConstants.SVG_FILL_ATTRIBUTE, style.getColor(STYLE_HOVER) + " !important");
    cluster_hover.setStatement(SVGConstants.SVG_CURSOR_TAG, SVGConstants.SVG_POINTER_VALUE);
    svgp.addCSSClassOrLogError(cluster_hover);

    // Unpaired cluster segment
    CSSClass cluster_unpaired = new CSSClass(this.getClass(), CLR_UNPAIRED_CLASS);
    cluster_unpaired.setStatement(SVGConstants.SVG_FILL_ATTRIBUTE, style.getBackgroundColor(STYLE));
    cluster_unpaired.setStatement(SVGConstants.SVG_STROKE_ATTRIBUTE, SVGConstants.CSS_NONE_VALUE);
    svgp.addCSSClassOrLogError(cluster_unpaired);

    // create Color shades for clusters
    String firstcol = style.getColor(STYLE_GRADIENT_FIRST);
    String secondcol = style.getColor(STYLE_GRADIENT_SECOND);
    String[] clusterColorShades = makeGradient(maxClusterSize, new String[] { firstcol, secondcol });

    for(int i = 0; i < maxClusterSize; i++) {
      CSSClass clusterClasses = new CSSClass(CircleSegmentsVisualizer.class, CLR_CLUSTER_CLASS_PREFIX + "_" + i);
      clusterClasses.setStatement(SVGConstants.SVG_FILL_ATTRIBUTE, clusterColorShades[i]);
      clusterClasses.setStatement(SVGConstants.SVG_STROKE_ATTRIBUTE, SVGConstants.SVG_NONE_VALUE);
      svgp.addCSSClassOrLogError(clusterClasses);
    }
  }

  /**
   * Create the segments
   */
  private void drawSegments() {
    final int clusterings = segments.getClusterings();

    // Reinitialize
    this.segmentToElements.clear();

    double angle_pair = (MathUtil.TWOPI - (SEGMENT_MIN_SEP_ANGLE * segments.size())) / segments.getPairCount(showUnclusteredPairs);
    final int pair_min_count = (int) Math.ceil(SEGMENT_MIN_ANGLE / angle_pair);

    // number of segments needed to be resized
    int cluster_min_count = 0;
    for(Segment segment : segments) {
      if(segment.getPairCount() <= pair_min_count) {
        cluster_min_count++;
      }
    }

    // update width of a pair
    angle_pair = (MathUtil.TWOPI - (SEGMENT_MIN_SEP_ANGLE * segments.size() + cluster_min_count * SEGMENT_MIN_ANGLE)) / (segments.getPairCount(showUnclusteredPairs) - cluster_min_count);
    double radius_delta = (RADIUS_OUTER - RADIUS_INNER - clusterings * RADIUS_DISTANCE) / clusterings;
    double border_width = SEGMENT_MIN_SEP_ANGLE;

    int refClustering = 0;
    int refSegment = Segment.UNCLUSTERED;
    double offsetAngle = 0.0;

    for(final Segment segment : segments) {
      long currentPairCount = segment.getPairCount();

      // resize small segments if below minimum
      double alpha = SEGMENT_MIN_ANGLE;
      if(currentPairCount > pair_min_count) {
        alpha = angle_pair * currentPairCount;
      }

      // ITERATE OVER ALL SEGMENT-CLUSTERS

      ArrayList<Element> elems = new ArrayList<Element>(clusterings);
      segmentToElements.put(segment, elems);
      // draw segment for every clustering

      for(int i = 0; i < clusterings; i++) {
        double currentRadius = i * (radius_delta + RADIUS_DISTANCE) + RADIUS_INNER;

        // Add border if the next segment is a different cluster in the
        // reference clustering
        if((refSegment != segment.get(refClustering)) && refClustering == i) {
          Element border = SVGUtil.svgCircleSegment(svgp, 0, 0, offsetAngle - SEGMENT_MIN_SEP_ANGLE, border_width, currentRadius, RADIUS_OUTER - RADIUS_DISTANCE);
          border.setAttribute(SVGConstants.SVG_CLASS_ATTRIBUTE, CLR_BORDER_CLASS);
          visLayer.appendChild(border);

          if(segment.get(refClustering) == Segment.UNCLUSTERED) {
            refClustering = Math.min(refClustering + 1, clusterings - 1);
          }
          refSegment = segment.get(refClustering);
        }

        int cluster = segment.get(i);

        // create ring segment
        Element segelement = SVGUtil.svgCircleSegment(svgp, 0, 0, offsetAngle, alpha, currentRadius, currentRadius + radius_delta);
        elems.add(segelement);

        // MouseEvents on segment cluster
        EventListener listener = new SegmentListenerProxy(segment, i);
        EventTarget targ = (EventTarget) segelement;
        targ.addEventListener(SVGConstants.SVG_MOUSEOVER_EVENT_TYPE, listener, false);
        targ.addEventListener(SVGConstants.SVG_MOUSEOUT_EVENT_TYPE, listener, false);
        targ.addEventListener(SVGConstants.SVG_CLICK_EVENT_TYPE, listener, false);

        // Coloring based on clusterID
        if(cluster >= 0) {
          segelement.setAttribute(SVGConstants.SVG_CLASS_ATTRIBUTE, CLR_CLUSTER_CLASS_PREFIX + "_" + cluster);
        }
        // if its an unpaired cluster set color to white
        else {
          segelement.setAttribute(SVGConstants.SVG_CLASS_ATTRIBUTE, CLR_UNPAIRED_CLASS);
        }

        visLayer.appendChild(segelement);
      }

      //
      // Add a extended strip for each segment to emphasis selection
      // (easier to track thin segments and their color coding and
      // differentiates them from cluster border lines)
      //

      double currentRadius = clusterings * (radius_delta + RADIUS_DISTANCE) + RADIUS_INNER;
      Element extension = SVGUtil.svgCircleSegment(svgp, 0, 0, offsetAngle, alpha, currentRadius, currentRadius + RADIUS_SELECTION);
      extension.setAttribute(SVGConstants.SVG_CLASS_ATTRIBUTE, CLR_UNPAIRED_CLASS);
      elems.add(extension);

      if(segment.isUnpaired()) {
        if(selection.segmentLabels.containsKey(segment)) {
          SVGUtil.addCSSClass(extension, SEG_UNPAIRED_SELECTED_CLASS);
        }
        else {
          // Remove highlight
          SVGUtil.removeCSSClass(extension, SEG_UNPAIRED_SELECTED_CLASS);
        }
      }
      else {
        int idx = policy.getSelectedSegments().indexOf(segment);
        if(idx >= 0) {
          String color = context.getStyleLibrary().getColorSet(StyleLibrary.PLOT).getColor(idx);
          extension.setAttribute(SVGConstants.SVG_STYLE_ATTRIBUTE, SVGConstants.CSS_FILL_PROPERTY + ":" + color);
        }
        else {
          // Remove styling
          extension.removeAttribute(SVGConstants.SVG_STYLE_ATTRIBUTE);
        }
      }

      visLayer.appendChild(extension);

      // calculate angle for next segment
      offsetAngle += alpha + SEGMENT_MIN_SEP_ANGLE;
    }
  }

  private void redrawSelection() {
    logger.debug("Updating selection only.");
    for(Entry<Segment, List<Element>> entry : segmentToElements.entrySet()) {
      Segment segment = entry.getKey();
      // The selection marker is the extra element in the list
      Element extension = entry.getValue().get(segments.getClusterings());
      if(segment.isUnpaired()) {
        if(selection.segmentLabels.containsKey(segment)) {
          SVGUtil.addCSSClass(extension, SEG_UNPAIRED_SELECTED_CLASS);
        }
        else {
          // Remove highlight
          SVGUtil.removeCSSClass(extension, SEG_UNPAIRED_SELECTED_CLASS);
        }
      }
      else {
        int idx = policy.getSelectedSegments().indexOf(segment);
        if(idx >= 0) {
          String color = context.getStyleLibrary().getColorSet(StyleLibrary.PLOT).getColor(idx);
          extension.setAttribute(SVGConstants.SVG_STYLE_ATTRIBUTE, SVGConstants.CSS_FILL_PROPERTY + ":" + color);
        }
        else {
          // Remove styling
          extension.removeAttribute(SVGConstants.SVG_STYLE_ATTRIBUTE);
        }
      }
    }
  }

  /**
   * Creates a gradient over a set of colors
   * 
   * @param shades number of colors in the gradient
   * @param colors colors for the gradient
   * @return array of colors for CSS
   */
  protected static String[] makeGradient(int shades, String[] colors) {
    if(shades <= colors.length) {
      return colors;
    }

    // Convert SVG colors into AWT colors for math
    Color[] cols = new Color[colors.length];
    for(int i = 0; i < colors.length; i++) {
      cols[i] = SVGUtil.stringToColor(colors[i]);
      if(cols[i] == null) {
        throw new AbortException("Error parsing color: " + colors[i]);
      }
    }

    // Step size
    double increment = (cols.length - 1.) / shades;

    String[] colorShades = new String[shades];

    for(int s = 0; s < shades; s++) {
      final int ppos = Math.min((int) Math.floor(increment * s), cols.length);
      final int npos = Math.min((int) Math.ceil(increment * s), cols.length);
      if(ppos == npos) {
        colorShades[s] = colors[ppos];
      }
      else {
        Color prev = cols[ppos];
        Color next = cols[npos];
        final double mix = (increment * s - ppos) / (npos - ppos);
        final int r = (int) ((1 - mix) * prev.getRed() + mix * next.getRed());
        final int g = (int) ((1 - mix) * prev.getGreen() + mix * next.getGreen());
        final int b = (int) ((1 - mix) * prev.getBlue() + mix * next.getBlue());
        colorShades[s] = SVGUtil.colorToString(((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
      }
    }

    return colorShades;
  }

  protected Element getClusteringInfo() {
    Element thumbnail = SVGUtil.svgElement(svgp.getDocument(), SVGConstants.SVG_G_TAG);

    // build thumbnail
    int startRadius = 4;
    int singleHeight = 12;
    int margin = 4;
    int radius = segments.getClusterings() * (singleHeight + margin) + startRadius;

    SVGUtil.setAtt(thumbnail, SVGConstants.SVG_HEIGHT_ATTRIBUTE, radius);

    for(int i = 0; i < segments.getClusterings(); i++) {
      double innerRadius = i * singleHeight + margin * i + startRadius;
      Element clr = SVGUtil.svgCircleSegment(svgp, radius - startRadius, radius - startRadius, Math.PI * 1.5, Math.PI * 0.5, innerRadius, innerRadius + singleHeight);
      // FIXME: Use StyleLibrary
      clr.setAttribute(SVGConstants.SVG_FILL_ATTRIBUTE, "#d4e4f1");
      clr.setAttribute(SVGConstants.SVG_STROKE_ATTRIBUTE, "#a0a0a0");
      clr.setAttribute(SVGConstants.SVG_STROKE_WIDTH_ATTRIBUTE, "1.0");

      String labelText = segments.getClusteringDescription(i);
      Element label = svgp.svgText(radius + startRadius, radius - innerRadius - startRadius, labelText);
      thumbnail.appendChild(label);

      thumbnail.appendChild(clr);
    }

    return thumbnail;
  }

  protected void segmentHover(Segment segment, int ringid, boolean active) {
    if(active) {
      // abort if this are the unclustered pairs
      if(segment.isNone()) {
        return;
      }
      if(logger.isDebugging()) {
        logger.debug("Hover on segment: " + segment + " unpaired: " + segment.isUnpaired());
      }

      if(!segment.isUnpaired()) {
        //
        // STANDARD CLUSTER SEGMENT
        // highlight all ring segments in this clustering and this cluster
        //
        // highlight all corresponding ring Segments
        for(Entry<Segment, List<Element>> entry : segmentToElements.entrySet()) {
          Segment other = entry.getKey();
          // Same cluster in same clustering?
          if(other.get(ringid) != segment.get(ringid)) {
            continue;
          }
          Element ringSegment = entry.getValue().get(ringid);
          SVGUtil.addCSSClass(ringSegment, CLR_HOVER_CLASS);
        }
      }
      else {
        //
        // UNPAIRED SEGMENT
        // highlight all ring segments in this clustering responsible for
        // unpaired
        // segment
        //
        // get the paired segments corresponding to the unpaired segment
        List<Segment> paired = segments.getPairedSegments(segment);

        for(Segment other : paired) {
          Element ringSegment = segmentToElements.get(other).get(ringid);
          SVGUtil.addCSSClass(ringSegment, CLR_HOVER_CLASS);
        }
      }
    }
    else {
      for(List<Element> elems : segmentToElements.values()) {
        for(Element current : elems) {
          SVGUtil.removeCSSClass(current, CLR_HOVER_CLASS);
        }
      }
    }
  }

  protected void segmentClick(Segment segment, Event evt, boolean dblClick) {
    MouseEvent mouse = (MouseEvent) evt;

    // CTRL (add) pressed?
    boolean ctrl = false;
    if(mouse.getCtrlKey()) {
      ctrl = true;
    }

    // Unselect others on double click
    if(dblClick) {
      selection.deselectAllSegments();
    }
    selection.select(segment, ctrl);
    // update stylePolicy
    context.getStyleResult().setStylingPolicy(policy);
    // fire changed event to trigger redraw
    context.getHierarchy().resultChanged(context.getStyleResult());
  }

  /**
   * Proxy element to connect signals.
   * 
   * @author Erich Schubert
   */
  private class SegmentListenerProxy implements EventListener {
    /**
     * Mouse double click time window in milliseconds
     * 
     * TODO: does Batik have double click events?
     */
    public static final int EVT_DBLCLICK_DELAY = 350;

    /**
     * Segment we are attached to
     */
    private Segment id;

    /**
     * Segment ring we are
     */
    private int ringid;

    /**
     * For detecting double clicks.
     */
    private long lastClick = 0;

    /**
     * Constructor.
     * 
     * @param id Segment id
     * @param ringid Ring id
     */
    public SegmentListenerProxy(Segment id, int ringid) {
      super();
      this.id = id;
      this.ringid = ringid;
    }

    @Override
    public void handleEvent(Event evt) {
      if(SVGConstants.SVG_MOUSEOVER_EVENT_TYPE.equals(evt.getType())) {
        segmentHover(id, ringid, true);
      }
      if(SVGConstants.SVG_MOUSEOUT_EVENT_TYPE.equals(evt.getType())) {
        segmentHover(id, ringid, false);
      }
      if(SVGConstants.SVG_CLICK_EVENT_TYPE.equals(evt.getType())) {
        // Check Double Click
        boolean dblClick = false;
        long time = java.util.Calendar.getInstance().getTimeInMillis();
        if(time - lastClick <= EVT_DBLCLICK_DELAY) {
          dblClick = true;
        }
        lastClick = time;

        segmentClick(id, evt, dblClick);
      }
    }
  }

  /**
   * Factory for visualizers for a circle segment
   * 
   * @author Sascha Goldhofer
   * 
   * @apiviz.stereotype factory
   * @apiviz.uses CircleSegmentsVisualizer oneway - - «create»
   */
  public static class Factory extends AbstractVisFactory {
    /**
     * Constructor
     */
    public Factory() {
      super();
    }

    @Override
    public Visualization makeVisualization(VisualizationTask task) {
      return new CircleSegmentsVisualizer(task);
    }

    @Override
    public void processNewResult(HierarchicalResult baseResult, Result result) {
      // If no comparison result found abort
      List<Segments> segments = ResultUtil.filterResults(result, Segments.class);
      for(Segments segmentResult : segments) {
        // create task for visualization
        final VisualizationTask task = new VisualizationTask(NAME, segmentResult, null, this);
        task.width = 2.0;
        task.height = 2.0;
        task.put(VisualizationTask.META_LEVEL, VisualizationTask.LEVEL_INTERACTIVE);
        baseResult.getHierarchy().add(segmentResult, task);
      }
    }
  };
}