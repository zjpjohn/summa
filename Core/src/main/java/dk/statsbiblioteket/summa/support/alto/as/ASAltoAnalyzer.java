/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.statsbiblioteket.summa.support.alto.as;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.SubConfigurationsNotSupportedException;
import dk.statsbiblioteket.summa.support.alto.Alto;
import dk.statsbiblioteket.summa.support.alto.AltoAnalyzerBase;
import dk.statsbiblioteket.summa.support.alto.AltoAnalyzerSetup;
import dk.statsbiblioteket.util.Strings;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Avissamling (a project at Statsbiblioteket) specific analyzer for Altos.
 */
public class ASAltoAnalyzer extends AltoAnalyzerBase<ASAltoAnalyzer.ASSegment> {
    private static Log log = LogFactory.getLog(ASAltoAnalyzer.class);

    /**
     * A list of sub configurations containing setups. When an Alto is precessed, the setup from the first matching
     * setup in the list is used.
     * </p><p>
     * Optional. Default is a list with 1 default {@link ASAltoAnalyzerSetup}.
     * </p>
     */
    public static final String CONF_SETUPS = "asaltoanalyzer.setups";

    public static final String CONF_URL_PREFIX = "asaltoanalyzer.url.prefix";
    public static final String DEFAULT_URL_PREFIX =
            "http://bja-linux2.sb.statsbiblioteket.dk/index.php?vScale=0.4&hScale=0.4&image=";


    private final List<ASAltoAnalyzerSetup> setups = new ArrayList<ASAltoAnalyzerSetup>();
    private final String URLPrefix;

    public ASAltoAnalyzer(Configuration conf) throws SubConfigurationsNotSupportedException {
        super(conf);
        URLPrefix = conf.getString(CONF_URL_PREFIX, DEFAULT_URL_PREFIX);
        if (conf.valueExists(CONF_SETUPS)) {
            List<Configuration> subs = conf.getSubConfigurations(CONF_SETUPS);
            for (Configuration sub: subs) {
                setups.add(new ASAltoAnalyzerSetup(sub));
            }
        } else {
            log.info("No setups defined under key " + CONF_SETUPS + ". Using a single default setup");
            setups.add(new ASAltoAnalyzerSetup(Configuration.newMemoryBased()));
        }
    }

    /**
     * The heart of the analyzer tries to extract Segments from the given alto. Special segments are "Program x" that
     * are used to mark subsequent segments with the right program.
     * @param alto an object representation of alto XML.
     * @return the Segments for the page in the alto (note: Currently only the first page is processed).
     */
    @Override
    public List<ASSegment> getSegments(Alto alto) {
        ASAltoAnalyzerSetup setup = getSetup(alto);
        // We'll do a lot of random access extraction so linked lists seems the obvious choice (ignoring caching)
        final List<Alto.TextBlock> blocks = new LinkedList<Alto.TextBlock>(alto.getLayout().get(0).getPrintSpace());
        final List<ASSegment> segments = new ArrayList<ASSegment>();
        int hPos = 0;
        int vPos = -1;
        int maxHPos = Integer.MAX_VALUE;

        while (!blocks.isEmpty()) {
            Alto.TextBlock best = null;

            // Find the first valid candidate with the given parameters
            for (Alto.TextBlock candidate: blocks) {
                if (candidate.getHpos() >= hPos && candidate.getVpos() > vPos && candidate.getHpos() <= maxHPos) {
                    best = candidate;
                    break;
                }
            }
            // Endless loop detection
            if (best == null && maxHPos == Integer.MAX_VALUE) {
                log.warn(String.format(
                        "getSegments found %d segments with %d remaining TextBlocks, where there should be 0 " +
                        "remaining. The content of the TextBlocks follows:\n%s",
                        segments.size(), blocks.size(), dumpFull(blocks)));
                return segments;
            }

            // If there are no candidate, adjust search parameters for next column
            if (best == null) {
                hPos = maxHPos;
                vPos = -1;
                maxHPos = Integer.MAX_VALUE;
                continue;
            }

            // See if there is a better candidate
            for (Alto.TextBlock candidate: blocks) {
                if (candidate.getHpos() >= hPos && candidate.getVpos() > vPos && candidate.getHpos() <= maxHPos) {
                    // Valid. Check is the distance is better
                    if (getDistance(setup, hPos, vPos, candidate) < getDistance(setup, hPos, vPos, best)) {
                        best = candidate;
                    }
                }
            }

            // We got the best block. Remove it from the pool
            blocks.remove(best);
            maxHPos = best.getHpos() + best.getWidth(); // Only skip to next column when the current one is exhausted
            vPos = best.getVpos();

            // Create the segment
            ASSegment segment = blockToSegment(alto, best);

            // See if there are blocks below that belongs to the current segment
            // TODO: Implement this
            segments.add(segment);
        }
        return segments;
    }

