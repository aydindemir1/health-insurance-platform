package com.aydindemir.health.search.application.port.in;

import com.aydindemir.health.search.application.dto.SearchRebuildResult;
import com.aydindemir.health.search.application.dto.SearchRebuildRecord;
import com.aydindemir.health.search.application.security.ActorContext;

import java.util.List;
import java.util.UUID;

public interface SearchRebuildUseCase {
    SearchRebuildResult create(ActorContext actor, int schemaVersion);
    SearchRebuildResult ingest(ActorContext actor, UUID runId, List<SearchRebuildRecord> records);
    SearchRebuildResult activate(ActorContext actor, UUID runId, long expectedDocumentCount);
    SearchRebuildResult rollback(ActorContext actor, UUID runId);
}
