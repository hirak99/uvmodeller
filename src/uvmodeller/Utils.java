package uvmodeller;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

class HelpDialog extends JFrame {
    private URL contents;
    private JEditorPane ep;
    private ArrayList urlHistory=new ArrayList();
    private int historyIndex=-1;
    private JButton btnContents,btnBack,btnForward;
    private void enableButtons() {
        btnBack.setEnabled(historyIndex>0);
        btnForward.setEnabled(historyIndex<urlHistory.size()-1);
    }
    private void setURL(URL url) {
        try {
            ep.setPage(url);
            if (historyIndex<0 || !url.equals((URL)urlHistory.get(historyIndex))) {
                urlHistory.add(url);
                historyIndex++;
                while(urlHistory.size()>historyIndex+1)
                   urlHistory.remove(historyIndex);
                enableButtons();
            }
        } catch(IOException t) { }
    }
    private void goBack() {
        if (historyIndex>0) setURL((URL)urlHistory.get(--historyIndex));
        enableButtons();
    }
    private void goForward() {
        if (historyIndex<urlHistory.size()-1) setURL((URL)urlHistory.get(++historyIndex));
        enableButtons();
    }
    public HelpDialog(String title,String htmlFile) {
        super(title);
        Container contentPane=getContentPane();
        ep=new JEditorPane();
        ep.setEditable(false);
        ep.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    setURL(e.getURL());
                }
            }
        });
        ActionListener buttonsListener=new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String cmd=e.getActionCommand();
                if (cmd.equals("Contents")) setURL(contents);
                else if (cmd.equals("Back")) goBack();
                else if (cmd.equals("Forward")) goForward();
            }
        };
        final JToolBar toolBar=new JToolBar();
        btnContents=new JButton(Utils.loadIcon("home.gif")); Utils.makeHot(btnContents);
        toolBar.add(btnContents); btnContents.addActionListener(buttonsListener);
        btnContents.setActionCommand("Contents");
        btnBack=new JButton(Utils.loadIcon("back.gif")); Utils.makeHot(btnBack);
        toolBar.add(btnBack); btnBack.addActionListener(buttonsListener);
        btnBack.setActionCommand("Back");
        btnForward=new JButton(Utils.loadIcon("forward.gif")); Utils.makeHot(btnForward);
        toolBar.add(btnForward); btnForward.addActionListener(buttonsListener);
        btnForward.setActionCommand("Forward");
        contentPane.add(toolBar,BorderLayout.NORTH);
        contentPane.add(new JScrollPane(ep));
        setSize(480,560);
        setLocationRelativeTo(null);
        setURL(contents=Utils.getURL(htmlFile));
    }
}

class Utils {
    // should set this if it is an applet
    static public Applet applet=null;
    // gets URL from a local file
    static URL getURL(String file) {
        if (applet==null) return Utils.class.getResource(file);
        else return applet.getClass().getResource(file);
    }
    // loads an icon
    static ImageIcon loadIcon(String file) {
        return new ImageIcon(getURL("images/"+file));
    }
    // adds alpha to a color
    static public Color changeAlpha(Color c,int alpha) {
        return new Color(c.getRed(),c.getGreen(),c.getBlue(),alpha);
    }
    // blends two colors... this is NOT symmetric
    static public Color blendColors(Color c1,Color c2,double amt) {
        if (c1==null || c2==null) return null;
        int red=(int)(c2.getRed()*amt+c1.getRed()*(1-amt));
        int green=(int)(c2.getGreen()*amt+c1.getGreen()*(1-amt));
        int blue=(int)(c2.getBlue()*amt+c1.getBlue()*(1-amt));
        int alpha=c1.getAlpha();    // keep first color's alpha
        return new Color(red,green,blue,alpha);
    }
    // creates a Container with a label and the component
    static public Container labeledComponent(String label,JComponent component,boolean horiz) {
        Box pane=new Box(horiz?BoxLayout.X_AXIS:BoxLayout.Y_AXIS);
        pane.add(new JLabel(label));
        component.setAlignmentX(0);
        pane.add(component);
        return pane;
    }
    // makes the component aware of mouse moving over it
    static public void makeHot(final JComponent comp) {
        class Border3D implements Border {
            private boolean m_raised;
            public Border3D(boolean raised) {m_raised=raised;}
            public boolean isBorderOpaque() {return false;}
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                g.setColor(Color.WHITE);
                g.draw3DRect(x,y,width-1,height-1,m_raised);
            }
            public Insets getBorderInsets(Component c) {return new Insets(1,2,1,2);}
        }
        final Border borderE=BorderFactory.createEmptyBorder(1,2,1,2);
        final Border borderR=new Border3D(true);
        final Border borderL=new Border3D(false);
        comp.setBorder(borderE);
        comp.addMouseListener(new MouseAdapter() {
            private boolean isEnabled() {
                if (comp instanceof JButton) return ((JButton)comp).isEnabled();
                else return true;
            }
            public void mouseEntered(MouseEvent e) {
                if (isEnabled())
                    comp.setBorder((e.getModifiersEx()&InputEvent.BUTTON1_DOWN_MASK)==0 ? borderR:borderL);
            }
            public void mouseExited(MouseEvent e) {comp.setBorder(borderE);}
            public void mouseReleased(MouseEvent e) {
                if (!isEnabled()) comp.setBorder(borderE);  // pressing the button might have disabled it
                else if (e.getButton()==MouseEvent.BUTTON1)
                    if(!comp.getBorder().equals(borderE)) comp.setBorder(borderR);
            }
            public void mousePressed(MouseEvent e) {
                if (isEnabled() && e.getButton()==MouseEvent.BUTTON1)
                    comp.setBorder(borderL);
            }
        } );
    }
    // Writes Color to DataOutputStream
    static public void writeColor(DataOutputStream s,Color c) throws IOException {
        s.writeInt(c.getRed()); s.writeInt(c.getGreen());
        s.writeInt(c.getBlue()); s.writeInt(c.getAlpha());
    }
    // Reads a Color to DataOutputStream
    static public Color readColor(DataInputStream s) throws IOException {
        return new Color(s.readInt(),s.readInt(),s.readInt(),s.readInt());
    }
}

