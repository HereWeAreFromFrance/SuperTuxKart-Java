package org.tuxkart.core;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Capture d'ecran PNG (touche F12) et support du mode demonstration. */
public final class Screenshot {

    private Screenshot() {
    }

    public static Path capture(Scene scene, Path target) {
        try {
            WritableImage img = scene.snapshot(null);
            Path out = target;
            if (out == null) {
                String stamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                out = Paths.get(System.getProperty("user.home"), "tuxkart-" + stamp + ".png");
            }
            File file = out.toFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", file);
            return out;
        } catch (Throwable t) {
            System.err.println("Capture impossible : " + t);
            return null;
        }
    }
}
