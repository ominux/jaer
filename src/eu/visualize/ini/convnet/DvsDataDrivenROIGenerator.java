/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.util.DrawGL;

/**
 * Generates data driven regions of interest DVS frames from DVS activity. These
 * are used to drive further processing, e.g. by CNN engine.
 *
 * @author Tobi Delbruck
 */
public class DvsDataDrivenROIGenerator extends EventFilter2D implements FrameAnnotater {

    /**
     * The ROIs. Indices are [scale][xidx][yidx]
     */
    protected ArrayList<ROI[][]> rois = null;
    protected ROI roiLastUpdated = null;
    private int numScales = getInt("numScales", 3);
    private int dimension = getInt("dimension", 64);
    private int stride = getInt("stride", dimension / 2);
    private int grayScale = getInt("grayScale", 100);
    private int dvsEventCount = getInt("dvsEventCount", 1000);
    private int sx, sy, nx, ny;
    private boolean showDvsFrames = false;
    private ImageDisplay dvsFrameImageDisplay; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private JFrame dvsFrame = null;
//    private ImageDisplay.Legend sliceBitmapLegend;
    /**
     * PropertyChangeEvent that is fired when a new ROI is available
     */
    public static final String EVENT_NEW_ROI_AVAILABLE = "NEW_ROI_AVAILABLE";

    // timers and flags for showing filter properties temporarily
    private final int SHOW_STUFF_DURATION_MS = 4000;
    private volatile TimerTask stopShowingStuffTask = null;
    private volatile boolean showROIsTemporarilyFlag = false;

    public DvsDataDrivenROIGenerator(AEChip chip) {
        super(chip);
        setPropertyTooltip("numScales", "number of scales of ROIs; 1 means only basic ROIs without subsampling");
        setPropertyTooltip("dimension", "width and height of each ROI in pixels. Corresponds to input pixels at scale 0, the finest scale");
        setPropertyTooltip("stride", "stride of adjacent ROIs in pixels; automatically set to half of dimension each time that is set unless overridden");
        setPropertyTooltip("dvsEventCount", "num DVS events accumulated to subsampled ROI to fill the frame");
        setPropertyTooltip("showDvsFrames", "shows the fully exposed (accumulated with events) frames in a separate window");
        setPropertyTooltip("grayScale", "sets the full scale value for the DVS frame rendering");
    }

