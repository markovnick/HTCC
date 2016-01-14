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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import javax.swing.*;

/**
 *
 * @author gavalian
 */
public class SimpleDetectorWithCanvas extends JFrame implements IDetectorListener, IDetectorProcessor, ActionListener {

    DetectorCollection<H1D> tdcH = new DetectorCollection<H1D>();
    DetectorCollection<H1D> adcH = new DetectorCollection<H1D>();
    DetectorCollection<H1D> pedH = new DetectorCollection<H1D>();

    DetectorCollection<H1D> npeH = new DetectorCollection<H1D>();
    private H1D[] npeHAsRing = new H1D[4];
    DetectorShapeTabView view = new DetectorShapeTabView();
    EmbeddedCanvas canvas = new EmbeddedCanvas();
    int nProcessed = 0;
    int[] nProcessedRing = {0, 0, 0, 0};
    EventDecoder decoder = new EventDecoder();
    DatabaseConstantProvider dbprovider = new DatabaseConstantProvider(10, "default");
    DatabaseConstantProvider dbprovider1 = new DatabaseConstantProvider(10, "default");

    private double[][][] gainArray = new double[6][2][4];
    private double[] ledArray = new double[4];

    private int[][][] statusArray = new int[6][2][4];

    private double[] npeArray = {0.0, 0.0, 0.0, 0.0};
    // ColorPalette class defines colors 
    ColorPalette palette = new ColorPalette();
    JTextField V1, V2, V3, Frequency;

