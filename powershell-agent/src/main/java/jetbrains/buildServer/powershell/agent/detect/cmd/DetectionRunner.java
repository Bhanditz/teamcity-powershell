/*
 *  Copyright 2000 - 2017 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.powershell.agent.detect.cmd;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.powershell.agent.Loggers;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
public class DetectionRunner {

  private static final Logger LOG = Loggers.DETECTION_LOGGER;

  /**
   * Runs detection script
   *
   * @param executablePath executable to run script with
   * @param detectionScriptPath file containing detection script
   * @return lines from stdout
   * @throws ExecutionException if there was an error during execution
   */
  List<String> runDetectionScript(@NotNull final String executablePath,
                                  @NotNull final String detectionScriptPath) throws ExecutionException {
    final GeneralCommandLine cl = new GeneralCommandLine();
    cl.setExePath(executablePath);
    cl.addParameter("-NoProfile");
    cl.addParameter("-File");
    cl.addParameter(detectionScriptPath);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Running detection script using command line: " + cl.getCommandLineString());
    }
    final ProcessOutput execResult = runProcess(cl);
    return execResult.getStdoutLines();
  }

  private static ProcessOutput runProcess(@NotNull final GeneralCommandLine cl) throws ExecutionException {
    final CapturingProcessHandler handler = new CapturingProcessHandler(cl.createProcess(), Charset.forName("UTF-8"));
    final ProcessOutput result = handler.runProcess(20000);
    if (result.isTimeout()) {
      throw new ExecutionException("Process execution of [" + cl.getCommandLineString() + "] has timed out");
    }
    final String errorOutput = result.getStderr();
    if (!StringUtil.isEmptyOrSpaces(errorOutput)) {
      throw new ExecutionException(errorOutput);
    }
    return result;
  }
}
