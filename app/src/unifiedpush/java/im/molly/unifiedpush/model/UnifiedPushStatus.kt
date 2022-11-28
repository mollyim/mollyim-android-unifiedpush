package im.molly.unifiedpush.model

enum class UnifiedPushStatus {
  DISABLED,
  AIR_GAPED,
  SERVER_NOT_FOUND_AT_URL,
  MISSING_ENDPOINT,
  FORBIDDEN_UUID,
  FORBIDDEN_ENDPOINT,
  NO_DISTRIBUTOR,
  PENDING,
  LINK_DEVICE_ERROR,
  OK,
  INTERNAL_ERROR,
  UNKNOWN,
}