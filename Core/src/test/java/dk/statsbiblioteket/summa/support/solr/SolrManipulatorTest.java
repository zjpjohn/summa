/**  Licensed under the Apache License, Version 2.0 (the "License");
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
package dk.statsbiblioteket.summa.support.solr;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.Resolver;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.common.filter.object.ObjectFilter;
import dk.statsbiblioteket.summa.common.unittest.PayloadFeederHelper;
import dk.statsbiblioteket.summa.index.IndexController;
import dk.statsbiblioteket.summa.index.IndexControllerImpl;
import dk.statsbiblioteket.summa.search.SearchNode;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.search.api.document.DocumentResponse;
import dk.statsbiblioteket.summa.support.embeddedsolr.EmbeddedJettyWithSolrServer;
import dk.statsbiblioteket.util.qa.QAInfo;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class SolrManipulatorTest extends TestCase {
    private static Log log = LogFactory.getLog(SolrManipulatorTest.class);
 
   	public static final String SOLR_HOME = "support/solr_home1"; //data-dir (index) will be created here.

    private EmbeddedJettyWithSolrServer server = null;

    public SolrManipulatorTest(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("basedir", ".");
        // TODO: Clear existing data
        server = new EmbeddedJettyWithSolrServer(SOLR_HOME);
        server.run();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        server.stopSolr();
    }

    public static Test suite() {
        return new TestSuite(SolrManipulatorTest.class);
    }

    public void testBasicIngest() throws Exception {
        ObjectFilter data = getDataProvider(false);
        ObjectFilter indexer = getIndexer();
        indexer.setSource(data);
        for (int i = 0 ; i < SAMPLES ; i++) {
            assertTrue("Check " + (i+1) + "/" + SAMPLES + ". There should be a next for the indexer",
                       indexer.hasNext());
            indexer.next();
        }
        assertFalse("After " + SAMPLES + " nexts, there should be no more Payloads", indexer.hasNext());
        indexer.close(true);
        log.debug("Finished basic ingest");
        verifyIndex();
    }

    public void testFaultyIngest() throws Exception {
        ObjectFilter data = new PayloadFeederHelper(Arrays.asList(
            new Payload(new Record("doc1", "dummy", "<doc>Invalid</doc>".getBytes("utf-8")))));
        ObjectFilter indexer = getIndexer();
        indexer.setSource(data);
        assertTrue("There should be a next for the indexer", indexer.hasNext());
        indexer.next();
        assertFalse("After 1 next, there should be no more Payloads", indexer.hasNext());
        indexer.close(true);
        log.debug("Finished basic ingest");
        SearchNode searcher = getSearcher();
        ResponseCollection responses = new ResponseCollection();
        searcher.search(new Request(
            DocumentKeys.SEARCH_QUERY, "text:first",
            SolrSearchNode.CONF_SOLR_PARAM_PREFIX + "fl", "recordId score title text"
        ), responses);
        assertTrue("There should be a response", responses.iterator().hasNext());
        assertEquals("There should be the right number of hits. Response was\n" + responses.toXML(),
                     0, ((DocumentResponse)responses.iterator().next()).getHitCount());
    }

    private void verifyIndex() throws Exception {
        SearchNode searcher = getSearcher();
        ResponseCollection responses = new ResponseCollection();
        searcher.search(new Request(
            DocumentKeys.SEARCH_QUERY, "text:first",
            SolrSearchNode.CONF_SOLR_PARAM_PREFIX + "fl", "recordId score title text"
        ), responses);
        assertTrue("There should be a response", responses.iterator().hasNext());
        assertEquals("There should be the right number of hits. Response was\n" + responses.toXML(),
                     1, ((DocumentResponse)responses.iterator().next()).getHitCount());

        String PHRASE = "Solr sample document";
        assertTrue("The result should contain the phrase '" + PHRASE + "'", responses.toXML().contains(PHRASE));
        searcher.close();
    }

    public void testDelete() throws Exception {
        testBasicIngest();
        SearchNode searcher = getSearcher();
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request(
            DocumentKeys.SEARCH_QUERY, "text:solr",
            SolrSearchNode.CONF_SOLR_PARAM_PREFIX + "fl", "recordId score title text"
        );
        searcher.search(request, responses);
        assertTrue("There should be a response", responses.iterator().hasNext());
        assertEquals("There should be the right number of hits. Response was\n" + responses.toXML(),
                     2, ((DocumentResponse)responses.iterator().next()).getHitCount());

        ObjectFilter data = getDataProvider(true);
        ObjectFilter indexer = getIndexer();
        indexer.setSource(data);
        int delCount = 0;
        while (indexer.hasNext()) {
            delCount++;
            indexer.next();
        }
        indexer.close(true);
        assertEquals("The number of Records send as deleted should match ingested Records", SAMPLES, delCount);

        responses.clear();
        searcher.search(request, responses);
        assertTrue("There should be a response after document delete", responses.iterator().hasNext());
        assertEquals("There should be the right number of hits after delete. Response was\n" + responses.toXML(),
                     0, ((DocumentResponse)responses.iterator().next()).getHitCount());
        searcher.close();
    }

    public void testClear() throws Exception {
        testBasicIngest();
        SearchNode searcher = getSearcher();
        Request request = new Request(
            DocumentKeys.SEARCH_QUERY, "text:solr",
            SolrSearchNode.CONF_SOLR_PARAM_PREFIX + "fl", "recordId score title text"
        );

        {
            ResponseCollection responses = new ResponseCollection();
            searcher.search(request, responses);
            assertTrue("There should be a response", responses.iterator().hasNext());
            assertEquals("There should be the right number of hits. Response was\n" + responses.toXML(),
                         2, ((DocumentResponse)responses.iterator().next()).getHitCount());
        }

        // Clear
        ObjectFilter indexer = getIndexer();
        ((IndexControllerImpl)indexer).clear();
        indexer.close(true);

        {
            ResponseCollection responses = new ResponseCollection();
            searcher.search(request, responses);
            assertTrue("There should be a response after document delete", responses.iterator().hasNext());
            assertEquals("There should be the right number of hits after delete. Response was\n" + responses.toXML(),
                         0, ((DocumentResponse)responses.iterator().next()).getHitCount());
        }
        searcher.close();
    }

    final int SAMPLES = 2;
    private ObjectFilter getDataProvider(boolean deleted) throws IOException {
        List<Payload> samples = new ArrayList<Payload>(SAMPLES);
        for (int i = 1 ; i <= SAMPLES ; i++) {
            Payload payload = new Payload(new Record(
                "doc" + i, "dummy", Resolver.getUTF8Content(
                "integration/solr/SolrSampleDocument" + i + ".xml").getBytes("utf-8")));
            payload.getRecord().setDeleted(deleted);
            samples.add(payload);
        }
        return new PayloadFeederHelper(samples);
    }

    private IndexController getIndexer() throws IOException {
        Configuration controllerConf = Configuration.newMemoryBased(
            IndexController.CONF_FILTER_NAME, "testcontroller");
        Configuration manipulatorConf = controllerConf.createSubConfigurations(
            IndexControllerImpl.CONF_MANIPULATORS, 1).get(0);
        manipulatorConf.set(IndexControllerImpl.CONF_MANIPULATOR_CLASS, SolrManipulator.class.getCanonicalName());
        manipulatorConf.set(SolrManipulator.CONF_ID_FIELD, "recordId"); // 'id' is the default ID field for Solr
        return new IndexControllerImpl(controllerConf);
    }

    private SearchNode getSearcher() throws RemoteException {
        return new SolrSearchNode(Configuration.newMemoryBased());
    }
}