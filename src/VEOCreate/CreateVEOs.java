/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 */
package VEOCreate;

import VERSCommon.VERSDate;
import VERSCommon.PFXUser;
import VERSCommon.VEOFatal;
import VERSCommon.VEOError;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;

/**
 * This class creates multiple VEOs from a text control file. The control file
 * is a text file containing multiple rows of tab separated commands. Each
 * command builds a part of a VEO (or controls how subsequent VEOs are to be
 * built). (Note that the class CreateVEO is an alternative way to build a VEO,
 * providing an API to programatically build a VEO.)
 * <h3>Command Line arguments</h3>
 * The following command line arguments must be supplied:
 * <ul>
 * <li><b>-c &lt;file&gt;</b> the control file which controls the production of
 * VEOs (see the next section for details about the control file).</li>
 * <li><b>-sf &lt;directory&gt;</b> the directory in which the neoVEO support
 * files are found.</li>
 * </ul>
 * <p>
 * The following command line arguments are optional:
 * <ul>
 * <li><b>-s &lt;PFXfile&gt; &lt;password&gt;</b> a PFX file containing details
 * about the signer (particularly the private key) and the password. The PFX
 * file can also be specified in the control file. If no -s command line
 * argument is present, the PFX file must be specified in the control file.
 * <li><b>-o &lt;outputDir&gt;</b> the directory in which the VEOs are to be
 * created. If not present, the VEOs will be created in the current
 * directory.</li>
 * <li><b>-t &lt;directory&gt;</b> the directory in which the metadata templates
 * will be found. This must be specified if you use the MP or MPC commands to
 * generate metadata packages. See the last section of this page for details
 * about the metadata templates.</li>
 * <li><b>-v</b> verbose output. By default off.</li>
 * <li><b>-d</b> debug mode. In this mode more logging will be generated, and
 * the VEO directories will not be deleted after the ZIP file is created. By
 * default off.</li>
 * <li><b>-ha &lt;algorithm&gt;</b> The hash algorithm used to protect the
 * content files and create signatures. Any of the standard Java MessageDigest
 * algorithm strings may be used. The default is 'SHA-256'. The hash algorithm
 * can also be set in the control file.
 * <li><b>-e &lt;encoding&gt;</b> If present this specifies the encoding used to
 * convert the control file into characters. The default is 'UTF-8'. Any of the
 * java.nio canonical names may be used.
 * </li>
 * </ul>
 * <p>
 * A minimal example of usage is<br>
 * <pre>
 *     createVEOs -c data.txt -t templates
 * </pre>
 * <h3>Control File</h3>
 * A control file is a text file with multiple lines. Each line contains tab
 * separated text. The first entry on each line is the command, subsequent
 * entries on the line are arguments to the command. The commands are:
 * <ul>
 * <li><b>'!'</b> A comment line. The remainder of the line is ignored.</li>
 * <li><b>'HASH' &lt;algorithm&gt;</b> Specifies the hash algorithm to use. Any
 * of the standard Java MessageDigest algorithm strings may be used. If present,
 * this overrides the -ha command line argument. It must appear at the start of
 * the control file, before the first 'BV' command.</li>
 * <li><b>'PFX' &lt;pfxFile&gt; &lt;password&gt;</b> Specifies a PFX file and
 * associated password. Multiple PFX lines may be present, this results in
 * multiple signatures being generated. PFX commands must occur before the first
 * BV command.</li>
 * <li><b>'BV' &lt;veoName&gt;</b> Begin a new VEO. The single argument is the
 * VEO name (i.e. the file name of the VEO to be generated). If a VEO is already
 * being constructed, a new BV command will complete the generation of the
 * current VEO and start the construction of a new VEO.</li>
 * <li><b>'IO' &lt;type&gt; [&lt;level&gt;]</b> Begin a new Information Object
 * within a VEO. The Information Object will have the specified type (which may
 * be blank) and level. If the level is not present, it will be set to 0. If an
 * Information Object is already being constructed, a new IO command will finish
 * the current Information Object and start a new one.</li>
 * <li><b>'MP' &lt;template&gt; [&lt;subs&gt;...]</b> Begin a new Metadata
 * Package within an Information Object. The first argument is the template
 * name, subsequent arguments are the substitutions. An MP command may be
 * followed by MPC commands (to construct a metadata package from several
 * templates) or ME, SME, &amp; EME commands (to add XML elements one at a
 * time). The current Metadata Package will be finished when an MP, XML-MP,
 * RDF-MP, AGLS-MP, ANZS5478-MP, IP, or IO command is encountered. All but the
 * last two (IP or IO) will start the creation of a new Metadata Package.</li>
 * <li><b>'XML-MP' &lt;semanticId&gt;</b> Begin a new generic XML Metadata
 * Package with the given semanticId (a URL). The syntaxId will be set to XML.
 * The contents of this metadata package can be added using the ME, SME, EME,
 * and MPC commands. The current Metadata Package will be finished when an MP,
 * XML-MP, RDF-MP, AGLS-MP, ANZS5478-MP, IP, or IO command is encountered. All
 * but the last two (IP or IO) will start the creation of a new Metadata
 * Package.</li>
 * <li><b>'RDF-MP' &lt;semanticId&gt; &lt;resourceId&gt;
 * [&lt;namespaces&gt;]</b>
 * Begin a new generic RDF Metadata Package with the given semanticId (a URL)
 * (the syntaxId on the metadata package is automatically set to indicate RDF).
 * The resourceId (a URI) is put in the rdf:about attribute of the
 * rdf:Description element. The optional namespaces are namespace definitions
 * (XML attributes) inserted in the start rdf:rdf tag. The contents of this
 * metadata package can be added using the ME, SME, EME, and MPC commands. The
 * current Metadata Package will be finished when an MP, XML-MP, RDF-MP,
 * AGLS-MP, ANZS5478-MP, IP, or IO command is encountered. All but the last two
 * (IP or IO) will start the creation of a new Metadata Package.</li>
 * <li><b>'AGLS-MP' &lt;resourceId&gt;</b> Begin a new AGLS Metadata Package
 * (The syntaxId will be set to RDF and the semanticID to AGLS). The resourceId
 * (a URI) is put in the rdf:about attribute of the rdf:Description element. The
 * AGLS contents of this metadata package can be added using the ME, SME, EME,
 * and MPC commands. The current Metadata Package will be finished when an MP,
 * XML-MP, RDF-MP, AGLS-MP, ANZS5478-MP, IP, or IO command is encountered. All
 * but the last two (IP or IO) will start the creation of a new Metadata
 * Package.</li>
 * <li><b>'ANZS5478-MP' &lt;resourceId&gt;</b> Begin a new ANZS-5478 Metadata
 * Package (The syntaxId will be set to RDF and the semanticID to ANZS-5478).
 * The resourceId (a URI) is put in the rdf:about attribute of the
 * rdf:Description element. The ANZS-5478 contents of this metadata package can
 * be added using the ME, SME, EME, and MPC commands. The current Metadata
 * Package will be finished when an MP, XML-MP, RDF-MP, AGLS-MP, ANZS5478-MP,
 * IP, or IO command is encountered. All but the last two (IP or IO) will start
 * the creation of a new Metadata Package.</li>
 * <li><b>'ME' &lt;tag&gt; [&lt;value&gt;] [&lt;attributes&gt;]</b> Add a simple
 * XML element to a Metadata Package (started with an 'MP', 'XML-MP', 'RDF-MP',
 * 'AGLS-MP', or 'ANZS5478-MP' command). A simple XML element is one that does
 * not contain any subelements, just a string value. The tag is the XML element
 * name (including namespace, if any). The value is the value of the element (it
 * may be blank or null, in which case the element is empty). Any XML unsafe
 * characters in the value (e.g. '&lt;') are encoded. The attributes, if
 * present, is a string of attribute definitions that are added to the start
 * element tag. Typically, they are used to define the syntax of the value (e.g.
 * 'rdf:datatype="xsd:dateTime"'). Logically, the attributes should come between
 * the tag and the value, but we put the attributes at the end because they are
 * not always necessary. Any XML unsafe characters in the attribute string are
 * <b>NOT</b> encoded - this is up to the creator of the control file.</li>
 * <li><b>'SME' &lt;tag&gt; [&lt;attributes&gt;]</b> Start a complex XML element
 * to a Metadata Package (started with an 'MP', 'XML-MP', 'RDF-MP', 'AGLS-MP',
 * or 'ANZS5478-MP' command). A complex XML element is one that does
 * subelements. The tag is the XML element name (including namespace, if any).
 * The attributes, if present, is a string of attribute definitions that are
 * added to the start element tag. Typically, they are used to define the syntax
 * of the value (e.g. 'rdf:datatype="xsd:dateTime"'). Any XML unsafe characters
 * in the attribute string are <b>NOT</b> encoded - this is up to the creator of
 * the control file. The contents of the complex element (i.e. the subelements)
 * are added by 'ME' and 'SME' commands, and the complex element is finally
 * completed by a 'EME' command.</li>
 * <li><b>'EME' &lt;tag&gt;</b> End a complex XML element in a Metadata Package
 * (started with an 'SME' command). The tag is the XML element name (including
 * namespace, if any) of the matching SME command. Note that the proper nesting
 * of the XML is not checked by CreateVEOs; this is up to the creator of the
 * control file.</li>
 * <li><b>'MPC' &lt;template&gt; [&lt;subs&gt;...]</b> Continue a Metadata
 * Package using another template and substitutions. This may be used after any
 * of the start MetadataPackage commands (MP, XML-MP, RDF-MP, AGLS-MP, or
 * ANZS5478-MP), or element commands (ME, SME, EME).</li>
 * <li><b>'IP' [&lt;label&gt;] &lt;file&gt; [&lt;files&gt;...]</b> Add an
 * Information Piece to the Information Object. The first (optional) argument is
 * the label for the information piece, subsequent arguments are the content ` *
 * files to include in the Information Piece. An IP command must be after all
 * the MP and MPC commands in this Information Object.</li>
 * <li><b> 'E' &lt;date&gt; &lt;event&gt; &lt;initiator&gt;
 * [&lt;description&gt;...] ['$$' &lt;error&gt;...]</b> Add an event to the VEO
 * History file. The first argument is the date/time of the event, the second a
 * label for the type of event, the third the name of the initiator of the
 * event. Then there are a series of arguments describing the event, and finally
 * an option special argument ('$$') and a series of error messages. Events may
 * occur at any point within the construction of a VEO (i.e. after a BV
 * command).</li>
 * </ul>
 * <p>
 * A simple example of a control file is:<br>
 * <pre>
 * hash	SHA-1
 * pfx	signer.pfx	Password
 *
 * !	VEO with two IOs, one with just an MP, the other with an MP and an IPs
 * BV	testVEO5
 * AC	S-37-6
 * IO	Record	1
 * MP	agls	laserfish   data    data    etc
 * IP	Data	S-37-6/S-37-6-Nov.docx
 * IO	Data	2
 * ANZS5478-MP	file://CABF-13-590
 * ME	anzs5478:EntityType	Record	rdf:datatype="xs:string"
 * ME	anzs5478:Category	Item	rdf:datatype="xs:string"
 * SME	anzs5478:Identifier	rdf:parseType="Resource"
 * ME	anzs5478:IdentifierString	IdValue	rdf:datatype="xs:string"
 * ME	anzs5478:IdentifierScheme	IdScheme	rdf:datatype="xs:string"
 * EME	anzs5478:Identifier
 * SME	anzs5478:Name	rdf:parseType="Resource"
 * ME	anzs5478:NameWords	NameValue	rdf:datatype="xs:string"
 * ME	anzs5478:NameScheme	NameScheme	rdf:datatype="xs:string"
 * EME	anzs5478:Name
 * IP	Content	S-37-6/S-37-6-Nov.docx	S-37-6/S-37-6-Nov.docx
 * E	2014-09-09	Opened	Andrew	Description	$$  Error
 * E	2014-09-10	Closed	Andrew	Description
 * ! begin new VEO
 * BV...
 * </pre> Note that this shows the two methods of creating Metadata Packages
 * (using a template, and constructing the package element by element). Both
 * methods may be used together in constructing a single Metadata Package.
 * <h3>Metadata Templates</h3>
 * The template files are found in the directory specified by the -t command
 * line argument. Templates are used to generate the metadata packages. Each MP
 * or MPC command in the control file specifies a template name (e.g. 'agls').
 * An associated text template file (e.g. 'agls.txt') must exist in the template
 * directory.
 * <p>
 * The template files contains the <i>contents</i> of the metadata package. The
 * contents composed of XML text, which will be included explicitly in each VEO,
 * and substitutions. The start of each substitution is marked by '$$' and the
 * end by '$$'. Possible substitutions are:
 * <ul>
 * <li>
 * $$ date $$ - substitute the current date and time in VERS format</li>
 * <li>
 * $$ [column] &lt;x&gt; $$ - substitute the contents of column &lt;x&gt;. Note
 * that keyword 'column' is optional.</li>
 * </ul>
 * <p>
 * The MP/MPC commands in the control file contain the information used in the
 * column or file substitutions. Note that the command occupies column 1, and
 * the template name column 2. So real data starts at column 3.
 */
