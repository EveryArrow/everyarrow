package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a batch containing a list of chunks.
 */
public class Batch {
    /**
     * List of chunks in the batch.
     */
    private final List<Chunk> chunks;

    /**
     * Constructs a new Batch with an empty list of chunks.
     */
    public Batch() {
        this.chunks = new ArrayList<>();
    }

    /**
     * Constructs a new Batch with an empty list of chunks.
     */
    public Batch(List<Chunk> chunks) {
        this.chunks = chunks;
    }

    public static Batch of(Chunk... chunks) {
        Batch batch = new Batch();
        for (Chunk chunk : chunks) {
            batch.addChunk(chunk);
        }
        return batch;
    }

    /**
     * Divides the total number into batches of size batchSize and each batch containing chunks indices of size
     * chunkSize
     *
     * @param dataStore The DataStore object.
     * @param chunkSize Maximum size of chunk.
     * @param batchSize Maximum limit of chunks in a batch.
     * @return List of batches containing list of chunks indices.
     * @throws DataStoreException Thrown if row id does not exist in the DataStore.
     */
    public static List<Batch> divideIntoBatchesAndChunks(DataStoreInterface dataStore, int chunkSize, int batchSize) throws DataStoreException {
        return divideIntoBatchesAndChunks(dataStore, chunkSize, batchSize, 0, -1);
    }

    private static List<Batch> divideIntoBatchesAndChunks(DataStoreInterface dataStore, int chunkSize, int batchSize, int startIndex, int totalEndIndex) throws DataStoreException {
        if (chunkSize < 1)
            throw new IllegalArgumentException(String.format(
                    "Invalid chunk size %s", chunkSize));
        if (batchSize < 1)
            throw new IllegalArgumentException(String.format(
                    "Invalid batch size %s", batchSize));

        List<Batch> batches = new LinkedList<>();
        Batch currentBatch = new Batch();
        int total = totalEndIndex == -1 ? dataStore.size() : totalEndIndex + 1;

        for (int i = startIndex; i < total; i += chunkSize) {

            int endIndex = Math.min(i + chunkSize - 1, total - 1);
            int lastBreakPoint = i - 1;
            for (int j = i; j <= endIndex; ++j) {
                if (!dataStore.isValid(j)) {
                    if (lastBreakPoint == j - 1) {
                        lastBreakPoint = j;
                        continue;
                    }

                    // We have some rows in between that are valid
                    Chunk chunk = new Chunk(lastBreakPoint + 1, j - 1);
                    lastBreakPoint = j;
                    currentBatch.addChunk(chunk);

                    if (currentBatch.getChunks().size() == batchSize) {
                        batches.add(currentBatch);
                        currentBatch = new Batch();
                    }
                }
            }

            // If there are chunks that contain valid rows but have not been added to the batch
            if (lastBreakPoint != endIndex) {
                // Edge case: First row should not get skipped if it is valid
                if (lastBreakPoint == i && dataStore.isValid(i))
                    lastBreakPoint--;

                Chunk chunk = new Chunk(lastBreakPoint + 1, endIndex);
                currentBatch.addChunk(chunk);

                if (currentBatch.getChunks().size() == batchSize) {
                    batches.add(currentBatch);
                    currentBatch = new Batch();
                }
            }
        }

        if (!currentBatch.getChunks().isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    /**
     * Divides total into chunks of size chunkSize.
     *
     * @param dataStore The DataStore object.
     * @param chunkSize Maximum size of each chunk.
     * @return List containing chunks indices.
     * @throws DataStoreException Thrown if row id does not exist in the DataStore.
     */
    public static List<Chunk> divideIntoChunks(DataStoreInterface dataStore, int chunkSize) throws DataStoreException {
        return divideIntoBatchesAndChunks(dataStore, chunkSize, 1).stream().map(batch -> batch.getChunks().getFirst()).toList();
    }

    public static Batch divideIntoChunks(DataStoreInterface dataStore, int startIndex, int endIndex) throws DataStoreException {
        return divideIntoChunks(dataStore, startIndex, endIndex, 1000);
    }

    public static Batch divideIntoChunks(DataStoreInterface dataStore, int startIndex, int endIndex, int chunkSize) throws DataStoreException {
        return new Batch(divideIntoBatchesAndChunks(dataStore, chunkSize, 1, startIndex, endIndex).stream().map(batch -> batch.getChunks().getFirst()).toList());
    }

    /**
     * Adds a chunk to the batch.
     *
     * @param chunk The chunk to add.
     */
    public void addChunk(Chunk chunk) {
        chunks.add(chunk);
    }

    /**
     * Gets the list of chunks in the batch.
     *
     * @return The list of chunks.
     */
    public List<Chunk> getChunks() {
        return chunks;
    }

    @Override
    public String toString() {
        return "Batch{" +
                "chunks=" + chunks +
                '}';
    }
}