    /**
     * Note: Not meant to be used by subclasses unless time limit is not
     * important
     *
     * @param in the input packet
     * @return input packet
     */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        for (BasicEvent be : in) {
            PolarityEvent e = (PolarityEvent) be;
            addEvent(e);
        }
        return in;
    }

    /**
     * Use this method to add events in an iterator. Fires the
     * PropertyChangeEvent EVENT_NEW_ROI_AVAILABLE with the ROI as the new
     * property value for each scale that fills up. Fills event into all
     * overlapping ROIs at all scales.
     *
     * @param e the event to add
     */
    public void addEvent(PolarityEvent e) {
        for (int s = 0; s < numScales; s++) {
            // For this scale, find the overlapping ROIs and put the event to them.

            // The ROIs overlap by the stride, scaled by the scale, so at one scale, 
            // an event can belong
            // to many ROIs depending on where it is and what is the stride.
            // we compute the containing ROIs by finding the x and y limits such that
            // the x,y of the event is >= ROI lower left and <= ROI upper right
            int subx = e.x >> s, suby = e.y >> s;
            ROI[][] roiArray = rois.get(s);
            int nx = roiArray.length, ny = roiArray[0].length;
            // TODO brute force, search until we find right side>= subx, then accumulate the roi x's until the left side is > than the subx
//            subx=0; suby=0; // debug
            yloop:
            for (int iy = 0; iy < ny; iy++) {
                xloop:
                for (int ix = 0; ix < nx; ix++) {
                    ROI roi = roiArray[ix][iy];
                    if (roi.xRight < subx || roi.yTop < suby) {
                        // continue while ROI UR corner has not reached event
                        continue;
                    }
                    if (roi.xLeft > subx) {
                        // break out of x loop when LL corner passes event x
                        // indices are zero based so use >
                        break xloop;
                    }
                    if (roi.yBot > suby) {
                        // break out of both loops when LL corner passes event
                        // indices are zero based so use >
                        break yloop;
                    }

                    int locx = subx - roi.xLeft, locy = suby - roi.yBot;
                    roi.addEvent(locx, locy, e.polarity);
                    if (roi.getAccumulatedEventCount() > dvsEventCount*(1<<(2*roi.scale))) {
                        getSupport().firePropertyChange(EVENT_NEW_ROI_AVAILABLE, null, roi);
                        if (showDvsFrames) {
                            drawDvsFrame(roi);
                        }
                        roi.clearAccumulatedEvents();
                    }
                }
            }
        }
    }

    @Override
    synchronized public void resetFilter() {
        if (rois != null) {
            for (ROI[][] a : rois) {
                for (ROI[] b : a) {
                    for (ROI c : b) {
                        c.clear();
                        c.setColorScale(grayScale);
                    }
                }
            }
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            reallocateRois();
        } else {
            rois = null;
        }
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (showROIsTemporarilyFlag) {
            Random random = new Random();
            float hue = 0;
            for (ROI[][] r : rois) {
                for (ROI[] rr : r) {
                    for (ROI rrr : rr) {
                        gl.glPushMatrix();
                        int rgb = Color.HSBtoRGB(hue += 0.1f, 1, 1);
                        if (hue > 1) {
                            hue -= 1;
                        }
                        Color c = new Color(rgb);
                        gl.glColor3fv(c.getColorComponents(null), 0);
                        DrawGL.drawBox(gl,
                                (rrr.xCenter) + 3 * (random.nextFloat() - 0.5f),
                                (rrr.yCenter) + 3 * (random.nextFloat() - 0.5f),
                                rrr.getWidth(), rrr.getHeight(), 0);
                        gl.glPopMatrix();
                    }
                }
            }

        }
        for (ROI[][] r : rois) {
            for (ROI[] rr : r) {
                for (ROI rrr : rr) {
                    rrr.draw(gl);
                }
            }
        }
    }

    private void showRoisTemporarily() {
        if (stopShowingStuffTask != null) {
            stopShowingStuffTask.cancel();
        }
        stopShowingStuffTask = new TimerTask() {
            @Override
            public void run() {
                showROIsTemporarilyFlag = false;
            }
        };
        Timer showAreaCountsAreasTimer = new Timer();
        showROIsTemporarilyFlag = true;
        showAreaCountsAreasTimer.schedule(stopShowingStuffTask, SHOW_STUFF_DURATION_MS);
    }

    /**
     * @return the numScales
     */
    public int getNumScales() {
        return numScales;
    }

    /**
     * @param numScales the numScales to set
     */
    synchronized public void setNumScales(int numScales) {
        this.numScales = numScales;
        putInt("numScales", numScales);
        reallocateRois();
        showRoisTemporarily();
    }

    /**
     * @return the dimension
     */
    public int getDimension() {
        return dimension;
    }

    /**
     * @param dimension the dimension to set
     */
    synchronized public void setDimension(int dimension) {
        this.dimension = dimension;
        putInt("dimension", dimension);
        setStride(dimension / 2);
        showRoisTemporarily();
    }

    /**
     * @return the stride
     */
    public int getStride() {
        return stride;
    }

    /**
     * @param stride the stride to set
     */
    synchronized public void setStride(int stride) {
        int old = this.stride;
        this.stride = stride;
        putInt("stride", stride);
        getSupport().firePropertyChange("stride", old, stride); // update GUI
        reallocateRois();
        showRoisTemporarily();
    }

    synchronized private void reallocateRois() {

        rois = new ArrayList<ROI[][]>(); // 2d array for each scale
        for (int s = 0; s < numScales; s++) {
            // the "size" at this scale is the sensor array size scaled by scale. events are subsampled to the ROIs based on the scale,
            // e.g. for scale=1, 2x2 pixels are collected to 1x1 ROI pixel
            sx = chip.getSizeX() >> s;
            sy = chip.getSizeY() >> s;
            // for this scale, determine how many x and y overlapping ROIs there will be
            // divide the number of pixels by the stride, and then check if there is a remainder, if so add 1 for partial ROI
            nx = sx / stride;
            if (nx % stride > 0) {
                nx++;
            }
            ny = sy / stride;
            if (ny % stride > 0) {
                ny++;
            }
            ROI[][] roiArray = new ROI[nx][ny];
            rois.add(roiArray);

            int yll = 0;
            for (int y = 0; y < ny; y++) {
                int xll = 0;
                for (int x = 0; x < nx; x++) {
                    roiArray[x][y] = new ROI(xll, yll, s, dimension, dimension, grayScale);
                    xll += stride << s;
                }
                yll += stride << s;
            }
        }
    }

    synchronized private void drawDvsFrame(DvsSubsamplerToFrame roi) {
        if (dvsFrame == null) {
            String windowName = "DVS frame";
            dvsFrame = new JFrame(windowName);
            dvsFrame.setLayout(new BoxLayout(dvsFrame.getContentPane(), BoxLayout.Y_AXIS));
            dvsFrame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            dvsFrameImageDisplay = ImageDisplay.createOpenGLCanvas();
            dvsFrameImageDisplay.setBorderSpacePixels(10);
            dvsFrameImageDisplay.setImageSize(dimension, dimension);
            dvsFrameImageDisplay.setSize(200, 200);
            dvsFrameImageDisplay.setGrayValue(0);
//            sliceBitmapLegend = sliceBitmapImageDisplay.addLegend(G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
            panel.add(dvsFrameImageDisplay);

            dvsFrame.getContentPane().add(panel);
            dvsFrame.pack();
            dvsFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowDvsFrames(false);
                }
            });
        }
        if (!dvsFrame.isVisible()) {
            dvsFrame.setVisible(true);
        }
        float scale = 1f / roi.getColorScale();
        if (dimension != dvsFrameImageDisplay.getSizeX()) {
            dvsFrameImageDisplay.setImageSize(dimension, dimension);
            dvsFrameImageDisplay.clearLegends();
//                    sliceBitmapLegend = dvsFrameImageDisplay.addLegend(G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
        }

