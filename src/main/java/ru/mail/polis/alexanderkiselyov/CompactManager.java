package ru.mail.polis.alexanderkiselyov;

import ru.mail.polis.BaseEntry;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CompactManager {
    private final BlockingQueue<Boolean> compactQueue;
    private final FileOperations fileOperations;
    private final ScheduledExecutorService compactService;
    private final ScheduledFuture<?> compactResult;

    public CompactManager(FileOperations fileOperations) {
        compactQueue = new LinkedBlockingQueue<>();
        compactService = Executors.newSingleThreadScheduledExecutor();
        this.fileOperations = fileOperations;
        compactResult = compactService.scheduleAtFixedRate(compactOperation, 0, 500,
                TimeUnit.MILLISECONDS);
    }

    public void performCompact(boolean hasPairs) {
        compactQueue.add(hasPairs);
    }

    public void closeCompactThreadService() {
        compactService.shutdown();
        if (compactResult != null && !compactResult.isDone()) {
            try {
                if (!compactService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS))
                {
                    throw new RuntimeException("Compact operation was terminated.");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted compaction service: " + e);
            }
        }
    }

    Runnable compactOperation = new Runnable() {
        @Override
        public void run() {
            try {
                if (compactQueue.size() != 0) {
                    compactOperation(compactQueue.poll());
                }
            } catch (IOException e) {
                throw new RuntimeException("Error during compaction: " + e);
            }
        }
    };

    private void compactOperation(boolean hasPairs) throws IOException {
        Iterator<BaseEntry<Byte[]>> iterator;
        iterator = new SkipNullValuesIterator(
                new IndexedPeekIterator(0, fileOperations.diskIterator(null, null))
        );
        if (!iterator.hasNext()) {
            return;
        }
        fileOperations.compact(iterator, hasPairs);
    }
}
