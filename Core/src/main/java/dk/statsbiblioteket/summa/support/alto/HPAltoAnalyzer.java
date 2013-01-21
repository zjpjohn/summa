/* $Id:$
 *
 * WordWar.
 * Copyright (C) 2012 Toke Eskildsen, te@ekot.dk
 *
 * This is confidential source code. Unless an explicit written permit has been obtained,
 * distribution, compiling and all other use of this code is prohibited.    
  */
package dk.statsbiblioteket.summa.support.alto;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hvide Programmer (a project at Statsbiblioteket) specific analyzer for Altos.
 */
public class HPAltoAnalyzer {
    private static Log log = LogFactory.getLog(HPAltoAnalyzer.class);

    // DESCRIPTION is a text block connected to an entry. The vdist and hdist specifies where it may be placed to
    // be considered a candidate

    public static final String CONF_DESCRIPTION_HDIST_MIN = "hp.description.hdist.min";
    public static final int DEFAULT_DESCRIPTION_HDIST_MIN = 10;

    public static final String CONF_DESCRIPTION_HDIST_MAX = "hp.description.hdist.max";
    public static final int DEFAULT_DESCRIPTION_HDIST_MAX = 50;

    public static final String CONF_DESCRIPTION_VDIST_MIN = "hp.description.vdist.min";
    public static final int DEFAULT_DESCRIPTION_VDIST_MIN = 1;
    
    public static final String CONF_DESCRIPTION_VDIST_MAX = "hp.description.vdist.max";
    public static final int DEFAULT_DESCRIPTION_VDIST_MAX = 20;

    private final int descriptionHDistMin;
    private final int descriptionHDistMax;
    private final int descriptionVDistMin;
    private final int descriptionVDistMax;

    public HPAltoAnalyzer(Configuration conf) {
        descriptionHDistMin = conf.getInt(CONF_DESCRIPTION_HDIST_MIN, DEFAULT_DESCRIPTION_HDIST_MIN);
        descriptionHDistMax = conf.getInt(CONF_DESCRIPTION_HDIST_MAX, DEFAULT_DESCRIPTION_HDIST_MAX);
        descriptionVDistMin = conf.getInt(CONF_DESCRIPTION_VDIST_MIN, DEFAULT_DESCRIPTION_VDIST_MIN);
        descriptionVDistMax = conf.getInt(CONF_DESCRIPTION_VDIST_MAX, DEFAULT_DESCRIPTION_VDIST_MAX);
    }

