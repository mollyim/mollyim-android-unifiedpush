/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import org.signal.libsignal.zkgroup.backups.BackupAuthCredential
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse.StoredMediaObject
import org.whispersystems.signalservice.api.backup.BackupKey
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.InputStream

/**
 * Class to interact with various archive-related endpoints.
 * Why is it called archive instead of backup? Because SVR took the "backup" endpoint namespace first :)
 */
interface ArchiveApi {

  /**
   * Retrieves a set of credentials one can use to authorize other requests.
   *
   * You'll receive a set of credentials spanning 7 days. Cache them and store them for later use.
   * It's important that (at least in the common case) you do not request credentials on-the-fly.
   * Instead, request them in advance on a regular schedule. This is because the purpose of these
   * credentials is to keep the caller anonymous, but that doesn't help if this authenticated request
   * happens right before all of the unauthenticated ones, as that would make it easier to correlate
   * traffic.
   */
  fun getServiceCredentials(currentTime: Long): NetworkResult<ArchiveServiceCredentialsResponse>

  fun getCdnReadCredentials(cdnNumber: Int, backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<GetArchiveCdnCredentialsResponse>

  /**
   * Ensures that you reserve a backupId on the service. This must be done before any other
   * backup-related calls. You only need to do it once, but repeated calls are safe.
   */
  fun triggerBackupIdReservation(backupKey: BackupKey): NetworkResult<Unit>

  /**
   * Sets a public key on the service derived from your [BackupKey]. This key is used to prevent
   * unauthorized  users from changing your backup data. You only need to do it once, but repeated
   * calls are safe.
   */
  fun setPublicKey(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<Unit>

  /**
   * Fetches an upload form you can use to upload your main message backup file to cloud storage.
   */
  fun getMessageBackupUploadForm(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<AttachmentUploadForm>

  /**
   * Fetches metadata about your current backup.
   * Will return a [NetworkResult.StatusCodeError] with status code 404 if you haven't uploaded a
   * backup yet.
   */
  fun getBackupInfo(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<ArchiveGetBackupInfoResponse>

  /**
   * Lists the media objects in the backup
   */
  fun listMediaObjects(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential, limit: Int, cursor: String? = null): NetworkResult<ArchiveGetMediaItemsResponse>

  /**
   * Retrieves a resumable upload URL you can use to upload your main message backup file or an arbitrary media file to cloud storage.
   */
  fun getBackupResumableUploadUrl(uploadForm: AttachmentUploadForm): NetworkResult<String>

  /**
   * Uploads your main backup file to cloud storage.
   */
  fun uploadBackupFile(uploadForm: AttachmentUploadForm, resumableUploadUrl: String, data: InputStream, dataLength: Long): NetworkResult<Unit>

  /**
   * Retrieves an [AttachmentUploadForm] that can be used to upload pre-existing media to the archive.
   * After uploading, the media still needs to be copied via [archiveAttachmentMedia].
   */
  fun getMediaUploadForm(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<AttachmentUploadForm>

  fun getResumableUploadSpec(uploadForm: AttachmentUploadForm, secretKey: ByteArray?): NetworkResult<ResumableUploadSpec>

  /**
   * Retrieves all media items in the user's archive. Note that this could be a very large number of items, making this only suitable for debugging.
   * Use [getArchiveMediaItemsPage] in production.
   */
  fun debugGetUploadedMediaItemMetadata(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<List<StoredMediaObject>>
  /**
   * Retrieves a page of media items in the user's archive.
   * @param limit The maximum number of items to return.
   * @param cursor A token that can be read from your previous response, telling the server where to start the next page.
   */
  fun getArchiveMediaItemsPage(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential, limit: Int, cursor: String?): NetworkResult<ArchiveGetMediaItemsResponse>

  /**
   * Copy and re-encrypt media from the attachments cdn into the backup cdn.
   *
   * Possible errors:
   *   400: Bad arguments, or made on an authenticated channel
   *   401: Invalid presentation or signature
   *   403: Insufficient permissions
   *   413: No media space remaining
   *   429: Rate-limited
   */
  fun archiveAttachmentMedia(
    backupKey: BackupKey,
    serviceCredential: ArchiveServiceCredential,
    item: ArchiveMediaRequest
  ): NetworkResult<ArchiveMediaResponse>

  /**
   * Copy and re-encrypt media from the attachments cdn into the backup cdn.
   */
  fun archiveAttachmentMedia(
    backupKey: BackupKey,
    serviceCredential: ArchiveServiceCredential,
    items: List<ArchiveMediaRequest>
  ): NetworkResult<BatchArchiveMediaResponse>

  /**
   * Delete media from the backup cdn.
   */
  fun deleteArchivedMedia(
    backupKey: BackupKey,
    serviceCredential: ArchiveServiceCredential,
    mediaToDelete: List<DeleteArchivedMediaRequest.ArchivedMediaObject>
  ): NetworkResult<Unit>

  fun getZkCredential(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): BackupAuthCredential
}
