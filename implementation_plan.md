# Detailed Implementation Plan: Syncing `feature/NCLS-1476-v2` with `master` & PR #361

This document provides an exhaustive, step-by-step breakdown of the synchronization between `feature/NCLS-1476-v2` and `origin/master`, as well as the resolution of conflicts with PR #361 (`origin/feature/CB-1485`).

---

## 1. Executive Summary & Strategy

- **Part 1: Syncing with `origin/master`**:
  - `origin/master` contains 2 recent commits (`CB-1832`, fixes timestamp parsing in `AbsIntegrationLayerWalletCreateResponseDto`).
  - Merging `origin/master` into `feature/NCLS-1476-v2` produces **0 conflicts** (100% clean merge).
  - **Action**: Execute `git merge origin/master` as the first step.

- **Part 2: Merging PR #361 (`origin/feature/CB-1485`)**:
  - PR #361 adds pending hold tracking (`pending_holds` DB table), account migration window guards (retryable 500 errors for migrating accounts), replay probes on transaction retries (`POST /api/v1/transactions/replay`), and orphan hold resolution.
  - Merging PR #361 into `feature/NCLS-1476-v2` results in **5 conflicting files**.
  - **Action**: Perform `git merge origin/feature/CB-1485` and resolve conflicts file-by-file following the detailed resolution patterns below.

---

## 2. File-by-File Conflict Analysis & Resolution Code

### File 1: `AccountMappingRepository.java`
**Location**: `src/main/java/com/m10/converter/service/domain/account/repository/AccountMappingRepository.java`

- **Conflict Cause**: Both branches add new Spring Data repository methods and import statements near line 1-16.
- **Branch `NCLS-1476-v2` added**:
  ```java
  List<AccountMapping> findByNanoIdIn(Collection<String> nanoIds);
  ```
- **PR #361 (`CB-1485`) added**:
  ```java
  boolean existsByNanoIdInAndMigrationStatusNotNullAndUpdatedAfter(Collection<String> nanoIds, Instant updatedAfter);
  Optional<AccountMapping> findFirstByNanoIdInAndMigrationStatusIn(Collection<String> nanoIds, Collection<MigrationStatus> statuses);
  ```
- **Resolution**:
  Combine all imports (`Instant`, `Collection`, `MigrationStatus`) and include all three new methods:
  ```java
  package com.m10.converter.service.domain.account.repository;

  import java.time.Instant;
  import java.util.Collection;
  import java.util.List;
  import java.util.Optional;
  import java.util.UUID;
  import org.springframework.data.relational.core.sql.LockMode;
  import org.springframework.data.relational.core.sql.LockMode.Mode;
  import org.springframework.data.repository.CrudRepository;
  import org.springframework.stereotype.Repository;

  import com.m10.converter.service.domain.account.model.AccountMapping;
  import com.m10.converter.service.domain.account.model.MigrationStatus;

  @Repository
  public interface AccountMappingRepository extends CrudRepository<AccountMapping, UUID> {

      Optional<AccountMapping> findByNanoId(String nanoId);

      /** Looks up mappings for a collection of accounts in a single query. Absence of a row means not migrated. */
      List<AccountMapping> findByNanoIdIn(Collection<String> nanoIds);

      boolean existsByNanoIdInAndMigrationStatusNotNullAndUpdatedAfter(Collection<String> nanoIds, Instant updatedAfter);

      Optional<AccountMapping> findFirstByNanoIdInAndMigrationStatusIn(Collection<String> nanoIds, Collection<MigrationStatus> statuses);

      @Lock(LockMode.PESSIMISTIC_WRITE)
      Optional<AccountMapping> findWithLockByNanoId(String nanoId);
  }
  ```

---

### File 2: `TransactionController.java` (Architectural Resolution)
**Location**: `src/main/java/com/m10/converter/service/domain/rest/TransactionController.java`

- **Conflict Cause**:
  - `NCLS-1476-v2` refactored `postTransaction` to delegate to `TransactionRoutingService.postTransaction(request)`.
  - PR #361 added `PendingHoldsChecker` logic (pending hold creation, migration status checks, replay logic, hold resolution) directly inside `TransactionController.postTransaction` and `rollbackTransaction`.