public class CreateVEOs {

    static String classname = "CreateVEOs"; // for reporting
    Path templateDir;       // directory that holds the templates
    Path supportDir;        // directory that holds the V3 support files (especially the VEOReadme.txt file)
    Path controlFile;       // control file to generate the VEOs
    Path baseDir;           // directory which to interpret the files in the control file
    Path outputDir;         // directory in which to place the VEOs
    List<PFXUser> signers;  // list of signers
    boolean chatty;         // true if report the start of each VEO
    boolean verbose;        // true if generate lots of detail
    boolean debug;          // true if debugging
    boolean help;           // true if printing a cheat list of command line options
    String hashAlg;         // hash algorithm to use
    String inputEncoding;   // how to translate the characters in the control file to UTF-16
    Templates templates;    // database of templates

    private static final String USAGE = "CreateVEOs [-v] [-d] -t <templateDir> -sf <supportDir> -c <controlFile> [-s <pfxFile> <password>]* [-o <outputDir>] [-ha <hashAlgorithm] [-e <encoding>]";

    // state of the VEOs being built
    private enum State {

        PREAMBLE, // No VEO has been generated
        VEO_STARTED, // VEO started, but Information Object has not
        VEO_FAILED // Construction of this VEO has failed, scan forward until new VEO is started
    }
    State state;      // the state of creation of the VEO

