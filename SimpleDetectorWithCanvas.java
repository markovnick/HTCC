/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.clas.detector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.jlab.clas.detector.DetectorCollection;
import org.jlab.clas.detector.DetectorDescriptor;
import org.jlab.clas.detector.DetectorType;
import org.jlab.clas12.basic.IDetectorProcessor;
import org.jlab.clas12.calib.DetectorShape2D;
import org.jlab.clas12.calib.DetectorShapeTabView;
import org.jlab.clas12.calib.DetectorShapeView2D;
import org.jlab.clas12.calib.IDetectorListener;
import org.jlab.data.io.DataEvent;
import org.jlab.evio.clas12.EvioDataEvent;
import org.root.attr.ColorPalette;
import org.root.histogram.H1D;
import org.root.pad.EmbeddedCanvas;
import org.jlab.clas.detector.*;
import org.jlab.clas12.detector.*;
import org.jlab.clasrec.main.DetectorEventProcessorDialog;
import org.jlab.clasrec.utils.*;
import org.root.attr.TStyle;

/**
 *
 * @author gavalian
 */
public class SimpleDetectorWithCanvas extends JFrame implements IDetectorListener, IDetectorProcessor, ActionListener {

    DetectorCollection<H1D> tdcH = new DetectorCollection<H1D>();
    DetectorCollection<H1D> adcH = new DetectorCollection<H1D>();
    DetectorCollection<H1D> pedH = new DetectorCollection<H1D>();

    DetectorCollection<H1D> HV = new DetectorCollection<H1D>();

    DetectorShapeTabView view = new DetectorShapeTabView();
    EmbeddedCanvas canvas = new EmbeddedCanvas();
    int nProcessed = 0;
    EventDecoder decoder = new EventDecoder();
    DatabaseConstantProvider dbprovider = new DatabaseConstantProvider(10, "default");

    // ColorPalette class defines colors 
    ColorPalette palette = new ColorPalette();

