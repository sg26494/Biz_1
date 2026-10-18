package com.store.notification;

/** Returns the provider message id (may be null). Throws on failure so the outbox retries. */
public interface EmailSender {

    String send(String to, String subject, String html) throws Exception;
}