    // private final static Logger rootLog = Logger.getLogger("veocreate");
    private final static Logger LOG = Logger.getLogger("veocreate.CreateVEOs");

    /**
     * Report on version...
     *
     * <pre>
     * 201502   1.0 Initial release
     * 20170608 1.1 File references in control file can be absolute, or relative to either control file or current working directory
     * 20170825 1.2 Added -e &lt;str&gt; so that non ASCII control files are handled correctly
     * 20180625 1.3 Content files are now directly zipped from the source location, not copied or linked.
     * 20180718 1.4 Handles Windows style filenames in UNIX environment
     * 20200120 1.5 Bug fixes, general cleanup
     * 20200414 2.0 Packaged for release. Lots of minor alterations
     * 20200803 2.1 VEOReadme.txt location changed
     * 2020803  2.2 Bug fix
     * 20210407 2.3 Standardised reporting of run, added versions
     * 20210409 2.4 Uses new PFXUser function to report on file name
     * 20210709 2.5 Change Base64 handling routines & provided support for PISA
     * 20210716 2.6 Changes to fix Lint warning
     * 20220124 2.7 Changed to using Apache ZIP to be consistant with VEOAnalysis
     * 20220218 3.0 Added the ability to construct metadata packages element by element. Rewrite of documentation
     * 20220316 3.1 Fixed bug in addAbsContentFile() - not current directory
     * 20220520 3.2 Changed to catch invalid file names (e.g. Paths.get() & in resolve())
     * 20220718 3.3 Now handles IP labels that are valid filesystem names
     * 20240327 3.4 Now allows sign() to separately sign VEOContent and VEOHistory
     * 20240417 3.5 Updated the semantic ids of AGLS and ANZS5478
     * 20240417 3.6 Undeprecated registerContentDirectories(), addContentFile(String), & getActualSourcePath() in CreateVEO, as CreateVEOs uses them
     * 20240417 3.7 Moved SignVEOs to be a package within neoVEO
     * 20240515 3.8 Can now ZIP a VEO anywhere, and also finalise without ZIPping
     * 20240703 3.9 Moved to latest version of Netbeans resulting in correcting warnings
     * </pre>
     */
    static String version() {
        return ("3.9");
    }

