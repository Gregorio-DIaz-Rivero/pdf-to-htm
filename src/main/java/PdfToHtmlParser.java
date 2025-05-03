import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;

public class PdfToHtmlParser {
    private static class LineWithSize {
        String text;
        float averageSize;

        LineWithSize(String text, float averageSize) {
            this.text = text;
            this.averageSize = averageSize;
        }
    }

    private static class CustomPDFTextStripper extends PDFTextStripper {
        private final List<LineWithSize> linesWithSize = new ArrayList<>();
        private final Map<Float, List<TextPosition>> linesMap = new TreeMap<>();

        public CustomPDFTextStripper() throws IOException {
            super();
            this.setLineSeparator("\n");
            this.setWordSeparator(" ");
            this.setDropThreshold(2.0f);
        }

        public List<LineWithSize> getLinesWithSize() {
            return linesWithSize;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            for (TextPosition tp : textPositions) {
                float y = Math.round(tp.getY());
                linesMap.computeIfAbsent(y, k -> new ArrayList<>()).add(tp);
            }
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            for (Map.Entry<Float, List<TextPosition>> entry : linesMap.entrySet()) {
                List<TextPosition> linePositions = entry.getValue();
                linePositions.sort(Comparator.comparing(TextPosition::getX));

                StringBuilder sb = new StringBuilder();
                float sizeSum = 0;
                float lastX = -1;
                float spaceThreshold = 3.0f; // Umbral para determinar si agregar un espacio

                for (TextPosition tp : linePositions) {
                    if (lastX != -1 && (tp.getX() - lastX) > spaceThreshold) {
                        sb.append(" ");
                    }
                    sb.append(tp.getUnicode());
                    sizeSum += tp.getFontSizeInPt();
                    lastX = tp.getX() + tp.getWidth();
                }

                float avgSize = sizeSum / linePositions.size();
                linesWithSize.add(new LineWithSize(sb.toString().trim(), avgSize));
            }
            linesMap.clear();
        }
    }

    public static void convertPdfToHtml(String pdfPath, String htmlPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            CustomPDFTextStripper stripper = new CustomPDFTextStripper();
            stripper.setSortByPosition(true);

            stripper.getText(document);

            List<LineWithSize> lines = stripper.getLinesWithSize();
            double globalAvg = lines.stream().mapToDouble(l -> l.averageSize).average().orElse(12.0);
            double threshold = globalAvg * 1.2;

            String title = new File(pdfPath).getName().replace(".pdf", "");

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html lang=\"es\" xmlns=\"http://www.w3.org/1999/html\">
                <head>
                    <title>%s</title>
                    <meta charset=\"UTF-8\">
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            line-height: 1.6;
                            max-width: 1200px;
                            margin: 0 auto;
                            padding: 20px;
                        }
                        h1, h2 {
                            color: #333;
                        }
                        h1 {
                            text-align: center;
                        }
                        .date {
                            text-align: center;
                            color: #666;
                            margin-bottom: 30px;
                        }
                        .nota {
                            background-color: #f9f9f9;
                            padding: 15px;
                            border-left: 4px solid #666;
                            margin: 20px 0;
                        }
                        .ejercicio {
                            margin: 20px 0;
                            padding: 15px;
                            border: 1px solid #ddd;
                            background-color: #f5f5f5;
                        }
                        .ejemplo {
                            margin: 20px 0;
                            padding: 15px;
                            background-color: #e9f7ef;
                        }
                        .formula {
                            font-family: \"Times New Roman\", Times, serif;
                            padding: 10px 0;
                            text-indent: 20px;
                        }
                        .definición {
                            background-color: #f0f7ff;
                            padding: 15px;
                            margin: 20px 0;
                            border-left: 4px solid #0066cc;
                        }
                        .lema {
                            background-color: #fff3e0;
                            padding: 15px;
                            margin: 20px 0;
                            border-left: 4px solid #ff9800;
                        }
                        .proposición {
                            background-color: #e8f4fd;
                            padding: 15px;
                            margin: 20px 0;
                            border-left: 4px solid #2196F3;
                            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                        }
                        .demostración {
                            background-color: #f5f0ff;
                            padding: 15px;
                            margin: 20px 0;
                            border-left: 4px solid #6200ea;
                            font-family: \"Times New Roman\", Times, serif;
                        }
                        .teorema {
                            background-color: #ffcdd2;
                            padding: 15px;
                            margin: 20px 0;
                            border-left: 4px solid #f44336;
                        }
                        .proof {
                            background-color: #e0f2f1;
                            padding: 15px;
                            margin: 20px 0;
                            border-left: 4px solid #4caf50;
                            font-style: italic;
                        }
                    </style>
                </head>
                <body>
                    <h1>%s</h1>
                    <div class=\"date\">%s</div>
                    <div class=\"content\">
                        %s
                    </div>
                </body>
                </html>
                """,
                    title,
                    title,
                    LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", new Locale("es", "ES"))),
                    formatContent(lines, threshold)
            );

            try (FileWriter writer = new FileWriter(htmlPath)) {
                writer.write(htmlContent);
            }
        }
    }

    private static String formatContent(List<LineWithSize> lines, double threshold) {
        StringBuilder formattedContent = new StringBuilder();

        for (LineWithSize line : lines) {
            String text = line.text;
            float size = line.averageSize;
            if (text.isEmpty()) continue;

            if (size > threshold) {
                formattedContent.append("<h2>").append(text).append("</h2>\n");
            } else if (text.toLowerCase().startsWith("ejercicio")) {
                formattedContent.append("<div class=\"ejercicio\">").append(text).append("</div>\n");
            } else if (text.toLowerCase().startsWith("ejemplo")) {
                formattedContent.append("<div class=\"ejemplo\">").append(text).append("</div>\n");
            } else if (text.toLowerCase().startsWith("lemma")) {
                formattedContent.append("<div class=\"lema\">").append(text).append("</div>\n");
            } else if (text.toLowerCase().startsWith("nota:")) {
                formattedContent.append("<div class=\"nota\">").append(text).append("</div>\n");
            } else if (text.toLowerCase().startsWith("definición")) {
                formattedContent.append("<div class=\"definición\">").append(text).append("</div>\n");
            } else if (text.toLowerCase().startsWith("proposition")) {
                formattedContent.append("<div class=\"proposición\">").append(text).append("</div>\n");
            } else if (text.toLowerCase().startsWith("teorema")) {
                formattedContent.append("<div class=\"teorema\">").append(text).append("</div>\n");
            } else if (text.toLowerCase().startsWith("demostración")) {
                formattedContent.append("<div class=\"proof\">").append(text).append("</div>\n");
            } else if (text.matches(".*[∑∫∏√].*")) {
                formattedContent.append("<div class=\"formula\">").append(text).append("</div>\n");
            } else {
                formattedContent.append("<p>").append(text).append("</p>\n");
            }
        }

        return formattedContent.toString();
    }
}