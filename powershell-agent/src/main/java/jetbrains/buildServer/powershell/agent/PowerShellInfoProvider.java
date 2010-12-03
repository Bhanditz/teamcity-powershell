/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.powershell.agent;

import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.powershell.agent.detect.PowerShellDetector;
import jetbrains.buildServer.powershell.agent.detect.PowerShellInfo;
import jetbrains.buildServer.powershell.common.PowerShellBitness;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 *         03.12.10 16:24
 */
public class PowerShellInfoProvider {
  private final BuildAgentConfiguration myConfig;

  public PowerShellInfoProvider(@NotNull final BuildAgentConfiguration config,
                                @NotNull final EventDispatcher<AgentLifeCycleListener> events,
                                @NotNull final PowerShellDetector detector) {
    myConfig = config;
    events.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void beforeAgentConfigurationLoaded(@NotNull final BuildAgent agent) {
        registerDetectedPowerShells(detector);
        events.removeListener(this);
      }
    });
  }

  private void registerDetectedPowerShells(@NotNull final PowerShellDetector detector) {
    for (PowerShellInfo info : detector.findPowerShells()) {
      info.saveInfo(myConfig);
    }
  }

  @NotNull
  public Collection<PowerShellInfo> getPowerShells() {
    Collection<PowerShellInfo> infos = new ArrayList<PowerShellInfo>(2);
    for (PowerShellBitness bit : PowerShellBitness.values()) {
      final PowerShellInfo i = PowerShellInfo.loadInfo(myConfig, bit);
      if (i != null) {
        infos.add(i);
      }
    }
    return infos;
  }
}
