package com.nickspicks.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Profile-aware application settings. Values are supplied by
 * application.yml and overridden per profile by application-local.yml
 * and application-prod.yml.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Human readable environment label, surfaced by /api/meta. */
    private String environment = "unknown";

    /** Origins allowed to call the API from a browser. */
    private List<String> allowedOrigins = List.of();

    /**
     * Emails promoted to ADMIN automatically on sign-in. Bootstrap only - the
     * admin page can promote or demote anyone afterwards, but the first admin
     * has to come from somewhere.
     */
    private List<String> adminEmails = List.of();

    /** Supabase project settings. */
    private Supabase supabase = new Supabase();

    /** Pick'em rules. */
    private Pickem pickem = new Pickem();

    /** CollegeFootballData API settings. */
    private Cfbd cfbd = new Cfbd();

    /** Shared-secret auth for the externally-triggered (Supabase pg_cron) endpoints. */
    private Cron cron = new Cron();

    public Cron getCron() {
        return cron;
    }

    public void setCron(Cron cron) {
        this.cron = cron;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(List<String> adminEmails) {
        this.adminEmails = adminEmails;
    }

    public Supabase getSupabase() {
        return supabase;
    }

    public void setSupabase(Supabase supabase) {
        this.supabase = supabase;
    }

    public Pickem getPickem() {
        return pickem;
    }

    public void setPickem(Pickem pickem) {
        this.pickem = pickem;
    }

    public Cfbd getCfbd() {
        return cfbd;
    }

    public void setCfbd(Cfbd cfbd) {
        this.cfbd = cfbd;
    }

    public static class Supabase {

        /**
         * https://<project-ref>.supabase.co. Drives the JWT issuer check in
         * SecurityConfig and the "is Supabase configured" flag on /api/meta.
         */
        private String url = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Pickem {

        /** Season the site is currently running. */
        private int season = 2026;

        /** Maximum picks a member may have in a single week. */
        private int maxPicksPerWeek = 10;

        /** Picks close this many minutes before kickoff. */
        private int lockLeadMinutes = 30;

        public int getSeason() {
            return season;
        }

        public void setSeason(int season) {
            this.season = season;
        }

        public int getMaxPicksPerWeek() {
            return maxPicksPerWeek;
        }

        public void setMaxPicksPerWeek(int maxPicksPerWeek) {
            this.maxPicksPerWeek = maxPicksPerWeek;
        }

        public int getLockLeadMinutes() {
            return lockLeadMinutes;
        }

        public void setLockLeadMinutes(int lockLeadMinutes) {
            this.lockLeadMinutes = lockLeadMinutes;
        }
    }

    public static class Cfbd {

        private String baseUrl = "https://api.collegefootballdata.com";

        /** Free tier key from collegefootballdata.com/key. */
        private String apiKey = "";

        /**
         * Connect and read timeout for every CFBD call, in seconds. Generous
         * on purpose - see {@code CfbdClient}. 0 or less falls back to the
         * client's own default.
         */
        private int timeoutSeconds = 90;

        /** Which games are ingested for picking. Members pick FBS games. */
        private String classification = "fbs";

        /**
         * Which programs are stored as reference data. /teams returns all 684
         * across every division in a single call, so including FCS costs
         * nothing extra and makes non-FBS opponents clickable instead of
         * plain text. Add "ii" and "iii" to go further.
         */
        private List<String> teamClassifications = List.of("fbs", "fcs");

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getClassification() {
            return classification;
        }

        public void setClassification(String classification) {
            this.classification = classification;
        }

        public List<String> getTeamClassifications() {
            return teamClassifications;
        }

        public void setTeamClassifications(List<String> teamClassifications) {
            this.teamClassifications = teamClassifications;
        }
    }

    public static class Cron {

        /**
         * Compared against the caller's X-Cron-Secret header. Blank means
         * "reject everything" - an unset secret must never accidentally
         * match an empty header, which is why the check in CronController
         * rejects a blank configured value before ever comparing.
         */
        private String secret = "";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

    }
}
