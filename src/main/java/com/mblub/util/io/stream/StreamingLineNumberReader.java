package com.mblub.util.io.stream;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamingLineNumberReader extends LineNumberReader {

  public StreamingLineNumberReader(Reader in) {
    super(in);
  }

  public StreamingLineNumberReader(Reader in, int sz) {
    super(in, sz);
  }

  public static StreamingLineNumberReader newReader(Path path) throws UncheckedIOException {
    try {
      return new StreamingLineNumberReader(Files.newBufferedReader(path));
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  public Stream<NumberedLine> numberedLines() {
    Iterator<NumberedLine> iter = new Iterator<NumberedLine>() {
      String nextLine = null;

      @Override
      public boolean hasNext() {
        if (nextLine != null) {
          return true;
        }
        try {
          nextLine = readLine();
          return (nextLine != null);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public NumberedLine next() {
        if (nextLine != null || hasNext()) {
          String line = nextLine;
          nextLine = null;
          return new NumberedLine(getLineNumber(), line);
        }
        throw new NoSuchElementException();
      }
    };
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED | Spliterator.NONNULL),
            false);
  }

  public static final class NumberedLine {
    private final int number;
    private final String line;

    public NumberedLine(int number, String line) {
      this.number = number;
      this.line = line;
    }

    public int getNumber() {
      return number;
    }

    public String getLine() {
      return line;
    }

    @Override
    public String toString() {
      return number + ": " + line;
    }
  }
}