    private ASAltoAnalyzerSetup getSetup(Alto alto) {
        for (ASAltoAnalyzerSetup setup: setups) {
            if (setup.fitsDate(getDateFromFilename(alto.getFilename()))) {
                return setup;
            }
        }
        throw new IllegalStateException(
                "Unable to find a ASAltoAnalyzerSetup that matches the date " + getDateFromFilename(alto.getFilename())
                + ". Consider adding a catch-all setup at the end of the setup chain");
    }

    private double getDistance(AltoAnalyzerSetup setup, int hPos, int vPos, Alto.TextBlock candidate) {
        return Math.sqrt(Math.pow((hPos-candidate.getHpos())*setup.getHdistFactor(), 2)
                         + Math.pow(vPos-candidate.getVpos(), 2));
    }

    @Override
    protected ASSegment blockToSegment(Alto alto, Alto.TextBlock textBlock) {
        ASSegment segment = super.blockToSegment(alto, textBlock);

        List<Alto.TextLine> lines = new LinkedList<Alto.TextLine>(textBlock.getLines());
        extractTitle(segment, lines);

        // Just add the rest of the lines
        for (Alto.TextLine line: lines) {
            segment.addParagraph(cleanTitle(line.getAllText()));
        }
        return segment;
    }

    private void extractTitle(ASSegment segment, List<Alto.TextLine> lines) {

        // TODO: Match textStyles for headline
        while ((segment.getTitle() == null || segment.getTitle().isEmpty()) && !lines.isEmpty()) { // First real text is the title
            //String textStyle = lines.get(0).getTextStrings().get(0).getStyleRefs();
            segment.setTitle(cleanTitle(lines.remove(0).getAllText()));
        }
    }

    // TODO: Improve cleanup by collapsing multiple spaces and removing "unrealistic" chars
    protected String cleanTitle(String text) {
        text = text.trim();
        if (".".equals(text)) {
            return "";
        }
        return text;
    }

    @Override
    public ASSegment createSegment() {
        return new ASSegment();
    }

    public class ASSegment extends AltoAnalyzerBase.Segment  {
        @Override
        public String toString() {
            return "ASSegment(title='" + getTitle() + "', #paragraphs=" + getParagraphs().size()
                   + (getParagraphs().isEmpty() ? "" : ": " + Strings.join(getParagraphs(), 10)) + ')';
        }

        @Override
        public String getType() {
            return "avisscanning";
        }

        @Override
        public void addIndexTerms(List<Term> terms) {
            terms.add(new Term("lma", "as"));
            terms.add(new Term("lma_long", "avisscanning"));
        }

        // TODO: This is extremely fragile. We need a more solid URL calculator
        // /home/te/projects/hvideprogrammer/samples_with_paths/dhp/data/Arkiv_A.1/1933_07-09/ALTO/A-1933-07-02-P-0008.xml
        // http://bja-linux2.sb/index.php?vScale=0.4&hScale=0.4&image=Arkiv_A.6/1929_07-09/PNG/A-1929-07-05-P-0015
        @Override
        public String getURL() {
            if (getOrigin() == null) {
                return null;
            }
            // Yes, unix path separator. Fragile, remember?
            String[] elements = getOrigin().split("/");
            if (elements.length < 4) {
                log.warn("Expected the origin '" + getOrigin() + "' to contain at least 4 path elements, but got only "
                         + elements.length);
                return null;
            }
            if (!elements[elements.length-1].endsWith(".xml")) {
                log.warn("Expected the origin '" + getOrigin() + "' to end with '.xml'");
                return null;
            }
            return URLPrefix
                   + elements[elements.length-4] + "/"
                   + elements[elements.length-3] + "/PNG/"
                   + elements[elements.length-1].substring(0, elements[elements.length-1].length()-".xml".length());
        }
    }
}