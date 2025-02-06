package org.roag.ocr;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;


import java.io.File;

public class ReceiptOCR {

    private static Logger LOG = LogManager.getLogger(ReceiptOCR.class);

    public static final String DEFAULT_TESSDATA_PATH = "/opt/local/share/tessdata/";
    public static final String OUTPUT_FOLDER = "output/";
    public static final String TMP_FILE_NAME = "tmp.jpg";

    private ITesseract tesseract;
    private String srcFile;
    private String tmpFile;
    private String ocrResult;

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
        return this;
    }

    //TODO: do not see any benefit for OCR, it makes recognition even worse
    // according to documentation tesseract does threshold implicitly
    public ReceiptOCR threshold(int threshold) {
        LOG.info("===> Threshold processing started");
        Mat currentMat = Imgcodecs.imread(srcFile, Imgcodecs.IMREAD_GRAYSCALE);
        Mat dstMat = new Mat(currentMat.rows(), currentMat.cols(), currentMat.type());
        Imgproc.threshold(currentMat, dstMat, threshold, 255, Imgproc.THRESH_OTSU);
        tmpFile = OUTPUT_FOLDER + TMP_FILE_NAME;
        Imgcodecs.imwrite(tmpFile, dstMat);
        LOG.info("<=== Threshold processing finished");
        return this;
    }

    public ReceiptOCR blur(){
        LOG.info("===> Bluring started");
        Mat currentMat = Imgcodecs.imread(srcFile, Imgcodecs.IMREAD_COLOR);
        Mat dstMat = new Mat(currentMat.rows(), currentMat.cols(), currentMat.type());
        Imgproc.bilateralFilter(currentMat, dstMat,  25, 25 * 2, 25/2);
        tmpFile = OUTPUT_FOLDER + TMP_FILE_NAME;
        Imgcodecs.imwrite(tmpFile, dstMat);
        LOG.info("<=== Bluring finished");
        return this;
    }

    public ReceiptOCR ocr() {
        try {
            LOG.info("===> OCR started");
            File outputFolder = new File(OUTPUT_FOLDER);
            if (!(outputFolder.exists() && outputFolder.isDirectory())) {
                outputFolder.mkdir();
            }
            tesseract.setPageSegMode(4); //Assume a single column of text of variable sizes.
            tesseract.setOcrEngineMode(1); // Neural nets LSTM engine only.
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
