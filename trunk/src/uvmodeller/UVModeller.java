package uvmodeller;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

class MainComponent extends JPanel {
    public ModelView modelView=new ModelView();
    private HelpDialog helpDialog;
    private Frame parentFrame;
    private OptionsDialog optionsDialog;
    private int saveSignature=(0x5556<<16);
    private String lastFileName=null;
    // handles commands passed as strings
    public void handleCommand(String cmd) {
        if (cmd.equals("FileNew")) {
            if (!isDirty("Do you want to save before creating a new file?")) newFile();
        }
        else if (cmd.equals("FileOpen")) loadFromFile();
        else if (cmd.equals("FileSave")) saveToFile(false);
        else if (cmd.equals("FileSaveAs")) saveToFile(true);
        else if (cmd.equals("ExportPNG")) exportPNG();
        else if (cmd.equals("TogCulling")) {
            modelView.backCulling=1-modelView.backCulling;
            btnSolid.setSelected(modelView.backCulling!=0);
            modelView.repaint();
        }
        else if (cmd.equals("TogAxes")) {
            modelView.bShowAxes=!modelView.bShowAxes;
            btnAxes.setSelected(modelView.bShowAxes);
            modelView.repaint();
        }
        else if (cmd.equals("EditScene")) optionsDialog.show();
        else if (cmd.equals("HelpReadme")) helpDialog.show();
        else JOptionPane.showMessageDialog(this,"Invalid command passed to handler: "+cmd,"Error",
            JOptionPane.ERROR_MESSAGE);
    }
    // returns true if dirty and if necessary shows a dialog box
    public boolean isDirty(String saveMessage) {
        if (Utils.applet!=null || !optionsDialog.isDirty()) return false;
        int result=JOptionPane.showConfirmDialog(this,saveMessage,"File Modified",
            JOptionPane.YES_NO_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
        switch (result) {
            case JOptionPane.YES_OPTION:
                if (saveToFile(false)) return false;
                break;
            case JOptionPane.NO_OPTION:
                return false;
        }
        return true;
    }
    // erases everything
    private void newFile() {
        modelView.reInitializeVars();
        String[] formulae={"ir=.3+.1*sin(4*Pi*u)\nr=ir*sin(2*Pi*v)+.5\n"
                +"x=r*sin(2*Pi*u)\ny=r*cos(2*Pi*u)\nz=1.5*ir*cos(Pi*v)",
            "u=-2+4u; v=-2+4v\n\nx=u-(u*u*u/3)+u*v*v\ny=v-(v*v*v/3)+u*u*v\n"
                +"z=u*u-v*v\n\nn=10; x=x/n; y=y/n; z=z/n",
            "ang=atan2(y,x)\nr2=x*x+y*y\nz=sin(5(ang-r2/3))*r2/3"};
        ModelView.ModelFunction func=modelView.functions.addFunction();
        func.expression=formulae[(int)(Math.random()*formulae.length)];
        func.gridDivsU=31; func.gridDivsV=31;
        func.surfaceColor=Color.MAGENTA;
        func.parseFunction();
        if (optionsDialog!=null) {
            optionsDialog.reLoadAll(); optionsDialog.setDirty(false);
        }
        setLastFileName(null);
        modelView.repaint();
        btnSolid.setSelected(modelView.backCulling!=0);
        btnAxes.setSelected(modelView.bShowAxes);
    }
    // Just in case I decide to put filename on title bar
    private void setLastFileName(String string) {
        final String title="3D Graph Explorer";
        lastFileName=string;
        if (parentFrame!=null) {
            if (string==null) parentFrame.setTitle(title+" v1.00a");
            else parentFrame.setTitle(title+" - "+string.substring(string.lastIndexOf(File.separatorChar)+1));
        }
    }
    // Sets lastFileName, ext (like ".uvm") is ignored for save=false
    private String lastDirectory=null;
    private String getFileName(boolean save,String ext) {
        final JFileChooser fileChooser=new JFileChooser();
        fileChooser.setCurrentDirectory(new File(lastDirectory==null?System.getProperty("user.dir"):lastDirectory));
        int result;
        if (save) result=fileChooser.showSaveDialog(MainComponent.this);
        else result=fileChooser.showOpenDialog(MainComponent.this);
        if (result==JFileChooser.APPROVE_OPTION) {
            String fileName=fileChooser.getSelectedFile().getPath();
            lastDirectory=fileName;
            // it also works if the next line is commented out!
            lastDirectory=lastDirectory.substring(0,lastDirectory.lastIndexOf(File.separatorChar));
            if (save && fileName.indexOf('.')==-1) fileName+=ext;
            if (save && (new File(fileName)).exists())
                if (JOptionPane.showConfirmDialog(this,"File "+fileName+" already exists. Overwrite?","Warning",
                    JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.NO_OPTION)
                    return null;
            return fileName;
        }
        else return null;
    }
    // Shows load dialog and loads
    private void loadFromFile() {
        loadFromFile(getFileName(false,null));
    }
    public void loadFromFile(String fileName) {
        if (fileName==null) return;
        try {
            DataInputStream s=new DataInputStream(new FileInputStream(fileName));
            int saveVersion=s.readInt()-saveSignature;
            if (saveVersion!=1) {
                JOptionPane.showMessageDialog(this,"File "+fileName+" doesn't conform save format.","Error",JOptionPane.WARNING_MESSAGE);
                return;
            }
            modelView.readFromStream(s);
            btnSolid.setSelected(modelView.backCulling!=0);
            btnAxes.setSelected(modelView.bShowAxes);
            optionsDialog.reLoadAll(); optionsDialog.setDirty(false);
            modelView.repaint();
            setLastFileName(fileName);
        } catch (IOException e) {
            // Show an error message not warning, since partially loaded data could be invalid
            JOptionPane.showMessageDialog(this,"Error while loading from file "+fileName,"Error",JOptionPane.ERROR_MESSAGE);
        }
    }
    // Shows save dialog optionally, and saves, returns true if succesful
    private boolean saveToFile(boolean askName) {
        String fileName;
        if (askName || lastFileName==null) fileName=getFileName(true,".uvm");
        else fileName=lastFileName;
        if (fileName==null) return false;
        try {
            DataOutputStream s=new DataOutputStream(new FileOutputStream(fileName));
            s.writeInt(saveSignature+1);
            modelView.writeToStream(s);
            setLastFileName(fileName);
            optionsDialog.setDirty(false);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,"Could not save to file "+fileName,"Warning",JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }
    private void exportPNG() {
        String fileName=getFileName(true,".png");;
        if (fileName==null) return;
        try {
            modelView.saveImage(fileName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,"Could not export to file "+fileName,"Warning",JOptionPane.WARNING_MESSAGE);
        }
    }
    private JButton btnNew,btnLoad,btnSave,btnSaveAs,btnExportPng,btnEdit;
    private JToggleButton btnSolid,btnAxes;
    public MainComponent(Frame frame) {
        parentFrame=frame;
        if (Utils.applet==null)
            helpDialog=new HelpDialog("3D Graph Explorer Readme","readme/readme.html");
        /* the common actionListener */
        ActionListener actionListener=new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                handleCommand(((AbstractButton)e.getSource()).getActionCommand());
            }
        };
        /* creating the toolbar */
        JToolBar toolBar=new JToolBar();
        setLayout(new BorderLayout());
        toolBar.add(btnNew=new JButton(Utils.loadIcon("filenew.gif"))); Utils.makeHot(btnNew);
        btnNew.setToolTipText("New File");
        btnNew.setActionCommand("load"); btnNew.setActionCommand("FileNew");
        btnNew.addActionListener(actionListener);
        if (Utils.applet==null) {
            toolBar.add(btnLoad=new JButton(Utils.loadIcon("fileopen.gif"))); Utils.makeHot(btnLoad);
            btnLoad.setToolTipText("Open File");
            btnLoad.setActionCommand("FileOpen"); btnLoad.addActionListener(actionListener);
            toolBar.add(btnSave=new JButton(Utils.loadIcon("filesave.gif"))); Utils.makeHot(btnSave);
            btnSave.setToolTipText("Save File");
            btnSave.setActionCommand("FileSave"); btnSave.addActionListener(actionListener);
            toolBar.add(btnSaveAs=new JButton(Utils.loadIcon("filesaveas.gif"))); Utils.makeHot(btnSaveAs);
            btnSaveAs.setToolTipText("Save File As...");
            btnSaveAs.setActionCommand("FileSaveAs"); btnSaveAs.addActionListener(actionListener);
            toolBar.add(btnExportPng=new JButton(Utils.loadIcon("fileexportpng.gif"))); Utils.makeHot(btnExportPng);
            btnExportPng.setToolTipText("Export Picture");
            btnExportPng.setActionCommand("ExportPNG"); btnExportPng.addActionListener(actionListener);
        }
        toolBar.addSeparator();
        toolBar.add(btnSolid=new JToggleButton(Utils.loadIcon("hollow.gif"))); Utils.makeHot(btnSolid);
        btnSolid.setToolTipText("Toggle Wireframe/Solid");
        btnSolid.setSelectedIcon(Utils.loadIcon("solid.gif"));
        btnSolid.setActionCommand("TogCulling"); btnSolid.addActionListener(actionListener);
        toolBar.add(btnAxes=new JToggleButton(Utils.loadIcon("axes.gif"))); Utils.makeHot(btnAxes);
        btnAxes.setToolTipText("Toggle Axes");
        btnAxes.setActionCommand("TogAxes"); btnAxes.addActionListener(actionListener);
        toolBar.addSeparator();
        toolBar.add(btnEdit=new JButton(Utils.loadIcon("edit.gif"))); Utils.makeHot(btnEdit);
        btnEdit.setToolTipText("Edit Scene");
        btnEdit.setActionCommand("EditScene"); btnEdit.addActionListener(actionListener);

        /* other things */
        newFile();
        Border border=null;
        if (Utils.applet==null) border=BorderFactory.createBevelBorder(BevelBorder.LOWERED);
        else border=BorderFactory.createLineBorder(Color.BLACK);
        modelView.setBorder(border);
        optionsDialog=new OptionsDialog(parentFrame,modelView);

        /* adding things to this JPanel */
        add(modelView,BorderLayout.CENTER);
        add(toolBar,BorderLayout.NORTH);
    }
}

public class UVModeller extends JApplet {
    private MainComponent mainComponent;
    public void init() {
        Utils.applet=this;
        getContentPane().add(mainComponent=new MainComponent(null));
    }
    private JMenuBar createMenu() {
        ActionListener actionListener=new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String cmd=((AbstractButton)e.getSource()).getActionCommand();
                mainComponent.handleCommand(cmd);
            }
        };
        final JMenuBar menuBar=new JMenuBar();
        final JMenu menuFile=new JMenu("File"); menuFile.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menuFile);
        final JMenuItem fileNew=new JMenuItem("New",KeyEvent.VK_N);
        fileNew.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,ActionEvent.CTRL_MASK));
        fileNew.setActionCommand("FileNew"); fileNew.addActionListener(actionListener);
        menuFile.add(fileNew);
        final JMenuItem fileOpen=new JMenuItem("Open...",KeyEvent.VK_O);
        fileOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,ActionEvent.CTRL_MASK));
        fileOpen.setActionCommand("FileOpen"); fileOpen.addActionListener(actionListener);
        menuFile.add(fileOpen);
        final JMenuItem fileSave=new JMenuItem("Save",KeyEvent.VK_S);
        fileSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,ActionEvent.CTRL_MASK));
        fileSave.setActionCommand("FileSave"); fileSave.addActionListener(actionListener);
        menuFile.add(fileSave);
        final JMenuItem fileSaveAs=new JMenuItem("Save As...",KeyEvent.VK_A);
        fileSaveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A,ActionEvent.CTRL_MASK));
        fileSaveAs.setActionCommand("FileSaveAs"); fileSaveAs.addActionListener(actionListener);
        menuFile.add(fileSaveAs);
        menuFile.addSeparator();
        final JMenuItem fileExportPNG=new JMenuItem("Export to PNG...",KeyEvent.VK_E);
        fileExportPNG.setActionCommand("ExportPNG"); fileExportPNG.addActionListener(actionListener);
        menuFile.add(fileExportPNG);
        menuFile.addSeparator();
        final JMenuItem fileExit=new JMenuItem("Exit",KeyEvent.VK_X);
        fileExit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        //fileExit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4,ActionEvent.ALT_MASK));
        menuFile.add(fileExit);
        final JMenu menuScene=new JMenu("Scene"); menuScene.setMnemonic(KeyEvent.VK_S);
        menuBar.add(menuScene);
        final JMenuItem sceneSolid=new JCheckBoxMenuItem("Toggle Solid"); sceneSolid.setMnemonic(KeyEvent.VK_S);
        sceneSolid.setActionCommand("TogCulling"); sceneSolid.addActionListener(actionListener);
        menuScene.add(sceneSolid);
        final JMenuItem sceneAxes=new JCheckBoxMenuItem("Toggle Axes"); sceneAxes.setMnemonic(KeyEvent.VK_A);
        sceneAxes.setActionCommand("TogAxes"); sceneAxes.addActionListener(actionListener);
        menuScene.add(sceneAxes);
        menuScene.addSeparator();
        final JMenuItem sceneEdit=new JMenuItem("Edit Scene...",KeyEvent.VK_E);
        sceneEdit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,ActionEvent.CTRL_MASK));
        sceneEdit.setActionCommand("EditScene"); sceneEdit.addActionListener(actionListener);
        menuScene.add(sceneEdit);
        menuScene.addMenuListener(new MenuListener() {
            public void menuDeselected(MenuEvent e) { }
            public void menuCanceled(MenuEvent e) { }
            public void menuSelected(MenuEvent e) {
                sceneSolid.setSelected(mainComponent.modelView.backCulling!=0);
                sceneAxes.setSelected(mainComponent.modelView.bShowAxes);
            }
        });
        final JMenu menuHelp=new JMenu("Help"); menuHelp.setMnemonic(KeyEvent.VK_H);
        menuBar.add(menuHelp);
        final JMenuItem helpReadme=new JMenuItem("Readme...",KeyEvent.VK_R);
        helpReadme.setActionCommand("HelpReadme"); helpReadme.addActionListener(actionListener);
        menuHelp.add(helpReadme);
        return menuBar;
    }
    private void close() {
        if (!mainComponent.isDirty("Do you want to save before exit?")) System.exit(0);
    }
    private void runApplication(String fileName) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) { }
        final JFrame frame=new JFrame();
        mainComponent=new MainComponent(frame);
        frame.getContentPane().add(mainComponent);
        frame.setSize(640,480);
        frame.setLocationRelativeTo(null);
        frame.setIconImage(Utils.loadIcon("uvmodeller.gif").getImage());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        frame.setJMenuBar(createMenu());
        mainComponent.loadFromFile(fileName);
        frame.setVisible(true);
    }
    public static void main(String[] args) {
        new UVModeller().runApplication(args.length>0?args[0]:null);
    }
}
