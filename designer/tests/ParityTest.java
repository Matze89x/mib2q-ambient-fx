import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;
import de.mk.ambientfx.designer.DesignEffect;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.Properties;

public class ParityTest {
    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
        VehicleData v = (VehicleData) unsafe.allocateInstance(VehicleData.class);
        Rgb[] pal = {new Rgb(255,255,255), new Rgb(255,0,0), new Rgb(0,255,0), new Rgb(0,0,255), new Rgb(255,160,0), new Rgb(170,0,255), new Rgb(0,220,220)};
        File[] fs = dir.listFiles();
        java.util.Arrays.sort(fs);
        for (int k = 0; k < fs.length; k++) {
            String n = fs[k].getName();
            if (!n.endsWith(".properties")) continue;
            String name = n.substring(0, n.length() - ".properties".length());
            boolean usePal = new File(dir, name + ".pal").exists();
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(fs[k]);
            p.load(in);
            in.close();
            DesignEffect e = new DesignEffect(p);
            e.start(1000L);
            Rgb out = new Rgb();
            for (int i = 0; i < 90; i++) {
                long now = 1000L + i * 100L;
                v.valid = true;
                v.lastUpdateMs = now;
                v.speedUnit = 0;
                v.speedKmh = (i * 3) % 160;
                v.acceleratorPct = (i * 7) % 101;
                v.wheelAngle = ((i * 13) % 361) - 180;
                v.engineRpm = 800 + i * 50;
                v.longAccel = (((i * 3) % 13) - 6) / 2f;
                v.latAccel = (((i * 5) % 13) - 6) / 2f;
                v.driverBraking = (i % 9 == 0);
                boolean day = (i % 20) < 15;
                e.tick(now, v, usePal ? pal : null, day, out);
                System.out.println(name + " " + i + " " + out.r + " " + out.g + " " + out.b);
            }
        }
    }
}
