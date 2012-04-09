package net.brandstaetter.sequenceprocessing;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import net.brandstaetter.sequenceprocessing.xmlconfig.Config;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.xml.sax.SAXException;

public class Main {
    
    /**
     * @param args
     */
    public static void main(final String[] args) {
        final File xmlfile = getXmlFileFromParameters(args);
        
        // sanity check
        if (xmlfile == null) {
            System.exit(-99);
        }
        
        Config config = null;
        try {
            config = parseXmlConfig(xmlfile);
        } catch (final IOException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "I/O Error reading the XML Config File",
                    e.getMessage());
            System.exit(20);
        } catch (final SAXException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Error parsing the XML Config File",
                    e.getMessage());
            System.exit(21);
        } catch (final JAXBException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Error processing the XML Config File",
                    e.getMessage());
            System.exit(22);
        }
        
        // start logging into log file too
        
        // sanity check
        if (config == null || config.getOutput() == null || config.getOutput().getName() == null) {
            System.exit(-98);
        }
        final File outputDirectory = new File(xmlfile.getParentFile(), config.getOutput().getName());
        if (!outputDirectory.isDirectory()) {
            outputDirectory.mkdirs();
        }
        Handler handle = null;
        try {
            handle = new FileHandler(new File(outputDirectory, config.getOutput().getName() + ".log").getAbsolutePath());
            handle.setFormatter(new SimpleFormatter());
            Logger.getLogger("").setLevel(Level.FINE);
            Logger.getLogger("").addHandler(handle);
        } catch (final IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (final SecurityException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        int retval = -100;
        try {
            final SequenceProcessingApplication spa = new SequenceProcessingApplication();
            retval = spa.run();
        } finally {
            if (handle != null) {
                Logger.getLogger(Main.class.getName()).log(Level.INFO, "Shutting down log file output.");
                handle.flush();
                Logger.getLogger("").removeHandler(handle);
                handle.close();
            }
        }
        System.exit(retval);
    }
    
    private static File getXmlFileFromParameters(final String[] args) {
        final CommandLineParser parser = new GnuParser();
        final Options options = defineParameters();
        File xmlfile = null;
        try {
            final CommandLine line = parser.parse(options, args);
            if (line.hasOption("xml")) {
                xmlfile = new File(line.getOptionValue("xml"));
                if (!xmlfile.isFile()) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE,
                            "Specified path is not a valid configuration file:");
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "      " + xmlfile.getAbsolutePath());
                    System.exit(11);
                }
            } else {
                final HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("SequenceProcessing", options);
                System.exit(10);
            }
        } catch (final ParseException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE,
                    "Parameter Parsing failed. Reason: " + e.getMessage());
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("SequenceProcessing", options);
            System.exit(12);
        }
        return xmlfile;
    }
    
    private static Options defineParameters() {
        final Options options = new Options();
        OptionBuilder.withArgName("filename");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("XML Settings file");
        final Option singleInput = OptionBuilder.create("xml");
        options.addOption(singleInput);
        return options;
    }
    
    private static Config unmarshal(@SuppressWarnings("rawtypes") final Class docClass, final File input)
            throws JAXBException {
        final String packageName = docClass.getPackage().getName();
        final JAXBContext jc = JAXBContext.newInstance(packageName);
        final Unmarshaller u = jc.createUnmarshaller();
        final Config doc = (Config) u.unmarshal(input);
        return doc;
    }
    
    private static boolean isInternetReachable() {
        try {
            // make a URL to a known source
            final URL url = new URL("https://github.com/brandstaetter/SequenceProcessing");
            
            // open a connection to that source
            final HttpURLConnection urlConnect = (HttpURLConnection) url.openConnection();
            
            // trying to retrieve data from the source. If there
            // is no connection, this line will fail
            @SuppressWarnings("unused")
            final Object objData = urlConnect.getContent();
            
        } catch (final UnknownHostException e) {
            Logger.getLogger(Main.class.getName()).log(Level.FINER, "Unknown host", e);
            return false;
        } catch (final IOException e) {
            Logger.getLogger(Main.class.getName()).log(Level.FINER, "I/O Exception", e);
            return false;
        }
        return true;
    }
    
    private static Config parseXmlConfig(final File xmlfile) throws IOException, SAXException, JAXBException {
        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema;
        if (isInternetReachable()) {
            final URL url = new URL(
                    "https://raw.github.com/brandstaetter/SequenceProcessing/master/src/main/resources/config.xsd");
            url.getContent();
            schema = factory.newSchema(url);
        } else {
            // web address unreachable, using fallback
            schema = factory.newSchema(new File("resources/config.xsd"));
        }
        final Validator validator = schema.newValidator();
        validator.validate(new StreamSource(xmlfile));
        final Config config = unmarshal(Config.class, xmlfile);
        return config;
    }
}
