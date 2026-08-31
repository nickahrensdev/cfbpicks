package com.nickspicks.api.espn;

/**
 * The slice of ESPN's core API we surface.
 *
 * <p>ESPN publishes far more than this per entity, most of it behind {@code
 * $ref} links that each cost another round trip. These records hold only what
 * arrives inline on the one call we make, so a profile page never fans out.
 */
public final class EspnDtos {

    private EspnDtos() {
    }

    /**
     * A player as ESPN knows them.
     *
     * <p>Complements our own roster row rather than replacing it: the biography
     * (age, birthplace, class, headshot) is ESPN-only, while jersey and
     * position exist on both sides and are shown from whichever is present.
     */
    public record EspnAthlete(
            String id,
            String displayName,
            String shortName,
            String jersey,
            String position,
            String positionAbbreviation,
            String displayHeight,
            String displayWeight,
            Integer age,
            String dateOfBirth,
            String birthCity,
            String birthState,
            String birthCountry,
            /** ESPN's class label - "Junior", "Redshirt Freshman" and so on. */
            String experience,
            Integer experienceYears,
            String headshotUrl,
            String flagUrl,
            boolean active,
            String status,
            /** Their page on espn.com, for anything we do not mirror. */
            String espnUrl,
            /**
             * The team they are on, taken from the {@code $ref} URL ESPN
             * returns instead of a plain id. Null if the shape changes.
             */
            Integer teamId) {
    }

    /**
     * A program as ESPN knows them. Adds branding and venue detail to the
     * CollegeFootballData record we already hold.
     */
    public record EspnTeam(
            String id,
            String displayName,
            String shortDisplayName,
            String nickname,
            String location,
            String name,
            String abbreviation,
            String color,
            String alternateColor,
            String logoUrl,
            String darkLogoUrl,
            boolean active,
            String venueName,
            String venueCity,
            String venueState,
            boolean venueIndoor,
            boolean venueGrass,
            String venueImageUrl,
            /** Their clubhouse on espn.com. */
            String espnUrl) {
    }
}
