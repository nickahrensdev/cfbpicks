package com.nickspicks.api.supabase;

import com.nickspicks.api.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Optional second door into Supabase: the PostgREST / Auth / Storage HTTP API.
 * Day-to-day data access goes through JPA over the pooled JDBC connection;
 * this client is for the things SQL cannot do (auth admin, storage, RPC).
 *
 * Only created when app.supabase.url is set, so local runs without Supabase
 * credentials still start cleanly.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.supabase", name = "url")
public class SupabaseRestClientConfig {

    @Bean
    public RestClient supabaseRestClient(AppProperties properties) {
        AppProperties.Supabase supabase = properties.getSupabase();
        String key = supabase.getServiceKey().isBlank()
                ? supabase.getAnonKey()
                : supabase.getServiceKey();

        return RestClient.builder()
                .baseUrl(supabase.getUrl())
                .defaultHeader("apikey", key)
                .defaultHeader("Authorization", "Bearer " + key)
                .build();
    }
}
