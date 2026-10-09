package com.example.slopproof.posts;

import java.time.Instant;

/** A short public text post, as the rules and the repository see it. */
public record Post(PostId id, String title, String body, Instant createdAt) {}
