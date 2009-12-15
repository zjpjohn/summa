package dk.statsbiblioteket.summa.storage.database;

import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.Files;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.util.StringMap;
import dk.statsbiblioteket.summa.storage.api.Storage;
import dk.statsbiblioteket.summa.storage.api.StorageFactory;
import dk.statsbiblioteket.summa.storage.api.QueryOptions;
import dk.statsbiblioteket.summa.storage.database.h2.H2Storage;

import java.io.File;
import java.util.List;
import java.util.Arrays;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * These test cases are meant to test functionality specifically requiring the
 * raw DatabaseStorage API which is not publicly available (ie. in the .api
 * package).
 *
 * @author mke
 * @since Dec 14, 2009
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke")
public class DatabaseStorageTest extends TestCase {
    private static Log log = LogFactory.getLog(DatabaseStorageTest.class);

    DatabaseStorage storage;

    static String testDBRoot = "test_db";
    static String dbPrefix = "db";
    static String testBase1 = "foobar";
    static String testBase2 = "frobnibar";
    static String testId1 = "testId1";
    static String testId2 = "testId2";
    static String testId3 = "testId3";
    static String testId4 = "testId4";
    static int storageCounter = 0;
    static byte[] testContent1 = new byte[] {'s', 'u', 'm', 'm', 'a'};
    static byte[] testContent2 = new byte[] {'b', '0', 'r', 'k'};
    long testStartTime;

    private static String lastStorageLocation = null;

    public static Configuration createConf () throws Exception {

        lastStorageLocation =
                testDBRoot + File.separator + dbPrefix + (storageCounter++);
        // H2 Config
        Configuration conf = Configuration.newMemoryBased(
                Storage.CONF_CLASS, H2Storage.class,
                DatabaseStorage.CONF_LOCATION, lastStorageLocation
        );

        // Derby Config
        /*Configuration conf = Configuration.newMemoryBased(
                Storage.CONF_CLASS,
                DerbyStorage.class,
                DatabaseStorage.CONF_LOCATION,
                testDBRoot + File.separator + dbPrefix + (storageCounter++)
        );*/

        // Postgres Config
        /*Configuration conf = Configuration.newMemoryBased(
                Storage.CONF_CLASS,
                PostgresStorage.class,
                DatabaseStorage.CONF_LOCATION,
                testDBRoot + File.separator + dbPrefix + (storageCounter++),
                DatabaseStorage.CONF_FORCENEW,
                true,
                DatabaseStorage.CONF_DATABASE,
                "summa",
                DatabaseStorage.CONF_USERNAME,
                "${user.name}",
                DatabaseStorage.CONF_PASSWORD,
                "",
                DatabaseStorage.CONF_HOST,
                ""
        );*/

        return conf;
    }

    public void setUp () throws Exception {
        File dbRoot = new File(testDBRoot);

        if (dbRoot.exists()) {
            Files.delete (dbRoot);
        }

        storage = (DatabaseStorage)StorageFactory.createStorage(createConf());

        /* We get spurious errors where the connection to the db isn't ready
         * when running the unit tests in batch mode */
        Thread.sleep(200);

        testStartTime = System.currentTimeMillis();
    }

    public void tearDown () throws Exception {
        log.info("Test case tear down commencing");

        storage.destroyDatabase();

        /* We get spurious errors where the connection to the db isn't ready
         * when running the unit tests in batch mode */
        Thread.sleep(200);

        storage.close();
        /* We get spurious errors where the connection to the db isn't ready
         * when running the unit tests in batch mode */
        Thread.sleep(200);
    }

    public void testStatsOnEmptyStorage() throws Exception {
        List<BaseStats> stats = storage.getStats();
        assertTrue(stats.isEmpty());
    }

    public void testStatsOnSingleRecord() throws Exception {
        long storageStart = storage.getModificationTime(null);
        Thread.sleep(2); // To make sure we have a timestamp delta
        storage.flush(new Record(testId1, testBase1, testContent1));
        List<BaseStats> stats = storage.getStats();

        assertEquals(1, stats.size());

        BaseStats base = stats.get(0);
        assertEquals(testBase1, base.getBaseName());
        assertEquals(1, base.getIndexableCount());
        assertEquals(0, base.getDeletedCount());
        assertEquals(1, base.getTotalCount());
        assertEquals(1, base.getLiveCount());
        assertTrue("Base mtime must be updated, but " +
                   "base.getModificationTime() <= storageStart: " +
                   base.getModificationTime() + " <= " +
                   storageStart,
                   base.getModificationTime() > storageStart);
    }

    public void testStatsOnTwoRecordsInTwoBases() throws Exception {
        storage.flush(new Record(testId1, testBase1, testContent1));
        storage.flush(new Record(testId2, testBase2, testContent1));
        List<BaseStats> stats = storage.getStats();

        assertEquals(2, stats.size());

        BaseStats base = stats.get(0);
        assertEquals(testBase1, base.getBaseName());
        assertEquals(1, base.getIndexableCount());
        assertEquals(0, base.getDeletedCount());
        assertEquals(1, base.getTotalCount());
        assertEquals(1, base.getLiveCount());

        base = stats.get(1);
        assertEquals(testBase2, base.getBaseName());
        assertEquals(1, base.getIndexableCount());
        assertEquals(0, base.getDeletedCount());
        assertEquals(1, base.getTotalCount());
        assertEquals(1, base.getLiveCount());
    }

    public void testStatsWithMixedStates() throws Exception {
        Record r1 = new Record(testId1, testBase1, testContent1);

        Record r2 = new Record(testId2, testBase1, testContent1);
        r2.setDeleted(true);

        Record r3 = new Record(testId3, testBase1, testContent1);
        r3.setIndexable(false);

        Record r4 = new Record(testId4, testBase1, testContent1);
        r4.setDeleted(true);
        r4.setIndexable(false);

        storage.flushAll(Arrays.asList(r1,r2,r3,r4));
        List<BaseStats> stats = storage.getStats();

        assertEquals(1, stats.size());

        BaseStats base = stats.get(0);
        assertEquals(testBase1, base.getBaseName());
        assertEquals(2, base.getIndexableCount());
        assertEquals(2, base.getDeletedCount());
        assertEquals(4, base.getTotalCount());
        assertEquals(1, base.getLiveCount());
    }

    public void testIllegalPrivateAccess() throws Exception {
        try {
            storage.getRecord("__holdings__", null);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // Good
        }
    }

    public void testGetHoldings() throws Exception {
        storage.flush(new Record(testId1, testBase1, testContent1));
        storage.flush(new Record(testId2, testBase2, testContent1));

        StringMap meta = new StringMap();
        meta.put("ALLOW_PRIVATE", "true");
        QueryOptions opts = new QueryOptions(null, null, 0, 0, meta);
        Record holdings = storage.getRecord("__holdings__", opts);
        String xml = holdings.getContentAsUTF8();

        assertTrue(xml.startsWith("<holdings"));
        assertTrue(xml.endsWith("</holdings>"));

        System.out.println(xml);
    }
}