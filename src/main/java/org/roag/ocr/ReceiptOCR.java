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

    private final String tesseractResultSample = """
            PAKNSAVE | |
            kk PAK NSAVE KILBIRNIE sws
            /6 RONGOTAI ROAD
            GALT'S SUPERMARKET LTD
            | PH: <04) 801-5068
            Order online and collect from the store 5
            Go to paknsave.co.nz/shop ,
            : KIA KAHA. STRESSED OR OVERWHELMED?
            CALL OR TEXT 1737 FOR FREE KORERO
            JANOLA BLEACH REGULAR 2.5L $5.89 ,
            NZ LEMON FISH (RIG) FILLETS , $30.27 |
            (0 HERRING MATIAS FILLETS 250G , |
            2 @ $7.29 $14.58
            BANANAS $3.62
            . MUSHROOMS LARGE PORTABELLO 200G $3.99
            TOMATOES RED LOOSE $4.62 ‘
            TRICKETTS GROVE WALNUTS IN SHELL 400G $8.99
            PAMS FROZEN HASH BROWN PATTIES 1KG $5.99 '
            GOPALA YOGHURT FULL CREAM 2KG $8.29 |
            HEADOW FRESH SOUR CREAM LITE 230G $3.49
            PERFECT ITALIAND RICOTTA ORIGINAL 250G $5.99 |
            VALUE SQUEEZABLE MOP REFILL $4.89
            PETITE APRICOT STICKS
            ; 2 @ $6.49 $12.98 b
            EMBORG GOUDA SLICED CHEESE 150G $5.59
            16 BALANCE LUE $119.18 3
            EFTPOS $119.18 |
            ssssxesssxss3905 ' |
            - Auth Code = 325666 |
            SUB TOTAL $103.63 ‘
            TOTAL GST $15.55 |
            TOTAL ' $119.18
            CHANGE | $0.00
            """;

    private static Logger LOG = LogManager.getLogger(ReceiptOCR.class);

    public static final String DEFAULT_TESSDATA_PATH = "/opt/local/share/tessdata/";
    public static final String OUTPUT_FOLDER = "output/";

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
        tmpFile = OUTPUT_FOLDER + "test.jpg";
        Imgcodecs.imwrite(tmpFile, dstMat);
        LOG.info("<=== Threshold processing finished");
        return this;
    }

    public ReceiptOCR blur(){
        LOG.info("===> Bluring started");
        Mat currentMat = Imgcodecs.imread(srcFile, Imgcodecs.IMREAD_COLOR);
        Mat dstMat = new Mat(currentMat.rows(), currentMat.cols(), currentMat.type());
        Imgproc.bilateralFilter(currentMat, dstMat,  25, 25 * 2, 25/2);
        tmpFile = OUTPUT_FOLDER + "test.jpg";
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
