package com.jsq.notebook.storage;

import com.jsq.notebook.util.StringUtils;
import net.rim.device.api.math.Fixed32;
import net.rim.device.api.system.Bitmap;
import net.rim.device.api.system.EncodedImage;
import net.rim.device.api.ui.component.Dialog;

import javax.microedition.content.ContentHandler;
import javax.microedition.content.Invocation;
import javax.microedition.content.Registry;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Manages binary attachments on the device SD Card using unsigned JSR-75 FileConnection APIs.
 * Prevents JVM heap exhaustion by keeping image data off internal NVRAM.
 */
public class AttachmentStorageManager {

    private static AttachmentStorageManager instance;
    private String storageRoot;
    private String attachmentDirectoryUrl;

    private AttachmentStorageManager() {
        initStoragePaths();
    }

    public static synchronized AttachmentStorageManager getInstance() {
        if (instance == null) {
            instance = new AttachmentStorageManager();
        }
        return instance;
    }

    /**
     * Determines whether an SD Card is mounted, otherwise falls back to internal device store.
     */
    private void initStoragePaths() {
        String card = System.getProperty("fileconn.dir.memorycard");
        if (card != null && card.length() > 0) {
            storageRoot = card;
        } else {
            // Check FileSystemRegistry roots
            boolean foundCard = false;
            try {
                Enumeration roots = FileSystemRegistry.listRoots();
                while (roots.hasMoreElements()) {
                    String root = (String) roots.nextElement();
                    String lower = root.toLowerCase();
                    if (lower.indexOf("sdcard") >= 0 || lower.indexOf("media") >= 0 || lower.indexOf("card") >= 0) {
                        storageRoot = "file:///" + root;
                        foundCard = true;
                        break;
                    }
                }
            } catch (Exception e) {
                // Ignore registry access error
            }

            if (!foundCard) {
                storageRoot = "file:///SDCard/";
            }
        }

        if (!storageRoot.endsWith("/")) {
            storageRoot = storageRoot + "/";
        }

        attachmentDirectoryUrl = storageRoot + "BlackBerry/documents/JSQNotebook/Attachments/";
        ensureDirectoryExists(attachmentDirectoryUrl);
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public String getAttachmentDirectoryUrl() {
        return attachmentDirectoryUrl;
    }

    /**
     * Creates all intermediate directories if they do not already exist.
     */
    public boolean ensureDirectoryExists(String dirUrl) {
        if (dirUrl == null || !dirUrl.startsWith("file:///")) {
            return false;
        }

        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirUrl, Connector.READ_WRITE);
            if (!fc.exists()) {
                // Ensure parent exists first
                String pathWithoutScheme = dirUrl.substring(8); // after file:///
                Vector parts = StringUtils.split(pathWithoutScheme, '/');
                StringBuffer currentPath = new StringBuffer("file:///");

                int count = parts.size();
                for (int i = 0; i < count; i++) {
                    String part = (String) parts.elementAt(i);
                    if (part.length() == 0) {
                        continue;
                    }
                    currentPath.append(part).append('/');
                    FileConnection stepFc = null;
                    try {
                        stepFc = (FileConnection) Connector.open(currentPath.toString(), Connector.READ_WRITE);
                        if (!stepFc.exists()) {
                            stepFc.mkdir();
                        }
                    } catch (IOException ioe) {
                        // May already exist or root
                    } finally {
                        if (stepFc != null) {
                            try {
                                stepFc.close();
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (fc != null) {
                try {
                    fc.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Determines whether a filename or URL represents a displayable image.
     */
    public static boolean isImageFile(String filenameOrUrl) {
        if (filenameOrUrl == null) return false;
        String lower = filenameOrUrl.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".png") || lower.endsWith(".gif") ||
               lower.endsWith(".bmp");
    }

    /**
     * Maps file extensions to standard MIME types for external application dispatch.
     */
    public static String getMimeType(String urlOrName) {
        if (urlOrName == null) return null;
        String lower = urlOrName.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = lower.substring(dot);

        // Images
        if (ext.equals(".jpg") || ext.equals(".jpeg")) return "image/jpeg";
        if (ext.equals(".png")) return "image/png";
        if (ext.equals(".gif")) return "image/gif";
        if (ext.equals(".bmp")) return "image/bmp";

        // Audio
        if (ext.equals(".mp3")) return "audio/mpeg";
        if (ext.equals(".amr")) return "audio/amr";
        if (ext.equals(".wav")) return "audio/wav";
        if (ext.equals(".aac")) return "audio/aac";
        if (ext.equals(".m4a")) return "audio/mp4";
        if (ext.equals(".wma")) return "audio/x-ms-wma";
        if (ext.equals(".mid") || ext.equals(".midi")) return "audio/midi";

        // Documents
        if (ext.equals(".doc")) return "application/msword";
        if (ext.equals(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (ext.equals(".xls")) return "application/vnd.ms-excel";
        if (ext.equals(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (ext.equals(".ppt")) return "application/vnd.ms-powerpoint";
        if (ext.equals(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (ext.equals(".pdf")) return "application/pdf";
        if (ext.equals(".txt")) return "text/plain";
        if (ext.equals(".csv")) return "text/csv";
        if (ext.equals(".html") || ext.equals(".htm")) return "text/html";

        // Video
        if (ext.equals(".mp4")) return "video/mp4";
        if (ext.equals(".3gp")) return "video/3gpp";
        if (ext.equals(".avi")) return "video/avi";
        if (ext.equals(".wmv")) return "video/x-ms-wmv";

        // Archives
        if (ext.equals(".zip")) return "application/zip";

        return null;
    }

    /**
     * Opens a local file or attachment in its default external application
     * using JSR-211 CHAPI or the BlackBerry Browser.
     */
    public boolean openFileExternally(String fileUrl) {
        if (fileUrl == null || fileUrl.length() == 0) {
            return false;
        }

        // Web URLs: open via JSR-211 CHAPI (unsigned standard JSR API)
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://") || fileUrl.startsWith("www.")) {
            String url = fileUrl.startsWith("www.") ? "http://" + fileUrl : fileUrl;
            try {
                Invocation invocation = new Invocation(url);
                invocation.setAction(ContentHandler.ACTION_OPEN);
                Registry registry = Registry.getRegistry(AttachmentStorageManager.class.getName());
                if (registry != null && registry.invoke(invocation)) {
                    return true;
                }
            } catch (Throwable t) {
            }
        }

        String resolved = resolveAttachmentUrl(fileUrl);
        if (resolved == null) {
            resolved = fileUrl;
        }

        String mime = getMimeType(resolved);

        // 1. Try JSR-211 CHAPI with MIME type
        try {
            Invocation invocation;
            if (mime != null && mime.length() > 0) {
                invocation = new Invocation(resolved, mime);
            } else {
                invocation = new Invocation(resolved);
            }
            invocation.setAction(ContentHandler.ACTION_OPEN);
            Registry registry = Registry.getRegistry(AttachmentStorageManager.class.getName());
            if (registry != null) {
                boolean invoked = registry.invoke(invocation);
                if (invoked) {
                    return true;
                }
            }
        } catch (Throwable t) {
        }

        // 2. Try Invocation without explicit action
        try {
            Invocation invocation = new Invocation(resolved);
            Registry registry = Registry.getRegistry(AttachmentStorageManager.class.getName());
            if (registry != null) {
                boolean invoked = registry.invoke(invocation);
                if (invoked) {
                    return true;
                }
            }
        } catch (Throwable t) {
        }

        // 3. Inform user if no handler found
        Dialog.alert("No external application found to open:\n" + resolved);
        return false;
    }

    /**
     * Imports an external file (image or other attachment) into the application's SD Card attachments directory.
     * Returns the relative filename of the saved attachment.
     */
    public String importAttachmentFromUrl(String sourceUrl) throws IOException {
        ensureDirectoryExists(attachmentDirectoryUrl);

        int lastSlash = sourceUrl.lastIndexOf('/');
        String originalName = lastSlash >= 0 ? sourceUrl.substring(lastSlash + 1) : "attachment.bin";
        String cleanName = StringUtils.sanitizeFileName(originalName);

        String prefix = isImageFile(cleanName) ? "img_" : "att_";
        String uniqueFileName = prefix + System.currentTimeMillis() + "_" + cleanName;
        String destUrl = attachmentDirectoryUrl + uniqueFileName;

        copyFile(sourceUrl, destUrl);
        return uniqueFileName;
    }

    /**
     * Copies binary data from a source URL to a destination URL using JSR-75 FileConnection.
     */
    public void copyFile(String srcUrl, String destUrl) throws IOException {
        FileConnection srcFc = null;
        FileConnection destFc = null;
        InputStream in = null;
        OutputStream out = null;

        try {
            srcFc = (FileConnection) Connector.open(srcUrl, Connector.READ);
            if (!srcFc.exists()) {
                throw new IOException("Source file does not exist: " + srcUrl);
            }

            destFc = (FileConnection) Connector.open(destUrl, Connector.READ_WRITE);
            if (destFc.exists()) {
                destFc.delete();
            }
            destFc.create();

            in = srcFc.openInputStream();
            out = destFc.openOutputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                }
            }
            if (srcFc != null) {
                try {
                    srcFc.close();
                } catch (Exception e) {
                }
            }
            if (destFc != null) {
                try {
                    destFc.close();
                } catch (Exception e) {
                }
            }
        }
    }


    /**
     * Reads an image attachment into an EncodedImage and scales it appropriately to fit targetWidth.
     */
    public Bitmap loadAttachmentBitmap(String fileName, int maxWidth) {
        if (fileName == null || fileName.length() == 0) {
            return null;
        }

        String fileUrl = resolveAttachmentUrl(fileName);
        if (fileUrl == null) {
            return null;
        }

        FileConnection fc = null;
        InputStream in = null;

        try {
            fc = (FileConnection) Connector.open(fileUrl, Connector.READ);
            if (!fc.exists()) {
                return null;
            }

            in = fc.openInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            byte[] data = baos.toByteArray();

            EncodedImage encoded = EncodedImage.createEncodedImage(data, 0, data.length);
            if (encoded == null) {
                return null;
            }

            int imgWidth = encoded.getWidth();
            int imgHeight = encoded.getHeight();

            if (maxWidth > 0 && imgWidth > maxWidth) {
                encoded = scaleEncodedImage(encoded, maxWidth);
            }

            return encoded.getBitmap();
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
            if (fc != null) {
                try {
                    fc.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Safely scales an EncodedImage down using RIM Fixed32 fixed-point arithmetic
     * without loading full bitmap uncompressed into memory.
     */
    private EncodedImage scaleEncodedImage(EncodedImage image, int targetWidth) {
        try {
            int currentWidth = image.getWidth();
            int currentHeight = image.getHeight();
            if (targetWidth <= 0 || targetWidth >= currentWidth) {
                return image;
            }
            int targetHeight = (currentHeight * targetWidth) / currentWidth;
            if (targetHeight <= 0) {
                targetHeight = 1;
            }

            int currentWidthFixed32  = Fixed32.toFP(currentWidth);
            int currentHeightFixed32 = Fixed32.toFP(currentHeight);
            int requiredWidthFixed32  = Fixed32.toFP(targetWidth);
            int requiredHeightFixed32 = Fixed32.toFP(targetHeight);

            int scaleX = Fixed32.div(currentWidthFixed32, requiredWidthFixed32);
            int scaleY = Fixed32.div(currentHeightFixed32, requiredHeightFixed32);

            return image.scaleImage32(scaleX, scaleY);
        } catch (Throwable t) {
            return image;
        }
    }

    /**
     * Resolves an attachment filename or relative reference to an existing file:// URL.
     */
    public String resolveAttachmentUrl(String fileName) {
        if (fileName == null || fileName.length() == 0) {
            return null;
        }

        // 1. Direct absolute URL
        if (fileName.startsWith("file:///")) {
            if (fileExists(fileName)) {
                return fileName;
            }
        }

        // 2. Primary attachment directory URL
        String primary = attachmentDirectoryUrl + fileName;
        if (fileExists(primary)) {
            return primary;
        }

        // 3. Alternative SDCard / Media Card root variations
        String altDir = null;
        if (attachmentDirectoryUrl.indexOf("SDCard") >= 0) {
            altDir = StringUtils.replace(attachmentDirectoryUrl, "SDCard", "Media Card");
        } else if (attachmentDirectoryUrl.indexOf("Media Card") >= 0) {
            altDir = StringUtils.replace(attachmentDirectoryUrl, "Media Card", "SDCard");
        }
        if (altDir != null && fileExists(altDir + fileName)) {
            return altDir + fileName;
        }

        // 4. Fuzzy match in attachments folder (e.g. if timestamp prefix was added)
        String found = searchDirectoryForFile(attachmentDirectoryUrl, fileName);
        if (found != null) {
            return found;
        }
        if (altDir != null) {
            found = searchDirectoryForFile(altDir, fileName);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private boolean fileExists(String url) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(url, Connector.READ);
            return fc.exists();
        } catch (Exception e) {
            return false;
        } finally {
            if (fc != null) {
                try {
                    fc.close();
                } catch (Exception e) {
                }
            }
        }
    }

    private String searchDirectoryForFile(String dirUrl, String targetName) {
        FileConnection dirFc = null;
        try {
            dirFc = (FileConnection) Connector.open(dirUrl, Connector.READ);
            if (dirFc.exists() && dirFc.isDirectory()) {
                Enumeration entries = dirFc.list("*", true);
                while (entries.hasMoreElements()) {
                    String entry = (String) entries.nextElement();
                    if (!entry.endsWith("/")) {
                        if (entry.equalsIgnoreCase(targetName) ||
                            entry.toLowerCase().endsWith(targetName.toLowerCase())) {
                            return dirUrl + entry;
                        }
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            if (dirFc != null) {
                try {
                    dirFc.close();
                } catch (Exception e) {
                }
            }
        }
        return null;
    }
}
