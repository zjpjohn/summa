/* $Id$
 * $Revision$
 * $Date$
 * $Author$
 *
 * The Summa project.
 * Copyright (C) 2005-2007  The State and University Library
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package dk.statsbiblioteket.summa.ingest.stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.regex.Pattern;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Filter;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.common.filter.object.ObjectFilter;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This reader performs a recursive scan for a given pattern of files.
 * When it finds a candidate for data it opens it and sends the
 * content onwards in unmodified form, packaged as a stream in a Payload.
 * If close(true) is called, processed files are marked with
 * a given postfix (default: .completed). Any currently opened file is kept open
 * until it has been emptied, but no new files are opened.
 * </p><p>
 * If close(false) is called, no files are marked with the postfix and any open
 * files are closed immediately.
 * </p><p>
 * Meta-info for delivered payloads will contain {@link #ORIGIN} which states
 * the opriginating file for the stream.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class FileReader implements ObjectFilter {
    private static Log log = LogFactory.getLog(FileReader.class);

    /**
     * The root folder to scan from.
     * </p><p>
     * This property must be specified.
     */
    public static final String CONF_ROOT_FOLDER =
            "summa.ingest.filereader.rootfolder";
    /**
     * Whether to perform a recursive scan or not (valid values: true, false).
     * </p><p>
     * This property is optional. Default is "true".
     */
    public static final String CONF_RECURSIVE =
            "summa.ingest.filereader.recursive";
    /**
     * The file pattern to match.
     * </p><p>
     * This property is optional. Default is ".*\.xml".
     */
    public static final String CONF_FILE_PATTERN =
            "summa.ingest.filereader.filepattern";
    private static final String DEFAULT_FILE_PATTERN = ".*\\.xml";
    /**
     * The postfix for the file when it has been fully processed.
     * </p><p>
     * This property is optional. Default is ".completed".
     */
    public static final String CONF_COMPLETED_POSTFIX =
            "summa.ingest.filereader.completedpostfix";

    /**
     * The key for the filename-value, added to meta-info in delivered payloads.
     */
    public static final String ORIGIN = "filename";

    private File root;
    private boolean recursive = true;
    private Pattern filePattern;
    @SuppressWarnings({"DuplicateStringLiteralInspection"})
    private String postfix = ".completed";

    private boolean started = false;
    private List<File> todo;
    private List<Payload> delivered;
    private boolean closedWithSuccess = false;

    /**
     * Sets up the properties for the FileReader. Scanning for files are
     * postponed until the first read() or pump() is called.
     * @param configuration the setup for the FileReader. See the CONF-constants
     *                      for available properties.
     */
    public FileReader(Configuration configuration) {
        log.trace("creating FileReader");
        try {
            String rootString = configuration.getString(CONF_ROOT_FOLDER);
            if ("".equals(rootString)) {
                log.debug("Setting root to current folder");
                root = new File(".").getAbsoluteFile();
            } else {
                log.trace("Got root-property '" + rootString + "'");
                root = new File(rootString).getAbsoluteFile();
                log.debug("Setting root to '" + root + "' from value '"
                          + rootString + "'");
                if (!root.exists()) {
                    //noinspection DuplicateStringLiteralInspection
                    log.warn("Root '" + root + "' does not exist");
                }
            }
        } catch (Exception e) {
            throw new ConfigurationException("No root specified for key "
                                             + CONF_ROOT_FOLDER);
        }
        recursive = configuration.getBoolean(CONF_RECURSIVE, recursive);
        filePattern = Pattern.compile(configuration.
                getString(CONF_FILE_PATTERN, DEFAULT_FILE_PATTERN));
        postfix = configuration.getString(CONF_COMPLETED_POSTFIX, postfix);
        log.info("FileReader created. Root: '" + root
                 + "', recursive: " + recursive
                 + ", file pattern: '" + filePattern.pattern()
                 + "', completed postfix: '" + postfix + "'");
    }

    public void setSource(Filter source) {
        throw new UnsupportedOperationException("A FileReader must be "
                                                + "positioned at the start of "
                                                + "a filter chain");
    }

    /* Recursive file adder */
    private void fillToDo(File start) {
        try {
            if (start.isDirectory()) {
                log.debug("fillTodo: Listing files in '" + start + "'");
                File files[] = start.listFiles();
                for (File file: files) {
                    fillToDo(file);
                }
            } else {
                if (filePattern.matcher(start.getName()).matches()) {
                    log.debug("fillToDo: Adding '" + start + "' to todo");
                    todo.add(start);
                } else {
                    log.trace("fillToDo: Skipping '" + start + "'");
                }
            }
        } catch (Exception e) {
            log.warn("fillToDo: Could not process '" + start + ". Skipping");
        }
    }

    /* One-time setup */
    private void checkInit() {
        if (started) {
            return;
        }
        log.trace("checkInit: Filling todo");
        todo = new ArrayList<File>(100);
        fillToDo(root);
        Collections.sort(todo);
        delivered = new ArrayList<Payload>(Math.max(1, todo.size()));
        started = true;
        log.info("Located " + todo.size() + " files matching pattern '"
                 + filePattern.pattern() + "' from root " + root.getPath());
    }

    /**
     * FileInputStream that is capable of renaming the file upon close.
     */
    class RenamingFileStream extends FileInputStream {
        private Log log = LogFactory.getLog(RenamingFileStream.class);

        private boolean success = false;
        private File file;
        private boolean closed = false;
        private boolean renamed = false;

        /**
         * Constructs a FileInputStream where the postfix will potentially be
         * used upon close.
         * @param file    the file to open.
         * @param postfix the postfix to add, is setSuccess(true) has been
         *                called and close is called.
         * @throws FileNotFoundException if the file could not be located.
         */
        public RenamingFileStream(File file, String postfix) throws
                                                         FileNotFoundException {
            super(file);
            log.trace("Created reader for '" + file
                      + "' with potential postfix '" + postfix + "'");
            this.file = file;
        }
        public void setSuccess(boolean success) {
            this.success = success;
            rename();
        }
        public void close() throws IOException {
            super.close();
            closed = true;
            rename();
        }

        private void rename() {
            if (renamed) {
                return;
            }
            if (closed && success && postfix != null && !"".equals(postfix)) {
                File newName = new File(file.getPath() + postfix);
                try {
                    file.renameTo(newName);
                    renamed = true;
                } catch(Exception e) {
                    log.error("Could not rename '" + file
                              + "' to '" + newName + "'", e);
                }
            }
        }
    }

    /**
     * Opens the next file in {@link #todo} and produces a Payload with a
     * stream to the file content.
     * @return a Payload with a stream for the next file or null if no further
     *         files are available.
     */
    public synchronized Payload next() {
        checkInit();
        if (todo.size() == 0) {
            log.info("next: No more files available");
            return null;
        }
        File current = todo.remove(0);
        log.info("Opening file '" + current + "'");
        try {
            RenamingFileStream in = new RenamingFileStream(current, postfix);
            Payload payload = new Payload(in);
            payload.getData().put(ORIGIN, current.getPath());
            log.debug("File '" + current + "' opened successfully");
//            System.out.println(delivered + " " + payload);
            delivered.add(payload);
            return payload;
        } catch (FileNotFoundException e) {
            //noinspection DuplicateStringLiteralInspection
            log.error("Could not locate '" + current
                      + "'. Skipping to next file");
            return next();
        }
    }

    /**
     * Graceful shutdown of opened files.
     * @param success if false, all opened files are closed immediately. If
     *                true, processed files are appended with {@link #postfix}
     *                and any currently opened files are kept open until they
     *                are emptied.
     */
    public synchronized void close(boolean success) {
        //noinspection DuplicateStringLiteralInspection
        log.debug("close(" + success + ") called");
        checkInit();
        //noinspection DuplicateStringLiteralInspection
        for (Payload payload: delivered) {
            if (payload.getStream() == null) {
                log.warn("close: Encountered payload without stream");
                continue;
            }
            if (!(payload.getStream() instanceof RenamingFileStream)) {
                log.warn("close: Encountered payload with stream that was not "
                         + "RenamingFileStream");
                continue;
            }
            RenamingFileStream stream = (RenamingFileStream)payload.getStream();
            stream.setSuccess(success);
            if (!success) {
                // Force close
                log.debug("Forcing close on payload " + payload);
                payload.close();
            }
        }
        if (todo.size() > 0) {
            log.debug("close: Discarding " + todo + " files from todo");
            todo.clear();
        }
        closedWithSuccess = success;
        // Note: if success, some streams might still be open.
    }

    /**
     * Pump iterates through all {@link #delivered} payloads and empties the
     * embedded streams. When all streams are emptied, a new payload is created.
     * @return true if pumping should continue, in order to process all data.
     * @throws IOException in case of read errors.
     */
    public synchronized boolean pump() throws IOException {
        checkInit();
        for (Payload payload: delivered) {
            if (payload.pump()) {
                return true;
            }
        }
        return hasNext() && next() != null;
    }

    /* Interface implementations */

    public boolean hasNext() {
        checkInit();
        return todo.size() > 0;
    }

    public void remove() {
        log.warn("Remove not implemented for FileReader");
    }
}



