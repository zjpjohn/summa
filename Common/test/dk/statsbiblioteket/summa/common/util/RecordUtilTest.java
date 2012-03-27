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
package dk.statsbiblioteket.summa.common.util;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;
import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Resolver;
import dk.statsbiblioteket.summa.common.unittest.ExtraAsserts;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.Arrays;
import java.net.URL;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

@SuppressWarnings({"DuplicateStringLiteralInspection"})
public class RecordUtilTest extends TestCase {
    private static Log log = LogFactory.getLog(RecordUtilTest.class);

    public RecordUtilTest(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public static Test suite() {
        return new TestSuite(RecordUtilTest.class);
    }

    private static final URL schema = Resolver.getURL(
            "Record.xsd");
    public void testSingleRecord() throws Exception {
        Record record = new Record(
                "foo&<", "/>baze",
                "Hello <![CDATA[&<]]> world".getBytes("utf-8"));
        String xml = RecordUtil.toXML(record);
        assertNotNull("The schema Record.xsd should be available",
                      schema.getFile());
        ExtraAsserts.assertValidates("single-level Record should validate",
                                     schema, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "\n" + xml);
        assertEquals("fromXML(toXML(simpleRecord)) should work",
                     record, RecordUtil.fromXML(xml));
        log.info("Generated XML:\n" + xml);
    }

    public void testRecordWithRelatives() throws Exception {
        Record record = new Record(
                "middleman", "bar",
                "<marc_xml_or_other_xml ...>Malcolm in the middle".getBytes("utf-8"));
        Record parent = new Record(
                "parent", "bar",
                "<marc_xml_or_other_xml ...>I am a parent Record".getBytes("utf-8"));
        Record child1 = new Record(
                "child_1", "bar",
                "<marc_xml_or_other_xml ...>I am a child".getBytes("utf-8"));
        Record child2 = new Record(
                "child_2", "bar",
                "<marc_xml_or_other_xml ...>I am another child".getBytes("utf-8"));
        record.setParents(Arrays.asList(parent));
        record.setChildren(Arrays.asList(child1, child2));

        String xml = RecordUtil.toXML(record);
        log.debug("Got content for " + record + ":\n" + xml);
        ExtraAsserts.assertValidates("single-level Record should validate",
                                     schema, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "\n" + xml);
        assertEquals("fromXML(toXML(simpleRecord)) for content should work",
                     record.getContentAsUTF8(), 
                     RecordUtil.fromXML(xml).getContentAsUTF8());
        assertEquals("fromXML(toXML(simpleRecord)) should work",
                     record, RecordUtil.fromXML(xml));
        log.info("Generated XML:\n" + xml);
    }

    public void testSingleNonEscaping() throws Exception {
        Record record = new Record(
                "middleman", "bar", // Plain
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<foo><bar/></foo>").getBytes("utf-8"));
        log.debug("Raw content for root:\n" + record.getContentAsUTF8());
        String xml = RecordUtil.toXML(record, false);
        log.debug("Got content for " + record + ":\n" + xml);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                          + "<foo><bar></bar></foo>";
        assertEquals("fromXML(toXML()) non-escaped content should work",
                     expected,
                     RecordUtil.fromXML(xml).getContentAsUTF8());
    }

    public void testEscaping() throws Exception {
        Record record = createRecordWithChildren();
        log.debug("Raw content for root:\n" + record.getContentAsUTF8());
        String xml = RecordUtil.toXML(record, true);
        log.debug("Got content for " + record + ":\n" + xml);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                          + "<foo><bar/></foo>";
        assertEquals("fromXML(toXML()) non-escaped content should work",
                     expected,
                     RecordUtil.fromXML(xml).getContentAsUTF8());
    }

    public void testNonEscaping() throws Exception {
        Record record = createRecordWithChildren();
        log.debug("Raw content for root:\n" + record.getContentAsUTF8());
        String xml = RecordUtil.toXML(record, false);
        log.debug("Got content for " + record + ":\n" + xml);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                          + "<foo><bar></bar></foo>";
        assertEquals("fromXML(toXML()) non-escaped content should work",
                     expected,
                     RecordUtil.fromXML(xml).getContentAsUTF8());
    }

    public void testXMLParsing() throws Exception {
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                         + "<foo><bar/></foo>";
        XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(
                new StringReader(content));
        reader.next();

        reader = xmlInputFactory.createXMLStreamReader(new StringReader(
                createRecordWithChildren().getContentAsUTF8()));
        reader.next();

        String xml = RecordUtil.toXML(createRecordWithChildren(), false);
        log.debug("toXML resulted in\n" + xml);
        reader = xmlInputFactory.createXMLStreamReader(new StringReader(xml));
        reader.next();

    }

    private Record createRecordWithChildren() throws UnsupportedEncodingException {
        Record record = new Record(
                "middleman", "bar", // Plain
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<foo><bar/></foo>").getBytes("utf-8"));
        Record child1 = new Record(
                "child_1", "bar", // BOM
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<zoo>kabloey &amp; kaflam</zoo>").getBytes("utf-8"));
        Record child2 = new Record(
                "child_2", "bar", // No declaration
                "<subsub>&lt;</subsub>".getBytes("utf-8"));
        record.setChildren(Arrays.asList(child1));
        child1.setChildren(Arrays.asList(child2));
        return record;
    }

    // TODO: Test BOM

    private static XMLOutputFactory xmlOutputFactory =
            XMLOutputFactory.newInstance();
    private static XMLInputFactory xmlInputFactory =
            XMLInputFactory.newInstance();
    
    public void testCopyContent() throws Exception {
        String base = "<foo><bar /> <!-- comment <hello> --> aloha "
                      + "<![CDATA[ my&<>CDATA ]]> <empty></empty> </foo>";
        String in = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + base;
        XMLStreamReader sr =
                xmlInputFactory.createXMLStreamReader(new StringReader(in));
        StringWriter out = new StringWriter(1000);
        XMLStreamWriter sw = xmlOutputFactory.createXMLStreamWriter(out);
        RecordUtil.copyContent(sr, sw, true, -1);

        String expected = "<foo><bar></bar> <!-- comment <hello> --> aloha "
                      + " my&amp;&lt;&gt;CDATA  <empty></empty> </foo>";

        log.debug(out.toString());
        assertEquals("Copy should strip header", expected, out.toString());
    }
    
    public void testToXML() throws Exception {
        StringWriter out = new StringWriter(1000);        
        XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(out);
        
        Record r1 = createRecordWithChildren();
        writer.writeStartDocument();
        RecordUtil.toXML(writer, 0, r1, true);
        writer.writeEndDocument();

        assertNotNull(out.toString());
        //System.out.println(out.toString());
    }
    
}

