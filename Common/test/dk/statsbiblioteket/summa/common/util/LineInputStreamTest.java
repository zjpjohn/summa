/* $Id$
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
package dk.statsbiblioteket.summa.common.util;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;
import dk.statsbiblioteket.util.qa.QAInfo;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;

@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class LineInputStreamTest extends TestCase {
    public LineInputStreamTest(String name) {
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
        return new TestSuite(LineInputStreamTest.class);
    }

    public void testBasics() throws Exception {
        // Source, expected
        String[][][] TESTS = new String[][][] {
                {{"Foo\nBar"}, {"Foo", "Bar"}}
        };
        for (String[][] test: TESTS) {
            String source = test[0][0];
            String[] expected = test[1];
            assertLines(source, expected);
        }
    }

    private void assertLines(String source, String[] expected) throws Exception{
        ByteArrayInputStream in = new ByteArrayInputStream(
                source.getBytes("utf-8"));
        LineInputStream lis = new LineInputStream(in);
        String line;
        int pos = 0;
        while ((line = lis.readLine()) != null) {
            assertEquals(expected[pos++], line);
        }
    }
}
