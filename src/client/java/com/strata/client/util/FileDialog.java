package com.strata.client.util;

import com.strata.Strata;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.function.Consumer;

/** The operating system's "open file" dialog, through LWJGL's tinyfd, which the game bundles. */
public final class FileDialog {

    private FileDialog() {
    }

    /** The folder of the last pick, so the next dialog opens there. */
    private static volatile String lastDir = "";

    private static volatile boolean open;

    /** True while a dialog is open: a second Browse is ignored, not queued. */
    public static boolean isOpen() {
        return open;
    }

    /**
     * Opens the dialog.
     *
     * @param pattern a filter such as "*.png"
     * @param description what the filter is called ("PNG images")
     * @param onPicked given the chosen file's path, on the main thread; not called on cancel
     */
    public static void pick(String title, String pattern, String description, Consumer<String> onPicked) {
        if (open) {
            return;
        }
        open = true;
        Thread worker = new Thread(() -> {
            String chosen = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8(pattern)).flip();
                chosen = TinyFileDialogs.tinyfd_openFileDialog(title, lastDir, filters, description, false);
            } catch (Throwable e) {
                Strata.LOGGER.warn("The file dialog could not open: {}", e.toString());
            }
            String result = chosen;
            Minecraft.getInstance().execute(() -> {
                open = false;
                if (result != null && !result.isBlank()) {
                    int cut = Math.max(result.lastIndexOf('\\'), result.lastIndexOf('/'));
                    if (cut > 0) {
                        lastDir = result.substring(0, cut + 1);
                    }
                    onPicked.accept(result);
                }
            });
        }, "Strata file dialog");
        worker.setDaemon(true);
        worker.start();
    }
}
