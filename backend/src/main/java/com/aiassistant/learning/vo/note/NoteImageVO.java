package com.aiassistant.learning.vo.note;

/** Authenticated image URL; never contains a user ID, filesystem path or token. */
public record NoteImageVO(String url, int width, int height) {}
