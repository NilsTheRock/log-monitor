package io.github.nils897.logmonitor;

/**
 * How far a single log file has already been scanned.
 *
 * @param byteOffset number of bytes that have been scanned, i.e. the position behind the last
 *                   complete line that was processed
 * @param lineCount  number of complete lines that have been scanned
 */
public record FileState(long byteOffset, long lineCount) {

    /** State of a file that has not been scanned yet. */
    public static final FileState INITIAL = new FileState(0, 0);
}
