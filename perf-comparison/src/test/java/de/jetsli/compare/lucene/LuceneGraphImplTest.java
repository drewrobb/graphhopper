/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.compare.lucene;

import de.jetsli.compare.misc.AbstractGraphTester;
import de.jetsli.graph.storage.Graph;

/**
 *
 * @author Peter Karich
 */
public class LuceneGraphImplTest extends AbstractGraphTester {

    @Override
    protected Graph createGraph(int size) {
        LuceneGraphImpl l = new LuceneGraphImpl(null).setTestMode(true);
        l.init(true);
        return l;
    }
}
