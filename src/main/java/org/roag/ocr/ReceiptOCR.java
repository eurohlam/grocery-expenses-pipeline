package org.roag.ocr;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;


import java.io.File;

public class ReceiptOCR {

    private static Logger LOG = LogManager.getLogger(ReceiptOCR.class);

    public static final String DEFAULT_TESSDATA_PATH = "/opt/local/share/tessdata/";
    public static final String OUTPUT_FOLDER = "output/";
    public static final String TMP_FILE_NAME = "tmp.jpg";

    // Receipts are a narrow domain: upper/lower letters, digits and a few symbols.
    // Whitelisting the expected characters keeps Tesseract from inventing noise that
    // the parser would otherwise have to strip out afterwards.
    public static final String DEFAULT_CHAR_WHITELIST =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.$ /&@%-";

    // PSM 6 = "assume a single uniform block of text". For most receipts this reads
    // the aligned item/price columns more reliably than PSM 4. Override via withPageSegMode().
    public static final int DEFAULT_PAGE_SEG_MODE = 6;

    private ITesseract tesseract;
    private String srcFile;
    private String tmpFile;
    private String ocrResult;

    // Working image shared across preprocessing steps so they compose instead of
    // each re-reading the source file from disk.
    private Mat image;

    private int pageSegMode = DEFAULT_PAGE_SEG_MODE;
    private String charWhitelist = DEFAULT_CHAR_WHITELIST;

    // When enabled, the working image is written to output/NN_<stage>.jpg after each
    // preprocessing step so the stages can be compared side by side.
    private boolean dumpStages = false;
    private int dumpCounter = 0;

    public ReceiptOCR() {
        this(DEFAULT_TESSDATA_PATH); // Default path to tessdata directory
    }

