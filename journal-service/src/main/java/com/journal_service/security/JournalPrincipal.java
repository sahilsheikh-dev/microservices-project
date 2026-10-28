package com.journal_service.security;

public record JournalPrincipal(Long userId, String email) {
}
