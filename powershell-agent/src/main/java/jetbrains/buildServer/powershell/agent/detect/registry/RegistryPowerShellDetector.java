/*
 *  Copyright 2000 - 2017 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.powershell.agent.detect.registry;

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.powershell.agent.detect.DetectionContext;
import jetbrains.buildServer.powershell.agent.detect.PowerShellDetector;
import jetbrains.buildServer.powershell.agent.detect.PowerShellInfo;
import jetbrains.buildServer.powershell.common.PowerShellBitness;
import jetbrains.buildServer.powershell.common.PowerShellEdition;
import jetbrains.buildServer.util.Win32RegistryAccessor;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 *         03.12.10 14:43
 */
public class RegistryPowerShellDetector implements PowerShellDetector {

  @NotNull
  private static final Logger LOG = Logger.getLogger(RegistryPowerShellDetector.class);

  @NotNull
  private final Win32RegistryAccessor myAccessor;

  public RegistryPowerShellDetector(@NotNull final Win32RegistryAccessor accessor) {
    myAccessor = accessor;
  }

  @Override
  @NotNull
  public Map<PowerShellBitness, PowerShellInfo> findPowerShells(@NotNull final DetectionContext detectionContext) {
    LOG.info("Detecting PowerShell using RegistryPowerShellDetector");
    if (!SystemInfo.isWindows) {
      LOG.info("RegistryPowerShellDetector is only available on Windows");
      return Collections.emptyMap();
    }
    final Map<PowerShellBitness, PowerShellInfo> result = new HashMap<PowerShellBitness, PowerShellInfo>(2);
    for (PowerShellBitness bitness: PowerShellBitness.values()) {
      final PowerShellRegistry reg = new PowerShellRegistry(bitness.toBitness(), myAccessor);

      if (!reg.isPowerShellInstalled()) {
        LOG.debug("Powershell for " + bitness + " was not found.");
        continue;
      }

      final String ver = reg.getInstalledVersion();
      final File home = reg.getPowerShellHome();

      if (ver == null || home == null) {
        LOG.debug("Found powershell: " + bitness + " " + ver + " " + home);
        continue;
      }

      final PowerShellInfo info = new PowerShellInfo(bitness, home, ver, PowerShellEdition.DESKTOP);
      LOG.info("Found: " + info);
      result.put(info.getBitness(), info);
    }
    return result;
  }
}