    public SimpleDetectorWithCanvas() {
        super();

        this.initDetector();
        this.initHistograms();
        this.initDBConstants();
        this.setLayout(new BorderLayout());
        JSplitPane splitPane = new JSplitPane();
        JSplitPane sideLeftPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel buttonPane = new JPanel();
        sideLeftPanel.setTopComponent(this.view);
        sideLeftPanel.setBottomComponent(buttonPane);

        splitPane.setLeftComponent(sideLeftPanel);
        splitPane.setRightComponent(this.canvas);
        this.add(splitPane, BorderLayout.CENTER);

        String[] labelStrings = {
            "V1 (V): ",
            "V2 (V): ",
            "V3 (V): ",
            "Frequency (Hz): "
        };

        JLabel[] labels = new JLabel[labelStrings.length];
        JComponent[] fields = new JComponent[labelStrings.length];
        int fieldNum = 0;

        //Create the text field and set it up.
        V1 = new JTextField(String.valueOf(ledArray[0]));
        V1.setColumns(3);
        fields[fieldNum++] = V1;
        V2 = new JTextField(String.valueOf(ledArray[1]));
        V2.setColumns(3);
        fields[fieldNum++] = V2;
        V3 = new JTextField(String.valueOf(ledArray[2]));
        V3.setColumns(3);
        fields[fieldNum++] = V3;
        Frequency = new JTextField(String.valueOf(ledArray[3]));
        Frequency.setColumns(4);
        fields[fieldNum++] = Frequency;
        for (int i = 0; i < labelStrings.length; i++) {
            labels[i] = new JLabel(labelStrings[i],
                    JLabel.TRAILING);
            labels[i].setLabelFor(fields[i]);
            buttonPane.add(labels[i]);
            buttonPane.add(fields[i]);
        }
        JButton setLMS = new JButton("Set LMS");
        buttonPane.add(setLMS);
        setLMS.addActionListener(this);
        JLabel label1 = new JLabel("LMS is");
        JLabel label2 = new JLabel("OFF");

   //     buttonPane.add(label1);
        //     buttonPane.add(label2);
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
        for (int ring = 0; ring < 4; ring++) {
            npeHAsRing[ring] = new H1D("npeHAsRing" + ring, 40, 0.0, 40.0);
        }
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
                    npeH.add(sector, halfSector, ring,
                            new H1D(DetectorDescriptor.getName("npeH", sector, halfSector, ring),
                                    30, 0.0, 30.0));
                }
            }
        }
    }

    private void initDBConstants() {
        dbprovider.loadTable("/test/cc_calib/htcc/htccGain");
        dbprovider.disconnect();
        for (int loop = 0; loop < dbprovider.length("/test/cc_calib/htcc/htccGain/gain"); loop++) {
            double value = dbprovider.getDouble("/test/cc_calib/htcc/htccGain/gain", loop);
            int sector = (int) loop / 8;
            int ring = loop % 4;
            int halfSector = (int) (loop - 8 * sector) / 4;
            gainArray[sector][halfSector][ring] = value;
        }

        dbprovider1.loadTable("/test/cc_calib/htcc/LED");
        dbprovider1.disconnect();
        ledArray[0] = dbprovider1.getDouble("/test/cc_calib/htcc/LED/V1", 0);
        ledArray[1] = dbprovider1.getDouble("/test/cc_calib/htcc/LED/V2", 0);
        ledArray[2] = dbprovider1.getDouble("/test/cc_calib/htcc/LED/V3", 0);
        ledArray[3] = dbprovider1.getDouble("/test/cc_calib/htcc/LED/Frequency", 0);
        System.out.println("v1: " + ledArray[0]);
        System.out.println("v2: " + ledArray[1]);
        System.out.println("v3: " + ledArray[2]);

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
        if (npeH.hasEntry(desc.getSector(), desc.getLayer(), desc.getComponent())) {
            H1D h1 = npeH.get(desc.getSector(), desc.getLayer(), desc.getComponent());
            h1.setTitle(h1.getName());
            canvas.cd(3);
            h1.setFillColor(6);
            canvas.draw(h1);
            H1D h2 = new H1D("h2", 30, 0, 30);
            if (nProcessedRing[desc.getComponent()] > 0) {
                h2.fill(npeArray[desc.getComponent()] / nProcessedRing[desc.getComponent()], 20000);
                h2.setFillColor(3);
                canvas.draw(h2, "same");
            }
        }

    }

    public float readButton(JTextField textF) {
        float value;
        String V1String = textF.getText();
        try {
            value = Float.parseFloat(V1String);
        } catch (NumberFormatException ex) {
            value = 0.0f; // default ??
            JOptionPane.showMessageDialog(this,
                    "Wrong input",
                    "Input error",
                    JOptionPane.ERROR_MESSAGE);
        }
        return value;
    }

    public void setLMSValue() {
        float v1Value = 0.0f, v2Value = 0.0f, v3Value = 0.0f, frequencyValue = 0.0f;
        v1Value = readButton(this.V1);
        v2Value = readButton(this.V2);
        v3Value = readButton(this.V3);
        frequencyValue = readButton(this.Frequency);
        if (v1Value > 3.2 && v1Value < 5.5 && v2Value > 3.2 && v2Value < 5.5 && v3Value > 3.2 && v3Value < 8 && frequencyValue<20000) {
            try {
                Process p = Runtime.getRuntime().exec("echo " + "tcpClient ltcc0 \"flpInit(0x00300000, 0)\"");
                p.waitFor();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();

                while (line != null) {
                    System.out.println(line);
                    line = reader.readLine();
                }

            } catch (IOException e1) {
            } catch (InterruptedException e2) {
            }

            try {
                Process p = Runtime.getRuntime().exec("echo " + "tcpClient ltcc0 \" flpEnableOutput(0)\"");
                p.waitFor();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();

                while (line != null) {
                    System.out.println(line);
                    line = reader.readLine();
                }

            } catch (IOException e1) {
            } catch (InterruptedException e2) {
            }
            try {
                Process p = Runtime.getRuntime().exec("echo " + " tcpClient ltcc0 \"flpSetOutputVoltages(1, " + v1Value + "," + v2Value + "," + v3Value + ")\"");
                p.waitFor();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();

                while (line != null) {
                    System.out.println(line);
                    line = reader.readLine();
                }

            } catch (IOException e1) {
            } catch (InterruptedException e2) {
            }
            try {
                Process p = Runtime.getRuntime().exec("echo " + " tcpClient ltcc0 \"flpSetPulserPeriod(1, " + 50000000 / frequencyValue + ")\"");
                p.waitFor();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();

                while (line != null) {
                    System.out.println(line);
                    line = reader.readLine();
                }

            } catch (IOException e1) {
            } catch (InterruptedException e2) {
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "Wrong input",
                    "Input error",
                    JOptionPane.ERROR_MESSAGE);
        }
        System.out.println("finished.");
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
                if (gainArray[sector][halfSector][ring] > 0) {
                    npeH.get(sector, halfSector, ring).fill((signal - pedestal) / gainArray[sector][halfSector][ring]);
                    npeArray[ring] = npeArray[ring] + (signal - pedestal) / gainArray[sector][halfSector][ring];
                    nProcessedRing[ring]++;
                }

            }
        }
        this.view.repaint();
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().compareTo("Process") == 0) {
            DetectorEventProcessorDialog dialog = new DetectorEventProcessorDialog(this);
        }
        if (e.getActionCommand().compareTo("Set LMS") == 0) {
            setLMSValue();
        }
    }

    public static void main(String[] args) {
        new SimpleDetectorWithCanvas();
    }
}
