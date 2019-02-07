package com.pervasivecode.utils.concurrent.example;

import java.io.PrintWriter;

/** A wrapper around a command-line application that makes it easy to run via Cucumber. */
public interface ExampleApplication {
  public void runExample(PrintWriter output) throws Exception;
}
