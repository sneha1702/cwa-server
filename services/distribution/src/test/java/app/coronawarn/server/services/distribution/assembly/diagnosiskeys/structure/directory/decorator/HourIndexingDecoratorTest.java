/*
 * ---license-start
 * Corona-Warn-App
 * ---
 * Copyright (C) 2020 SAP SE and all other contributors
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package app.coronawarn.server.services.distribution.assembly.diagnosiskeys.structure.directory.decorator;

import static app.coronawarn.server.services.distribution.common.Helpers.buildDiagnosisKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.coronawarn.server.common.persistence.domain.DiagnosisKey;
import app.coronawarn.server.services.distribution.assembly.component.CryptoProvider;
import app.coronawarn.server.services.distribution.assembly.diagnosiskeys.DiagnosisKeyBundler;
import app.coronawarn.server.services.distribution.assembly.diagnosiskeys.ProdDiagnosisKeyBundler;
import app.coronawarn.server.services.distribution.assembly.diagnosiskeys.structure.directory.DiagnosisKeysHourDirectory;
import app.coronawarn.server.services.distribution.assembly.structure.util.ImmutableStack;
import app.coronawarn.server.services.distribution.assembly.structure.util.TimeUtils;
import app.coronawarn.server.services.distribution.config.DistributionServiceConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@EnableConfigurationProperties(value = DistributionServiceConfig.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CryptoProvider.class, DistributionServiceConfig.class},
    initializers = ConfigFileApplicationContextInitializer.class)
class HourIndexingDecoratorTest {

  @Autowired
  DistributionServiceConfig distributionServiceConfig;

  @Autowired
  CryptoProvider cryptoProvider;

  private DiagnosisKeyBundler diagnosisKeyBundler;

  @BeforeEach
  void setup() {
    diagnosisKeyBundler = new ProdDiagnosisKeyBundler(distributionServiceConfig);
  }

  @AfterEach
  void tearDown() {
    TimeUtils.setNow(null);
  }

  @Test
  void excludesHoursThatExceedTheMaximumNumberOfKeysPerBundle() {
    List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(6, LocalDateTime.of(1970, 1, 3, 4, 0), 2);

    TimeUtils.setNow(LocalDateTime.of(1970, 1, 3, 0, 0).toInstant(ZoneOffset.UTC));

    DistributionServiceConfig svcConfig = mock(DistributionServiceConfig.class);
    when(svcConfig.getExpiryPolicyMinutes()).thenReturn(120);
    when(svcConfig.getShiftingPolicyThreshold()).thenReturn(1);
    when(svcConfig.getMaximumNumberOfKeysPerBundle()).thenReturn(1);

    DiagnosisKeyBundler diagnosisKeyBundler = new ProdDiagnosisKeyBundler(svcConfig);
    diagnosisKeyBundler.setDiagnosisKeys(diagnosisKeys, LocalDateTime.of(1970, 1, 3, 5, 0));

    HourIndexingDecorator decorator = makeDecoratedHourDirectory(diagnosisKeyBundler);

    decorator.prepare(new ImmutableStack<>().push("DE").push(LocalDate.of(1970, 1, 3)));
    Set<LocalDateTime> index = decorator.getIndex(new ImmutableStack<>().push(LocalDate.of(1970, 1, 3)));

    assertThat(index).isEmpty();
  }

  @Test
  void excludesEmptyHoursFromIndex() {
    List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(6, LocalDateTime.of(1970, 1, 5, 0, 0), 5);

    TimeUtils.setNow(LocalDateTime.of(1970, 1, 5, 1, 0).toInstant(ZoneOffset.UTC));

    diagnosisKeyBundler.setDiagnosisKeys(diagnosisKeys, LocalDateTime.of(1970, 1, 5, 1, 0));
    HourIndexingDecorator decorator = makeDecoratedHourDirectory(diagnosisKeyBundler);
    decorator.prepare(new ImmutableStack<>().push("DE").push(LocalDate.of(1970, 1, 5)));

    Set<LocalDateTime> index = decorator.getIndex(new ImmutableStack<>().push(LocalDate.of(1970, 1, 5)));

    assertThat(index).contains(LocalDateTime.of(1970, 1, 5, 0, 0))
        .doesNotContain(LocalDateTime.of(1970, 1, 5, 1, 0));
  }

  private HourIndexingDecorator makeDecoratedHourDirectory(DiagnosisKeyBundler diagnosisKeyBundler) {
    return new HourIndexingDecorator(
        new DiagnosisKeysHourDirectory(diagnosisKeyBundler, cryptoProvider, distributionServiceConfig),
        distributionServiceConfig);
  }
}
