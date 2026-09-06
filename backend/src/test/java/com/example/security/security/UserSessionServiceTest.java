package com.example.security.security;

import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSessionServiceTest {

    @Test
    void deletesSpringSessionsByTheirMaterialisedPrincipalField() {
        MongoOperations mongo = mock(MongoOperations.class);
        when(mongo.remove(org.mockito.ArgumentMatchers.any(Query.class), eq("spring_sessions")))
                .thenReturn(DeleteResult.acknowledged(2));

        new UserSessionService(mongo, "spring_sessions").invalidateAllForUser("alice");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq("spring_sessions"));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("principal", "alice"));
    }
}
