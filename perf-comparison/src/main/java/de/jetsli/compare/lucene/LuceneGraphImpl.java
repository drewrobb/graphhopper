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

import de.jetsli.graph.storage.EdgeWithFlags;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.BitUtil;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.MyIteratorable;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StraightBytesDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class LuceneGraphImpl implements Graph {

    private static final String LAT = "_lat";
    private static final String LON = "_lon";
    // TODO hack around so that we can use the faster internal document ids for referencing in edges 
    // ... but then updating isn't possible!
    private static final String ID = "_id";
    private static final String EDGES = "edges";
    private Logger logger = LoggerFactory.getLogger(getClass());
    private IndexWriter writer;
    private DirectoryReader reader;
    private String storageDir;
    private boolean testMode;

    public LuceneGraphImpl(String storageDir) {
        this.storageDir = storageDir;
    }

    public LuceneGraphImpl setTestMode(boolean test) {
        testMode = test;
        return this;
    }

    public boolean init(boolean forceCreate) {
        try {
            Directory dir;
            if (storageDir != null) {
                File file = new File(storageDir);
                if (forceCreate)
                    Helper.deleteDir(file);

                dir = FSDirectory.open(file);
            } else
                dir = new RAMDirectory();

            IndexWriterConfig cfg = new IndexWriterConfig(Version.LUCENE_40, new KeywordAnalyzer());
            LogByteSizeMergePolicy mp = new LogByteSizeMergePolicy();
            mp.setMaxMergeMB(3000);
            cfg.setRAMBufferSizeMB(128);
            cfg.setTermIndexInterval(512);
            cfg.setMergePolicy(mp);
            cfg.setOpenMode(forceCreate ? IndexWriterConfig.OpenMode.CREATE : IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            writer = new IndexWriter(dir, cfg);
            return true;
        } catch (Exception ex) {
            logger.error("cannot init lucene storage", ex);
            return false;
        }
    }

    public void close() {
        Helper.close(writer);
    }

    public void flush() {
        try {
            // does not work:
            // reader = DirectoryReader.openIfChanged(reader, writer, true);
            // 
            // This at the moment not good for bulk indexing but faster to implement for now:
            writer.commit();
            if (reader != null)
                reader.close();
            reader = DirectoryReader.open(writer, true);
        } catch (Exception ex) {
            logger.error("cannot commit lucene", ex);
        }
    }

    public void ensureCapacity(int cap) {
    }

    public int getLocations() {
        if (reader == null)
            return 0;
        IndexSearcher searcher = new IndexSearcher(reader);
        try {
            TotalHitCountCollector coll = new TotalHitCountCollector();
            searcher.search(new MatchAllDocsQuery(), coll);
            return coll.getTotalHits();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private BytesRef getBytes(int integ) {
        // TODO if we reuse the bytesref we get a strange assert error in lucene => is this related to our StoredField? 
        // see Field javadocs "NOTE: the provided BytesRef is not copied so be sure not to change it until you're done with this field."
        BytesRef bytes = new BytesRef();
        NumericUtils.intToPrefixCoded(integ, 0, bytes);
        return bytes;
    }

    private void updateDoc(Document doc) {
        try {
            writer.updateDocument(new Term(ID, getBytes(doc.getField(ID).numericValue().intValue())), doc);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    void addId(Document doc) {
        if (doc.getField(ID) != null)
            throw new IllegalStateException("document must not have an id when adding an id");
        doc.add(new IntField(ID, locCounter, IntField.TYPE_STORED));
        locCounter++;
    }

    private void saveDoc(Document doc) {
        try {
            addId(doc);
            writer.addDocument(doc);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Document ensureDoc(int index) {
        Document d = getDoc(index);
        if (d == null)
            addId(d = new Document());
        return d;
    }

    private Document getDoc(int index) {
        try {
            if (reader == null)
                return null;
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs td = searcher.search(new TermQuery(new Term(ID, getBytes(index))), 2);
            if (td.totalHits > 1)
                throw new IllegalStateException("document exists more than once?? " + index);
            if (td.scoreDocs.length == 0)
                return null;
            return reader.document(td.scoreDocs[0].doc);
//            IndexReaderContext trc = searcher.getTopReaderContext();
//            for (AtomicReaderContext subreaderctx : trc.leaves()) {
//                AtomicReader subreader = subreaderctx.reader();
//                DocsEnum docs = subreader.terms(ID).iterator(null).docs(subreader.getLiveDocs(), null, false);
//                if (docs != null) {
//                    int docID = docs.nextDoc();
//                    if (docID != DocsEnum.NO_MORE_DOCS) {
//                        return subreader.document(docID);
//                    }
//                }
//            }
//            return null;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public double getLatitude(int index) {
        try {
            return getDoc(index).getField(LAT).numericValue().doubleValue();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public double getLongitude(int index) {
        try {
            return getDoc(index).getField(LON).numericValue().doubleValue();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    int locCounter = 0;

    public int addLocation(double lat, double lon) {
        int tmp = locCounter;
        Document doc = new Document();
        doc.add(new FloatField(LAT, (float) lat, FloatField.TYPE_STORED));
        doc.add(new FloatField(LON, (float) lon, FloatField.TYPE_STORED));
        saveDoc(doc);

        if (testMode)
            flush();
        return tmp;
    }

    public void edge(int fromId, int toId, double distance, boolean bothDirections) {
        Document from = ensureDoc(fromId);
        Document to = ensureDoc(toId);
        byte flags = 3;
        if (!bothDirections)
            flags = 1;

        createEdge(from, to, toId, distance, flags);
        updateDoc(from);
        if (!bothDirections)
            flags = 2;
        createEdge(to, from, fromId, distance, flags);
        updateDoc(to);

        if (testMode)
            flush();
    }

    private static BytesRef toBytesRef(EdgeWithFlags e) {
        BytesRef br = new BytesRef(4 + 8 + 1);
        BitUtil.fromInt(br.bytes, e.node, 0);
        BitUtil.fromDouble(br.bytes, e.distance, 4);
        br.bytes[12] = e.flags;
        return br;
    }

    private static EdgeWithFlags extractEdge(BytesRef ref) {
        if (ref.length - ref.offset != 13)
            throw new IllegalStateException("ref.length is " + ref.length + ", ref.offset is " + ref.offset);

        int id = BitUtil.toInt(ref.bytes, ref.offset + 0);
        double dist = BitUtil.toDouble(ref.bytes, ref.offset + 8);
        return new EdgeWithFlags(id, dist, ref.bytes[ref.offset + 12]);
    }

    public void createEdge(Document from, Document to, int toId, double distance, byte flags) {
        Iterator<IndexableField> iter = from.iterator();
        EdgeWithFlags e = null;
        while (iter.hasNext()) {
            IndexableField f = iter.next();
            if (!f.name().equals(EDGES))
                continue;
            e = extractEdge(f.binaryValue());
            if (e.node == toId) {
                iter.remove();
                e.distance = distance;
                e.flags = flags;
                break;
            }
        }
        if (e == null)
            e = new EdgeWithFlags(toId, distance, flags);
//         from.add(new StoredField(EDGES, toBytesRef(e)));
        from.add(new StraightBytesDocValuesField(EDGES, toBytesRef(e), true));
    }

    public MyIteratorable<EdgeWithFlags> getEdges(int index) {
        return new MyLuceneIterable(getDoc(index));
    }

    public MyIteratorable<EdgeWithFlags> getIncoming(int index) {
        return new MyLuceneIterable(getDoc(index));
    }

    public MyIteratorable<EdgeWithFlags> getOutgoing(int index) {
        return new MyLuceneIterable(getDoc(index));
    }

    private static class MyLuceneIterable extends MyIteratorable<EdgeWithFlags> {

        IndexableField[] fields;
        int counter = 0;

        public MyLuceneIterable(Document node) {
            if (node == null)
                throw new IllegalStateException("Couldn't find node");
            fields = node.getFields(EDGES);
        }

        public boolean hasNext() {
            return counter < fields.length;
        }

        public EdgeWithFlags next() {
            EdgeWithFlags e = extractEdge(fields[counter].binaryValue());
            counter++;
            return e;
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    public Graph clone() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean markDeleted(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean isDeleted(int index) {
        return false;
    }

    public void optimize() {
        try {
            logger.info("starting optimize");
            writer.forceMerge(1);
            logger.info("finished optimize");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
