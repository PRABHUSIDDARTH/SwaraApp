package com.psthetech.swara.ui.metadata;

import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.SongMetadata;

/**
 * Result model for song metadata persistence operations.
 */
public class SaveMetadataResult {

    public enum Status {
        SUCCESS,
        PARTIAL_SUCCESS,
        SYSTEM_PROMPT_REQUIRED,
        UNSUPPORTED_FORMAT,
        FILE_NOT_FOUND,
        FAILED
    }

    private final Status status;
    @Nullable private final IntentSenderRequest intentSenderRequest;
    @Nullable private final String message;
    @Nullable private final SongMetadata updatedMetadata;

    private SaveMetadataResult(Status status,
                               @Nullable IntentSenderRequest request,
                               @Nullable String message,
                               @Nullable SongMetadata updatedMetadata) {
        this.status = status;
        this.intentSenderRequest = request;
        this.message = message;
        this.updatedMetadata = updatedMetadata;
    }

    public static SaveMetadataResult success(@NonNull SongMetadata metadata) {
        return new SaveMetadataResult(Status.SUCCESS, null, null, metadata);
    }

    public static SaveMetadataResult partialSuccess(@NonNull SongMetadata metadata, @NonNull String note) {
        return new SaveMetadataResult(Status.PARTIAL_SUCCESS, null, note, metadata);
    }

    public static SaveMetadataResult systemPromptRequired(@NonNull IntentSenderRequest request) {
        return new SaveMetadataResult(Status.SYSTEM_PROMPT_REQUIRED, request, null, null);
    }

    public static SaveMetadataResult unsupportedFormat(@NonNull String message) {
        return new SaveMetadataResult(Status.UNSUPPORTED_FORMAT, null, message, null);
    }

    public static SaveMetadataResult fileNotFound(@NonNull String message) {
        return new SaveMetadataResult(Status.FILE_NOT_FOUND, null, message, null);
    }

    public static SaveMetadataResult failed(@NonNull String message) {
        return new SaveMetadataResult(Status.FAILED, null, message, null);
    }

    public Status getStatus() { return status; }
    @Nullable public IntentSenderRequest getIntentSenderRequest() { return intentSenderRequest; }
    @Nullable public String getMessage() { return message; }
    @Nullable public SongMetadata getUpdatedMetadata() { return updatedMetadata; }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.PARTIAL_SUCCESS;
    }
}
