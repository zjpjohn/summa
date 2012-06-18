package org.apache.lucene.search.exposed;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.CompositeReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.exposed.facet.request.FacetRequestGroup;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

public class ExposedCache implements IndexReader.ReaderClosedListener {

  private final Set<IndexReader> readers = new HashSet<IndexReader>();
  private final ArrayList<TermProvider> cache = new ArrayList<TermProvider>(5);
  private final List<PurgeCallback> remoteCaches =
      new ArrayList<PurgeCallback>();

  private static final ExposedCache exposedCache;
  static {
    exposedCache = new ExposedCache();
  }
  public static ExposedCache getInstance() {
    return exposedCache;
  }

  private ExposedCache() {
  }

  public void addRemoteCache(PurgeCallback callback) {
    remoteCaches.add(callback);
  }

  public boolean removeRemoteCache(PurgeCallback callback) {
    return remoteCaches.remove(callback);
  }

  // NOTE: Reverse is ignored in providers
  public TermProvider getProvider(
      IndexReader reader, ExposedRequest.Group group) throws IOException {
    return getProvider(reader, group.getName(), group.getFieldNames(), 
        group.getComparator(), group.getComparatorID());
  }

  public TermProvider getProvider(
      IndexReader reader, String groupName, List<String> fieldNames,
      Comparator<BytesRef> comparator,String comparatorID)
                                                            throws IOException {
    if (readers.add(reader)) {
      reader.addReaderClosedListener(this);
    }
    ExposedRequest.Group groupRequest = FacetRequestGroup.createGroup(
        groupName, fieldNames, comparator, false, comparatorID);

    for (TermProvider provider: cache) {
      if (provider instanceof GroupTermProvider
          && ((GroupTermProvider) provider).getRequest().worksfor(groupRequest)
          && provider.getReaderHash() == reader.hashCode()) {
        return provider;
      }
    }
    if (ExposedSettings.debug) {
      System.out.println("Creating provider for " + groupName);
    }

     // No cached value. Modify the comparator IDs to LUCENE-order if they were
    // stated as free. Aw we create the query ourselves, this is okay.
    groupRequest.normalizeComparatorIDs();

    boolean isSingle = true;
    IndexReader[] readers;
    if (reader instanceof AtomicReader) {
      readers = new IndexReader[1];
      readers[0] = reader;
    } else {
      readers = ((CompositeReader)reader).getSequentialSubReaders();
      isSingle = false;
    }

    List<TermProvider> fieldProviders =
        new ArrayList<TermProvider>(readers.length * fieldNames.size());

    long fieldProviderConstruction = -System.currentTimeMillis();
    int docBase = 0;
    for (IndexReader sub: readers) {
      // TODO: Why is the docBase always 0?
/*      int docBase = ((IndexReader.AtomicReaderContext)sub.getTopReaderContext()).docBase;
      System.out.println("Skipping to reader of type " + sub.getClass().getSimpleName() + " with docBase=" + docBase + " and maxDoc=" + sub.maxDoc());*/
      for (ExposedRequest.Field fieldRequest: groupRequest.getFields()) {
        fieldProviders.add(getProvider(
            sub, isSingle ? 0 : docBase, fieldRequest, true, true));
      }
      // Used in DirectoryReader.initialize so it should be fairly safe
      docBase += sub.maxDoc();
    }
    fieldProviderConstruction += System.currentTimeMillis();

    long groupProviderconstruction = -System.currentTimeMillis();
    TermProvider groupProvider = new GroupTermProvider(
        reader.hashCode(), fieldProviders, groupRequest, true);
    groupProviderconstruction += System.currentTimeMillis();
    cache.add(groupProvider);

//    System.out.println("Field: " + fieldProviderConstruction
//        + "ms, group: " + groupProviderconstruction + "ms");
    return groupProvider;
  }

  FieldTermProvider getProvider(
      IndexReader segmentReader, int docIDBase, ExposedRequest.Field request,
      boolean cacheTables, boolean cacheProvider) throws IOException {
    if (readers.add(segmentReader)) {
      segmentReader.addReaderClosedListener(this);
    }
    for (TermProvider provider: cache) {
      if (provider instanceof FieldTermProvider) {
        if (provider.getRecursiveHash() == segmentReader.hashCode()
            && ((FieldTermProvider)provider).getRequest().equals(request)) {
          return (FieldTermProvider)provider;
        }
      }
    }
    FieldTermProvider provider =
        new FieldTermProvider(segmentReader, docIDBase, request, cacheTables);
    if (cacheProvider) {
      cache.add(provider);
    }
    return provider;
  }

  public void purgeAllCaches() {
    if (ExposedSettings.debug) {
      System.out.println("ExposedCache.purgeAllCaches() called");
    }
    cache.clear();
    for (PurgeCallback purger: remoteCaches) {
      purger.purgeAllCaches();
    }
  }

  /**
   * Purges all entries with connections to the given index reader. For exposed
   * structures that means all structures except the ones that are
   * FieldTermProviders not relying on the given index reader.
   * @param r the reader to purge.
   */
  public synchronized void purge(IndexReader r) {
    if (ExposedSettings.debug) {
      System.out.println("ExposedCache.purge(" + r + ") called");
    }
    Iterator<TermProvider> remover =
        cache.iterator();
    while (remover.hasNext()) {
      TermProvider provider = remover.next();
      if (!(provider instanceof FieldTermProvider) ||
          provider.getRecursiveHash() == r.hashCode()) {
        remover.remove();
      }
    }
    for (PurgeCallback purger: remoteCaches) {
      purger.purge(r);
    }
  }

  @Override
  public void onClose(IndexReader reader) {
    purge(reader);
  }

  public static interface PurgeCallback {
    void purgeAllCaches();
    void purge(IndexReader r);
  }

  public String toString() {
    StringWriter sw = new StringWriter();
    sw.append("ExposedCache(\n");
    for (TermProvider provider: cache) {
      sw.append("  ");
      sw.append(provider.toString());
      sw.append("\n");
    }
    sw.append(")");
    return sw.toString();

  }

  /**
   * Release all inner cached values for the term providers for all providers
   * that satisfies the given constraints.
   * @param keepRoots if true, providers at the root level should release their
   *                  inner caches.
   */
  public void transitiveReleaseCaches(boolean keepRoots) {
    for (TermProvider provider: cache) {
      provider.transitiveReleaseCaches(0, keepRoots);
    }
  }
}