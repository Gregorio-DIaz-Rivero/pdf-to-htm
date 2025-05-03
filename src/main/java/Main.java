import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {
            // Ruta específica donde se encuentra el PDF
            String pdfPath = "C:\\Users\\goyo\\OneDrive\\Escritorio\\latex\\Unicode\\Turn txt into html\\pdf-to-htm\\Guia7deLenguajes.pdf";
            String htmlPath = "C:\\Users\\goyo\\OneDrive\\Escritorio\\latex\\Unicode\\Turn txt into html\\pdf-to-htm\\Guia7 de Lenguajes.html";

            // Verificar si el archivo existe
            File pdfFile = new File(pdfPath);
            if (!pdfFile.exists()) {
                System.err.println("Error: El archivo PDF no existe en: " + pdfPath);
                System.err.println("Por favor, verifica que la ruta sea correcta y que el archivo exista.");
                return;
            }

            System.out.println("Iniciando conversión...");
            System.out.println("PDF ubicado en: " + pdfPath);

            PdfToHtmlParser.convertPdfToHtml(pdfPath, htmlPath);
            System.out.println("Conversión completada exitosamente.");
            System.out.println("HTML generado en: " + htmlPath);
        } catch (IOException e) {
            System.err.println("Error durante la conversión: " + e.getMessage());
            e.printStackTrace();
        }
    }
}