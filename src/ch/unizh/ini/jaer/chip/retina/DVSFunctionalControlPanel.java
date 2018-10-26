/*
 * Tmpdiff128FunctionalBiasgenPanel.java
 *
 * Created on June 19, 2006, 1:48 PM
 */
package ch.unizh.ini.jaer.chip.retina;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;

import net.sf.jaer.biasgen.PotTweaker;

/**
 * A panel for simplified control of DVS retina biases.
 *
 * @author tobi
 */
public class DVSFunctionalControlPanel extends javax.swing.JPanel implements PropertyChangeListener {

    AETemporalConstastRetina chip;
    DVSTweaks biasgen;
    private static final Logger log = Logger.getLogger("DVSFunctionalControlPanel");

    /**
     * Creates new form Tmpdiff128FunctionalBiasgenPanel
     */
    public DVSFunctionalControlPanel(AETemporalConstastRetina chip) {
        initComponents();
        this.chip = chip;
        biasgen = (DVSTweaks) chip.getBiasgen();
        PotTweaker[] tweakers = {thresholdTweaker, onOffBalanceTweaker, maxFiringRateTweaker, bandwidthTweaker};
        for (PotTweaker tweaker : tweakers) {
            chip.getBiasgen().getSupport().addPropertyChangeListener(tweaker); // to reset sliders on load/save of biases
        }
        setEstimatedThresholdValues();
        chip.getSupport().addPropertyChangeListener(this);
    }

    private void setFileModified() {
        if (chip != null && chip.getAeViewer() != null && chip.getAeViewer().getBiasgenFrame() != null) {
            chip.getAeViewer().getBiasgenFrame().setFileModified(true);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        bandwidthTweaker = new net.sf.jaer.biasgen.PotTweaker();
        thresholdTweaker = new net.sf.jaer.biasgen.PotTweaker();
        jPanel1 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        onThrTF = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        offThrTF = new javax.swing.JTextField();
        onOffBalanceTweaker = new net.sf.jaer.biasgen.PotTweaker();
        maxFiringRateTweaker = new net.sf.jaer.biasgen.PotTweaker();

        setLayout(new java.awt.GridLayout(0, 1));

        jLabel1.setText("<html>This panel allows \"tweaking\" bias values around the nominal ones loaded from the XML file. Change made here are <b>not</b> permanent until the settings are saved to an XML file. On restart, these new settings will then become the nominal settings.");
        add(jLabel1);

        bandwidthTweaker.setLessDescription("Slower");
        bandwidthTweaker.setMoreDescription("Faster");
        bandwidthTweaker.setName("Bandwidth"); // NOI18N
        bandwidthTweaker.setTweakDescription("Tweaks bandwidth of pixel front end.");
        bandwidthTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                bandwidthTweakerStateChanged(evt);
            }
        });
        add(bandwidthTweaker);

        thresholdTweaker.setLessDescription("Lower/more events");
        thresholdTweaker.setMoreDescription("Higher/less events");
        thresholdTweaker.setName("Threshold"); // NOI18N
        thresholdTweaker.setTweakDescription("Adjusts event threshold");
        thresholdTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                thresholdTweakerStateChanged(evt);
            }
        });
        add(thresholdTweaker);

        jLabel2.setText("ON threshold");
        jPanel1.add(jLabel2);

        onThrTF.setEditable(false);
        onThrTF.setColumns(10);
        onThrTF.setToolTipText("Estimated DVS  temporal contrast threshold  (log base e units)");
        jPanel1.add(onThrTF);

        jLabel3.setText("OFF threshold");
        jPanel1.add(jLabel3);

        offThrTF.setEditable(false);
        offThrTF.setColumns(10);
        offThrTF.setToolTipText("Estimated DVS  temporal contrast threshold  (log base e units)");
        offThrTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offThrTFActionPerformed(evt);
            }
        });
        jPanel1.add(offThrTF);

        add(jPanel1);

        onOffBalanceTweaker.setLessDescription("More Off events");
        onOffBalanceTweaker.setMoreDescription("More On events");
        onOffBalanceTweaker.setName("On/Off balance"); // NOI18N
        onOffBalanceTweaker.setTweakDescription("Adjusts balance bewteen On and Off events");
        onOffBalanceTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                onOffBalanceTweakerStateChanged(evt);
            }
        });
        add(onOffBalanceTweaker);

        maxFiringRateTweaker.setLessDescription("Slower");
        maxFiringRateTweaker.setMoreDescription("Faster");
        maxFiringRateTweaker.setName("Maximum firing rate"); // NOI18N
        maxFiringRateTweaker.setTweakDescription("Adjusts maximum pixel firing rate (1/refactory period)");
        maxFiringRateTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                maxFiringRateTweakerStateChanged(evt);
            }
        });
        add(maxFiringRateTweaker);
    }// </editor-fold>//GEN-END:initComponents

    private void bandwidthTweakerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_bandwidthTweakerStateChanged
        biasgen.setBandwidthTweak(bandwidthTweaker.getValue());
        setFileModified();
    }//GEN-LAST:event_bandwidthTweakerStateChanged

    private void thresholdTweakerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_thresholdTweakerStateChanged
        biasgen.setThresholdTweak(thresholdTweaker.getValue());

        setEstimatedThresholdValues();
        setFileModified();
    }//GEN-LAST:event_thresholdTweakerStateChanged

    private void setEstimatedThresholdValues() {
        onThrTF.setText(String.format("%.3f", biasgen.getOnThresholdLogE()));
        offThrTF.setText(String.format("%.3f", biasgen.getOffThresholdLogE()));
    }

    private void maxFiringRateTweakerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_maxFiringRateTweakerStateChanged
        biasgen.setMaxFiringRateTweak(maxFiringRateTweaker.getValue());
        setFileModified();
    }//GEN-LAST:event_maxFiringRateTweakerStateChanged

    private void onOffBalanceTweakerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_onOffBalanceTweakerStateChanged
        biasgen.setOnOffBalanceTweak(onOffBalanceTweaker.getValue());
        setEstimatedThresholdValues();
        setFileModified();
    }//GEN-LAST:event_onOffBalanceTweakerStateChanged

    private void offThrTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offThrTFActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_offThrTFActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private net.sf.jaer.biasgen.PotTweaker bandwidthTweaker;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private net.sf.jaer.biasgen.PotTweaker maxFiringRateTweaker;
    private javax.swing.JTextField offThrTF;
    private net.sf.jaer.biasgen.PotTweaker onOffBalanceTweaker;
    private javax.swing.JTextField onThrTF;
    private net.sf.jaer.biasgen.PotTweaker thresholdTweaker;
    // End of variables declaration//GEN-END:variables

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        try {
            if (evt.getPropertyName() == DVSTweaks.THRESHOLD) {
                float v = (Float) evt.getNewValue();
                thresholdTweaker.setValue(v);

            } else if (evt.getPropertyName() == DVSTweaks.BANDWIDTH) {
                float v = (Float) evt.getNewValue();
                bandwidthTweaker.setValue(v);

            } else if (evt.getPropertyName() == DVSTweaks.MAX_FIRING_RATE) {
                float v = (Float) evt.getNewValue();
                maxFiringRateTweaker.setValue(v);

            } else if (evt.getPropertyName() == DVSTweaks.ON_OFF_BALANCE) {
                float v = (Float) evt.getNewValue();
                onOffBalanceTweaker.setValue(v);

            }
        } catch (Exception e) {
            log.warning("responding to property change, caught " + e.toString());
            e.printStackTrace();
        }
    }
}
