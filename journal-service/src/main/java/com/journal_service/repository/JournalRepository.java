package com.journal_service.repository;

import com.journal_service.entity.Journal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalRepository extends MongoRepository<Journal, String> {
    List<Journal> findByUserId(String userId);
}
