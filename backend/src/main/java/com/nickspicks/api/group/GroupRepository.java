package com.nickspicks.api.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    /** (createdBy, count) pairs, for the admin member list's "created" column. */
    @org.springframework.data.jpa.repository.Query(
            "select g.createdBy, count(g) from Group g where g.createdBy is not null "
                    + "group by g.createdBy")
    List<Object[]> countByCreator();

    List<Group> findAllByOrderByNameAsc();

    /**
     * The account's own board, if it has been made yet. At most one exists -
     * see the partial unique index in V24.
     */
    java.util.Optional<Group> findByCreatedByAndPersonalTrue(UUID createdBy);

    /**
     * Group search. Private groups are unlisted, so this never returns one -
     * they are joined by an owner or admin adding you.
     *
     * <p>The term is never null: an empty one matches every public group, which
     * is what an empty search box should do. A nullable parameter would need an
     * {@code (:term is null or ...)} branch, and Postgres infers a null String
     * bind as {@code bytea}, which {@code lower()} has no overload for.
     */
    @Query("""
            select g from Group g
            where g.visibility = com.nickspicks.api.group.Visibility.PUBLIC
              and lower(g.name) like lower(concat('%', :term, '%'))
            order by g.name asc
            """)
    List<Group> searchPublic(@Param("term") String term);
}
