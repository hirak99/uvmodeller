package uvmodeller;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;

class ColorDialog extends JDialog {
    /** This is set to true if user cancels the dialog */
    public boolean cancelled=false;
    static private final JColorChooser colorChooser=new JColorChooser();
    private JButton btnOk,btnCancel;
    public ColorDialog(Frame owner,Color color) {
        super(owner,"Color Chooser",true);
        Container pane=getContentPane();
        pane.setLayout(new BoxLayout(pane,BoxLayout.Y_AXIS));
        pane.add(colorChooser);
        colorChooser.setColor(color);
        colorChooser.setAlignmentX(1);
        
        Box buttonsBox=new Box(BoxLayout.X_AXIS);
        buttonsBox.add(btnOk=new JButton("Ok")); btnOk.setMnemonic('O');
        buttonsBox.add(Box.createHorizontalStrut(3));
        buttonsBox.add(btnCancel=new JButton("Cancel")); btnCancel.setMnemonic('C');
        ActionListener buttonListener=new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(e.getSource()==btnCancel) cancelled=true;
                dispose();
            }
        };
        btnOk.addActionListener(buttonListener);
        btnCancel.addActionListener(buttonListener);
        buttonsBox.setAlignmentX(1);
        pane.add(buttonsBox);

        getRootPane().setDefaultButton(btnOk);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }
    public Color getColor() {
        return colorChooser.getColor();
    }
}

class JColor extends JPanel {
    private Color color;
    public JColor(int width,int height) {
        Dimension d=new Dimension(width,height);
        setPreferredSize(d);
        setMaximumSize(d);
        setMinimumSize(d);
        color=Color.RED;
        setAlignmentX(0);
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                ColorDialog colorDialog=new ColorDialog(null,color);
                colorDialog.show();
                if (!colorDialog.cancelled) {
                    int alpha=color.getAlpha();
                    color=Utils.changeAlpha(colorDialog.getColor(),alpha);
                    repaint();
                }
            }
        });
    }
    public void setColor(Color newColor) {color=newColor; repaint();}
    public Color getColor() {return color;}
    public void paintComponent(Graphics g) {
        Rectangle r=getBounds();
        for (int x=0; x<r.width; x+=5)
        for (int y=0; y<r.height; y+=5) {
            if ((x+y)%10==0) g.setColor(Color.WHITE);
            else g.setColor(Color.BLACK);
            g.fillRect(x,y,5,5);
        }
        g.setColor(color);
        g.fill3DRect(0,0,r.width,r.height,true);
    }
}

class OptionsDialog extends JFrame {
    private Frame parentFrame;
    private ModelView modelView;
    private ModelView.FunctionsList functions;
    // dirty=true means file changed
    private boolean dirty=false;
    public boolean isDirty() {return dirty;}
    public void setDirty(boolean dirty) {this.dirty=dirty;}

