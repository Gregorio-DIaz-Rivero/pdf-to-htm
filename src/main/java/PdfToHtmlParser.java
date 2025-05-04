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
        float yPosition;  // Añadimos la posición Y

        LineWithSize(String text, float averageSize, float yPosition) {
            this.text = text;
            this.averageSize = averageSize;
            this.yPosition = yPosition;
        }
    }

    private static class CustomPDFTextStripper extends PDFTextStripper {

        private final List<LineWithSize> linesWithSize = new ArrayList<>();
        private final Map<Float, List<TextPosition>> linesMap = new TreeMap<>();
        private static final float Y_TOLERANCE = 5.0f;  // Tolerancia para considerar elementos en la misma línea
        private static final float PAGE_NUMBER_THRESHOLD = 0.9f; // 90% de la altura de la página

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
            // Modificamos la tolerancia vertical para caracteres especiales
            float VERTICAL_TOLERANCE = 5.0f; // Aumentamos la tolerancia para superíndices
            float lastY = -1;
            float baseY = -1;

            for (TextPosition tp : textPositions) {
                float currentY = tp.getY();

                // Si es el primer carácter o está dentro de la tolerancia vertical
                if (baseY == -1) {
                    baseY = currentY;
                }

                // Si el carácter está cerca verticalmente del texto base, lo consideramos parte de la misma línea
                if (Math.abs(currentY - baseY) <= VERTICAL_TOLERANCE) {
                    float y = baseY; // Usamos la posición Y base para mantener la consistencia
                    linesMap.computeIfAbsent(y, k -> new ArrayList<>()).add(tp);
                } else {
                    linesMap.computeIfAbsent(currentY, k -> new ArrayList<>()).add(tp);
                    baseY = currentY;
                }

                lastY = currentY;
            }
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            float pageHeight = page.getMediaBox().getHeight();

            List<List<TextPosition>> lineGroups = new ArrayList<>();
            List<TextPosition> currentGroup = null;
            float lastY = -1;

            // Primero, agrupamos los elementos por proximidad vertical
            for (Map.Entry<Float, List<TextPosition>> entry : linesMap.entrySet()) {
                List<TextPosition> linePositions = entry.getValue();
                linePositions.sort(Comparator.comparing(TextPosition::getX));

                for (TextPosition tp : linePositions) {
                    float currentY = tp.getY();

                    // Ignorar números de página
                    if (isPageNumber(tp, pageHeight)) {
                        continue;
                    }

                    if (currentGroup == null || Math.abs(currentY - lastY) > Y_TOLERANCE) {
                        currentGroup = new ArrayList<>();
                        lineGroups.add(currentGroup);
                    }

                    currentGroup.add(tp);
                    lastY = currentY;
                }
            }

            // Procesamos cada grupo como una línea
            for (List<TextPosition> group : lineGroups) {
                StringBuilder sb = new StringBuilder();
                float maxSize = 0;
                float averageY = 0;
                float lastX = -1;
                float spaceThreshold = 3.0f;

                // Encontrar el tamaño máximo de fuente en el grupo
                for (TextPosition tp : group) {
                    maxSize = Math.max(maxSize, tp.getFontSizeInPt());
                    averageY += tp.getY();
                }
                averageY /= group.size();

                // Construir el texto
                for (TextPosition tp : group) {
                    if (lastX != -1 && (tp.getX() - lastX) > spaceThreshold) {
                        sb.append(" ");
                    }
                    sb.append(tp.getUnicode());
                    lastX = tp.getX() + tp.getWidth();
                }

                String finalText = sb.toString().trim();
                if (!finalText.matches("^\\d{1,3}$")) { // Solo si no es un número simple (1-3 dígitos)
                    linesWithSize.add(new LineWithSize(finalText, maxSize, averageY));
                }
            }

            linesMap.clear();
        }

        private boolean isPageNumber(TextPosition tp, float pageHeight) {
            // Verificar si está en la parte inferior de la página
            boolean isAtBottom = tp.getY() > (pageHeight * PAGE_NUMBER_THRESHOLD);
        
            // Verificar si es un número
            String text = tp.getUnicode().trim();
            boolean isNumber = text.matches("\\d+");
        
            // Verificar si está centrado o en los extremos
            float pageWidth = tp.getPageWidth();
            boolean isPositionedAsPageNumber = 
                tp.getX() < (pageWidth * 0.2) ||     // Cerca del margen izquierdo
                tp.getX() > (pageWidth * 0.8) ||     // Cerca del margen derecho
                Math.abs(tp.getX() - (pageWidth/2)) < 20; // Centrado
        
            return isAtBottom && isNumber && isPositionedAsPageNumber;
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
                <html lang="es" xmlns="http://www.w3.org/1999/html">
                <head>
                    <title>%s</title>
                    <meta charset="UTF-8">
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
                            font-family: "Times New Roman", Times, serif;
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
                            font-family: "Times New Roman", Times, serif;
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
                        sup {
                            vertical-align: super;
                            font-size: smaller;
                            line-height: 0;
                        }
                    </style>
                </head>
                <body>
                    <h1>%s</h1>
                    <div class="date">%s</div>
                    <div class="content">
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

    // En el método formatContent, ajustamos la lógica para considerar la continuidad del texto
    private static String formatContent(List<LineWithSize> lines, double threshold) {
        StringBuilder formattedContent = new StringBuilder();
        StringBuilder currentTitle = new StringBuilder();
        boolean inTitle = false;
        float lastY = -1;
        float lastSize = -1;
        float Y_TOLERANCE = 2.0f;

        for (int i = 0; i < lines.size(); i++) {
            LineWithSize line = lines.get(i);
            String text = line.text;
            float size = line.averageSize;

            if (text.isEmpty()) continue;

            if (size > threshold) {
                if (!inTitle) {
                    currentTitle = new StringBuilder(text);
                    inTitle = true;
                } else if (Math.abs(line.yPosition - lastY) <= Y_TOLERANCE) {
                    currentTitle.append(" ").append(text);
                } else if (line.yPosition < lastY && text.length() <= 5 && size < lastSize) {
                    // Si es más alto (menor y) y pequeño, probablemente un exponente
                    currentTitle.append("<sup>").append(text).append("</sup>");
                } else {
                    // Nuevo título
                    formattedContent.append("<h2>").append(currentTitle).append("</h2>\n");
                    currentTitle = new StringBuilder(text);
                }
                lastY = line.yPosition;
                lastSize = size;
            } else {
                if (inTitle && !currentTitle.isEmpty()) {
                    formattedContent.append("<h2>").append(currentTitle).append("</h2>\n");
                    currentTitle = new StringBuilder();
                    inTitle = false;
                }

                if (text.toLowerCase().startsWith("ejercicio")) {
                    formattedContent.append("<div class=\"ejercicio\">").append(text).append("</div>\n");
                } else {
                    formattedContent.append("<p>").append(text).append("</p>\n");
                }
            }
        }

        if (inTitle && !currentTitle.isEmpty()) {
            formattedContent.append("<h2>").append(currentTitle).append("</h2>\n");
        }

        return formattedContent.toString();
    }
}