    /**
     * Constructor. Processes the command line arguments to set program up, and
     * parses the metadata templates from the template directory.
     * <p>
     * The defaults are as follows. The templates are found in "./Templates".
     * Output is created in the current directory. The hash algorithm is
     * "SHA256".
     *
     * @param args command line arguments
     * @throws VEOFatal when cannot continue to generate any VEOs
     */
    public CreateVEOs(String[] args) throws VEOFatal {
        SimpleDateFormat sdf;
        TimeZone tz;
        int i;
        PFXUser pfxu;

        // sanity check
        if (args == null) {
            throw new VEOFatal(classname, 1, "Null command line argument");
        }

        // defaults...
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        LOG.setLevel(Level.WARNING);
        templateDir = null;
        supportDir = null;
        outputDir = Paths.get("."); // default is the current working directory
        controlFile = null;
        signers = new LinkedList<>();
        verbose = false;
        chatty = false;
        debug = false;
        help = false;
        hashAlg = "SHA-256";
        inputEncoding = "UTF-8";
        state = State.PREAMBLE;

        // process command line arguments
        configure(args);

        // tell what is happening
        System.out.println("******************************************************************************");
        System.out.println("*                                                                            *");
        System.out.println("*                 V E O ( V 3 )   C R E A T I O N   T O O L                  *");
        System.out.println("*                                                                            *");
        System.out.println("*                                Version " + version() + "                                *");
        System.out.println("*               Copyright 2015 Public Record Office Victoria                 *");
        System.out.println("*                                                                            *");
        System.out.println("******************************************************************************");
        System.out.println("");
        System.out.print("Run at ");
        tz = TimeZone.getTimeZone("GMT+10:00");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
        sdf.setTimeZone(tz);
        System.out.println(sdf.format(new Date()));
        System.out.println("");
        if (help) {
            // CreateVEOs [-vv] [-v] [-d] -t <templateDir> -sf <supportDir> -c <controlFile> [-s <pfxFile> <password>] [-o <outputDir>] [-ha <hashAlgorithm] [-e <encoding>]";
            System.out.println("Command line arguments:");
            System.out.println(" Mandatory:");
            System.out.println("  -c <file>: file path to the control file");
            System.out.println("  -sf <directory>: file path to where the support files are located");
            System.out.println("");
            System.out.println(" Optional:");
            System.out.println("  -s <pfxFile> <password>: path to a PFX file and its password for signing a VEO (can be repeated)");
            System.out.println("  -o <directory>: the directory in which the VEOs are created (default is current working directory)");
            System.out.println("  -t <directory>: file path to where the templates are located");
            System.out.println("  -ha <hashAlgorithm>: specifies the hash algorithm (default SHA-256)");
            System.out.println("  -e: character encoding for the control file (default UTF-8)");
            System.out.println("");
            System.out.println("  -v: verbose mode: give more details about processing");
            System.out.println("  -d: debug mode: give a lot of details about processing");
            System.out.println("  -help: print this listing");
            System.out.println("");
        }

        // check to see that user specified a support directory and control file
        if (supportDir == null) {
            throw new VEOFatal(classname, 4, "No support directory specified. Usage: " + USAGE);
        }
        if (controlFile == null) {
            throw new VEOFatal(classname, 5, "No control file specified. Usage: " + USAGE);
        }

        System.out.println("Configuration:");
        if (templateDir != null) {
            System.out.println(" Template directory: '" + templateDir.toString() + "'");
        } else {
            System.out.println(" Template directory is not set. Cannot use MP or MPC commands in control file");
        }
        System.out.println(" Support directory: '" + supportDir.toString() + "'");
        System.out.println(" Control file: '" + controlFile.toString() + "'");
        if (outputDir != null) {
            System.out.println(" Output directory: '" + outputDir.toString() + "'");
        }
        System.out.println(" Hash algorithm (specified on command line or the default): " + hashAlg);
        System.out.println(" Character encoding used for the control file: " + inputEncoding);
        if (!signers.isEmpty()) {
            System.out.println(" Signers specified on command line:");
            for (i = 0; i < signers.size(); i++) {
                pfxu = signers.get(i);
                System.out.println("  PFX file: '" + pfxu.getFileName() + "'");
            }
        } else {
            System.out.println(" No PFX files specified on command line for signing");
        }
        if (chatty) {
            System.out.println(" Report when each VEO is commenced & echo comments in the command file");
        }
        if (verbose) {
            System.out.println(" Verbose output is selected");
        }

        // read templates
        if (templateDir != null) {
            templates = new Templates(templateDir);
        } else {
            templates = null;
        }
    }

    /**
     * This method configures the VEO creator from the arguments on the command
     * line. See the general description of this class for the command line
     * arguments.
     *
     * @param args[] the command line arguments
     * @throws VEOFatal if any errors are found in the command line arguments
     */
    private void configure(String args[]) throws VEOFatal {
        int i;
        PFXUser user;   // details about user
        Path pfxFile;   // path of a PFX file
        String password;// password to PFX file

        // check for no arguments...
        if (args.length == 0) {
            throw new VEOFatal(classname, 10, "No arguments. Usage: " + USAGE);
        }

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {

                    // get template directory
                    case "-t":
                        i++;
                        templateDir = checkFile("template directory", args[i], true);
                        i++;
                        break;

                    // get supoort directory
                    case "-sf":
                        i++;
                        supportDir = checkFile("support directory", args[i], true);
                        i++;
                        break;

                    // get data file
                    case "-c":
                        i++;
                        controlFile = checkFile("control file", args[i], false);
                        try {
                            baseDir = controlFile.toRealPath().getParent();
                        } catch (IOException ioe) {
                            throw new VEOFatal(classname, "configure", 1, "Couldn't convert control file '" + args[i] + "' to a path:" + ioe.getMessage());
                        }
                        i++;
                        break;

                    // get pfx file
                    case "-s":
                        i++;
                        pfxFile = checkFile("PFX file", args[i], false);
                        i++;
                        password = args[i];
                        i++;
                        user = new PFXUser(pfxFile.toString(), password);
                        signers.add(user);
                        break;

                    // get output directory
                    case "-o":
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        i++;
                        break;

                    // get hash algorithm
                    case "-ha":
                        i++;
                        hashAlg = args[i];
                        i++;
                        break;

                    // get input encoding
                    case "-e":
                        i++;
                        inputEncoding = args[i];
                        i++;
                        break;

                    // write a summary of the command line options to the std out
                    case "-help":
                        help = true;
                        i++;
                        break;

                    // copy content - ignore
                    case "-copy":
                        i++;
                        LOG.log(Level.INFO, "-copy argument is now redundant");
                        break;

                    // move content
                    case "-move":
                        i++;
                        LOG.log(Level.INFO, "-move argument is now redundant");
                        break;

                    // link content
                    case "-link":
                        i++;
                        LOG.log(Level.INFO, "-link argument is now reduntant");
                        break;

                    // if verbose...
                    case "-v":
                        chatty = true;
                        i++;
                        break;

                    // if very verbose...
                    case "-vv":
                        verbose = true;
                        i++;
                        LOG.setLevel(Level.INFO);
                        break;

                    // if debugging...
                    case "-d":
                        debug = true;
                        i++;
                        LOG.setLevel(Level.FINE);
                        break;

                    // if unrecognised arguement, print help string and exit
                    default:
                        throw new VEOFatal(classname, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + USAGE);
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new VEOFatal(classname, 3, "Missing argument. Usage: " + USAGE);
        }
    }

    /**
     * Check a file to see that it exists and is of the correct type (regular
     * file or directory). The program terminates if an error is encountered.
     *
     * @param type a String describing the file to be opened
     * @param name the file name to be opened
     * @param isDirectory true if the file is supposed to be a directory
     * @throws VEOFatal if the file does not exist, or is of the correct type
     * @return the File opened
     */
    private Path checkFile(String type, String name, boolean isDirectory) throws VEOFatal {
        Path p;

        try {
            p = Paths.get(name);
        } catch (InvalidPathException ipe) {
            throw new VEOFatal(classname, 9, type + " '" + name + "' is not a valid file name: " + ipe.getMessage());
        }

        if (!Files.exists(p)) {
            throw new VEOFatal(classname, 6, type + " '" + p.toAbsolutePath().toString() + "' does not exist");
        }
        if (isDirectory && !Files.isDirectory(p)) {
            throw new VEOFatal(classname, 7, type + " '" + p.toAbsolutePath().toString() + "' is a file not a directory");
        }
        if (!isDirectory && Files.isDirectory(p)) {
            throw new VEOFatal(classname, 8, type + " '" + p.toAbsolutePath().toString() + "' is a directory not a file");
        }
        return p;
    }