//        TextRenderer textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 12));

        /* Reset the image first */
        dvsFrameImageDisplay.clearImage();
        /* Rendering the reference patch in t-imuWarningDialog slice, it's on the center with color red */
        for (int x = 0; x < dimension; x++) {
            for (int y = 0; y < dimension; y++) {
                float f = roi.getValueAtPixel(x, y);
                float[] rgb = {f, f, f};
                dvsFrameImageDisplay.setPixmapRGB(x, y, rgb);
            }
        }

//        if (sliceBitmapLegend != null) {
//            sliceBitmapLegend.s = G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH + "\nScale: " + subSampleBy;
//        }
        dvsFrameImageDisplay.repaint();
    }

    /**
     * @return the showDvsFrames
     */
    public boolean isShowDvsFrames() {
        return showDvsFrames;
    }

    /**
     * @param showDvsFrames the showDvsFrames to set
     */
    public void setShowDvsFrames(boolean showDvsFrames) {
        this.showDvsFrames = showDvsFrames;
    }

    /**
     * @return the dvsEventCount
     */
    public int getDvsEventCount() {
        return dvsEventCount;
    }

    /**
     * @param dvsEventCount the dvsEventCount to set
     */
    public void setDvsEventCount(int dvsEventCount) {
        int old = this.dvsEventCount;
        this.dvsEventCount = dvsEventCount;
        putInt("dvsEventCount", dvsEventCount);
        getSupport().firePropertyChange("dvsEventCount", old, this.dvsEventCount); // for when enclosed sets it
    }

    /**
     * @return the grayScale
     */
    public int getGrayScale() {
        return grayScale;
    }

    /**
     * @param grayScale the grayScale to set
     */
    public void setGrayScale(int grayScale) {
        this.grayScale = grayScale;
        putInt("grayScale", grayScale);
        resetFilter();
    }

    /**
     * Returns number of ROIs in x direction for scale 0
     *
     * @return the nx
     */
    public int getNx() {
        return nx;
    }

    /**
     * Returns number of ROIs in y direction for scale 0
     *
     * @return the ny
     */
    public int getNy() {
        return ny;

    }

    /**
     * One region of interest (ROI)
     */
    public class ROI extends DvsSubsamplerToFrame {

        private int xLeft, xRight;
        private int yBot, yTop;
        private float xCenter, yCenter;
        private int scale;
        private int xidx;
        private int yidx;
        // CNN output layer activations asssociated with the ROI and the RGB alpha color values to draw for it
        private float activations[], rgba[];

        public ROI(int xLowerLeft, int yLowerLeft, int scale, int dimX, int dimY, int colorScale) {
            super(dimX, dimY, colorScale);
            this.xLeft = xLowerLeft;
            this.yBot = yLowerLeft;
            this.xRight = xLeft + (dimX << scale) - 1;
            this.yTop = yBot + (dimY << scale) - 1;
            xCenter = (xLeft + xRight) / 2;
            yCenter = (yBot + yTop) / 2;
            this.scale = scale;
        }

        @Override
        public void clear() {
            super.clear();
            if (activations != null) {
                Arrays.fill(activations, 0);
            }
            if (rgba != null) {
                Arrays.fill(rgba, 0);
            }
        }

        private void clearAccumulatedEvents() {
            super.clear();
        }

        public void draw(GL2 gl) {
            if (rgba == null) {
                return;
            }
            try {
                gl.glEnable(GL2.GL_BLEND);
                gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_CONSTANT_ALPHA);
                gl.glBlendEquation(GL2.GL_FUNC_ADD); // use additive color here to just brighten the pixels already there
                gl.glBlendColor(1, 1, 1, 1);
            } catch (final GLException e) {
                e.printStackTrace();
            }
//            rgba[3] = 0.05f;
            gl.glColor4fv(rgba, 0);
//            gl.glColor4fv(new float[]{0,1,0,.05f}, 0);

            gl.glRectf(xLeft, yBot, xRight, yTop);
            if (roiLastUpdated == this) {
                // mark it
                gl.glColor3f(0, 1, 0);
                gl.glPushMatrix();
                DrawGL.drawBox(gl, xCenter, yCenter, width<<scale, height<<scale, 0);
                gl.glPopMatrix();
            }
        }

        @Override
        public String toString() {
            return "ROI{" + "xLeft=" + xLeft + ", xRight=" + xRight + ", yBot=" + yBot + ", yTop=" + yTop + ", scale=" + scale + ", xidx=" + xidx + ", yidx=" + yidx + ", activations=" + activations + ", rgba=" + rgba + '}';
        }

        /**
         * @return the xLeft
         */
        public int getxLeft() {
            return xLeft;
        }

        /**
         * @return the yBot
         */
        public int getyBot() {
            return yBot;
        }

        /**
         * @return the scale
         */
        public int getScale() {
            return scale;
        }

        /**
         * @return the xidx
         */
        public int getXidx() {
            return xidx;
        }

        /**
         * @return the yidx
         */
        public int getYidx() {
            return yidx;
        }

        /**
         * @return the activations
         */
        public float[] getActivations() {
            return activations;
        }

        /**
         * @param activations the activations to set
         */
        public void setActivations(float[] activations) {
            this.activations = activations;
            roiLastUpdated = this;
        }

        /**
         * @return the rgba
         */
        public float[] getRgba() {
            return rgba;
        }

        /**
         * @param rgba the rgba to set
         */
        public void setRgba(float[] rgba) {
            this.rgba = rgba;
            roiLastUpdated = this;
        }

    }

}