    /**
     * The heart of the analyzer tries to extract Segments from the given alto. Special segments are "Program x" that
     * are used to mark subsequent segments with the right program.
     * @param alto an object representation of alto XML.
     * @return the Segments for the page in the alto (note: Currently only the first page is processed).
     */
    public List<Segment> getSegments(Alto alto) {
        // We'll do a lot of random access extraction so linked lists seems the obvious choice (ignoring caching)
        final List<Alto.TextBlock> blocks = new LinkedList<Alto.TextBlock>(alto.getLayout().get(0).getPrintSpace());
        final List<Segment> segments = new ArrayList<Segment>();
        String lastProgram = null; // Last encountered program
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
                        "getSegments found %d segments with %d remaining TextBlocks, where there should be 0 remaining",
                        segments.size(), blocks.size()));
                return segments;
            }

            // If there are no candidate, adjust search parameters for next column
            if (best == null) {
                hPos = maxHPos;
                vPos = -1;
                maxHPos = Integer.MAX_VALUE;
                continue;
            }

            // See if there are a better candidate
            for (Alto.TextBlock candidate: blocks) {
                if (candidate.getHpos() >= hPos && candidate.getVpos() > vPos && candidate.getHpos() <= maxHPos) {
                    // Valid. Check is the distance is better
                    if (getDistance(hPos, vPos, candidate) < getDistance(hPos, vPos, best)) {
                        best = candidate;
                    }
                }
            }

            // We got the best block. Remove it from the pool
            blocks.remove(best);
            maxHPos = best.getHpos() + best.getWidth(); // Only skip to next column when the current one is exhausted
            vPos = best.getVpos();

            // Is the block a program?
            String program = getProgram(best);
            if (program != null) {
                lastProgram = program;
                continue;
            }

            // Create the segment
            Segment segment = blockToSegment(alto, best, lastProgram);

            // See if there are blocks below that belongs to the current segment
            // TODO: Implement this
            segments.add(segment);
        }
        return collapse(segments);
    }

    /*
     * Iterate segments and fill missing endTimes by using next startTime
     * Segments with missing titles are merged with subsequent segment if it does not have time
     */
    private List<Segment> collapse(List<Segment> segments) {
        return null;  // TODO: Implement this
    }

    // TODO: Consider weights that prefers closer vDistance over hDistance
    private double getDistance(int hPos, int vPos, Alto.TextBlock candidate) {
        return Math.sqrt(Math.pow(hPos-candidate.getHpos(), 2) + Math.pow(vPos-candidate.getVpos(), 2));
    }

    // TODO: Improve this regexp
    private Pattern programPattern = Pattern.compile("(PROGRAM.+)", Pattern.CASE_INSENSITIVE);
    private String getProgram(Alto.TextBlock textBlock) {
        Matcher programMatcher = programPattern.matcher(textBlock.getAllText());
        if (programMatcher.matches()) {
            String program = programMatcher.group(1);
            log.trace("Found program " + program);
            return program;
        }
        return null;
    }

    private int counter = 0;
    private Segment blockToSegment(Alto alto, Alto.TextBlock textBlock, String program) {
        Segment segment = new Segment();
        segment.filename = alto.getFilename();
        segment.date = getDateFromFilename(segment.filename);
        segment.id = "alto_" + segment.date + "_" + counter++;
        segment.program = program;
        segment.hpos = textBlock.getHpos();
        segment.vpos = textBlock.getVpos();
        segment.width = textBlock.getWidth();
        segment.height = textBlock.getHeight();

        List<Alto.TextLine> lines = new LinkedList<Alto.TextLine>(textBlock.getLines());

        extractTimeAndtitle(segment, lines);

        // Just add the rest of the lines
        for (Alto.TextLine line: lines) {
            segment.paragraphs.add(clean(line.getAllText()));
        }
        return segment;
    }

    private Pattern approximateTimePattern =
            Pattern.compile("(ca[.]|~) *(.*)", Pattern.CASE_INSENSITIVE);
    // TODO: Improve these regexps
    private Pattern fromTimePattern =
            Pattern.compile("([0-9]{1,2}) ?. ?([0-9]{2}).?(.*)");
    private Pattern fullTimePattern =
            Pattern.compile("([0-9]{1,2}) ?. ?([0-9]{2})[^0-9]{1,2}([0-9]{1,2}) ?. ?([0-9]{2}).?(.*)");
    private boolean extractTimeAndtitle(Segment segment, List<Alto.TextLine> lines) {
        boolean gotTime = false;
        String line = lines.get(0).getAllText();
        Matcher aMatcher = approximateTimePattern.matcher(line);
        if (aMatcher.matches()) {
            segment.timeApproximate = true;
            line = aMatcher.group(2);
        }

        Matcher fullTimeMatcher = fullTimePattern.matcher(line);
        if (fullTimeMatcher.matches()) {
            segment.startTime = fullTimeMatcher.group(1) + ":" + fullTimeMatcher.group(2);
            segment.endTime = fullTimeMatcher.group(3) + ":" + fullTimeMatcher.group(4);
            segment.title = clean(fullTimeMatcher.group(5));
            log.trace("Found start time " + segment.startTime + " and end time " + segment.endTime + " with title "
                      + segment.title);
            gotTime = true;
        } else {
            Matcher fromTimeMatcher = fromTimePattern.matcher(line);
            if (fromTimeMatcher.matches()) {
                segment.startTime = fromTimeMatcher.group(1) + ":" + fromTimeMatcher.group(2);
                segment.title = clean(fromTimeMatcher.group(3));
                log.trace("Found start time " + segment.startTime + " with title " + segment.title);
                gotTime = true;
            }
        }
        if (gotTime) {
            lines.remove(0);
        }

        // TODO: Match textStyles for headline
        while ((segment.title == null || segment.title.isEmpty()) && !lines.isEmpty()) { // First real text is the title
            //String textStyle = lines.get(0).getTextStrings().get(0).getStyleRefs();
            segment.title = clean(lines.remove(0).getAllText());
        }

        return gotTime;
    }

    // TODO: Improve cleanup by collapsing multiple spaces and removing "unrealistic" chars
    private String clean(String text) {
        return text.trim();
    }

    // B-1977-10-02-P-0003.xml -> 19771002
    private final Pattern dateFromFile = Pattern.compile("..([0-9]{4})-([0-9]{2})-([0-9]{2}).*");
    private String getDateFromFilename(String filename) {
        Matcher matcher = dateFromFile.matcher(filename);
        return matcher.matches() ? matcher.group(1) + matcher.group(2) + matcher.group(3) : null;
    }

    public static class Segment {
        private String filename = null;
        private String date = null;
        private String program = null;
        private String id = null;
        private int hpos = -1;
        private int vpos = -1;
        private int width = -1;
        private int height = -1;

        private String startTime = null; // HH:MM
        private String endTime = null;
        private boolean timeApproximate = false;
        private String title = null;
        private List<String> paragraphs = new ArrayList<String>();

        @Override
        public String toString() {
            return "Segment(time=" + (timeApproximate ? "~" : "") + startTime + '-' + endTime
                   + ", title='" + title + "', program='" + program + '\'' + ", #paragraphs=" + paragraphs.size()
                   + (paragraphs.isEmpty() ? "" : ": " + paragraphs.get(0)) + ')';
        }

        public String getFilename() {
            return filename;
        }
        public String getDate() {
            return date;
        }
        public String getProgram() {
            return program;
        }
        public String getId() {
            return id;
        }
        public int getHpos() {
            return hpos;
        }
        public int getVpos() {
            return vpos;
        }
        public int getWidth() {
            return width;
        }
        public int getHeight() {
            return height;
        }
        public String getStartTime() {
            return startTime;
        }
        public String getEndTime() {
            return endTime;
        }
        public boolean isTimeApproximate() {
            return timeApproximate;
        }
        public String getTitle() {
            return title;
        }
        public List<String> getParagraphs() {
            return paragraphs;
        }
    }
}