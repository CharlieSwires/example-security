package com.example.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;

/**
 * Stores Spring Security HTTP sessions in MongoDB / MongoDB Atlas.
 *
 * With this enabled, duplicate backend containers can sit behind a load balancer
 * without sticky sessions because the JSESSIONID points to a shared MongoDB
 * session document rather than memory inside one JVM.
 */
@Configuration
@EnableMongoHttpSession(collectionName = "${spring.session.mongodb.collection-name:spring_sessions}")
public class MongoHttpSessionConfig {
}
