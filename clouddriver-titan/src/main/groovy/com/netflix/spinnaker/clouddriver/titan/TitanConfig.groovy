/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titan.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titan.credentials.NetflixTitanCredentials
import com.netflix.titanclient.RegionScopedTitanClient
import com.netflix.titanclient.TitanRegion
import com.netflix.titanclient.model.TitanClientObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
@ConditionalOnProperty('titan.enabled')
@EnableConfigurationProperties
@ComponentScan(['com.netflix.spinnaker.kato.titan', 'com.netflix.spinnaker.oort.titan'])
class TitanConfig {

  @Bean
  ObjectMapper objectMapper() {
    new TitanClientObjectMapper()
  }

  @Bean
  @ConfigurationProperties("titan")
  TitanCredentialsConfig titanCredentialsConfig() {
    new TitanCredentialsConfig()
  }

  @Bean
  List<NetflixTitanCredentials> netflixTitanCredentials(TitanCredentialsConfig titanCredentialsConfig, AccountCredentialsRepository repository) {
    List<NetflixTitanCredentials> accounts = []
    for (TitanCredentialsConfig.Account account in titanCredentialsConfig.accounts) {
      List<TitanRegion> regions = account.regions.collect { new TitanRegion(it.name, account.name, it.endpoint) }
      def credentials = new NetflixTitanCredentials(account.name, regions)
      accounts.add(credentials)
      repository.save(account.name, credentials)
    }
    accounts
  }

  @Bean
  @DependsOn("netflixTitanCredentials")
  TitanClientProvider titanClientProvider(List<NetflixTitanCredentials> credentialsList, ObjectMapper objectMapper) {
    List<TitanClientProvider.TitanClientHolder> titanClientHolders = []
    credentialsList.each { credentials ->
      credentials.regions.each { region ->
        titanClientHolders << new TitanClientProvider.TitanClientHolder(credentials.name, region.name, new RegionScopedTitanClient(region, objectMapper))
      }
    }
    new TitanClientProvider(titanClientHolders)
  }

  static class TitanCredentialsConfig {
    List<Account> accounts

    static class Account {
      String name
      List<Region> regions
    }

    static class Region {
      String name
      String endpoint
    }
  }
}
