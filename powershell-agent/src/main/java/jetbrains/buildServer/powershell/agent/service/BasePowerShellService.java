/*
 *  Copyright 2000 - 2017 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.powershell.agent.service;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.RunBuildException;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.runner.*;
import jetbrains.buildServer.powershell.agent.PowerShellCommandLineProvider;
import jetbrains.buildServer.powershell.agent.PowerShellInfoProvider;
import jetbrains.buildServer.powershell.agent.ScriptGenerator;
import jetbrains.buildServer.powershell.agent.detect.PowerShellInfo;
import jetbrains.buildServer.powershell.agent.system.PowerShellCommands;
import jetbrains.buildServer.powershell.common.PowerShellBitness;
import jetbrains.buildServer.powershell.common.PowerShellConstants;
import jetbrains.buildServer.powershell.common.PowerShellEdition;
import jetbrains.buildServer.powershell.common.PowerShellExecutionMode;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static jetbrains.buildServer.powershell.common.PowerShellConstants.*;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
public abstract class BasePowerShellService extends BuildServiceAdapter {

  static final Logger LOG = Logger.getInstance(BasePowerShellService.class.getName());

  @NotNull
  private PowerShellInfoProvider myInfoProvider;

  @NotNull
  private final ScriptGenerator myScriptGenerator;

  @NotNull
  private final PowerShellCommandLineProvider myCmdProvider;

  @NotNull
  final Collection<File> myFilesToRemove = new ArrayList<File>();

  @NotNull
  final PowerShellCommands myCommands;

  BasePowerShellService(@NotNull final PowerShellInfoProvider infoProvider,
                               @NotNull final ScriptGenerator scriptGenerator,
                               @NotNull final PowerShellCommandLineProvider cmdProvider,
                               @NotNull final PowerShellCommands commands) {
    myInfoProvider = infoProvider;
    myScriptGenerator = scriptGenerator;
    myCmdProvider = cmdProvider;
    myCommands = commands;
  }

  @NotNull
  @Override
  public ProgramCommandLine makeProgramCommandLine() throws RunBuildException {
    final PowerShellInfo info = selectTool();
    final String psExecutable = info.getExecutablePath();
    final String workDir = getWorkingDirectory().getPath();
    final PowerShellExecutionMode mode = PowerShellExecutionMode.fromString(getRunnerParameters().get(RUNNER_EXECUTION_MODE));
    final BuildProgressLogger buildLogger = getBuild().getBuildLogger();
    buildLogger.message("PowerShell Executable: " + psExecutable);
    buildLogger.message("Working directory: " + workDir);
    if (PowerShellExecutionMode.STDIN == mode) {
      return getStdInCommandLine(info, getEnv(info), workDir, generateCommand(info));
    } else if (PowerShellExecutionMode.PS1 == mode) {
      return getFileCommandLine(info, getEnv(info), workDir, generateArguments(info));
    } else {
      throw new RunBuildException("Could not select PowerShell tool for mode [" + mode + "]");
    }
  }

  @NotNull
  private String generateCommand(@NotNull final PowerShellInfo info) throws RunBuildException {
    final ParametersList parametersList = new ParametersList();
    final Map<String, String> runnerParameters = getRunnerParameters();
    final File scriptFile = myScriptGenerator.generateScript(runnerParameters, getCheckoutDirectory(), getBuildTempDirectory());
    // if  we have script entered in runner params it will be dumped to temp file. This file must be removed after build finishes
    if (ScriptGenerator.shouldRemoveGeneratedScript(runnerParameters)) {
      myFilesToRemove.add(scriptFile);
    }
    parametersList.add(info.getExecutablePath());
    parametersList.addAll(myCmdProvider.provideCommandLine(info, runnerParameters, scriptFile, useExecutionPolicy(info)));
    return parametersList.getParametersString();
  }

  private List<String> generateArguments(@NotNull final PowerShellInfo info) throws RunBuildException {
    final Map<String, String> runnerParameters = getRunnerParameters();
    final File scriptFile = myScriptGenerator.generateScript(runnerParameters, getCheckoutDirectory(), getBuildTempDirectory());
    // if  we have script entered in runner params it will be dumped to temp file. This file must be removed after build finishes
    if (ScriptGenerator.shouldRemoveGeneratedScript(runnerParameters)) {
      myFilesToRemove.add(scriptFile);
    }
    return myCmdProvider.provideCommandLine(info, runnerParameters, scriptFile, useExecutionPolicy(info));
  }

  private PowerShellInfo selectTool() throws RunBuildException {
    final PowerShellBitness bit = PowerShellBitness.fromString(getRunnerParameters().get(RUNNER_BITNESS));
    final String version = getRunnerParameters().get(RUNNER_MIN_VERSION);
    final PowerShellEdition edition = PowerShellEdition.fromString(getRunnerParameters().get(RUNNER_EDITION));
    final PowerShellInfo result = myInfoProvider.selectTool(bit, version, edition);
    if (result == null) {
      throw new RunBuildException("Could not select PowerShell for given bitness "
          + (bit == null ? "<Auto>" : bit.getDisplayName() + " and version "
          + (version == null ? "<Any>" : version)));
    }
    return result;
  }



  @Override
  public boolean isCommandLineLoggingEnabled() {
    return false;
  }

  protected abstract SimpleProgramCommandLine getStdInCommandLine(@NotNull final PowerShellInfo info,
                                                                  @NotNull final Map<String, String> env,
                                                                  @NotNull final String workDir,
                                                                  @NotNull final String command) throws RunBuildException;

  protected abstract SimpleProgramCommandLine getFileCommandLine(@NotNull final PowerShellInfo info,
                                                                 @NotNull final Map<String, String> env,
                                                                 @NotNull final String workDir,
                                                                 @NotNull final List<String> args) throws RunBuildException;

  protected abstract boolean useExecutionPolicy(@NotNull final PowerShellInfo info);

  protected abstract Map<String, String> getEnv(@NotNull final PowerShellInfo info);

  @NotNull
  @Override
  public List<ProcessListener> getListeners() {
    final boolean logToError = !StringUtil.isEmptyOrSpaces(getRunnerParameters().get(PowerShellConstants.RUNNER_LOG_ERR_TO_ERROR));
    final BuildProgressLogger logger = getLogger();
    return Collections.<ProcessListener>singletonList(new ProcessListenerAdapter() {
      private final org.apache.log4j.Logger OUT_LOG = org.apache.log4j.Logger.getLogger("teamcity.out");
      @Override
      public void onStandardOutput(@NotNull final String text) {
        logger.message(text);
        OUT_LOG.info(text);
      }

      @Override
      public void onErrorOutput(@NotNull final String text) {
        if (logToError) {
          logger.error(text);
        } else {
          logger.warning(text);
        }
        OUT_LOG.warn(text);
      }
    });
  }

  private boolean shouldKeepGeneratedFiles() {
    return getConfigParameters().containsKey(CONFIG_KEEP_GENERATED) || getConfigParameters().containsKey("teamcity.dont.delete.temp.files");
  }

  @Override
  public void afterProcessFinished() throws RunBuildException {
    super.afterProcessFinished();
    if (!shouldKeepGeneratedFiles()) {
      for (File file: myFilesToRemove) {
        FileUtil.delete(file);
      }
      myFilesToRemove.clear();
    }
  }
}