    private JComboBox comboFunction;
    private JLabel lblFunctions;
    private JToggleButton tglIsCurve;
    private JCheckBox chkVisible;
    private JButton btnAdd,btnDelete;
    private JSpinner spinUSteps,spinVSteps;
    private JSpinner spinPenWidth;
    private Container paneUSteps,paneVSteps,panePenWidth,paneSurfaceColor; // need to show/hide
    private JTextArea textFuncDef;
    private JCheckBox chkFillSurface;
    private JColor curveColor,surfaceColor;
    private JSlider sldAlpha;
    private JLabel lblArea;
    private JComponent containerFunc;
    private JTabbedPane jtpMain;
    private ModelView.ModelFunction lastFunction;
    // updates the label beside functions combo box
    private void updateLblFunctions() {
        lblFunctions.setText("Number of function(s): "+functions.size());
    }
    private void updateBorderFunction() {
        TitledBorder borderFunc=(TitledBorder)containerFunc.getBorder();
        borderFunc.setTitle("\""+lastFunction.name+"\" properties");
        containerFunc.repaint();
    }
    private void updateLblArea() {
        double area=lastFunction.getArea(); area=(double)(int)(area*10000+.5)/10000;
        if (lastFunction.isCurve)
            lblArea.setText("Aproximate length : "+area);
        else lblArea.setText("Aproximate surface area : "+area);
    }
    // the left right buttons beside the combo box
    private JButton btnGoLeft,btnGoRight;
    // creates the 'toolbar'
    private Container createFunctionTools() {
        JPanel pane=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.fill=GridBagConstraints.HORIZONTAL; c.weightx=0; c.insets=new Insets(0,2,0,0);
        pane.add(btnGoLeft=new JButton(Utils.loadIcon("goleft.gif")),c); Utils.makeHot(btnGoLeft);
        c.weightx=1; c.insets=new Insets(0,0,0,0);
        pane.add(comboFunction=new JComboBox(),c);
        c.weightx=0; c.insets=new Insets(0,0,2,0);
        pane.add(btnGoRight=new JButton(Utils.loadIcon("goright.gif")),c); Utils.makeHot(btnGoRight);
        class OnLeftRight implements ActionListener {
            public void actionPerformed(ActionEvent e) {
                int index=(e.getSource().equals(btnGoLeft))?
                    comboFunction.getSelectedIndex()-1 :
                    comboFunction.getSelectedIndex()+1;
                index=index%comboFunction.getItemCount();
                comboFunction.setSelectedIndex(index);
            }
        };
        btnGoLeft.addActionListener(new OnLeftRight());
        btnGoRight.addActionListener(new OnLeftRight());
        comboFunction.setEditable(true);
        comboFunction.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange()==ItemEvent.SELECTED) {
                    Object selected=((JComboBox)e.getSource()).getSelectedItem();
                    if (selected instanceof ModelView.ModelFunction) {
                        lastFunction=(ModelView.ModelFunction)((JComboBox)e.getSource()).getSelectedItem();
                        getFunctionFields();
                    }
                }
                else setFunctionFields(lastFunction);
            }
        });
        comboFunction.addActionListener(new ActionListener() {
            // This lets the user edit the name of function.
            public void actionPerformed(ActionEvent e) {
                if (lastFunction!=null) {
                    lastFunction.name=comboFunction.getSelectedItem().toString();
                    updateBorderFunction();
                }
            }
        });
        c.weightx=0; c.fill=GridBagConstraints.NONE; c.insets=new Insets(0,2,0,0);
        pane.add(chkVisible=new JCheckBox(),c);
        chkVisible.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setFunctionFields();
            }
        });
        c.insets=new Insets(0,0,0,5);
        JLabel lblImage=new JLabel(Utils.loadIcon("eye.gif"));
        pane.add(lblImage,c);
        lblImage.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                chkVisible.setSelected(!chkVisible.isSelected());
                setFunctionFields();
            }
        });
        c.insets=new Insets(0,2,0,2);
        pane.add(btnAdd=new JButton("Add"),c);
        pane.add(btnDelete=new JButton("Delete"),c);
        c.weightx=2; c.anchor=GridBagConstraints.LINE_END; c.insets=new Insets(0,5,0,5);
        pane.add(lblFunctions=new JLabel(),c);
        updateLblFunctions();
        ActionListener listener=new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (e.getSource()==btnAdd) {
                    ModelView.ModelFunction func=functions.addFunction();
                    func.parseFunction();
                    comboFunction.addItem(func);
                    comboFunction.setSelectedIndex(comboFunction.getItemCount()-1);
                } else if(comboFunction.getItemCount()>1) {
                    int nFunc=comboFunction.getSelectedIndex();
                    comboFunction.removeItemAt(nFunc);
                    functions.removeFunction(nFunc);
                }
                updateLblFunctions();
                modelView.repaint();
            }
        };
        btnAdd.addActionListener(listener);
        btnDelete.addActionListener(listener);
        pane.setMaximumSize(new Dimension(Short.MAX_VALUE,30));
        return pane;
    }
    private JPanel jpanVStepsFill;    // fills the space for GridStepsV
    private JCheckBox chkAbsoluteWidth; // check box to specify Absolute Width
    // creates controls shown beside the text box
    private Container createFunctionControls() {
        JComponent pane=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(0,5,3,5);
        c.gridx=0; c.gridy=GridBagConstraints.RELATIVE; c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL; c.gridwidth=GridBagConstraints.REMAINDER;
        pane.add(tglIsCurve=new JToggleButton("Function is a curve",false),c);
        tglIsCurve.setSelected(true);   // fires the event
        tglIsCurve.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                boolean curve=tglIsCurve.isSelected();
                chkFillSurface.setVisible(!curve);
                ((JLabel)paneUSteps.getComponent(0)).setText(curve?"t steps:":"u steps:");
                paneVSteps.setVisible(!curve); jpanVStepsFill.setVisible(curve);
                panePenWidth.setVisible(curve); chkAbsoluteWidth.setVisible(curve);
                paneSurfaceColor.setVisible(!curve);
                if (lastFunction.isCurve!=curve) lblArea.setText("");
                else updateLblArea(); 
            }
        });
        c.gridx = 0; c.gridwidth=1;
        pane.add(paneUSteps=Utils.labeledComponent("u steps:",
            spinUSteps=new JSpinner(new SpinnerNumberModel(30,1,5000,1)),
            true),c);
        c.gridx=1;
        pane.add(jpanVStepsFill=new JPanel(),c);
        pane.add(paneVSteps=Utils.labeledComponent("v steps:",
            spinVSteps=new JSpinner(new SpinnerNumberModel(30,1,5000,1)),
            true),c);
        c.gridx = 0;
        pane.add(panePenWidth=Utils.labeledComponent("Pen Width: ",
            spinPenWidth=new JSpinner(new SpinnerNumberModel(2,0,1000,1)),
            true),c);
        c.gridx = 1;
        pane.add(chkAbsoluteWidth=new JCheckBox("Absolute Width"),c);
        c.gridx=0; c.gridy=GridBagConstraints.RELATIVE; c.gridwidth=GridBagConstraints.REMAINDER;
        pane.add(chkFillSurface=new JCheckBox("Fill Surface"),c);
        c.weightx=0; c.gridy=GridBagConstraints.RELATIVE; c.gridwidth=1;
        pane.add(Utils.labeledComponent("Curve color:",curveColor=new JColor(50,20),true),c);
        c.gridx=1; c.gridy=GridBagConstraints.RELATIVE;
        pane.add(paneSurfaceColor=Utils.labeledComponent("Surface color:",surfaceColor=new JColor(50,20),true),c);
        c.gridx=0; c.gridwidth=GridBagConstraints.REMAINDER;
        pane.add(Utils.labeledComponent("Transparency:",sldAlpha=new JSlider(0,255),true),c);
        sldAlpha.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int transp=255-sldAlpha.getValue();
                curveColor.setColor(Utils.changeAlpha(curveColor.getColor(),transp));
                surfaceColor.setColor(Utils.changeAlpha(surfaceColor.getColor(),transp));
            }
        });
        pane.add(lblArea=new JLabel("Area"),c); lblArea.setHorizontalAlignment(JLabel.CENTER);
        pane.setBorder(new EmptyBorder(5,5,5,5));
        return pane;
    }
    // puts the editor and the controls on a Container
    private Container createFunctionEditor() {
        Component component;
        JPanel pane=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.weightx=2; c.weighty=1;
        c.fill=GridBagConstraints.BOTH;
        component=Utils.labeledComponent("Function definition:",
            new JScrollPane(textFuncDef=new JTextArea()),false);
        pane.add(component,c);
        c.weightx=0;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.anchor=GridBagConstraints.PAGE_START;
        component=createFunctionControls();
        pane.add(component,c);
        pane.setBorder(BorderFactory.createTitledBorder("Function Properties"));
        return containerFunc=pane;
    }
    // collects all the function controls together
    private Container createFunctionsPane() {
        Box pane=new Box(BoxLayout.Y_AXIS);
        pane.add(Box.createVerticalStrut(2));
        pane.add(createFunctionTools());
        pane.add(createFunctionEditor());
        return pane;
    }
    // retrieve values to the controls
    private void getFunctionFields() {
        ModelView.ModelFunction function=lastFunction;
        if (function==null) return;
        updateBorderFunction();
        tglIsCurve.setSelected(function.isCurve);
        chkVisible.setSelected(function.visible);
        spinUSteps.setValue(new Integer(function.gridDivsU-1));
        spinVSteps.setValue(new Integer(function.gridDivsV-1));
        spinPenWidth.setValue(new Integer(function.curveWidth));
        chkAbsoluteWidth.setSelected(function.absoluteWidth);
        chkFillSurface.setSelected(function.fillSurface);
        curveColor.setColor(function.curveColor);
        surfaceColor.setColor(function.surfaceColor);
        sldAlpha.setValue(255-function.surfaceColor.getAlpha());
        textFuncDef.setText(function.expression);
        updateLblArea();
    }
    // wrapper to setFunctionFields to operate on lastFunction
    private void setFunctionFields() {
        setFunctionFields(lastFunction);
    }
    // puts values from the controls into functions
    private void setFunctionFields(ModelView.ModelFunction function) {
        int n;
        if (function==null) return;
        boolean needsParse=false,needsRepaint=false;
        if (tglIsCurve.isSelected()!=function.isCurve) {
            function.isCurve=tglIsCurve.isSelected();
            needsParse=true;
        }
        if (function.visible!=chkVisible.isSelected()) {
            function.visible=chkVisible.isSelected();
            needsRepaint=true; }
        n=((Integer)spinUSteps.getValue()).intValue()+1;
        if (function.gridDivsU!=n) {function.gridDivsU=n; needsParse=true;}
        n=((Integer)spinVSteps.getValue()).intValue()+1;
        if (function.gridDivsV!=n) {function.gridDivsV=n; needsParse=true;}
        n=((Integer)spinPenWidth.getValue()).intValue();
        if (function.curveWidth!=n) {function.curveWidth=n; needsRepaint=true;}
        if (function.absoluteWidth!=chkAbsoluteWidth.isSelected()) {
            function.absoluteWidth=chkAbsoluteWidth.isSelected();
            needsRepaint=true; }
        if (function.fillSurface!=chkFillSurface.isSelected()) {
            function.fillSurface=chkFillSurface.isSelected();
            needsRepaint=true; }
        if (!function.curveColor.equals(curveColor.getColor())) {
            function.curveColor=curveColor.getColor();
            needsRepaint=true; }
        if (!function.surfaceColor.equals(surfaceColor.getColor())) {
            function.surfaceColor=surfaceColor.getColor();
            needsRepaint=true; }
        if (!function.expression.equals(textFuncDef.getText())) {
            function.expression=textFuncDef.getText();
            needsParse=true;
        }
        if (needsParse) {function.parseFunction(); updateLblArea(); needsRepaint=true;}
        if (needsRepaint) {modelView.repaint(); dirty=true;}
    }

    private JTextField jtfTitle;
    private JColor bgColor;
    private JCheckBox chkFog;
    private JSpinner spinFogStart,spinFogEnd;
    private JSpinner spinBoxWidth,spinBoxHeight;
    private JSpinner spinFOV;
    // creates the Global Options
    private Container createGlobalControls() {
        final JPanel pane=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(0,5,3,5);
        c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL;
        c.gridwidth=GridBagConstraints.REMAINDER; c.gridy=0;
        pane.add(Utils.labeledComponent("Title to display:",jtfTitle=new JTextField(),true),c);
        JPanel bg=new JPanel(new FlowLayout());
        bg.add(Utils.labeledComponent("Background color:",bgColor=new JColor(50,20),true),c);
        bg.add(chkFog=new JCheckBox("Fog"),c);
        c.gridy=1; c.gridwidth=GridBagConstraints.REMAINDER;
        pane.add(bg,c);
        c.gridy=2; c.gridwidth=1;
        pane.add(Utils.labeledComponent("Fog start:",spinFogStart=new JSpinner(new SpinnerNumberModel(0.0,0.0,1000.0,1.0)),true),c);
        pane.add(Utils.labeledComponent("Fog end:",spinFogEnd=new JSpinner(new SpinnerNumberModel(0.0,0.0,1000.0,1.0)),true),c);
        c.gridy=3;
        pane.add(Utils.labeledComponent("Box width:",spinBoxWidth=new JSpinner(new SpinnerNumberModel(10,0,2000,1)),true),c);
        pane.add(Utils.labeledComponent("Box height:",spinBoxHeight=new JSpinner(new SpinnerNumberModel(10,0,2000,1)),true),c);
        spinBoxWidth.setEnabled(Utils.applet==null); spinBoxHeight.setEnabled(Utils.applet==null);
        modelView.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                spinBoxWidth.setValue(new Integer(modelView.getWidth()));
                spinBoxHeight.setValue(new Integer(modelView.getHeight()));
            }
        });
        c.gridy=4; c.gridwidth=GridBagConstraints.REMAINDER;
        bg=new JPanel(new FlowLayout());
        bg.add(new JPanel());
        bg.add(Utils.labeledComponent("Field of Vision:",spinFOV=new JSpinner(new SpinnerNumberModel(90.0,10.0,170.0,1.0)),true));
        // Manage the FOV graphic
        bg.add(new JPanel() {
            {
                spinFOV.addChangeListener(new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        repaint();
                    }
                });
            }
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0,0,getWidth(),getHeight());
                double fov=((Double)(spinFOV.getValue())).doubleValue()/2;
                g.translate(getWidth()/2,getHeight()-((fov>=60)?1:0));
                double fovrad=fov*Math.PI/180;
                double x=Math.sin(fovrad),y=Math.cos(fovrad);
                int r=20;
                int nx=(int)(r*x+.5),ny=(int)(r*y+.5);
                g.setColor(Color.LIGHT_GRAY);
                r=(int)(8*(1-fov/180));
                g.fillArc(-r,-r,2*r,2*r,(int)(90-fov),(int)(2*fov));
                g.setColor(Color.GRAY);
                g.drawArc(-r,-r,2*r,2*r,(int)(90-fov),(int)(2*fov));
                g.setColor(Color.BLACK);
                g.drawLine(-1,0,nx-1,-ny);
                g.drawLine(0,0,-nx,-ny);
            }
        });
        bg.add(new JPanel());
        pane.add(bg,c);
        pane.setBorder(BorderFactory.createTitledBorder("Global Settings"));
        return pane;
    }
    private Container createGlobalPane() {
        final JPanel pane=new JPanel(new GridBagLayout());
        pane.add(createGlobalControls());
        return pane;
    }
    public void getGlobalFields() {
        jtfTitle.setText(modelView.title);
        bgColor.setColor(modelView.bgColor);
        chkFog.setSelected(modelView.fogEnabled);
        spinFogStart.setValue(new Double(modelView.fogStart));
        spinFogEnd.setValue(new Double(modelView.fogEnd));
        spinFOV.setValue(new Double(modelView.getFov()));
    }
    private void setGlobalFields() {
        boolean needsRepaint=false;
        if (!modelView.title.equals(jtfTitle.getText())) {modelView.title=jtfTitle.getText(); needsRepaint=true;}
        if (!modelView.bgColor.equals(bgColor.getColor()))
        {modelView.bgColor=bgColor.getColor(); needsRepaint=true;}
        if (modelView.fogEnabled!=chkFog.isSelected())
        {modelView.fogEnabled=chkFog.isSelected(); needsRepaint=true;}
        double n=((Double)spinFogStart.getValue()).doubleValue();
        if (modelView.fogStart!=n) {modelView.fogStart=n; needsRepaint=true;}
        n=((Double)spinFogEnd.getValue()).doubleValue();
        if (modelView.fogEnd!=n) {modelView.fogEnd=n; needsRepaint=true;}
        n=((Double)spinFOV.getValue()).doubleValue();
        if (modelView.getFov()!=n) {modelView.setFov(n); needsRepaint=true;}
        Dimension s=modelView.getSize();
        int width=((Integer)spinBoxWidth.getValue()).intValue();
        int height=((Integer)spinBoxHeight.getValue()).intValue();
        if (s.height!=height || s.width!=width) {
            Dimension p=parentFrame.getSize();
            parentFrame.setSize(p.width-s.width+width,p.height-s.height+height);
            parentFrame.validate();
        }
        if (needsRepaint) {modelView.repaint(); dirty=true;}
    }

    private JCheckBox[] axisShown=new JCheckBox[3];
    private JSpinner[] spnAxesMin=new JSpinner[3];
    private JSpinner[] spnAxesMax=new JSpinner[3];
    private JColor axesColor;
    private JSpinner spnAxesIncr,spnAxesTicks,spnAxesWidth;
    private Container createAxisExtents() {
        Box pane=new Box(BoxLayout.Y_AXIS);
        final String[] axisNames={"X","Z","Y"};
        for (int i=0; i<3; i++) {
            int index=(i==0?0:3-i); // in the order 0,2,1
            Box horiz=new Box(BoxLayout.X_AXIS);
            horiz.add(new JLabel(axisNames[index]));
            horiz.add(axisShown[index]=new JCheckBox());
            horiz.add(Box.createHorizontalStrut(5));
            horiz.add(Utils.labeledComponent("Min:",spnAxesMin[index]=new JSpinner(new SpinnerNumberModel(0.0,-5000.0,5000.0,0.1)),true));
            horiz.add(Box.createHorizontalStrut(5));
            horiz.add(Utils.labeledComponent("Max:",spnAxesMax[index]=new JSpinner(new SpinnerNumberModel(1.0,-5000.0,5000.0,0.1)),true));
            horiz.setAlignmentX(1.0f);
            pane.add(horiz);
        }
        pane.setBorder(BorderFactory.createEmptyBorder(2,0,2,0));
        return pane;
    }
    private Container createAxisControls() {
        final JPanel pane=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(0,5,3,5); c.gridwidth=1;
        c.gridy=0; c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL;
        pane.add(Utils.labeledComponent("Axis color:",axesColor=new JColor(50,20),true),c);
        pane.add(Utils.labeledComponent("Line width:",spnAxesWidth=new JSpinner(new SpinnerNumberModel(2,1,3,1)),true),c);
        c.gridy=1;
        pane.add(Utils.labeledComponent("Step density:",spnAxesIncr=new JSpinner(new SpinnerNumberModel(0.1,0.0,500.0,0.01)),true),c);
        pane.add(Utils.labeledComponent("Tick density:",spnAxesTicks=new JSpinner(new SpinnerNumberModel(0.1,0.0,5000.0,0.05)),true),c);
        c.gridy=2; c.gridwidth=GridBagConstraints.REMAINDER;
        pane.add(createAxisExtents(),c);
        pane.setBorder(BorderFactory.createTitledBorder("Axis Settings"));
        return pane;
    }
    private Container createAxisPane() {
        final JPanel pane=new JPanel(new GridBagLayout());
        pane.add(createAxisControls());
        return pane;
    }
    private void getAxisFields() {
        ModelView.AxesDefinition ad=modelView.axesDefinition;
        for (int i=0; i<3; i++) {
            axisShown[i].setSelected(ad.shown[i]);
            spnAxesMin[i].setValue(new Double(ad.min[i]));
            spnAxesMax[i].setValue(new Double(ad.max[i]));
        }
        axesColor.setColor(ad.color);
        spnAxesIncr.setValue(new Double(ad.incr));
        spnAxesTicks.setValue(new Double(ad.tickDensity));
        spnAxesWidth.setValue(new Integer(ad.width));
    }
    private void setAxisFields() {
        boolean needsRepaint=false;
        ModelView.AxesDefinition ad=modelView.axesDefinition;
        double x,y; int n;
        for (int i=0; i<3; i++) {
            x=((Double)spnAxesMin[i].getValue()).doubleValue();
            if (ad.min[i]!=x) {ad.min[i]=x; needsRepaint=true;}
            y=((Double)spnAxesMax[i].getValue()).doubleValue();
            if (ad.max[i]!=y) {ad.max[i]=y; needsRepaint=true;}
            if (y<=x) axisShown[i].setSelected(false);
            if (axisShown[i].isSelected()!=ad.shown[i]) {ad.shown[i]=axisShown[i].isSelected(); needsRepaint=true;}
        }
        if (!ad.color.equals(axesColor.getColor())) {ad.color=axesColor.getColor(); needsRepaint=true;}
        x=((Double)spnAxesIncr.getValue()).doubleValue();
        if (ad.incr!=x) {ad.incr=x; needsRepaint=true;}
        x=((Double)spnAxesTicks.getValue()).doubleValue();
        if (ad.tickDensity!=x) {ad.tickDensity=x; needsRepaint=true;}
        n=((Integer)spnAxesWidth.getValue()).intValue();
        if (ad.width!=n) {ad.width=n; needsRepaint=true;}
        if (needsRepaint && modelView.bShowAxes) {modelView.repaint(); dirty=true;}
    }

    // creates the Update, Close buttons
    private Container createDialogButtons() {
        JPanel pane=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.anchor=GridBagConstraints.LINE_END;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.weightx=5; pane.add(new JPanel(),c); c.weightx=0;
        c.insets=new Insets(0,2,0,2);
        JButton btnUpdate,btnClose;
        pane.add(btnUpdate=new JButton("Redraw"),c); btnUpdate.setMnemonic('R');
        pane.add(btnClose=new JButton("Close"),c); btnClose.setMnemonic('C');
        btnUpdate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jtpMain.getSelectedIndex()==0) setGlobalFields();
                else if (jtpMain.getSelectedIndex()==1) {setFunctionFields(); textFuncDef.requestFocus();}
                else setAxisFields();
            }
        });
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jtpMain.getSelectedIndex()==0) setGlobalFields();
                else if (jtpMain.getSelectedIndex()==1) setFunctionFields();
                else setAxisFields();
                dispose();
            }
        });
        getRootPane().setDefaultButton(btnClose);
        pane.setMaximumSize(new Dimension(Integer.MAX_VALUE,btnClose.getHeight()));
        return pane;
    }
    // called when a file is loaded
    public void reLoadAll() {
        lastFunction=null;
        comboFunction.removeAllItems();
        for (int nFunc=0; nFunc<functions.size(); nFunc++)
            comboFunction.addItem(functions.getFunction(nFunc));
        lastFunction=functions.getFunction(0);
        getFunctionFields(); getGlobalFields(); getAxisFields();
    }
    // constructor
    public OptionsDialog(Frame parentFrame,ModelView theModelView) {
        super("Functions Editor");
        this.parentFrame=parentFrame;
        modelView=theModelView; functions=theModelView.functions;
        jtpMain=new JTabbedPane();
        jtpMain.add("Global",createGlobalPane());
        jtpMain.add("Functions",createFunctionsPane());
        jtpMain.add("Axes",createAxisPane());
        jtpMain.setSelectedIndex(1);
        jtpMain.addChangeListener(new ChangeListener() {
            int nLastIndex=1;
            public void stateChanged(ChangeEvent e) {
                if (nLastIndex==0) setGlobalFields();
                else if (nLastIndex==1) setFunctionFields();
                else setAxisFields();
                nLastIndex=jtpMain.getSelectedIndex();
            }
        });
        Container pane=new Box(BoxLayout.Y_AXIS);
        pane.add(jtpMain);
        pane.add(Box.createVerticalStrut(3));
        pane.add(createDialogButtons());
        getContentPane().add(pane);
        setSize(500,300); setLocationRelativeTo(null);
        reLoadAll();
    }
}
