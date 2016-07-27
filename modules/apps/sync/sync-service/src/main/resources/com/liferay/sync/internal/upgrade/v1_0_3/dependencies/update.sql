alter table SyncDevice add hostname VARCHAR(75) null;

alter table SyncDLObject add lanTokenKey VARCHAR(75) null;

COMMIT_TRANSACTION;