    public ReceiptOCR(String dataPath) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        tesseract = new Tesseract();
        tesseract.setDatapath(dataPath); // Path to tessdata directory
    }

    public ReceiptOCR withSource(String imageFile) {
        srcFile = imageFile;
        tmpFile = srcFile;
        image = null;
        return this;
    }

    public ReceiptOCR withPageSegMode(int pageSegMode) {
        this.pageSegMode = pageSegMode;
        return this;
    }

    public ReceiptOCR withCharWhitelist(String charWhitelist) {
        this.charWhitelist = charWhitelist;
        return this;
    }

    /**
     * Enables dumping the working image after each preprocessing step to
     * {@code output/NN_<stage>.jpg}, for side-by-side comparison of the stages.
     */
    public ReceiptOCR withStageDump(boolean dumpStages) {
        this.dumpStages = dumpStages;
        return this;
    }

    private void ensureOutputFolder() {
        File outputFolder = new File(OUTPUT_FOLDER);
        if (!(outputFolder.exists() && outputFolder.isDirectory())) {
            outputFolder.mkdir();
        }
    }

    private void dumpStage(String name) {
        if (!dumpStages || image == null) {
            return;
        }
        ensureOutputFolder();
        String file = OUTPUT_FOLDER + String.format("%02d_%s.jpg", dumpCounter++, name);
        Imgcodecs.imwrite(file, image);
        LOG.info("   Stage image dumped: {}", file);
    }

    /**
     * Lazily loads the source image into the working {@link Mat} the first time a
     * preprocessing step needs it. Subsequent steps operate on the result of the
     * previous one, so the chain composes.
     */
    private void ensureLoaded() {
        if (image == null) {
            image = Imgcodecs.imread(srcFile, Imgcodecs.IMREAD_COLOR);
            if (image.empty()) {
                throw new IllegalStateException("Could not read image from: " + srcFile);
            }
            dumpStage("original");
        }
    }

    /**
     * Converts the working image to single-channel grayscale. Adaptive threshold and
     * deskew both require a single channel, and Tesseract works on grayscale anyway.
     */
    public ReceiptOCR grayscale() {
        ensureLoaded();
        if (image.channels() > 1) {
            Mat gray = new Mat();
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            image = gray;
            dumpStage("grayscale");
        }
        return this;
    }

    /**
     * Upscales the image when it is small. Tesseract prefers ~300 DPI; low-resolution
     * (e.g. thermal) receipts read noticeably better when enlarged. High-resolution
     * phone photos are left untouched.
     *
     * @param minSide target minimum side length in pixels; no-op if already larger
     */
    public ReceiptOCR upscale(int minSide) {
        ensureLoaded();
        int smaller = Math.min(image.rows(), image.cols());
        if (smaller > 0 && smaller < minSide) {
            double scale = (double) minSide / smaller;
            LOG.info("===> Upscaling image by factor {}", scale);
            Mat dst = new Mat();
            Imgproc.resize(image, dst, new Size(), scale, scale, Imgproc.INTER_CUBIC);
            image = dst;
            dumpStage("upscale");
        }
        return this;
    }

    /**
     * Edge-preserving denoise. Smooths out sensor/paper noise while keeping character
     * edges sharp, which helps OCR more than a plain Gaussian blur.
     */
    public ReceiptOCR blur() {
        LOG.info("===> Blurring started");
        ensureLoaded();
        Mat dst = new Mat();
        Imgproc.bilateralFilter(image, dst, 9, 75, 75);
        image = dst;
        dumpStage("blur");
        LOG.info("<=== Blurring finished");
        return this;
    }

    /**
     * Detects the dominant text angle and rotates the image upright. Phone photos of
     * receipts are rarely perfectly straight, and Tesseract is sensitive to skew.
     */
    public ReceiptOCR deskew() {
        LOG.info("===> Deskew started");
        grayscale();
        // Binarise (inverted) so text becomes the set of foreground points to measure.
        Mat bin = new Mat();
        Imgproc.threshold(image, bin, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

        Mat nonZero = new Mat();
        Core.findNonZero(bin, nonZero);
        if (nonZero.empty()) {
            LOG.info("<=== Deskew skipped (no foreground pixels)");
            return this;
        }

        MatOfPoint2f points = new MatOfPoint2f();
        nonZero.convertTo(points, CvType.CV_32F);
        RotatedRect box = Imgproc.minAreaRect(points);

        double angle = box.angle;
        if (angle < -45) {
            angle += 90;
        } else if (angle > 45) {
            angle -= 90;
        }

        // Skip tiny corrections; rotating always costs a bit of quality via interpolation.
        if (Math.abs(angle) < 0.5) {
            LOG.info("<=== Deskew skipped (angle {} below threshold)", angle);
            return this;
        }

        LOG.info("   Correcting skew angle: {}", angle);
        Point center = new Point(image.cols() / 2.0, image.rows() / 2.0);
        Mat rot = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat rotated = new Mat();
        Imgproc.warpAffine(image, rotated, rot, image.size(),
                Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE, new org.opencv.core.Scalar(255));
        image = rotated;
        dumpStage("deskew");
        LOG.info("<=== Deskew finished");
        return this;
    }

    /**
     * Adaptive (local) binarisation. Unlike a single global threshold, this copes with
     * the uneven lighting and shadows typical of a photographed receipt.
     */
    public ReceiptOCR adaptiveThreshold() {
        LOG.info("===> Adaptive threshold started");
        grayscale();
        Mat dst = new Mat();
        Imgproc.adaptiveThreshold(image, dst, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 31, 15);
        image = dst;
        dumpStage("adaptiveThreshold");
        LOG.info("<=== Adaptive threshold finished");
        return this;
    }

    //TODO: kept for backward compatibility; adaptiveThreshold() generally does better
    // on photographed receipts because lighting is uneven.
    public ReceiptOCR threshold(int threshold) {
        LOG.info("===> Threshold processing started");
        grayscale();
        Mat dstMat = new Mat();
        Imgproc.threshold(image, dstMat, threshold, 255, Imgproc.THRESH_OTSU);
        image = dstMat;
        dumpStage("threshold");
        LOG.info("<=== Threshold processing finished");
        return this;
    }

    public ReceiptOCR ocr() {
        try {
            LOG.info("===> OCR started");
            ensureOutputFolder();
            // Persist the preprocessed image for Tesseract (and for inspection). If no
            // preprocessing ran, fall back to the original source file.
            if (image != null) {
                tmpFile = OUTPUT_FOLDER + TMP_FILE_NAME;
                Imgcodecs.imwrite(tmpFile, image);
            }
            tesseract.setPageSegMode(pageSegMode);
            tesseract.setOcrEngineMode(1); // Neural nets LSTM engine only.
            // Domain tuning: restrict output alphabet and turn off dictionary correction,
            // since product names ("TRICKETTS GROVE") are not dictionary words and
            // correction actively hurts here.
            if (charWhitelist != null && !charWhitelist.isEmpty()) {
                tesseract.setVariable("tessedit_char_whitelist", charWhitelist);
            }
            tesseract.setVariable("load_system_dawg", "0");
            tesseract.setVariable("load_freq_dawg", "0");
            ocrResult = tesseract.doOCR(new File(tmpFile));
            LOG.info("  ===> OCR result:\n {}", ocrResult);
            LOG.info("<=== OCR finished");
        } catch (TesseractException e) {
            LOG.error(e);
        }
        return this;
    }

    public String getOcrAsString() {
        return ocrResult;
    }

}
