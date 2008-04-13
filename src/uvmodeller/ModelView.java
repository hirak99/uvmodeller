package uvmodeller;

import MathParser.MathParser;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

interface Printable {
    public void writeToStream(DataOutputStream s) throws IOException ;
    public void readFromStream(DataInputStream s) throws IOException ;
}

/** An extension of JPanel, this is the main class. */
public class ModelView extends JPanel implements Printable {
    public void writeToStream(DataOutputStream s) throws IOException {
        s.writeUTF(title);
        eyePosition.writeToStream(s); eyeDirection.writeToStream(s);
        up.writeToStream(s); screenUp.writeToStream(s); screenRight.writeToStream(s);
        s.writeDouble(getFov()); s.writeInt(backCulling);
        Utils.writeColor(s,bgColor);
        s.writeBoolean(fogEnabled); s.writeDouble(fogStart); s.writeDouble(fogEnd);
        functions.writeToStream(s); s.writeBoolean(bShowAxes);
        axesDefinition.writeToStream(s);
    }
    public void readFromStream(DataInputStream s) throws IOException {
        title=s.readUTF();
        eyePosition.readFromStream(s); eyeDirection.readFromStream(s);
        up.readFromStream(s); screenUp.readFromStream(s); screenRight.readFromStream(s);
        setFov(s.readDouble()); backCulling=s.readInt();
        bgColor=Utils.readColor(s);
        fogEnabled=s.readBoolean(); fogStart=s.readDouble(); fogEnd=s.readDouble();
        functions.readFromStream(s); bShowAxes=s.readBoolean();
        axesDefinition.readFromStream(s);
    }
    public void reInitializeVars() {
        title="";
        eyePosition=new Vec(0,1,-1.5);
        eyeDirection=new Vec(0,-1,1.5).normalize();
        up=new Vec(0,1,0); recalcUpRight();
        fov=90; recalcScale();
        backCulling=0;
        bgColor=new Color(254,254,228);
        fogEnabled=true;
        fogStart=0; fogEnd=5;
        functions.clear();
        bShowAxes=false;
    }
    // Title of the graph
    String title="";
    // Location of the camera
    private Vec eyePosition=new Vec(0,1,-1.5);
    // Direction of the camera
    private Vec eyeDirection=new Vec(0,-1,1.5).normalize();
    // The up vector
    private Vec up=new Vec(0,1,0);
    // The up and right vectors for the screen, calculated from up vector
    private Vec screenUp,screenRight;
    // The distance of screen. Other than computations, this has no use.
    final private double screenDistance=1;
    // Field Of Vision
    private double fov=90;
    public double getFov() {return fov;}
    public void setFov(double fov) {this.fov=fov; recalcScale();}
    // Should not be set. Automatic onResize depending on screenDistance and fov
    private double scale;
    private void recalcScale() {
        Dimension s=getSize();
        //double windowSize=s.width>s.height?s.width:s.height;
        double windowSize=Math.sqrt(s.height*s.height+s.width*s.width);
        scale=windowSize/(2*Math.tan((fov*Math.PI/180)/2)*screenDistance);
    }
    // 0-Wireframe, 1-Solid, 2-Transparent (removed implementation). Mutator: setCulling()
    public int backCulling=0;
    /** Background color. */
    public Color bgColor=new Color(254,254,228);
    /** Enables fog. */
    public boolean fogEnabled=true;
    /** Fog parameters. */
    public double fogStart=0,fogEnd=5;
    // Whether to draw the axes
    public boolean bShowAxes=false;
    // The parser class created by JavaCC
    private MathParser mathParser=new MathParser();
    // Need to smooth Keyboard Interaction
    private Timer timer;
    // The class (struct?) that stores the keyboard constants
    private static class KeyBoard {
        // The target velocities
        private double targetForward=0;
        private Point2D.Double targetTranslate=new Point2D.Double(0,0);
        private Point2D.Double targetRotate=new Point2D.Double(0,0);
        private double targetBank=0;
        private Point2D.Double targetPivotRotate=new Point2D.Double(0,0);
        // The current velocities
        private double velForward=0;
        private Point2D.Double velTranslate=new Point2D.Double(0,0);
        private Point2D.Double velRotate=new Point2D.Double(0,0);
        private double velBank=0;
        private Point2D.Double velPivotRotate=new Point2D.Double(0,0);
    };
    /** Instanciation of the KeyBoard class... only one needed */
    private KeyBoard keyBoard=new KeyBoard();
    /** Class for encapsulationg functions */
    public class ModelFunction implements Printable {
        public void writeToStream(DataOutputStream s) throws IOException {
            s.writeBoolean(visible); s.writeUTF(name); s.writeUTF(expression);
            s.writeBoolean(isCurve); s.writeInt(curveWidth); s.writeBoolean(absoluteWidth);
            s.writeInt(gridDivsU); s.writeInt(gridDivsV);
            s.writeBoolean(fillSurface);
            Utils.writeColor(s,curveColor); Utils.writeColor(s,surfaceColor);
        }
        public void readFromStream(DataInputStream s) throws IOException {
            visible=s.readBoolean(); name=s.readUTF(); expression=s.readUTF();
            isCurve=s.readBoolean(); curveWidth=s.readInt(); absoluteWidth=s.readBoolean();
            gridDivsU=s.readInt(); gridDivsV=s.readInt();
            fillSurface=s.readBoolean();
            curveColor=Utils.readColor(s); surfaceColor=Utils.readColor(s);
            parseFunction();
        }
        /** Makes it visible/invisible */
        public boolean visible=true;
        /** Name of the function */
        public String name;
        /** The math expression */
        public String expression;
        /** Curve settings */
        boolean isCurve=false;
        int curveWidth=2;
        boolean absoluteWidth=false;
        /** Grid settings */
        public int gridDivsU=11,gridDivsV=11;
        public boolean fillSurface=true;
        public Color curveColor;
        public Color surfaceColor;
        /** To let this to be added to JComboBox as an Object */
        public String toString() {return name;}
        /** Area or length of the curve, calculated in parseFunction. */
        private double area; public double getArea() {return area;}
        /** Public method to parse the function into coords[][] */
        public void parseFunction() {
            coords=new Vec[gridDivsU][isCurve?1:gridDivsV];
            area=0; mathParser.resetVariables();
            mathParser.setVariable("uSteps",gridDivsU-1); mathParser.setVariable("tSteps",gridDivsU-1);
            mathParser.setVariable("vSteps",isCurve?0:gridDivsV-1);
            for (int i=0; i<gridDivsU; i++) {
                double u=(float)i/(gridDivsU-1);
                for (int j=0; j<(isCurve?1:gridDivsV); j++) {
                    double v=(float)j/(gridDivsV-1);
                    mathParser.setVariable("u",u); mathParser.setVariable("t",u);
                    mathParser.setVariable("v",v);
                    mathParser.setVariable("x",isCurve?0:2*u-1);
                    mathParser.setVariable("y",isCurve?0:2*v-1);
                    mathParser.setVariable("z",0);
                    try {
                        mathParser.parseExpression(expression);
                        coords[i][j]=new Vec(
                            mathParser.getVariable("x"),
                            mathParser.getVariable("z"),    // change z,y to display 'mathematically'
                            mathParser.getVariable("y"));
                        if (isCurve) {if (i>0) area+=coords[i][0].substract(coords[i-1][0]).norm();}
                        else {
                            if (i>0 && j>0) area+=
                                coords[i][j-1].substract(coords[i-1][j-1]).
                                crossProduct(coords[i-1][j].substract(coords[i-1][j-1])).norm()/2 +
                                coords[i][j-1].substract(coords[i][j]).
                                crossProduct(coords[i-1][j].substract(coords[i][j])).norm()/2;
                        }
                    } catch (Exception e) {
                        coords[i][j]=new Vec(Double.NaN,Double.NaN,Double.NaN);
                        //e.printStackTrace();
                    }
                }
            }
        }
        /** The coordinates describing the grid to be drawn. */    
        private Vec coords[][];
    };
    /** Class for storing the list of functions. */
    public class FunctionsList implements Printable {
        private java.util.ArrayList functions=new java.util.ArrayList();
        public void writeToStream(DataOutputStream s) throws IOException {
            s.writeInt(size());
            for(int i=0; i<size(); i++) getFunction(i).writeToStream(s);
        }
        public void readFromStream(DataInputStream s) throws IOException {
            int n=s.readInt();
            functions.clear();
            for (int i=0; i<n; i++) {
                ModelFunction func=addFunction();
                func.readFromStream(s);
            }
        }
        public ModelFunction addFunction() {
            ModelFunction func=new ModelFunction();
            String name;
            for (int num=1; ;num++) {
                int i;
                name="Function "+num;
                for (i=0; i<functions.size(); i++)
                    if (((ModelFunction)functions.get(i)).name.equals(name)) break;
                if (i==functions.size()) break;
            }
            func.name=name;
            func.expression="z=0";
            func.curveColor=Color.BLACK;
            func.surfaceColor=new Color(
                (float)Math.random(),
                (float)Math.random(),
                (float)Math.random());
            functions.add(func);
            return func;
        }
        public void removeFunction(int index) {
            functions.remove(index);
        }
        public void clear() {functions.clear();}
        public ModelFunction getFunction(int index) {
            return (ModelFunction)functions.get(index);
        }
        public int size() {
            return functions.size();
        }
    }
    /** Instanciation of FunctionsList ... only one needed. */
    public FunctionsList functions=new FunctionsList();