- **Resolution Strategy**:
  1. `TransactionController` will keep `PendingHoldsChecker` for `rollbackTransaction` and pass `PendingHoldsChecker` into `TransactionRoutingService` (or invoke `PendingHoldsChecker` guard logic prior to routing/within routing).
  2. In `TransactionController`:
     ```java
     @PostMapping
     public TransactionResponse postTransaction(@Valid @RequestBody RestDto<TransactionRequest> request) {
         return transactionRoutingService.postTransaction(request);
     }

     @PostMapping("/rollback")
     public TransactionResponse rollbackTransaction(@RequestBody RestDto<RollbackTransactionRequest> request) {
         pendingHoldsChecker.checkHoldNotTooOld(request.message().externalOperationId());

         RestDto<com.m10.rabbit.dto.accounting.request.RollbackTransactionRequest> accountingRequest =
             new RestDto<>(request.messageId(), request.additionalData(), restDtoMapper.map(request.message()));
         com.m10.rabbit.dto.accounting.response.TransactionResponse transactionResponse =
             accountingService.rollbackTransaction(accountingRequest);

         pendingHoldsChecker.markResolved(request.message().externalOperationId());
         return restDtoMapper.map(transactionResponse);
     }
     ```
  3. In `AccountingPaymentExecutor` / `TransactionRoutingServiceImpl`:
     Integrate the PR #361 checks:
     - For `PROGRESS` mode: `pendingHoldsChecker.addPendingHold(...)`.
     - For `COMMIT` mode: `pendingHoldsChecker.checkNotMigrating(...)`, `pendingHoldsChecker.anyRecentlyAffectedByMigration(...)` -> `accountingService.replayTransaction(...)`, `pendingHoldsChecker.checkHoldNotTooOld(...)`.
     - Upon posting to accounting: catch `LogicExecutorException` and run `pendingHoldsChecker.checkNotBlockedByMigration(...)` if `ACCOUNT_BLOCKED`.
     - Upon `COMMIT` success: `pendingHoldsChecker.markResolved(...)`.

---

### File 3: `application.yml`
**Location**: `src/main/resources/application.yml`

- **Conflict Cause**: Both branches add configuration properties under the `converter:` block.
- **Resolution**: Combine both properties sections under `converter:`:
  ```yaml
  converter:
    idempotence:
      key-ttl: PT30M
      response-timeout: ${CONVERTER_IDEMPOTENCE_RESPONSE_TIMEOUT:PT6S}
    pending-holds:
      active-window: ${CONVERTER_PENDING_HOLDS_ACTIVE_WINDOW:P2D}
      max-age: ${CONVERTER_PENDING_HOLDS_MAX_AGE:P1M}
      replay-window: ${CONVERTER_PENDING_HOLDS_REPLAY_WINDOW:P7D}
  ```
  And retain `- path: '/api/v1/topup/**'` in logbook.

---

### File 4 & 5: Contract Tests
**Locations**:
- `src/test/java/com/m10/converter/service/domain/rest/ConverterTariffsWireContractTest.java`
- `src/test/java/com/m10/converter/service/domain/rest/TransactionControllerContractTest.java`

- **Conflict Cause**:
  - `NCLS-1476-v2` updated controller tests to mock `TransactionRoutingService`.
  - PR #361 added extensive tests verifying pending hold behavior, replay logic, and HTTP status codes (`ACCOUNT_BLOCKED` vs `INTERNAL_ERROR` 500 remap).
- **Resolution**:
  Combine test methods from PR #361 into `TransactionControllerContractTest.java` and `TransactionRoutingServiceImplTest.java`, ensuring all new scenarios (replay hit, replay miss, migration 500 retry, orphan hold resolution) pass against `TransactionRoutingService`.

---

## 3. Step-by-Step Execution Workflow

1. **Step 1: Merge `origin/master`**
   ```bash
   git merge origin/master -m "Merge branch 'master' into feature/NCLS-1476-v2"
   ```
   *Expected Result*: Clean merge, 0 conflicts.

2. **Step 2: Initiate Merge of PR #361 (`origin/feature/CB-1485`)**
   ```bash
   git merge origin/feature/CB-1485
   ```
   *Expected Result*: Git notifies about conflicts in 5 files.

3. **Step 3: Resolve Conflicts File-by-File**
   - Resolve `AccountMappingRepository.java` (combine imports & methods).
   - Resolve `application.yml` (merge configuration sections).
   - Integrate PR #361 `PendingHoldsChecker` flow into `TransactionController.java` & `TransactionRoutingServiceImpl.java`.
   - Update contract test mocks and include all new PR #361 test cases.

4. **Step 4: Compilation & Test Suite Verification**
   ```bash
   mvn test-compile
   mvn test
   ```

5. **Step 5: Review & Final Confirmation**
   - Perform `git diff` review to ensure no unintentional code loss.
   - Confirm successful build status to the user.