    /**
     * Build VEOs specified by the control file. See the general description of
     * this class for a description of the control file and the various commands
     * that can appear in it.
     *
     * @throws VEOFatal if an error occurs that prevents any further VEOs from
     * being constructed
     */
    public void buildVEOs() throws VEOFatal {
        String method = "buildVEOs";
        FileInputStream fis;     // source of control file to build VEOs
        InputStreamReader isr;
        BufferedReader br;

        // sanity check (redundant, but just in case)...
        if (controlFile == null) {
            throw new VEOFatal(classname, method, 1, "Control file is null");
        }

        // open control file for reading
        try {
            fis = new FileInputStream(controlFile.toString());
            isr = new InputStreamReader(fis, inputEncoding);
            br = new BufferedReader(isr);
        } catch (FileNotFoundException e) {
            throw new VEOFatal(classname, method, 2, "Failed to open control file '" + controlFile.toString() + "'" + e.toString());
        } catch (UnsupportedEncodingException e) {
            throw new VEOFatal(classname, method, 3, "The encoding '" + inputEncoding + "' used when reading the control file is invalid");
        }

        // build VEOs
        buildVEOs(br);

        // close the control file
        try {
            br.close();
        } catch (IOException e) {
            /* ignore */ }
        try {
            isr.close();
            //fr.close();
        } catch (IOException e) {
            /* ignore */ }
        try {
            fis.close();
        } catch (IOException e) {
            /* ignore */ }
    }