    public SimpleDetectorWithCanvas() {
        super();

        this.initDetector();
        this.initHistograms();
        this.initDBConstants();
        this.setLayout(new BorderLayout());
        JSplitPane splitPane = new JSplitPane();
        splitPane.setLeftComponent(this.view);
        splitPane.setRightComponent(this.canvas);
        this.add(splitPane, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton process = new JButton("Process");
        buttons.setLayout(new FlowLayout());
        buttons.add(process);
        process.addActionListener(this);
        this.add(buttons, BorderLayout.PAGE_END);
        this.pack();
        this.setVisible(true);
        TStyle.setOptStat(false);

    }

    private void initHistograms() {
        for (int sector = 0; sector < 6; sector++) {
            for (int halfSector = 0; halfSector < 2; halfSector++) {
                for (int ring = 0; ring < 4; ring++) {
                    // DetectorDescriptor.getName() returns a numbered 
                    // String with sector, layer and paddle numbers.
                    tdcH.add(sector, halfSector, ring,
                            new H1D(DetectorDescriptor.getName("Signal", sector, halfSector, ring),
                                    200, 0.0, 200.0));
                    adcH.add(sector, halfSector, ring,
                            new H1D(DetectorDescriptor.getName("ADC ", sector, halfSector, ring),
                                    500, 0.0, 10000.0));
                    pedH.add(sector, halfSector, ring,
                            new H1D(DetectorDescriptor.getName("Pedestal", sector, halfSector, ring),
                                    500, 0.0, 10000.0));

                    HV.add(sector, halfSector, ring,
                            new H1D(DetectorDescriptor.getName("HV", sector, halfSector, ring),
                                    3500, 0.0, 3500.0));
                }
            }
        }
    }

    private void initDBConstants() {
        dbprovider.loadTable("/test/cchv_markov/hvcc_table");
        dbprovider.disconnect();
        dbprovider.show();
        for (int loop = 0; loop < dbprovider.length("/test/cchv_markov/hvcc_table/hv"); loop++) {
            double value = dbprovider.getDouble("/test/cchv_markov/hvcc_table/hv", loop);
            int sector = (int) loop / 8;
            int ring = loop % 4;
            int halfSector = (int) (loop - 8 * sector) / 4;
            HV.get(sector, halfSector, ring).fill(value);
            int nent = HV.get(sector, halfSector, ring).getEntries();
            // for integer values use dbprovider.getInteger("/calibration/ftof/attenuation/y_offset",loop);
        }

    }

    /**
     * Creates a detector Shape.
     */
    private void initDetector() {

        DetectorShapeView2D dv2 = new DetectorShapeView2D("HTCC");
        for (int sector = 0; sector < 6; sector++) {
            for (int halfSector = 0; halfSector < 2; halfSector++) {
                for (int ring = 0; ring < 4; ring++) {
                    DetectorShape2D shape = new DetectorShape2D(DetectorType.HTCC, sector, halfSector, ring);
                    shape.createArc(12 + ring * 12, 12 + ring * 12 + 12, -30.0, 0.0);
                    shape.getShapePath().rotateZ(Math.toRadians((sector * 2 + halfSector) * 30.0));

                    dv2.addShape(shape);
                }
            }
        }
        this.view.addDetectorLayer(dv2);
        view.addDetectorListener(this);
    }

    /**
     * When the detector is clicked, this function is called
     *
     * @param desc
     */
    public void detectorSelected(DetectorDescriptor desc) {
        this.canvas.divide(1, 4);
        if (tdcH.hasEntry(desc.getSector(), desc.getLayer(), desc.getComponent())) {
            H1D h1 = tdcH.get(desc.getSector(), desc.getLayer(), desc.getComponent());
            h1.setTitle(h1.getName());
            canvas.cd(0);
            h1.setFillColor(6);
            h1.setXTitle("Channels");

            canvas.draw(h1);
        }
        if (adcH.hasEntry(desc.getSector(), desc.getLayer(), desc.getComponent())) {
            H1D h1 = adcH.get(desc.getSector(), desc.getLayer(), desc.getComponent());
            h1.setTitle(h1.getName());
            h1.setXTitle("Channels");
            canvas.cd(1);
            h1.setFillColor(4);
            canvas.draw(h1);
        }
        if (pedH.hasEntry(desc.getSector(), desc.getLayer(), desc.getComponent())) {
            H1D h1 = pedH.get(desc.getSector(), desc.getLayer(), desc.getComponent());
            h1.setTitle(h1.getName());
            h1.setXTitle("Channels");
            canvas.cd(2);
            h1.setFillColor(3);
            canvas.draw(h1);
        }
        if (HV.hasEntry(desc.getSector(), desc.getLayer(), desc.getComponent())) {
            H1D h1 = HV.get(desc.getSector(), desc.getLayer(), desc.getComponent());
            h1.setTitle(h1.getName());
            canvas.cd(3);
            h1.setFillColor(6);
            h1.setXTitle("Volts");

            canvas.draw(h1);
        }

    }

    /**
     * Each redraw of the canvas passes detector shape object to this routine
     * and user can change the color of specific component depending on
     * оccupancy or some other criteria.
     *
     * @param shape
     */
    public void update(DetectorShape2D shape) {

        int sector = shape.getDescriptor().getSector();
        int ring = shape.getDescriptor().getComponent();
        int halfSector = shape.getDescriptor().getLayer();
     //   System.out.println("sector : " + sector + " halfsector " + halfSector + "ring: " + ring);

        //  shape.setColor(200, 200, 200);
        if (this.tdcH.hasEntry(sector, halfSector, ring)) {
            int nent = this.adcH.get(sector, halfSector, ring).getEntries();
            Color col = palette.getColor3D(nent, nProcessed, true);

            int colorRed = 120;
            if (nProcessed != 0 && nent != 0) {
                nent = nent / 1000;
                colorRed = (240 * nent) / (nent * (sector + 1));
            }
            shape.setColor(colorRed, col.getGreen(), col.getBlue());
        }
    }

    public void processEvent(DataEvent de) {

        EvioDataEvent event = (EvioDataEvent) de;

        nProcessed++;
        decoder.decode(event);
        List<DetectorBankEntry> counters = decoder.getDataEntries("HTCC");
        // The entire list of decoded data can be obtained by:
        List<DetectorCounter> myCounters = decoder.getDetectorCounters(DetectorType.FTOF1A);
        for (DetectorBankEntry cnt : counters) {
            if (cnt.getType() == BankType.ADCPULSE) {
                H1D hp = EventDecoder.getADCPulse(cnt);
                int sector = cnt.getDescriptor().getSector();
                int ring = cnt.getDescriptor().getLayer();
                int halfSector = cnt.getDescriptor().getComponent();
                double signal = 0;
                double pedestal = 0;
                for (int bin = 0; bin < hp.getxAxis().getNBins(); bin++) {
                    if (bin > 130 && bin < 140) {
                        signal = signal + hp.getBinContent(bin);
                    }
                    if (bin > 30 && bin < 40) {
                        pedestal = pedestal + hp.getBinContent(bin);
                    }
                    tdcH.get(sector, halfSector, ring).fill(bin, hp.getBinContent(bin));
                }
                adcH.get(sector, halfSector, ring).fill(signal);
                pedH.get(sector, halfSector, ring).fill(pedestal);

            }
        }

        this.view.repaint();

    }

    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().compareTo("Process") == 0) {
            DetectorEventProcessorDialog dialog = new DetectorEventProcessorDialog(this);
        }
    }

    public static void main(String[] args) {
        new SimpleDetectorWithCanvas();
    }
}
