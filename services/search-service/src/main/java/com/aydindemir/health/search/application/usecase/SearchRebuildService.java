package com.aydindemir.health.search.application.usecase;

import com.aydindemir.health.search.application.dto.SearchRebuildResult;
import com.aydindemir.health.search.application.dto.SearchRebuildRecord;
import com.aydindemir.health.search.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.search.application.exception.SearchRecoveryConflictException;
import com.aydindemir.health.search.application.port.in.SearchRebuildUseCase;
import com.aydindemir.health.search.application.port.out.SearchRebuildIndex;
import com.aydindemir.health.search.application.security.ActorContext;
import com.aydindemir.health.search.application.security.ApplicationRole;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SearchRebuildService implements SearchRebuildUseCase {
    private static final int MAX_BATCH_SIZE = 200;
    private final SearchRebuildIndex index;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();

    public SearchRebuildService(SearchRebuildIndex index) {
        this.index = Objects.requireNonNull(index);
    }

    @Override
    public synchronized SearchRebuildResult create(ActorContext actor, int schemaVersion) {
        requireAdministrator(actor);
        if (schemaVersion < 1 || schemaVersion > 9999) {
            throw new IllegalArgumentException("schemaVersion must be between 1 and 9999");
        }
        UUID runId = UUID.randomUUID();
        String predecessor = index.currentIndex();
        String candidate = "healthcare-operations-v" + schemaVersion + "-"
                + runId.toString().replace("-", "");
        index.createIndex(candidate);
        var run = new Run(runId, candidate, predecessor, Status.PREPARING);
        runs.put(runId, run);
        return result(run);
    }

    @Override
    public synchronized SearchRebuildResult ingest(
            ActorContext actor, UUID runId, List<SearchRebuildRecord> records) {
        requireAdministrator(actor);
        Run run = preparing(runId);
        List<SearchRebuildRecord> batch = List.copyOf(Objects.requireNonNull(records));
        if (batch.isEmpty() || batch.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("records must contain between 1 and 200 items");
        }
        batch.forEach(record -> index.save(
                run.candidateIndex, Objects.requireNonNull(record).toDomain()));
        return result(run);
    }

    @Override
    public synchronized SearchRebuildResult activate(
            ActorContext actor, UUID runId, long expectedDocumentCount) {
        requireAdministrator(actor);
        if (expectedDocumentCount < 0) {
            throw new IllegalArgumentException("expectedDocumentCount must not be negative");
        }
        Run run = preparing(runId);
        index.refresh(run.candidateIndex);
        long actual = index.count(run.candidateIndex);
        if (actual != expectedDocumentCount) {
            throw new SearchRecoveryConflictException(
                    "Candidate count " + actual + " does not match expected count " + expectedDocumentCount);
        }
        index.swapAlias(run.predecessorIndex, run.candidateIndex);
        run.status = Status.ACTIVE;
        return result(run);
    }

    @Override
    public synchronized SearchRebuildResult rollback(ActorContext actor, UUID runId) {
        requireAdministrator(actor);
        Run run = existing(runId);
        if (run.status != Status.ACTIVE) {
            throw new SearchRecoveryConflictException("Only an active rebuild can be rolled back");
        }
        index.swapAlias(run.candidateIndex, run.predecessorIndex);
        run.status = Status.ROLLED_BACK;
        return result(run);
    }

    private Run preparing(UUID runId) {
        Run run = existing(runId);
        if (run.status != Status.PREPARING) {
            throw new SearchRecoveryConflictException("Rebuild is not preparing: " + runId);
        }
        return run;
    }

    private Run existing(UUID runId) {
        Run run = runs.get(Objects.requireNonNull(runId));
        if (run == null) throw new IllegalArgumentException("Unknown rebuild run: " + runId);
        return run;
    }

    private void requireAdministrator(ActorContext actor) {
        if (actor == null || !actor.hasRole(ApplicationRole.SYSTEM_ADMIN)) {
            throw new ApplicationAccessDeniedException("Required role: SYSTEM_ADMIN");
        }
    }

    private SearchRebuildResult result(Run run) {
        return new SearchRebuildResult(
                run.runId, run.candidateIndex, run.predecessorIndex,
                run.status.name(), index.count(run.candidateIndex));
    }

    private enum Status { PREPARING, ACTIVE, ROLLED_BACK }

    private static final class Run {
        private final UUID runId;
        private final String candidateIndex;
        private final String predecessorIndex;
        private Status status;

        private Run(UUID runId, String candidateIndex, String predecessorIndex, Status status) {
            this.runId = runId;
            this.candidateIndex = candidateIndex;
            this.predecessorIndex = predecessorIndex;
            this.status = status;
        }
    }
}