    private class OnKey extends KeyAdapter {
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_A:
                case KeyEvent.VK_Z:
                    keyBoard.targetForward=0; break;
                case KeyEvent.VK_DOWN:
                case KeyEvent.VK_UP:
                    keyBoard.targetTranslate.y=0;
                    keyBoard.targetPivotRotate.y=0;
                    keyBoard.targetRotate.y=0;
                    break;
                case KeyEvent.VK_LEFT:
                case KeyEvent.VK_RIGHT:
                    keyBoard.targetTranslate.x=0;
                    keyBoard.targetPivotRotate.x=0;
                    keyBoard.targetRotate.x=0;
                    break;
                case KeyEvent.VK_DELETE:
                case KeyEvent.VK_Q:
                case KeyEvent.VK_PAGE_DOWN:
                case KeyEvent.VK_E:
                    keyBoard.targetBank=0;
                    break;
            }
        }
        public void keyPressed(KeyEvent e) {
            boolean startTimer=true;
            switch(e.getKeyCode()) {
                case KeyEvent.VK_A:
                    keyBoard.targetForward=0.15; break;
                case KeyEvent.VK_Z:
                    keyBoard.targetForward=-0.15; break;
                case KeyEvent.VK_DOWN:
                    if (e.isAltDown()) keyBoard.targetTranslate.y=-0.15;
                    else if (e.isControlDown()) keyBoard.targetPivotRotate.y=0.15;
                    else keyBoard.targetRotate.y=0.15;
                    break;
                case KeyEvent.VK_UP:
                    if (e.isAltDown()) keyBoard.targetTranslate.y=0.15;
                    else if (e.isControlDown()) keyBoard.targetPivotRotate.y=-0.15;
                    else keyBoard.targetRotate.y=-0.15;
                    break;
                case KeyEvent.VK_LEFT:
                    if (e.isAltDown()) keyBoard.targetTranslate.x=0.15;
                    else if (e.isControlDown()) keyBoard.targetPivotRotate.x=0.15;
                    else keyBoard.targetRotate.x=0.15;
                    break;
                case KeyEvent.VK_RIGHT:
                    if (e.isAltDown()) keyBoard.targetTranslate.x=-0.15;
                    else if (e.isControlDown()) keyBoard.targetPivotRotate.x=-0.15;
                    else keyBoard.targetRotate.x=-0.15;
                    break;
                case KeyEvent.VK_DELETE:
                case KeyEvent.VK_Q:
                    keyBoard.targetBank=-0.15;
                    break;
                case KeyEvent.VK_PAGE_DOWN:
                case KeyEvent.VK_E:
                    keyBoard.targetBank=0.15;
                    break;
                default:
                    startTimer=false;
            }
            if (startTimer) timer.start();
        }
    }
    private class TimerListener implements ActionListener {
        private long lastTime=0;
        public void actionPerformed(ActionEvent e) {
            long curTime=System.currentTimeMillis();
            long delta=curTime-lastTime; if (delta<=0 || delta>2*timer.getDelay()) delta=timer.getDelay();
            double mult=(double)delta/100;  // To maintain acceleration as if timer was paced at 100ms
            lastTime=curTime;
            boolean stopTimer=true;
            final double elas=.2; // 0 means instant accel, near 1 means slow accel
            double elasticity=Math.pow(elas,mult);
            keyBoard.velForward=keyBoard.velForward*elasticity+keyBoard.targetForward*mult*(1-elasticity);
            if (Math.abs(keyBoard.velForward)>0.001) 
            {stopTimer=false; cameraForward(keyBoard.velForward);}
            keyBoard.velTranslate.x=keyBoard.velTranslate.x*elasticity+keyBoard.targetTranslate.x*mult*(1-elasticity);
            keyBoard.velTranslate.y=keyBoard.velTranslate.y*elasticity+keyBoard.targetTranslate.y*mult*(1-elasticity);
            if (Math.abs(keyBoard.velTranslate.x)>.001 || Math.abs(keyBoard.velTranslate.y)>.001) {
                stopTimer=false; 
                cameraTranslate(keyBoard.velTranslate.x,keyBoard.velTranslate.y);
            }
            keyBoard.velRotate.x=keyBoard.velRotate.x*elasticity+keyBoard.targetRotate.x*mult*(1-elasticity);
            keyBoard.velRotate.y=keyBoard.velRotate.y*elasticity+keyBoard.targetRotate.y*mult*(1-elasticity);
            if (Math.abs(keyBoard.velRotate.x)>.001 || Math.abs(keyBoard.velRotate.y)>.001) {
                stopTimer=false;
                cameraRotate(keyBoard.velRotate.x,keyBoard.velRotate.y,false);
            }
            keyBoard.velBank=keyBoard.velBank*elasticity+keyBoard.targetBank*mult*(1-elasticity);
            if (Math.abs(keyBoard.velBank)>.001) 
            {stopTimer=false; cameraBank(keyBoard.velBank);}
            keyBoard.velPivotRotate.x=keyBoard.velPivotRotate.x*elasticity+keyBoard.targetPivotRotate.x*mult*(1-elasticity);
            keyBoard.velPivotRotate.y=keyBoard.velPivotRotate.y*elasticity+keyBoard.targetPivotRotate.y*mult*(1-elasticity);
            if (Math.abs(keyBoard.velPivotRotate.x)>.001 || Math.abs(keyBoard.velPivotRotate.y)>.001) {
                stopTimer=false;
                cameraRotate(keyBoard.velPivotRotate.x,keyBoard.velPivotRotate.y,true);
            }
            if (stopTimer) timer.stop(); else timer.restart();
        }
    }
    private class OnMouse extends MouseInputAdapter {
        private Point lastPoint=new Point(0,0);
        public void mousePressed(MouseEvent e) {
            lastPoint=new Point(e.getX(),e.getY());
            requestFocusInWindow();
        }
        public void mouseDragged(MouseEvent e) {
            Point curPoint=new Point(e.getX(),e.getY());
            double dx=(curPoint.x-lastPoint.x)/100f;
            double dy=(curPoint.y-lastPoint.y)/100f;
            if (e.isControlDown()) {cameraRotate(dx,dy,false);}
            else if ((e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK)!=0) {cameraForward(dy);}
            else if ((e.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK)!=0) {cameraTranslate(dx,dy);}
            else if (e.isShiftDown()) {
                Rectangle r=getBounds();
                double xdir=curPoint.x-(r.x+r.width/2),ydir=curPoint.y-(r.y+r.height/2);
                double length=Math.sqrt(xdir*xdir+ydir*ydir);
                cameraBank((dx*ydir-dy*xdir)/length);
            }
            else if (e.isAltDown()) {cameraTranslate(dx,dy);}
            else cameraRotate(-dx,-dy,true);
            lastPoint=curPoint;
        }
    }
    public ModelView() {
        reInitializeVars();
        setPreferredSize(new Dimension(320,240));
        OnMouse onMouse=new OnMouse();
        addMouseListener(onMouse);
        addMouseMotionListener(onMouse);
        addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                keyBoard.velForward=e.getWheelRotation()*.5;
                timer.start();
            }
        });
        addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) {
                    recalcScale();
                }
            });
        addKeyListener(new OnKey());
        recalcUpRight();
        timer=new Timer(50,new TimerListener());
    }
    /** Updates screenUp and screenRight using the current up vector.
     * The current up vector needs to be of norm 1.
     */    
    private void recalcUpRight() {
        screenUp=new Vec(up);
        screenUp=screenUp.substract(eyeDirection.scalarMult(eyeDirection.dotProduct(up)));
        screenUp=screenUp.normalize();
        screenRight=screenUp.crossProduct(eyeDirection).normalize();
    }
    private void cameraForward(double amt) {
        eyePosition=eyePosition.add(eyeDirection.scalarMult(amt));
        repaint();
    }
    private void cameraRotate(double amtx,double amty,boolean onPivot) {
        Vec eyeRef=null;
        if (onPivot) eyeRef=new Vec(eyePosition.dotProduct(eyeDirection),
            eyePosition.dotProduct(screenUp),eyePosition.dotProduct(screenRight));
        eyeDirection=eyeDirection.substract(screenRight.scalarMult(amtx));
        eyeDirection=eyeDirection.add(screenUp.scalarMult(amty));
        eyeDirection=eyeDirection.normalize();
        if (onPivot) {
            recalcUpRight();
            eyePosition=eyeDirection.scalarMult(eyeRef.x).add(
                screenUp.scalarMult(eyeRef.y)).add(screenRight.scalarMult(eyeRef.z));
            // Tend to preserve up direction to real up
        }
        if (eyeDirection.y>-.9 && eyeDirection.y<.9) {
            /* Note: Orthogonal unit vectors a,b define a plane. The vector in this plane with max dot
                product with unit vector u is pa+qb, where p^2+q^2=1 and p=<a,u>/sqrt(<a,u>^2+<b,u>^2) */
            int sign=up.y>0?1:-1;   // sign of up vector... since need (0,-1,0) whin upside down
            double au=sign*screenUp.y,bu=sign*eyeDirection.y;
            double len=Math.sqrt(au*au+bu*bu);
            up=screenUp.scalarMult(au/len).add(eyeDirection.scalarMult(bu/len));
            up=up.add(new Vec(0,.1*sign,0)).normalize();
        }
        else up=screenUp;
        recalcUpRight();
        repaint();
    }
    private void cameraTranslate(double amtx,double amty) {
        eyePosition=eyePosition.substract(screenRight.scalarMult(amtx));
        eyePosition=eyePosition.add(screenUp.scalarMult(amty));
        repaint();
    }
    private void cameraBank(double amt) {
        up=screenUp.add(screenRight.scalarMult(amt)).normalize();
        recalcUpRight();
        repaint();
    }
    /** Returns a vector whose x,y coordinates are screen x,y, and z is distance */
    private Vec project(Vec vec) {
        vec=vec.substract(eyePosition);
        double dist=vec.dotProduct(eyeDirection);
        vec=vec.scalarMult(screenDistance/dist).substract(eyeDirection.scalarMult(screenDistance));
        Vec result=new Vec(scale*screenRight.dotProduct(vec),scale*screenUp.dotProduct(vec),dist);
        return result;
    }
    /** Draws the line, truncates the line if necessary when bahind screen */
    private void draw3DLine(Graphics2D g2d,Vec v1,Vec v2) {
        final double truncDistance=0.01;
        if (v1.isNaN() || v2.isNaN()) return;
        Vec p1=project(v1),p2=project(v2);
        if (p1.z<truncDistance && p2.z<truncDistance) return;
        if (p1.z<truncDistance) {Vec t=v1; v1=v2; v2=t; t=p1; p1=p2; p2=t;}
        if (p2.z<truncDistance) {
            v2=v1.scalarMult(truncDistance-p2.z).add(v2.scalarMult(p1.z-truncDistance)).scalarDivide(p1.z-p2.z);
            p2=project(v2);
        }
        g2d.drawLine((int)p1.x,-(int)p1.y,(int)p2.x,-(int)p2.y);
    }

    // superclass for drawable objects
    private abstract class Element implements Comparable {
        boolean renderable=true;    // should be set to false if this object does not render
        boolean isRenderable() {return renderable;}
        protected double dist;
        public int compareTo(Object o) {
            Element e=(Element )o;
            if (dist>e.dist) return -1;
            else if (dist<e.dist) return 1;
            else return 0;
        }
        double getBlendAmt() {
            double blendAmt;
            if (fogEnabled) {
                blendAmt=(dist-fogStart)/(fogEnd-fogStart);
                if (blendAmt>1) return 1;
                else if (blendAmt<0) return 0;
                else return blendAmt;
            } else return 0;
        }
        abstract public void render(Graphics2D g2d);
    }
    // class to represent a rectangle
    private class ElementRect extends Element {
        //TODO: Optional triangulation
        //TODO: Add global controls to specify light position, color
        private double shinyNess=0.15,shineIntensity=0.5;
        /**
         * shinyNess: Lower values will spread out the shine
         * shineIntensity: Maximum intensity of the shine
         */
        //TODO: Make the shinyness members of the object
        private Polygon s;
        private Color curveColor,surfaceColor;
        public ElementRect(Vec p1, Vec p2, Vec p3, Vec p4,Color curveColor,Color surfaceColor) {
            double intensity=0;
            Vec dir1=p2.substract(p1),dir2=p3.substract(p2);
            Vec dir3=p4.substract(p3),dir4=p1.substract(p4);
            p1=project(p1); p2=project(p2); p3=project(p3); p4=project(p4);
            if (p1.z<0 || p2.z<0 || p3.z<0 || p4.z<0) {renderable=false; return;}
            dist=(p1.z+p2.z+p3.z+p4.z)/4;
            double blendAmt=getBlendAmt(); if (blendAmt>=1) {renderable=false; return;}
            s=new Polygon();
            s.addPoint((int)p1.x,-(int)p1.y);
            s.addPoint((int)p2.x,-(int)p2.y);
            s.addPoint((int)p3.x,-(int)p3.y);
            s.addPoint((int)p4.x,-(int)p4.y);
            this.curveColor=Utils.blendColors(curveColor,bgColor,blendAmt);
            if (shineIntensity>0 && shinyNess>0) {
                Vec lightDirection=eyeDirection;
                Vec normalDirection=dir1.crossProduct(dir2);
                normalDirection=normalDirection.add(dir3.crossProduct(dir4));
                intensity+=normalDirection.normalize().dotProduct(lightDirection);
                intensity=Math.abs(intensity);
                intensity=(intensity-1)/(1-shinyNess)+1;
                if (intensity<0) intensity=0; else if (intensity>1) intensity=1;
                surfaceColor=Utils.blendColors(surfaceColor,Color.WHITE,intensity*shineIntensity);
            }
            this.surfaceColor=Utils.blendColors(surfaceColor,bgColor,blendAmt);
        }
        public void render(Graphics2D g2d) {
            if (!renderable) return;
            if (surfaceColor!=null) {g2d.setColor(surfaceColor); g2d.fill(s);}
            g2d.setColor(curveColor); g2d.draw(s);
        }
    }
    // class to represent a line
    private class ElementCurve extends Element {
        private int x1,y1,x2,y2;
        private Color curveColor;
        private int curveWidth;
        public ElementCurve(Vec p1, Vec p2,Color curveColor,int curveWidth,boolean absoluteWidth) {
            p1=project(p1); p2=project(p2);
            if (p1.z<0 || p2.z<0) {renderable=false; return;}
            dist=(p1.z+p2.z)/2;
            double blendAmt=getBlendAmt(); if (blendAmt>=1) {renderable=false; return;}
            x1=(int)p1.x; y1=-(int)p1.y;
            x2=(int)p2.x; y2=-(int)p2.y;
            this.curveColor=Utils.blendColors(curveColor,bgColor,blendAmt);
            this.curveWidth=absoluteWidth?(int)curveWidth:(int)(screenDistance/dist*curveWidth);
        }
        public void render(Graphics2D g2d) {
            if (!renderable) return;
            Stroke oldStroke=g2d.getStroke();
            g2d.setStroke(new BasicStroke(curveWidth,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g2d.setColor(curveColor);
            g2d.drawLine(x1,y1,x2,y2);
            g2d.setStroke(oldStroke);
        }
    }
    // class to represent text
    private class ElementString extends Element {
        private double x,y;
        private Color color;
        private String string;
        boolean tick=false;
        double tickX,tickY;
        // tickPerp is to indicate direction perpendicular to the tick, null for no tick
        public ElementString(String str,Vec pos,Color color,Vec tickPerp) {
            pos=project(pos);
            if (pos.z<0) {renderable=false; return;}
            dist=pos.z;
            double blendAmt=getBlendAmt(); if (blendAmt>=1) {renderable=false; return;}
            x=pos.x; y=-pos.y;
            this.color=Utils.blendColors(color,bgColor,blendAmt);
            this.string=str;
            if (tickPerp!=null) {
                tickPerp=project(tickPerp).substract(project(new Vec(0,0,0)));
                tickX=tickPerp.y; tickY=tickPerp.x;
                double norm=Math.sqrt(tickX*tickX+tickY*tickY);
                tickX/=norm; tickY/=norm;
                tick=true;
            }
        }
        public void render(Graphics2D g2d) {
            if (!renderable) return;
            g2d.setColor(color);
            if (tick) {
                g2d.drawLine((int)(x-tickX*3),(int)(y-tickY*3),(int)(x+tickX*3),(int)(y+tickY*3));
                g2d.drawString(string,(int)x+5,(int)y+12);
            }
            else g2d.drawString(string,(int)x-5,(int)y+5);
        }
    }

    public class AxesDefinition implements Printable {
        public void readFromStream(DataInputStream s) throws IOException {
            for (int i=0; i<3; i++) {
                axisVectors[i].readFromStream(s);
                shown[i]=s.readBoolean();
                min[i]=s.readDouble(); max[i]=s.readDouble();
            }
            incr=s.readDouble(); tickDensity=s.readDouble();
            color=Utils.readColor(s); width=s.readInt();
        }
        public void writeToStream(DataOutputStream s) throws IOException {
            for (int i=0; i<3; i++) {
                axisVectors[i].writeToStream(s);
                s.writeBoolean(shown[i]);
                s.writeDouble(min[i]); s.writeDouble(max[i]);
            }
            s.writeDouble(incr); s.writeDouble(tickDensity);
            Utils.writeColor(s,color); s.writeInt(width);
        }
        public final Vec[] axisVectors={new Vec(1,0,0),new Vec(0,1,0),new Vec(0,0,1)};
        boolean[] shown={true,true,true};
        public double[] min={0,0,0},max={1,1,1};
        public double incr=0.05;
        public double tickDensity=.2;
        public Color color=Color.BLACK;
        public int width=2;
    }
    public AxesDefinition axesDefinition=new AxesDefinition();
    // this method is only called by paintAsSolid
    private void createAxes(LinkedList elements) {
        final String[] axisNames={"X","Z","Y"};
        for (int i=0; i<3; i++) {
            if (!axesDefinition.shown[i]) continue;
            Vec axisVector=axesDefinition.axisVectors[i];
            double min=axesDefinition.min[i],max=axesDefinition.max[i]+.05;
            // create the axis line segments
            double incr=axesDefinition.incr;
            for (double v=min; v<max-1e-8; v+=incr) {
                elements.add(new ElementCurve(axisVector.scalarMult(v),
                    axisVector.scalarMult(v+incr),axesDefinition.color,axesDefinition.width,true));
            }
            // create the tick mark labels
            for (double v=Math.ceil(min/axesDefinition.tickDensity)*axesDefinition.tickDensity;
                 v<=max+1e-8;
                 v+=axesDefinition.tickDensity) {
                double pos=(double)Math.round(v*1000)/1000;
                if (pos!=0) elements.add(new ElementString(String.valueOf(pos),
                        axisVector.scalarMult(v),axesDefinition.color,axisVector));
            }
            // create the arrows
            Vec planeVec=axesDefinition.axisVectors[i==0?2:0];
            elements.add(new ElementCurve(axisVector.scalarMult(max),
                axisVector.scalarMult(max*.97).substract(planeVec.scalarMult(.03)),
                axesDefinition.color,axesDefinition.width,true));
            elements.add(new ElementCurve(axisVector.scalarMult(max),
                axisVector.scalarMult(max*.97).add(planeVec.scalarMult(.03)),
                axesDefinition.color,axesDefinition.width,true));
            elements.add(new ElementString(axisNames[i],
                    axisVector.scalarMult(max*1.05),axesDefinition.color,null));
        }
    }
    private void paintAsSolid(Graphics2D g2d) {
        LinkedList elements=new LinkedList();
        if (bShowAxes) createAxes(elements);
        for (int nFunc=0; nFunc<functions.size(); nFunc++) {
            ModelFunction func=functions.getFunction(nFunc);
            if (!func.visible) continue;
            Vec[][] coords=func.coords;
            int n1=func.gridDivsU,n2=func.gridDivsV;
            for (int i=0; i<n1-1; i++) {
                if (func.isCurve) {
                    ElementCurve er=new ElementCurve(coords[i][0],coords[i+1][0],
                        func.curveColor,func.curveWidth,func.absoluteWidth);
                    if (er.isRenderable()) elements.add(er);
                }
                else
                    for (int j=0; j<n2-1; j++) {
                        Element er;
                        if (func.fillSurface) {
                            er=new ElementRect(coords[i][j],coords[i+1][j],coords[i+1][j+1],coords[i][j+1],
                                func.curveColor,func.fillSurface?func.surfaceColor:null);
                            if (er.isRenderable()) elements.add(er);
                        }
                        else {
                            er=new ElementCurve(coords[i][j],coords[i+1][j],func.curveColor,0,true);
                            if (er.isRenderable()) elements.add(er);
                            er=new ElementCurve(coords[i][j],coords[i][j+1],func.curveColor,0,true);
                            if (er.isRenderable()) elements.add(er);
                            if (i==n1-2) {
                                er=new ElementCurve(coords[i+1][j+1],coords[i+1][j],func.curveColor,0,true);
                                if (er.isRenderable()) elements.add(er);
                            }
                            if (j==n2-2) {
                                er=new ElementCurve(coords[i+1][j+1],coords[i][j+1],func.curveColor,0,true);
                                if (er.isRenderable()) elements.add(er);
                            }
                        }
                    }
            }
        }
        java.util.Collections.sort(elements);   // sort by distance
        for (Iterator iterator=elements.iterator(); iterator.hasNext();) {
            Element recIndex=(Element) iterator.next();
            recIndex.render(g2d);
        }
    }
    private void paintAsWireframe(Graphics2D g2d) {
        g2d.setColor(Utils.blendColors(axesDefinition.color,Color.WHITE,.75));
        if (bShowAxes)
            for (int i=0; i<3; i++)
                if (axesDefinition.shown[i])
                    draw3DLine(g2d,axesDefinition.axisVectors[i].scalarMult(axesDefinition.min[i]),
                        axesDefinition.axisVectors[i].scalarMult(axesDefinition.max[i]));
        //g2d.setColor(Color.BLUE);
        //draw3DLine(g2d,eyePosition.add(eyeDirection.scalarDivide(pivotDistance)),
        //    eyePosition.add(eyeDirection.scalarDivide(pivotDistance)).add(up.scalarMult(10)));
        for (int nFunc=0; nFunc<functions.size(); nFunc++) {
            ModelFunction function=functions.getFunction(nFunc);
            if (!function.visible) continue;
            Vec[][] coords=function.coords;
            if (!function.isCurve && function.fillSurface)
                g2d.setColor(Utils.blendColors(Color.BLACK,function.surfaceColor,.5));
            else g2d.setColor(Utils.blendColors(Color.BLACK,function.curveColor,.5));
            if (function.isCurve)
                for (int i=0;i<function.gridDivsU-1;i++)
                    draw3DLine(g2d,coords[i][0],coords[i+1][0]);
            else
                for (int i=0;i<function.gridDivsU;i++)
                for (int j=0;j<function.gridDivsV;j++) {
                    if (j<coords[i].length-1) draw3DLine(g2d,coords[i][j],coords[i][j+1]);
                    if (i<coords.length-1) draw3DLine(g2d,coords[i][j],coords[i+1][j]);
                }
        }
    }
    public void paintToGrphics(final Graphics g,int width,int height) {
        Graphics2D g2d=(Graphics2D)g.create();
        if (backCulling==0) g2d.setColor(Color.WHITE);
        else g2d.setColor(bgColor);
        g2d.fillRect(0,0,width,height);
        g2d.translate(width/2,height/2);
        if (backCulling!=0) paintAsSolid(g2d);
        else paintAsWireframe(g2d);
        if (!title.equals("")) {
            g2d=(Graphics2D)g.create();
            g2d.setFont(g2d.getFont().deriveFont(backCulling==0?Font.PLAIN:Font.BOLD,12f));
            Rectangle2D textR=g2d.getFontMetrics().getStringBounds(title,g2d);
            Rectangle screenR=new Rectangle(width/2-(int)textR.getWidth()/2,height-(int)textR.getHeight()-10,
                (int)textR.getWidth(),(int)textR.getHeight());
            // draw text
            if (backCulling!=0) {
                g2d.setColor(new Color(0,0,0,32));
                g2d.fillRect(0,screenR.y+3,width,screenR.height+2);
                g2d.setColor(new Color(255,255,255,128));
                g2d.drawString(title,screenR.x,screenR.y+screenR.height);
            }
            g2d.setColor(new Color(0,0,0,128));
            g2d.drawString(title,screenR.x+1,screenR.y+screenR.height+1);
        }
    }
    protected void paintComponent(Graphics g) {
        Dimension d=getSize();
        paintToGrphics(g,d.width,d.height);
    }
    public void saveImage(String fileName) throws IOException {
        BufferedImage img=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics g=img.getGraphics();
        int width=img.getWidth(),height=img.getHeight();
        paintToGrphics(img.getGraphics(),img.getWidth(),img.getHeight());
        Color bg=backCulling!=0?bgColor:Color.WHITE;
        g.setColor(Utils.blendColors(Color.BLACK,bg,.5));
        g.drawRect(1,1,width-3,height-3);
        g.setColor(Utils.blendColors(Color.WHITE,bg,.5));
        g.drawRect(0,0,width-1,height-1);
        ImageIO.write(img,"png",new File(fileName));
    }
}
