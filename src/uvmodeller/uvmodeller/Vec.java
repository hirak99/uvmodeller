package uvmodeller;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A simple vector class - could have done better if operator overloading 
 *   was supported :(
 * @author Arnab
 */
public class Vec implements Printable{
    public Vec() {}
    public Vec(final double a,final double b,final double c) {x=a;y=b;z=c;}
    public Vec(final Vec vec) {x=vec.x;y=vec.y;z=vec.z;}
    public double x,y,z;
    public double dotProduct(final Vec p) {return x*p.x+y*p.y+z*p.z;}
    public double norm() {return Math.sqrt(x*x+y*y+z*z);}
    public Vec normalize() {final double n=norm(); return new Vec(x/n,y/n,z/n);}
    public Vec add(final Vec vec) {return new Vec(x+vec.x,y+vec.y,z+vec.z);}
    public Vec substract(final Vec vec) {return new Vec(x-vec.x,y-vec.y,z-vec.z);}
    public Vec scalarMult(final double c) {return new Vec(c*x,c*y,c*z);}
    public Vec scalarDivide(final double c) {return new Vec(x/c,y/c,z/c);}
    public Vec crossProduct(final Vec vec) {return new Vec(y*vec.z-z*vec.y,z*vec.x-x*vec.z,x*vec.y-y*vec.x);}
    public boolean isNaN() {return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z);}
    public boolean isZero() {return x==0 && y==0 && z==0;}
    public void writeToStream(DataOutputStream s) throws IOException {
        s.writeDouble(x); s.writeDouble(y); s.writeDouble(z);
    }
    public void readFromStream(DataInputStream s) throws IOException {
        x=s.readDouble(); y=s.readDouble(); z=s.readDouble();
    }
    // for debugging purpose only
    public String toString() {return "Vector: ("+x+","+y+","+z+")";}
}

