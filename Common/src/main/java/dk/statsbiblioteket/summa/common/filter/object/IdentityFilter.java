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
package dk.statsbiblioteket.summa.common.filter.object;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

/**
 * Passes all Payloads unchanged. Can be used with a muxer for processing some
 * Payloads while not touching others.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class IdentityFilter extends ObjectFilterImpl  {
    private static Log log = LogFactory.getLog(IdentityFilter.class);

    public IdentityFilter(Configuration conf) {
        super(conf);
        log.debug("Created IdentityFilter");
    }

    @Override
    protected boolean processPayload(Payload payload) throws PayloadException {
        if (log.isDebugEnabled()) {
            log.debug("Encountered " + payload + ", passing it on");
        }
        return true;
    }
}