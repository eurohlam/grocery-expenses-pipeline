package org.roag.pipeline;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public class MainCLI {

    public enum CLIArgument {
        FILE("-file"),
        REST_ENDPOINT("-restEndpoint"),
        STORE_NAME("-storeName");

        private final String name;

        CLIArgument(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static CLIArgument fromName(String name) {
            for (CLIArgument arg : CLIArgument.values()) {
                if (arg.toString().equals(name)) {
                    return arg;
                }
            }
            throw new IllegalArgumentException("Illegal name of argument: " + name);
        }
    }

    public static void main(String[] args) {

        System.out.println("args = " + Arrays.toString(args));
        Map<CLIArgument, String> arguments = new EnumMap<>(CLIArgument.class);

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-") && i + 1 < args.length) {
                var arg = CLIArgument.fromName(args[i]);
                arguments.put(arg, args[i + 1]);
                i++; // Skip the next argument since it's a value
            }
        }

        // Print parsed arguments
        if (arguments.isEmpty()) {
            System.out.println("No valid arguments provided.");
            return;
        } else if (!arguments.containsKey(CLIArgument.FILE)) {
            System.out.println("No input file provided. It must be provided as input parameter: \"-file path\"");
            return;
        } else {
            System.out.println("Parsed Arguments:");
            arguments.forEach((key, value) -> System.out.println(key + " = " + value));
        }

        //Running pipeline
        System.out.println("Running Optical Character Recognition (OCR) and parsing for file: " + arguments.get(CLIArgument.FILE));
        var pipeline = new ReceiptPipeline();
        var ocr = new Object() {
            String value = "";
        };
        var json = new Object() {
            String value = "";
        };
        pipeline
                .withFile(arguments.get(CLIArgument.FILE))
                .ocr(o -> ocr.value = o.blur().ocr().getOcrAsString())
                .parse(parser ->
                        json.value = parser
                                .withText(ocr.value)
                                .withStoreName(arguments.get(CLIArgument.STORE_NAME))
                                .removeNoiseSymbols()
                                .processMultilines()
                                .parseAndConvertToTable()
                                .toJson());

        if (arguments.containsKey(CLIArgument.REST_ENDPOINT)) {
            System.out.println("Sending result of recognition to endpoint: " + arguments.get(CLIArgument.REST_ENDPOINT));
            pipeline
                    .withEndpoint(arguments.get(CLIArgument.REST_ENDPOINT))
                    .send(json.value);
        }
    }
}