    /**
     * Read commands from the Reader to build VEOs. See the general description
     * of this class for a description of the control file and the various
     * commands that can appear in it.
     *
     * @param br file to read the commands from
     * @throws VERSCommon.VEOFatal if prevented from continuing processing at
     * all
     */
    public void buildVEOs(BufferedReader br) throws VEOFatal {
        String method = "buildVEOs";
        String s;           // current line read from control file
        String[] tokens;    // tokens extracted from line
        int line;           // which line in control file (for errors)
        int i;
        CreateVEO veo;      // current VEO being created
        String attributes, value;
        String AGLSNamespaces
                = "xmlns:dcterms=\"http://purl.org/dc/terms/\"\r\n"
                + "    xmlns:aglsterms=\"http://www.agls.gov.au/agls/terms/\"\r\n"
                + "    xmlns:versterms=\"http://www.prov.vic.gov.au/vers/terms\"";
        String ANZS5478Namespaces
                = "xmlns:anzs5478=\"http://www.prov.vic.gov.au/vers/ANZS5478\"";

        // sanity check (redundant, but just in case)...
        if (controlFile == null) {
            throw new VEOFatal(classname, method, 1, "Control file is null");
        }

        // go through command file line by line
        line = 0;
        veo = null;
        try {
            while ((s = br.readLine()) != null && !s.toLowerCase().trim().equals("end")) {
                // log.log(Level.FINE, "Processing: ''{0}''", new Object[]{s});
                line++;

                // split into tokens and check for blank line
                tokens = s.split("\t");
                if (s.equals("") || tokens.length == 0) {
                    continue;
                }

                // encoding of tab in tokens
                for (i = 0; i < tokens.length; i++) {
                    tokens[i] = tokens[i].replaceAll("<tab>", "\t");
                }

                switch (tokens[0].toLowerCase().trim()) {

                    // comment - ignore line
                    case "!":
                        if (tokens.length < 2) {
                            break;
                        }
                        if (chatty) {
                            System.out.println("COMMENT: " + tokens[1]);
                        } else {
                            LOG.log(Level.FINE, "COMMENT: {0}", new Object[]{tokens[1]});
                        }
                        break;

                    // set the hash algoritm. Can only do this before the first VEO is started
                    case "hash":
                        if (state != State.PREAMBLE) {
                            throw createVEOFatal(1, line, "HASH command must be specified before first VEO generated");
                        }
                        if (tokens.length < 2) {
                            throw createVEOFatal(1, line, "HASH command doesn't specify algorithm (format: 'HASH' <algorithm>");
                        }
                        hashAlg = tokens[1];
                        System.out.println("Now using hash algorithm: " + hashAlg + " (set from control file)");
                        break;

                    // set a user to sign the VEO. Can only do this before the first VEO
                    case "pfx":
                        PFXUser pfx;
                        if (state != State.PREAMBLE) {
                            throw createVEOFatal(1, line, "PFX command must be specified before first VEO generated");
                        }
                        if (tokens.length < 3) {
                            throw createVEOFatal(1, line, "PFX command doesn't specify pfx file and/or password (format: 'PFX' <pfxFile> <password>)");
                        }

                        try {
                            pfx = new PFXUser(getRealFile(tokens[1]).toString(), tokens[2]);
                        } catch (VEOError e) {
                            veoFailed(line, "In PFX command, failed to process PFX file", e);
                            break;
                        }
                        signers.add(pfx);
                        if (signers.size() == 1) {
                            System.out.println("Signing using PFX file: " + pfx.getFileName() + " (set from control file)");
                        } else {
                            System.out.println("Also signing using PFX file: " + pfx.getFileName() + " (set from control file)");
                        }
                        break;

                    // Begin a new VEO. If necessary, finish the old VEO up
                    case "bv":

                        // check that a signer has been defined
                        if (signers.isEmpty()) {
                            throw new VEOFatal(classname, method, 1, "Attempting to begin construction of a VEO without specifying a signer using a PFX command or -s command line argument");
                        }

                        // if we are already constructing a VEO, finalise it before starting a new one
                        if (veo != null) {
                            try {
                                veo.finishFiles();
                                sign(veo);
                                veo.finalise(false);
                            } catch (VEOError e) {
                                veoFailed(line, "When starting new BV command, failed to finalise previous VEO", e);
                                veo.abandon(debug);
                                if (e instanceof VEOFatal) {
                                    return;
                                }
                            }
                            veo = null;
                        }

                        // check command arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing VEO name in BV command (format: 'BV' <veoName>)");
                            if (veo != null) {
                                veo.abandon(debug);
                            }
                            veo = null;
                            break;
                        }

                        // tell the world if verbose...
                        if (chatty) {
                            System.out.println(System.currentTimeMillis() / 1000 + " Generating: " + tokens[1]);
                        }
                        LOG.log(Level.FINE, "Beginning VEO ''{0}'' (State: {1}) at {3}", new Object[]{tokens[1], state, System.currentTimeMillis() / 1000});

                        // create VEO & add VEOReadme.txt from template directory
                        try {
                            veo = new CreateVEO(outputDir, tokens[1], hashAlg, debug);
                            veo.addVEOReadme(supportDir);
                        } catch (VEOError e) {
                            veoFailed(line, "Failed in starting new VEO in BV command", e);
                            if (veo != null) {
                                veo.abandon(debug);
                            }
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }

                        // we have started...
                        state = State.VEO_STARTED;
                        break;

                    // Add content directories to a VEO
                    case "ac":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "AC command before first BV");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing content directory in AC command (format: 'AC' <contentDirectory> [<contentDirectory>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        LOG.log(Level.FINE, "Adding content directories (State: {0})", new Object[]{state});

                        // go through list of directories adding them
                        try {
                            for (i = 1; i < tokens.length; i++) {
                                veo.registerContentDirectories(getRealFile(tokens[i]));
                            }
                        } catch (VEOError e) {
                            veoFailed(line, "AC command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // Start a new information object
                    case "io":
                        String label;
                        int depth;

                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "IO command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing label in IO command (format: 'IO' <label> [<level>])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        // default is anonymous IO with depth = 1
                        depth = 0;

                        // label is the first argument
                        label = tokens[1].trim();

                        // depth is second argument - if empty then default to 0
                        if (tokens.length > 2) {
                            try {
                                depth = Integer.parseInt(tokens[2]);
                            } catch (NumberFormatException e) {
                                veoFailed(line, "Level in IO command is not a valid integer");
                                veo.abandon(debug);
                                veo = null;
                                break;
                            }
                            if (depth < 0) {
                                veoFailed(line, "Level in IO command is not zero or a positive integer");
                                veo.abandon(debug);
                                veo = null;
                                break;
                            }
                        }

                        // add the information object
                        try {
                            veo.addInformationObject(label, depth);
                        } catch (VEOError e) {
                            veoFailed(line, "Error in an IO command", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        LOG.log(Level.FINE, "Starting new Information Object ''{0}'' level {1} (State: {2})", new Object[]{label, depth, state});
                        break;

                    // start a new Metadata Package
                    case "mp":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "MP command before first BV command");
                            break;
                        }
                        if (templates == null) {
                            veoFailed(line, "Using an MP command requires a template directory to be specified (-t command line argument)");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing template in MP command (format: 'MP' <template> [<subs>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        LOG.log(Level.FINE, "Starting new Metadata Package ''{1}'' (State: {0})", new Object[]{state, tokens[1]});

                        // get template
                        try {
                            veo.addMetadataPackage(templates.findTemplate(tokens[1]), tokens);
                        } catch (VEOError e) {
                            veoFailed(line, "Applying template '" + tokens[1] + "' in MP command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }

                        // log.log(Level.FINE, "Found template. Schema ''{0}'' Syntax ''{1}''", new Object[]{template.getSchemaId(), template.getSyntaxId()});
                        break;

                    // start a new generic XML metadata package...
                    case "xml-mp":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "XML-MP command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing semanticId in XML-MP command (format: 'XML-MP' <semanticId>)");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        LOG.log(Level.FINE, "Starting new XML Metadata Package ''{1}'' (State: {0})", new Object[]{state, tokens[1]});

                        // start generic XML metadata package
                        try {
                            veo.startXMLMetadataPackage(tokens[1]);
                        } catch (VEOError e) {
                            veoFailed(line, "XML-MP ('" + tokens[1] + "')command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // start a new generic RDF metadata package...
                    case "rdf-mp":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "RDF-MP command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 3) {
                            veoFailed(line, "Missing semanticId or resourceId in RDF-MP command (format: 'RDF-MP' <semanticId> <resourceId> [<namespaceDefns>])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        if (tokens.length > 3) {
                            attributes = tokens[3];
                        } else {
                            attributes = null;
                        }

                        LOG.log(Level.FINE, "Starting new RDF Metadata Package semanticId: ''{1}'' objectId: ''{2}'' (State: {0})", new Object[]{state, tokens[1], tokens[2]});

                        // start generic RDF metadata package
                        try {
                            veo.startRDFMetadataPackage(tokens[1], attributes, new URL(tokens[2]));
                        } catch (VEOError e) {
                            veoFailed(line, "RDF-MP ('" + tokens[1] + "', '" + tokens[2] + "')command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // start an AGLS metadata package...
                    case "agls-mp":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "AGLS-MP command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing resourceId in AGLS-MP command (format: 'AGLS-MP' <resourceId>)");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        LOG.log(Level.FINE, "Starting new AGLS Metadata Package resourceId: ''{1}'' (State: {0})", new Object[]{state, tokens[1]});

                        // start AGLS (RDF) metadata package
                        try {
                            veo.startRDFMetadataPackage("http://www.vic.gov.au/blog/wp-content/uploads/2013/11/AGLS-Victoria-2011-V4-Final-2011.pdf", AGLSNamespaces, new URL(tokens[1]));
                        } catch (VEOError e) {
                            veoFailed(line, "AGLS-MP ('" + tokens[1] + "')command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // start an ANZS-5478 metadata package...
                    case "anzs5478-mp":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "ANZS5478-MP command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing resourceId in ANZS5478-MP command (format: 'ANZS5478-MP' <resourceId>)");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        LOG.log(Level.FINE, "Starting new ANZS5478 Metadata Package resourceId: ''{1}'' (State: {0})", new Object[]{state, tokens[1]});

                        // start AGLS (RDF) metadata package
                        try {
                            veo.startRDFMetadataPackage("http://www.prov.vic.gov.au/vers/schema/ANZS5478", ANZS5478Namespaces, new URL(tokens[1]));
                        } catch (VEOError e) {
                            veoFailed(line, "ANZS5478-MP ('" + tokens[1] + "')command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // continue an existing metadata package...
                    case "mpc":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "MPC command before first BV command");
                            break;
                        }
                        if (templates == null) {
                            veoFailed(line, "Using an MPC command requires a template directory to be specified (-t command line argument)");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing template in MPC command (format: 'MPC' <template> [<subs>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        LOG.log(Level.FINE, "Continuing a Metadata Package ''{1}'' (State: {0})", new Object[]{state, tokens[1]});

                        try {
                            veo.continueMetadataPackage(templates.findTemplate(tokens[1]), tokens);
                        } catch (VEOError e) {
                            veoFailed(line, "Applying template in MPC command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // add a simple metadata element to a metadata package
                    case "me":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "ME command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing XML element tag in ME command (format: 'ME' <tagName> [value] [attribute])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        if (tokens.length > 2) {
                            value = tokens[2];
                        } else {
                            value = null;
                        }
                        if (tokens.length > 3) {
                            attributes = tokens[3];
                        } else {
                            attributes = null;
                        }

                        LOG.log(Level.FINE, "Adding simple metadata element to metadata package: ''{1}'' ''{2}'' ''{3}''(State: {0})", new Object[]{state, tokens[1], attributes == null ? " " : attributes, value == null ? "[No value]" : value});

                        // add element
                        try {
                            veo.addSimpleMetadataElementToMP(tokens[1], attributes, value);
                        } catch (VEOError e) {
                            veoFailed(line, "ME ('" + tokens[1] + "')command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // start a complex metadata element in a metadata package
                    case "sme":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "SME command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing XML element tag in SME command (format: 'SME' <tagName> [attribute])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        if (tokens.length > 2) {
                            attributes = tokens[2];
                        } else {
                            attributes = null;
                        }

                        LOG.log(Level.FINE, "Adding simple metadata element to metadata package: ''{1}'' ''{2}'' ''{3}''(State: {0})", new Object[]{state, tokens[1], attributes == null ? " " : attributes});

                        // add element
                        try {
                            veo.startComplexMetadataElementInMP(tokens[1], attributes);
                        } catch (VEOError e) {
                            veoFailed(line, "SME ('" + tokens[1] + "')command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // end a complex metadata element in a metadata package
                    case "eme":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "EME command before first BV command");
                            break;
                        }
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing XML element tag in EME command (format: 'SME' <tagName>)");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        LOG.log(Level.FINE, "Ending metadata element in metadata package: ''{1}'' (State: {0})", new Object[]{state, tokens[1]});

                        // add element
                        try {
                            veo.endComplexMetadataElementInMP(tokens[1]);
                        } catch (VEOError e) {
                            veoFailed(line, "EME ('" + tokens[1] + "')command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // add an information package to an information object
                    case "ip":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "IP command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing file in IP command (format: 'IP' [<label>] <file> [<files>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        // check to see if first argument is a label or a file...
                        Path p;
                        label = null;
                        i = 1;
                        try {
                            p = veo.getActualSourcePath(tokens[1]);
                        } catch (VEOError ve) {
                            p = null;
                        }
                        if (p == null || !Files.exists(p) || !Files.isRegularFile(p)) {
                            i = 2;
                            label = tokens[1];
                            if (tokens.length < 3) {
                                veoFailed(line, "Missing file after a label in IP command (format: 'IP' [<label>] <file> [<files>...]). Possibly referenced file hasn't been added in an AC command");
                                veo.abandon(debug);
                                veo = null;
                                break;
                            }
                        }
                        LOG.log(Level.FINE, "Starting new Information Piece {1} ''{2}'' (State: {0})", new Object[]{state, i, tokens[1]});

                        // add Information Packages...
                        try {
                            veo.addInformationPiece(label);

                            // go through list of files to add
                            while (i < tokens.length) {
                                LOG.log(Level.FINE, "Adding ''{0}''", tokens[i]);
                                veo.addContentFile(tokens[i]);
                                i++;
                            }
                        } catch (VEOError e) {
                            veoFailed(line, "IP command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // add an event to the VEO history file
                    case "e":
                        boolean error;  // true if processing error part of event
                        List<String> descriptions = new ArrayList<>(); // description strings in command
                        List<String> errors = new ArrayList<>(); // error strings in comman

                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "E command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 5) {
                            veoFailed(line, "Missing mandatory argument in E command (format: 'E' <date> <event> <initiator> <description> [<description>...] ['$$' <error>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        if (state != State.VEO_STARTED) {
                            veoFailed(line, "E command must be specified after a BV command");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        LOG.log(Level.FINE, "Adding an event ''{1}'' ''{2}'' ''{3}'' ''{4}''... (State: {0})", new Object[]{state, tokens[1], tokens[2], tokens[3], tokens[4]});

                        error = false;
                        for (i = 4; i < tokens.length; i++) {
                            if (tokens[i].trim().equals("$$")) {
                                error = true;
                            } else if (!error) {
                                descriptions.add(tokens[i]);
                            } else {
                                errors.add(tokens[i]);
                            }
                        }

                        // must have at least one description...
                        if (descriptions.isEmpty()) {
                            veoFailed(line, "Missing mandatory argument in E command - 4th argument is a $$ (format: 'E' <date> <event> <initiator> <description> [<description>...] ['$$' <error>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        // Add event
                        try {
                            veo.addEvent(tokens[1], tokens[2], tokens[3], descriptions.toArray(new String[descriptions.size()]), errors.toArray(new String[errors.size()]));
                        } catch (VEOError e) {
                            veoFailed(line, "Adding event in E command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // shorthand for a VEO that has but one MP, one IO, one IP
                    case "veo":
                        // check that a signer has been defined
                        if (signers.isEmpty()) {
                            throw new VEOFatal(classname, method, 1, "Attempting to begin construction of a VEO without specifying a signer using a PFX command or -s command line argument");
                        }

                        // if we are already constructing a VEO, finalise it before starting a new one
                        if (veo != null) {
                            try {
                                veo.finishFiles();
                                sign(veo);
                                veo.finalise(false);
                            } catch (VEOError e) {
                                veoFailed(line, "When starting new BV command, failed to finalise previous VEO:", e);
                                veo.abandon(debug);
                                if (e instanceof VEOFatal) {
                                    return;
                                }
                            }
                            veo = null;
                        }

                        // check command arguments
                        if (templates == null) {
                            veoFailed(line, "Using an VEO command requires a template directory to be specified (-t command line argument)");
                            break;
                        }
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing VEO name in VEO command (format: 'VEO' <veoName> <label> <template> [<data>...] '$$' [<files>...]))");
                            if (veo != null) {
                                veo.abandon(debug);
                            }
                            veo = null;
                            break;
                        }
                        // tell the world if verbose...
                        if (chatty) {
                            System.out.println(System.currentTimeMillis() / 1000 + " Starting: " + tokens[1]);
                        }
                        LOG.log(Level.FINE, "Beginning VEO ''{0}'' (State: {1}) at {3}", new Object[]{tokens[1], state, System.currentTimeMillis() / 1000});

                        // create VEO
                        try {
                            // create VEO & add VEOReadme.txt from template directory
                            veo = new CreateVEO(outputDir, tokens[1], hashAlg, debug);
                            veo.addVEOReadme(supportDir);

                            // which contains one anonymous IO
                            veo.addInformationObject(tokens[2], 0);

                            // which contains one metadata package
                            veo.addMetadataPackage(templates.findTemplate(tokens[3]), tokens);

                            // and multiple anonymous IP (one for each content file)
                            // go through list of files to add
                            boolean foundFiles = false;
                            for (i = 0; i < tokens.length; i++) {
                                if (tokens[i].equals("$$")) {
                                    foundFiles = true;
                                    continue;
                                }
                                if (foundFiles) {
                                    // create information piece
                                    veo.addInformationPiece(null);

                                    // add the content file to the information piece
                                    LOG.log(Level.FINE, "Adding ''{0}''", tokens[i]);
                                    veo.addAbsContentFile(tokens[i]);
                                }
                            }

                            // and one event documenting creation of the VEO
                            veo.addEvent(VERSDate.versDateTime((long) 0), "VEO Created", "VEOCreate", new String[]{"No Description"}, new String[]{"No Errors"});
                        } catch (VEOError e) {
                            veoFailed(line, "Failed in creating new VEO in VEO command", e);
                            if (veo != null) {
                                veo.abandon(debug);
                            }
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        // we have started...
                        state = State.VEO_STARTED;
                        break;

                    default:
                        LOG.log(Level.SEVERE, "Error in control file around line {0}: unknown command: ''{1}''", new Object[]{line, tokens[0]});
                }
            }
        } catch (PatternSyntaxException | IOException ex) {
            throw new VEOFatal(classname, method, 1, "unexpected error: " + ex.toString());
        }

        // if we are already constructing a VEO, finalise it...
        if (veo != null) {
            try {
                veo.finishFiles();
                sign(veo);
                veo.finalise(false);
            } catch (VEOError e) {
                veoFailed(line, "Failed when finalising last VEO", e);
                veo.abandon(debug);
            }
        }
        if (chatty) {
            System.out.println(System.currentTimeMillis() / 1000 + " Finished");
        }
    }

    /**
     * Generate file reference The control file contains references to other
     * files. These references may be absolute, they may be relative to the
     * directory containing the control file, or they may be relative to the
     * current working directory. If the file starts with the root (typically a
     * slash), the file ref is absolute. If the file ref starts with a '.', it
     * is considered relative to the current working direction.
     *
     * @param fileRef the file reference from the control file
     * @return the real path of the referenced file or directory
     */
    private Path getRealFile(String fileRef) throws VEOError {
        Path f;

        try {
            f = Paths.get(fileRef);
        } catch (InvalidPathException ipe) {
            throw new VEOError(classname, "getRealFile", 1, "Invalid file reference (" + fileRef + ") in control file:");
        }

        // if fileRef is not relative to current working directory and
        // not absolute (relative to the directory containing the control file)
        if (!f.startsWith(".") && !f.isAbsolute()) {
            f = baseDir.resolve(fileRef);
        }
        try {
            f = f.toRealPath();
        } catch (IOException ioe) {
            throw new VEOError(classname, "getRealFile", 2, "Invalid file reference in control file: '" + ioe.getMessage() + "'; typically this file doesn't exist");
        }
        return f;
    }

    /**
     * Generate a VEOContentSignature.xml or VEOHistorySignature.xml file. If
     * multiple PFX files have been specified, multiple pairs of signature files
     * are generated.
     *
     * @param file name of file to be signed
     * @param veo the VEO that is being constructed
     * @param signer the information about the signer
     * @param password the password
     * @throws VEOError if the signing failed for any reason
     */
    private void sign(CreateVEO veo) throws VEOError {

        for (PFXUser user : signers) {
            LOG.log(Level.FINE, "Signing {0} with ''{1}''", new Object[]{user.toString(), hashAlg});
            veo.sign(user, hashAlg);
        }
    }

    /**
     * VERSDate method to throw a fatal error.
     *
     * @param errno unique error number
     * @param line line number in control file
     * @param s string description of error
     * @return a VEOFatal exception to throw
     */
    private VEOFatal createVEOFatal(int errno, int line, String s) {
        return new VEOFatal(classname, "buildVEOs", errno, "Error in control file around line " + line + ": " + s);
    }

    /**
     * VERSDate method to report an error resulting from a VEOFatal or VEOError
     * exception.
     *
     * @param line line in control file in which error occurred
     * @param s a string describing error
     * @param e the error that caused the failure
     */
    private void veoFailed(int line, String s, Throwable e) {
        s = s + ". Error was: " + e.getMessage() + ". ";
        if (e instanceof VEOFatal) {
            s = s + "Creation of VEOs halted.";
        } else {
            s = s + "VEO being abandoned.";
        }
        veoFailed(line, s);
    }

    /**
     * VERSDate method to report an error
     *
     * @param line line in control file in which error occurred
     * @param s a string describing error
     */
    private void veoFailed(int line, String s) {
        LOG.log(Level.WARNING, "Error in control file around line {0}: {1}.", new Object[]{line, s});
        state = State.VEO_FAILED;
    }

    /**
     * Abandon construction of these VEOs and free any resources associated with
     * it.
     *
     * @param debug true if information is to be left for debugging
     */
    public void abandon(boolean debug) {

    }

    /**
     * Main program.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        CreateVEOs cv;

        if (args.length == 0) {
            // args = new String[]{"-c", "Test/Demo/createANZStests.txt", "-t", "Test/Demo/templates", "-o", "../neoVEOOutput/TestAnalysis"};
            // args = new String[]{"-c", "Test/Demo/control.txt", "-t", "Test/Demo/templates", "-o", "../neoVEOOutput/TestAnalysis"};
        }
        try {
            cv = new CreateVEOs(args);
            cv.buildVEOs();
        } catch (VEOFatal e) {
            System.err.println(e.getMessage());
        }
    }
}
