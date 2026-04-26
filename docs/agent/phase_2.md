# Phase 2: Transactional Apply, Audit, and Inverse Patches

## What Was Built

`PatchService` is the write-side entry point for agent mutations. It validates patch batches, gates destructive operations behind confirmation, applies patches inside a transaction, records audit rows, and supports undo through inverse patches.

Supporting pieces:

- `AuditEntity`: Room entity for applied patch batches, inverse patches, request IDs, timestamps, confirmation state, and undo metadata.
- `AuditDao`: audit insertion and recent/get-by-id reads.
- `InverseComputer`: computes inverse patches from pre-apply database state.
- `TransactionRunner`: abstraction over Room transactions so tests can inject a no-op runner.

## Database Changes

Version 9 to 10 adds the `audit_log` table. Additional DAO methods support moving workout days, deleting workout days, and renaming exercises.

## Test Approach

The current suite uses Mockito-based JVM unit tests to cover validation gates, DAO call ordering, inverse patch content, audit writing, and undo wiring.

Room integration tests should live under `androidTest` because full Room/database behavior is better validated on an emulator or device.

## Known Follow-Ups

- Mark undo audit rows with `isUndo = true`.
- Add pruning or retention rules for audit history.
- Cache pre-state reads for patch types that currently read the same entity more than once.
