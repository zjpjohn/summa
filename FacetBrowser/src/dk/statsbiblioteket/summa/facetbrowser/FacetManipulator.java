/* $Id$
 * $Revision$
 * $Date$
 * $Author$
 *
 * The Summa project.
 * Copyright (C) 2005-2008  The State and University Library
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
package dk.statsbiblioteket.summa.facetbrowser;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.facetbrowser.build.Builder;
import dk.statsbiblioteket.summa.facetbrowser.build.BuilderFactory;
import dk.statsbiblioteket.summa.facetbrowser.core.map.CoreMap;
import dk.statsbiblioteket.summa.facetbrowser.core.map.CoreMapFactory;
import dk.statsbiblioteket.summa.facetbrowser.core.tags.TagHandler;
import dk.statsbiblioteket.summa.facetbrowser.core.tags.TagHandlerFactory;
import dk.statsbiblioteket.summa.index.IndexManipulator;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;

/**
 * To be used under the Summa Index-framework. This manipulator maintains a
 * persistent view on Facet-information coupled to a document index (such as
 * Lucene). The manipulator allows for iterative updates and is capable of
 * doing a complete rebuild if needed.
 * </p><p>
 * This class is abstract and a document searcher specific handling of updates
 * needs to be implemented. The base case is Lucene support, where the
 * implementation extracts the contents of fields and rebuilds based on a
 * complete index.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class FacetManipulator implements IndexManipulator {
    private static Logger log = Logger.getLogger(FacetManipulator.class);

    /**
     * If true, both the mapping from docID=>Tag and the Tags themselves are
     * cleared when a remove is called. If false, only the mapping is cleared.
     * Clearing Tags means that non-used Tags are removed at the cost of
     * increased rebuild-time.
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_CLEAR_TAGS_ON_CLEAR =
            "summa.facet.cleartagsonclear";
    public static final boolean DEFAULT_CLEAR_TAGS_ON_CLEAR = true;

    /**
     * If true, both the mapping from docID=>Tag and the Tags themselves are
     * cleared when a consolidate is called. If false, only the mapping is
     * cleared. A full facet-rebuild is fairly fast (~10 minutes for
     * 8 million records for the corpus at Statsbiblioteket).
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_CLEAR_TAGS_ON_CONSOLIDATE =
            "summa.facet.cleartagsonconsolidate";
    public static final boolean DEFAULT_CLEAR_TAGS_ON_CONSOLIDATE = true;

    /**
     * If true, both the mapping from docID=>Tag and the Tags themselves are
     * cleared when a commit is called and {@link #CONF_SKIP_FACET_ON_UPDATE}
     * is true. If false, only the mapping is cleared.
     * </p><p>
     * If commits are very frequent, {@link #CONF_SKIP_FACET_ON_UPDATE} should
     * be false and clear tags on commit is irrelevant. If there is a fairly
     * long time between commits (10,000+, depending on hardware), clear tags
     * on commit as well as {@link #CONF_SKIP_FACET_ON_UPDATE} should be true.
     * If there is a shorter time between commits, clear tags on commit should
     * be false and {@link #CONF_SKIP_FACET_ON_UPDATE} true. 
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_CLEAR_TAGS_ON_COMMIT =
            "summa.facet.cleartagsoncommite";
    public static final boolean DEFAULT_CLEAR_TAGS_ON_COMMIT = true;

    /**
     * If true, the facet structure isn't updated when {@link #update} is
     * called. A side-effect is that the facet structure is generated upon
     * commit (and consolidate, but that is always the case).
     * </p><p>
     * As iterative updates of the facet structure is O(n*m) and re-build is
     * O(n*log(m)), setting this to true is best for large batch-updates.
     * </p><p>
     * Optional. Default is false.
     */
    public static final String CONF_SKIP_FACET_ON_UPDATE =
            "summa.facet.skipfacetonupdate";
    public static final boolean DEFAULT_SKIP_FACET_ON_UPDATE = false;

    protected boolean clearTagsOnClear = DEFAULT_CLEAR_TAGS_ON_CLEAR;
    protected boolean clearTagsOnConsolidate =
            DEFAULT_CLEAR_TAGS_ON_CONSOLIDATE;
    protected boolean clearTagsOnCommit = DEFAULT_CLEAR_TAGS_ON_COMMIT;
    protected boolean skipFacetOnUpdate = false;

    /**
     * The builder is responsible for all manipulations of the structure for
     * facet/tags. The FacetManipulator is just a wrapper.
     */
    protected Builder builder;
    private boolean orderChanged = false; // Since last commit

    public FacetManipulator(Configuration conf) throws RemoteException {
        log.info("Constructing FacetManipulator");
        clearTagsOnClear = conf.getBoolean(CONF_CLEAR_TAGS_ON_CLEAR,
                                           DEFAULT_CLEAR_TAGS_ON_CLEAR);
        clearTagsOnConsolidate = conf.getBoolean(
                CONF_CLEAR_TAGS_ON_CONSOLIDATE,
                DEFAULT_CLEAR_TAGS_ON_CONSOLIDATE);
        clearTagsOnCommit = conf.getBoolean(
                CONF_CLEAR_TAGS_ON_COMMIT, DEFAULT_CLEAR_TAGS_ON_COMMIT);
        skipFacetOnUpdate = conf.getBoolean(
                CONF_SKIP_FACET_ON_UPDATE, DEFAULT_SKIP_FACET_ON_UPDATE);
        Structure structure = new Structure(conf);
        TagHandler tagHandler =
                TagHandlerFactory.getTagHandler(conf, structure, false);
        CoreMap coreMap = CoreMapFactory.getCoreMap(conf, structure);
        builder =
                BuilderFactory.getBuilder(conf, structure, coreMap, tagHandler);
        log.info(String.format(
                "FacetManipulator(clearTagsOnClear=%b, clearTagsOnConsolidate=%"
                + "b, skipFacetOnUpdate=%b) created with facets %s",
                clearTagsOnClear, clearTagsOnConsolidate, skipFacetOnUpdate,
                Strings.join(structure.getFacetNames(), ", ")));
    }

    public void clear() throws IOException {
        builder.clear(!clearTagsOnClear);
    }

    public void close() throws IOException {
        builder.close();
    }

    public void commit() throws IOException {
        if (skipFacetOnUpdate || orderChanged) {
            log.debug(String.format(
                    "skipFacetOnUpdate == %b, orderChanged == %b. "
                    + "Rebuilding facet structure",
                    skipFacetOnUpdate, orderChanged));
            builder.build(!clearTagsOnCommit);
        }
        builder.store();
        orderChanged = false;
    }

    public void consolidate() throws IOException {
        log.debug("Consolidating Facets");
        builder.build(!clearTagsOnConsolidate);
        builder.store();
    }

    // TODO: Auto-rebuild on missing facets
    public void open(File indexRoot) throws IOException {
        log.debug(String.format(
                "Facetmanipulator.open(%s) called", indexRoot));
        if (indexRoot == null) {
            log.debug("open(null) called, which is equivalent to close()");
            close();
            return;
        }
        builder.open(indexRoot);
        //noinspection DuplicateStringLiteralInspection
        log.trace("open(" + indexRoot + ") finished");
    }

    public synchronized boolean update(Payload payload) throws IOException {
        if (skipFacetOnUpdate) {
            if (log.isTraceEnabled()) {
                log.trace("Skipping facet update as "
                          + CONF_SKIP_FACET_ON_UPDATE
                          + " is true. Payload skipped is " + payload);
            }
            return false;
        }
        if (orderChanged) {
            if (log.isTraceEnabled()) {
                log.trace("Skipping facet update as orderChangedSinceLastConso"
                          + "lidate == true. Payload skipped is " + payload);
            }
            return false;
        }
        if (log.isTraceEnabled()) {
            //noinspection DuplicateStringLiteralInspection
            log.trace("Adding " + payload + " to Facet structure");
        }
        return !builder.update(payload);
    }

    @Override
    public void orderChangedSinceLastCommit() {
        orderChanged = true;
    }

    /**
     * The FacetManipulator never sets orderChanged to true by itself. It only
     * reacts on external messages.
     * @return true if the order has been marked as changed since last commit
     *              or consolidate.
     */
    @Override
    public boolean isOrderChangedSinceLastCommit() {
        return orderChanged;
    }
}
