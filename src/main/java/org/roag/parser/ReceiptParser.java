package org.roag.parser;

import org.json.simple.JSONObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.*;

public class ReceiptParser {

    String s = """
            'TeV ilson’s\\n
            Moore Wilsons Wellington\\n
            Cnr Lorne, Coliege & Tory Streets\\n
            Te Aro\\n
            Wie Tington\\n
            04 384 9905\\n
            \\n
            ih\\n\\n
            Receipe 2 Uuy\\n
            Date 3/08/2024 BS awiane, 13\\n
            Loyaity Card Number:\\n\\n
            | Beker Bovs Afchan Biscuits 700g\\n\\n
            ETA 5) = 6.25\\n
            2 B/Boys Spi icot Yoghurt Biscuit 6505\\n
            | @ £6.25 $6.25\\n
            3 Genoese Gruund PR sta 360g\\n
            1@ $16.50 = $16.50\\n
            4 Camil] Dar’ we 750m]\\n
            10 SAiae = $2.95\\n
            r M/Wilse ied Mango Slices 500g\\n
            18 Oy O5 = $10.95\\n
            Ceres Bio Gherkins No Sugar 670g\\n
            1@ $8.95 = $3.95\\n
            tal oe fore G5| $45.05\\n
            SeIBE ax $6. /6\\n
            Total ine GST $51.85\\n
            Debit $51.8\\n\\n'
            """;
    private static Logger LOG = LogManager.getLogger(ReceiptParser.class);

    private String originalText;
    private String processedText;
    private Map<String, String> resultMap;

    public ReceiptParser(){
    }

    public ReceiptParser withText(String text) {
        this.originalText = text;
        this.processedText = text;
        this.resultMap = new LinkedHashMap<>();
        return this;
    }

    public String getOriginalText() {
        return this.originalText;
    }

    /**
     * The first phase of the parsing where we remove noise symbols that can be presented as side-effects of OCR
     * @return
     */
    public ReceiptParser removeNoiseSymbols() {
        LOG.info("===> Noise removing started");
        var stringBuilder = new StringBuilder();
        processedText
                .lines()
                .filter(line -> !line.isBlank())
                .forEach(
                        line -> stringBuilder
                                .append(line.replaceAll("[:`'=+;\"|,()*\\[\\]^$]", "").strip())
                                .append("\n")
                );
        processedText = stringBuilder.toString();
        LOG.info("  ===> Result after removing noise:\n {}", processedText);
        LOG.info("<=== Noise removing finished");
        return this;
    }

    /**
     * The second phase of the parsing where we identify items in the receipt that are printed in two lines
     * @return
     */
    public ReceiptParser processMultilines() {
        LOG.info("===> Multiline items processing started");
        var lines = processedText.lines().toList();
        var modifiedLines = new ArrayList<String>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            //looking for lines like:
            //    0ty 2@ $7.69 each 19.38
            if (line.matches("[\\w\\s]*\\d+\\s*@\\s*\\$?\\d+\\.\\d{2}.*")) {
                var newLine = lines.get(i - 1) + " " +line;
                LOG.info("Found multiline: \n {}", newLine);
                modifiedLines.set(modifiedLines.size() - 1, newLine);
            } else {
                modifiedLines.add(line);
            }
        }
        var result = String.join("\n", modifiedLines);
        LOG.info("  ===> Result text after processing multilines: \n {}",result);
        processedText = result;
        LOG.info("<=== Multiline items processing finished");
        return this;
    }


    /**
     * Final phase of the parsing where we extract items and prices from the receipt and put them into Map.
     * @return
     */
    public ReceiptParser parseAndConvertToTable() {
        LOG.info("===> Parsing and converting started");
        var lines = processedText
                .lines()
                //looking for lines like:
                //  TRICKETTS GROVE WALNUTS IN SHELL 400G   8.99
                .filter(line -> line.matches("[\\w\\s\\./&@$]+?(\\d+\\.\\d{2}).*"))
                .toList();
        for (String line : lines) {
            var item = line.replaceFirst("(\\d+\\.\\d{2})$", "");
            var scanner = new Scanner(line);
            var price = scanner.findInLine("(\\d+\\.\\d{2})$");
            LOG.info("   " + item + " = " + price);
            resultMap.put(item, price);
        }
        LOG.info("<=== Parsing and converting finished");
        return this;
    }

    public Map<String, String> toMap() {
        return this.resultMap;
    }

    public String toJson() {
        return new JSONObject(resultMap).toJSONString();
    }
}
