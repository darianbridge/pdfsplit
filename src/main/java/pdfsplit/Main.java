package pdfsplit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.tools.ExtractText;

/**
 * Main class for PDF split utility.
 *
 * Splits any number of PDF files into separate plain text files, based on a capturing string to name the file and a delimiter string to split the file.
 *
 * arg 0 - The folder containing PDF files to read.
 * arg 1 - Regular Expression for the prefix of the capturing parameter used as the name of the split file to write.
 * arg 2 - Regular Expression for the delimiter to indicate the beginning of the next file to write.
 *
 * Dependency: org.apache.pdfbox:pdfbox-app:2.0.13
 */
public class Main
{
    /** Platform dependent line separator. */
    private static final String NEWLINE = System.getProperty("line.separator");

    /**
     * Main entry point for the PDF split utility.
     *
     * Search a given folder for files ending in ".pdf" and split them into various text files.
     *
     * @param args the argument parameters.
     * @throws Exception thrown if a problem occurs.
     */
    public static void main(final String[] args) throws Exception
    {
        // Find all the PDF files in a folder.
        File folder = new File(args.length > 0 ? args[0] : ".");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".pdf"));

        // Iterate through each of the PDF files.
        for (File file : files)
        {
            // Read the PDF file and write it out as plain text.
            extractText(file);

            // Split the text file.
            splitText(new File(file.getParentFile(), file.getName().replace(".pdf", ".txt")),
                args.length > 1 ? args[1] : "part [\\d]*. ",
                args.length > 2 ? args[2] : "(payment|salary)");
        }

        System.out.println("Done.");
    }

    /**
     * Extract text from a PDF file. Uses the PdfBox ExtractText utility.
     *
     * TODO Unsure if the PdfBox ExtractText utility will handle the PDF cells correctly. Need to test it out on a sample file.
     *
     * @param pdfFile the PDF file to extract text from.
     * @throws IOException thrown if a problem occurs.
     */
    public static void extractText(final File pdfFile) throws IOException
    {
        System.out.println("Reading: " + pdfFile.getName());
        ExtractText.main(new String[] {pdfFile.getPath()});
    }

    /**
     * Parse the text for lines to capture and delimiters to split.
     *
     * @param textFile the text file to parse.
     * @param capturePrefix the prefix of the capturing parameter used as the name of the split file to write.
     * @param delimiter the delimiter to indicate the beginning of the next file to write.
     * @throws Exception thrown if a problem occurs.
     */
    public static void splitText(final File textFile, final String capturePrefix, final String delimiter) throws Exception
    {
        // Open the text file for reading.
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), "UTF-8")))
        {
            // Compile the regular expressions.
            Pattern capturePattern = Pattern.compile("(?i)^" + capturePrefix + "(.*)$");
            Pattern delimiterPattern = Pattern.compile("(?i)^" + delimiter + "$");

            BufferedWriter writer = null;
            StringBuilder buffer = new StringBuilder();

            try
            {
                int i = 0;
                String capture = null;

                // Iterate through each line of the text file.
                for (String line; (line = br.readLine()) != null; )
                {
                    // Check if the line has a capture.
                    Matcher captureMatcher = capturePattern.matcher(line);
                    if (captureMatcher.find())
                    {
                        // Capture the name of the file.
                        capture = captureMatcher.group(1);
                        capture = capture.replace(' ', '-').toLowerCase();

                        i = 0;
                    }

                    // Check if the line has a delimiter.
                    Matcher delimiterMatcher = delimiterPattern.matcher(line);
                    if (delimiterMatcher.find())
                    {
                        // Write out the line before splitting to the next file.
                        writeLine(buffer, writer, line);

                        // Create the next file name.
                        File outputFile = new File(textFile.getParentFile(), capture + (i > 0 ? ("-" + i) : "") + ".txt");

                        if (i > 0)
                        {
                            System.out.println("Warning: mismatched delimiter [" + line.toLowerCase() + "] for capture [" + capture + "].");
                        }

                        // Close any previous file.
                        safeClose(writer);

                        // Open the next file for writing.
                        System.out.println("Writing: " + outputFile.getName());
                        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"));

                        // We are expecting one delimiter for each capture, so keep track of any mismatches.
                        i++;
                    }
                    else
                    {
                        writeLine(buffer, writer, line);
                    }
                }
            }
            finally
            {
                safeClose(writer);
            }
        }
    }

    /**
     * Writes a line to the writer. If the writer is not yet prepared, then the line will be written to the buffer instead.
     * If the buffer contains previously written lines, and a writer has been prepared, then the buffer will be emptied into the writer.
     *
     * @param buffer the buffer to write temporarily while there is no writer instantiated.
     * @param writer the writer to write the line to.
     * @param line the line to write.
     * @throws IOException thrown if a problem occurs.
     */
    private static void writeLine(final StringBuilder buffer, final BufferedWriter writer, final String line) throws IOException
    {
        if (writer == null)
        {
            buffer.append(line);
            buffer.append(NEWLINE);
        }
        else if (buffer.length() > 0)
        {
            writer.write(buffer.toString());
            buffer.setLength(0);

            writer.write(line);
            writer.newLine();
        }
        else
        {
            writer.write(line);
            writer.newLine();
        }
    }

    /**
     * Null check safe close.
     *
     * @param closeable the closeable to close.
     * @throws IOException thrown if a problem occurs.
     */
    private static void safeClose(final Closeable closeable) throws IOException
    {
        if (closeable != null)
        {
            closeable.close();
        }
    }
}

