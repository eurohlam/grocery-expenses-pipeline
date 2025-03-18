package org.roag.parser;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.*;

public class ReceiptParser {

    private static Logger LOG = LogManager.getLogger(ReceiptParser.class);

    private String storeName = "Undefined";
    private String originalText;
    private String processedText;
    private Map<String, String> resultMap;

    public ReceiptParser() {
    }

    public ReceiptParser withText(String text) {
        this.originalText = text;
        this.processedText = text;
        this.resultMap = new LinkedHashMap<>();
        return this;
    }

    public ReceiptParser withStoreName(String storeName) {
        if (StringUtils.isNotEmpty(storeName)) {
            this.storeName = storeName;
        }
        return this;
    }

    public String getOriginalText() {
        return this.originalText;
    }

    /**
     * The first phase of the parsing where we remove noise symbols that can be presented as side-effects of OCR
     *
     * @return
     */
    public ReceiptParser removeNoiseSymbols() {
        LOG.info("===> Noise removing started");
        var stringBuilder = new StringBuilder();
        processedText
                .lines()
                .filter(line -> !line.isBlank())
                .forEach(
                        line -> {
                            var cleanLine = line.replaceAll("[:`'=+;\"“|,()*\\[\\]^$]", "").strip();
                            cleanLine = cleanLine.replaceFirst("^[~]", "").strip();
                            stringBuilder
                                    .append(cleanLine)
                                    .append("\n");
                        }
                );
        processedText = stringBuilder.toString();
        LOG.info("  ===> Result after removing noise:\n {}", processedText);
        LOG.info("<=== Noise removing finished");
        return this;
    }

    /**
     * The second phase of the parsing where we identify items in the receipt that are printed in two lines
     *
     * @return
     */
    public ReceiptParser processMultilines() {
        LOG.info("===> Multiline items processing started");
        var lines = processedText.lines().toList();
        var modifiedLines = new ArrayList<String>(lines.size());
        //TODO: this approach works only for pack'n'save
        //for countdown need to try inverted approach, like:
        // 1. search for line that does not have price
        // 2. look up for the next line
        // 2.1 if next line has price then merge lines and got to line (i+2)
        // 2.2 otherwise ignore the line
        var i = 0;
        while (i < lines.size()) {
            // looking for line that does not end with price and check the next line
            if (!lines.get(i).matches("^.+(\\d+\\.\\d{2})$")
                    && ((lines.size() > (i + 1)) && (lines.get(i + 1).matches("^.+(\\d+\\.\\d{2})$")))) {
                    var newLine = lines.get(i) + " " + lines.get(i + 1);
                    LOG.info("Found multiline: \n {}", newLine);
                    modifiedLines.add(newLine);
                    i++;
            } else {
                modifiedLines.add(lines.get(i));
            }
            i++;
        }

        /*for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            //looking for lines like:
            //    0ty 2@ $7.69 each 19.38
            if (line.matches("[\\w\\s]*\\d+\\s*@\\s*\\$?\\d+\\.\\d{2}.*")) {
                var newLine = lines.get(i - 1) + " " + line;
                LOG.info("Found multiline: \n {}", newLine);
                modifiedLines.set(modifiedLines.size() - 1, newLine);
            } else {
                modifiedLines.add(line);
            }
        }*/
        var result = String.join("\n", modifiedLines);
        LOG.info("  ===> Result text after processing multilines: \n {}", result);
        processedText = result;
        LOG.info("<=== Multiline items processing finished");
        return this;
    }


    /**
     * Final phase of the parsing where we extract items and prices from the receipt and put them into Map.
     *
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
        var jsonMap = new Hashtable<>();
        jsonMap.put("storeName", storeName);
        var jsonItems = new ArrayList<>();
        resultMap.forEach((k, v) -> {
            var item = new Hashtable<>();
            item.put("item", k);
            item.put("price", v != null ? v : 0);
            jsonItems.add(item);
        });
        jsonMap.put("items", jsonItems);
        return JSONObject.toJSONString(jsonMap);
    }
}
