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
package dk.statsbiblioteket.summa.search.api.dummy;

import dk.statsbiblioteket.summa.search.api.Response;
import dk.statsbiblioteket.util.Strings;

import java.util.ArrayList;

/**
 * {@link Response} object generated by {@link dk.statsbiblioteket.summa.search.dummy.SummaSearcherDummy} and
 * {@link dk.statsbiblioteket.summa.search.dummy.SearchNodeDummy}.
 */
public class DummyResponse implements Response {
    private static final long serialVersionUID = 12684L;
    
    protected String warmUps;
    protected String opens;
    protected String closes;
    protected String searches;
    protected ArrayList<String> ids;

    public DummyResponse (String id, int warmUps, int opens, int closes,
                          int searches) {
        this.warmUps = "" + warmUps;
        this.opens = "" + opens;
        this.closes = "" + closes;
        this.searches = "" + searches;
        ids = new ArrayList<String>(10);
        ids.add(id);
    }

    public String getName () {
        return "DummyResponse";
    }

    public void merge (Response other) throws ClassCastException {
        DummyResponse resp = (DummyResponse)other;
        warmUps += ", " + resp.warmUps;
        opens += ", " + resp.opens;
        closes += ", " + resp.closes;
        searches += ", " + resp.searches;
        ids.addAll(resp.ids);
    }

    public String toXML () {
        return String.format ("<DummyResponse>\n" +
                              "  <warmUps>%s</warmUps>\n"+
                              "  <ids>%s</ids>\n"+
                              "  <opens>%s</opens>\n"+
                              "  <closes>%s</closes>\n"+
                              "  <searches>%s</searches>\n"+
                              "</DummyResponse>",
                              warmUps, Strings.join(ids, ", "), opens, closes,
                              searches);
    }

    public ArrayList<String> getIds() {
        return ids;
    }
}




