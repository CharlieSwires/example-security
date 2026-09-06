package com.example.security.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/** Invalidates MongoDB-backed Spring Sessions belonging to a principal. */
@Service
public class UserSessionService {
    private static final Logger log = LoggerFactory.getLogger(UserSessionService.class);

    private final MongoOperations mongoOperations;
    private final String sessionCollection;

    public UserSessionService(MongoOperations mongoOperations,
                              @Value("${spring.session.mongodb.collection-name:spring_sessions}")
                              String sessionCollection) {
        this.mongoOperations = mongoOperations;
        this.sessionCollection = sessionCollection;
    }

    public void invalidateAllForUser(String username) {
        if (username == null || username.isBlank()) return;
        // Spring Session Data MongoDB materialises the principal-name index
        // in the top-level "principal" field of each session document.
        Query query = Query.query(Criteria.where("principal").is(username));
        long deleted = mongoOperations.remove(query, sessionCollection).getDeletedCount();
        log.info("Invalidated {} session(s) for user {}", deleted, username);
    }
}
